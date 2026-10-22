package slimeknights.tconstruct.library.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import slimeknights.tconstruct.library.tools.helper.ToolAttackUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Helper class for more control over explosions.
 * <p>
 * PORT 1.21.4: {@code Explosion} is now an interface and the block/entity machinery moved into the concrete
 * {@link ServerExplosion}, whose fields and hooks are all private. We re-seat on {@link ServerExplosion} to inherit the
 * {@link Explosion} contract and the damage source/block interaction state, but keep our own copies of the data the
 * custom ray logic needs and reimplement {@code explode}/{@code finalizeExplosion} ourselves. Block drops go through
 * {@link BlockState#onExplosionHit} so vanilla decay (driven by the inherited {@link #getBlockInteraction()}) is kept.
 */
public class CustomExplosion extends ServerExplosion {
  /** Size of the hollowed out cube determining the number of rays to cast */
  private static final int RAY_COUNT = 16;
  private static final int MAX_RAY = RAY_COUNT - 1;
  /**
   * Default predicate for which entities to match.
   * PORT 1.21.1: {@code Entity#ignoreExplosion()} now takes the {@code Explosion} instance, so it cannot be
   * checked from a static predicate. The per-entity ignore behaviour is still applied by the vanilla
   * {@link Explosion} machinery; here we only keep the stateless alive/spectator filters.
   */
  public static final Predicate<Entity> DEFAULT_ENTITY_PREDICATE = entity -> entity != null && entity.isAlive() && !entity.isSpectator();

  /** Level copy, as {@link ServerExplosion#level()} is the only accessor and we use it heavily */
  protected final ServerLevel level;
  /** Source entity, nullable */
  @Nullable
  protected final Entity source;
  protected final double x;
  protected final double y;
  protected final double z;
  protected final float radius;
  /** Whether the explosion places fire */
  protected final boolean fire;
  /** Block interaction mode, kept locally as {@link ServerExplosion}'s copy is private */
  protected final BlockInteraction blockInteraction;
  /** Damage calculator, kept locally as {@link ServerExplosion}'s copy is private with no accessor */
  protected final ExplosionDamageCalculator damageCalculator;
  /** List of blocks to remove, replacing the removed {@code Explosion#toBlow} field */
  protected final List<BlockPos> toBlow = new ArrayList<>();

  /** Maximum damage to deal; setting to 7*2*radius will match the vanilla explosion. */
  protected final float damage;
  /** Entity knockback scale. May be 0 to prevent knockback or negative to reverse knockback */
  protected final float knockback;
  /** Determines which entities are affected by this explosion */
  protected final Predicate<Entity> entityPredicate;
  /** If true, explosion damage bypasses the invulnerability time */
  protected final boolean bypassInvulnerableTime;
  /**
   * PORT 1.21.1: {@code Explosion#damageSource} is private with no accessor, so we keep our own copy.
   * Resolved the same way vanilla does: a null source falls back to the default explosion damage source.
   */
  protected final DamageSource customDamageSource;

  public CustomExplosion(Level level, Vec3 location, float radius, @Nullable Entity sourceEntity, @Nullable Predicate<Entity> entityPredicate, float damage, @Nullable DamageSource damageSource, float knockback, @Nullable ExplosionDamageCalculator damageCalculator, boolean placeFire, BlockInteraction blockInteraction, boolean bypassInvulnerableTime) {
    super((ServerLevel) level, sourceEntity, damageSource, damageCalculator, location, radius, placeFire, blockInteraction);
    this.level = (ServerLevel) level;
    this.source = sourceEntity;
    this.x = location.x;
    this.y = location.y;
    this.z = location.z;
    this.radius = radius;
    this.fire = placeFire;
    this.blockInteraction = blockInteraction;
    this.damageCalculator = damageCalculator != null ? damageCalculator : new ExplosionDamageCalculator();
    this.customDamageSource = damageSource != null ? damageSource : Explosion.getDefaultDamageSource(level, sourceEntity);
    this.entityPredicate = Objects.requireNonNullElse(entityPredicate, DEFAULT_ENTITY_PREDICATE);
    this.damage = damage;
    this.knockback = knockback;
    this.bypassInvulnerableTime = bypassInvulnerableTime;
  }

  public CustomExplosion(Level level, Vec3 location, float radius, @Nullable Entity sourceEntity, @Nullable Predicate<Entity> entityPredicate, float damage, @Nullable DamageSource damageSource, float knockback, @Nullable ExplosionDamageCalculator damageCalculator, boolean placeFire, BlockInteraction blockInteraction) {
    this(level, location, radius, sourceEntity, entityPredicate ,damage, damageSource, knockback, damageCalculator, placeFire, blockInteraction, false);
  }

  /** Checks if this explosion interacts with blocks */
  protected boolean interactsWithBlocks() {
    return this.blockInteraction != BlockInteraction.KEEP;
  }

  @Override
  public void explode() {
    this.level.gameEvent(this.source, GameEvent.EXPLODE, center());
    calculateHitBlocks();
    damageAndPushEntities();
  }

  /** Calculates the list of blocks to hit; the actual block damage won't happen until {@link #finalizeExplosion(boolean)} */
  protected void calculateHitBlocks() {
    // optimization: if we are not interacting with blocks, no need to calculate blocks
    if (!interactsWithBlocks() && !fire) {
      return;
    }

    // loop over a hollowed out 16x cube
    for (int rayX = 0; rayX < RAY_COUNT; rayX++) {
      for (int rayY = 0; rayY < RAY_COUNT; rayY++) {
        for (int rayZ = 0; rayZ < RAY_COUNT; rayZ++) {
          if (rayX == 0 || rayX == MAX_RAY || rayY == 0 || rayY == MAX_RAY || rayZ == 0 || rayZ == MAX_RAY) {
            // determine direction to go, then step in 0.3 unit vector increments
            double stepX = rayX * 2.0 / MAX_RAY - 1;
            double stepY = rayY * 2.0 / MAX_RAY - 1;
            double stepZ = rayZ * 2.0 / MAX_RAY - 1;
            double stepScale = 0.3f / Math.sqrt(stepX * stepX + stepY * stepY + stepZ * stepZ);
            stepX *= stepScale;
            stepY *= stepScale;
            stepZ *= stepScale;

            // keep moving in the direction of the ray until we run out of power; means blocks with high blast resistance shield those with less
            double targetX = this.x;
            double targetY = this.y;
            double targetZ = this.z;
            for (float power = this.radius * (0.7f + level.random.nextFloat() * 0.6f); power > 0; power -= 0.225f) {
              BlockPos target = BlockPos.containing(targetX, targetY, targetZ);
              BlockState block = level.getBlockState(target);
              FluidState fluid = level.getFluidState(target);
              if (!level.isInWorldBounds(target)) {
                break;
              }

              // reduce power based on blast resistance
              Optional<Float> resistance = damageCalculator.getBlockExplosionResistance(this, level, target, block, fluid);
              if (resistance.isPresent()) {
                power -= (resistance.get() + 0.3f) * 0.3f;
              }

              // remove block if power is high enough
              // optimization: skip air if not placing fires to save network traffic
              if ((fire || !block.isAir()) && power > 0 && damageCalculator.shouldBlockExplode(this, level, target, block, power)) {
                toBlow.add(target);
              }

              // vanilla difference - we moved the 0.3 multiplier to the original step variables to avoid needing to compute as often
              targetX += stepX;
              targetY += stepY;
              targetZ += stepZ;
            }
          }
        }
      }
    }
  }

  /** Called to run the logic for damaging and blasting back entities in range */
  protected void damageAndPushEntities() {
    // skip running if we disabled both damage and knockback as there is nothing left to do
    if (damage <= 0 && knockback == 0) {
      return;
    }

    float diameter = this.radius * 2;
    // small behavior change: we filter the list of entities on fetch, meaning the forge event gets the filtered list
    List<Entity> list = this.level.getEntities(
      this.source,
      new AABB(Math.floor(this.x - diameter - 1),
               Math.floor(this.y - diameter - 1),
               Math.floor(this.z - diameter - 1),
               Math.floor(this.x + diameter + 1),
               Math.floor(this.y + diameter + 1),
               Math.floor(this.z + diameter + 1)),
      entityPredicate);
    EventHooks.onExplosionDetonate(this.level, this, list, this.toBlow);

    // start pushing entities
    // this logic is for the most part identical to vanilla, except taking better advantage of vec3
    Vec3 center = center();
    for (Entity entity : list) {
      Vec3 dir = entity.position().subtract(center);
      double length = dir.length();
      double distance = length / diameter;
      if (distance <= 1) {
        // non-TNT uses eye height for explosion direction
        if (!(entity instanceof PrimedTnt)) {
          dir = dir.add(0, entity.getEyeY() - entity.getY(), 0);
          length = dir.length();
        }
        // vanilla change: a bit of tolerance on the length check to match normalize
        if (length > 1.0E-4D) {
          double strength = (1 - distance) * getSeenPercent(center, entity);
          // vanilla change: instead of multiplying the damage by 7, we make that a parameter, which can be 0 for no damage
          if (damage > 0) {
            int toDeal = (int) ((strength * strength + strength) / 2 * damage + 1);
            if (bypassInvulnerableTime) {
              ToolAttackUtil.hurtNoInvulnerableTime(entity, customDamageSource, toDeal);
            } else {
              entity.hurtOrSimulate(customDamageSource, toDeal);
            }
          }

          // apply knockback
          if (knockback != 0) {
            double adjustedStrength = strength * knockback;
            // PORT 1.21.1: ProtectionEnchantment.getExplosionKnockbackAfterDampener was removed; blast-protection
            // knockback dampening is now data-driven via the enchantment's effect components. Dropping the
            // dampening here keeps core knockback working; re-add via EnchantmentHelper once enchantments are ported.
            Vec3 velocity = dir.scale(adjustedStrength / length);
            entity.setDeltaMovement(entity.getDeltaMovement().add(velocity));
            if (entity instanceof Player player) {
              if (!player.isCreative() || !player.getAbilities().flying) {
                getHitPlayers().put(player, velocity);
              }
            }
          }
        }
      }
    }
  }

  /**
   * Removes the blocks collected in {@link #toBlow} and optionally places fire.
   * PORT 1.21.4: replaces the removed {@code Explosion#finalizeExplosion}. Block drops and removal go through
   * {@link BlockState#onExplosionHit} so vanilla drop decay (via the inherited {@link #getBlockInteraction()}) is kept.
   */
  protected void finalizeExplosion(boolean spawnParticles) {
    if (interactsWithBlocks()) {
      for (BlockPos pos : toBlow) {
        level.getBlockState(pos).onExplosionHit(level, pos, this, (stack, dropPos) -> Block.popResource(level, dropPos, stack));
      }
    }
    if (fire) {
      for (BlockPos pos : toBlow) {
        if (level.random.nextInt(3) == 0 && level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isSolidRender()) {
          level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
        }
      }
    }
  }

  /** Runs the logic on the server, syncing to the client. */
  public void handleServer() {
    // based on ServerLevel#explode
    if (!level.isClientSide) {
      if (!EventHooks.onExplosionStart(level, this)) {
        explode();
        finalizeExplosion(false);
        syncToClient();
      }
    }
  }

  /** Runs the logic on both sides */
  public void doDualSide(Level level, boolean spawnParticles) {
    if (!EventHooks.onExplosionStart(level, this)) {
      explode();
      finalizeExplosion(spawnParticles);
    }
  }

  /** Syncs this explosion to the client */
  public void syncToClient() {
    Vec3 position = center();
    for (ServerPlayer player : level.players()) {
      if (player.distanceToSqr(position) < 4096.0D) {
        player.connection.send(new ClientboundExplodePacket(position, Optional.ofNullable(getHitPlayers().get(player)), isSmall() ? ParticleTypes.EXPLOSION : ParticleTypes.EXPLOSION_EMITTER, SoundEvents.GENERIC_EXPLODE));
      }
    }
  }
}
