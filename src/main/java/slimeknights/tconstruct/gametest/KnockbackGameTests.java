package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import slimeknights.tconstruct.TConstruct;
import net.minecraft.world.phys.EntityHitResult;
import slimeknights.tconstruct.gadgets.entity.shuriken.FlintShurikenEntity;
import slimeknights.tconstruct.gadgets.entity.shuriken.ShurikenEntityBase;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.interaction.GeneralInteractionModifierHook;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.helper.ToolAttackUtil;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.data.ModifierIds;
import slimeknights.tconstruct.tools.data.material.MaterialIds;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Knockback reference (M8 plan R1 guard S8): 26.2 gives {@code LivingEntity#knockback} a damage source, a damage amount and an effect flag, and widens
 * knockback resistance to {@code [-2, 1]}; a wrong argument at one of the Tinkers call sites changes how far things fly without failing anything. Each
 * test runs one call site on a cow (a husk for the counterattack) standing on stone and compares the peak horizontal speed of the target within
 * {@link #TICKS} ticks of the hit (blocks per tick, the knockback velocity) with the numbers measured on 26.1.2. The peak rather than the distance:
 * a target knocked into the arena wall stops there, which would hide a stronger knockback.
 */
public class KnockbackGameTests {
  /** Ticks after the hit during which the peak speed is tracked */
  private static final int TICKS = 10;
  /** Allowed difference to the reference peak speed, in blocks per tick */
  private static final double TOLERANCE = 0.02;
  /** Peak horizontal speed within {@link #TICKS} ticks of the hit measured on 26.1.2 (M8 Phase 0.5c), blocks per tick */
  private static final Map<String, Double> PEAK_SPEED = Map.of(
    "sword", 0.400, "sword_knockback_2", 1.200,
    "sweep_neighbour", 0.577, "circle_neighbour", 0.579,
    "shuriken", 0.800, "springy_counter", 0.992, "bonking", 3.750);

  /** Fills the arena floor with stone, so friction is the same for every target */
  private static void floor(GameTestHelper helper) {
    for (int x = 0; x < 9; x++) {
      for (int z = 0; z < 9; z++) {
        helper.setBlock(x, 0, z, Blocks.STONE);
      }
    }
  }

  /** A cow without AI goals at the relative position */
  private static Cow cow(GameTestHelper helper, double x, double z) {
    return helper.spawnWithNoFreeWill(EntityType.COW, new Vec3(x, 1, z));
  }

  /** A mock player on the ground at the relative position facing south (+z), towards the targets */
  private static Player attacker(GameTestHelper helper, double x, double z) {
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    Vec3 pos = helper.absoluteVec(new Vec3(x, 1, z));
    player.snapTo(pos.x, pos.y, pos.z, 0, 0);
    player.setOnGround(true);
    return player;
  }

  /** An iron tool of the given item, with the modifier added if set */
  private static ToolStack tool(Item item, @Nullable ModifierId modifier, int level) {
    ToolStack tool = ToolStack.from(ToolBuildHandler.createSingleMaterial((IModifiable) item, MaterialVariant.of((MaterialVariantId) MaterialIds.iron)));
    if (modifier != null) {
      tool.addModifier(modifier, level);
    }
    return tool;
  }

  /** A fully charged tool attack of the mock player on the target, as {@code ToolAttackUtil#attackEntity} does */
  private static void attack(Player player, ToolStack tool, LivingEntity target) {
    player.setItemSlot(EquipmentSlot.MAINHAND, tool.createStack());
    ToolAttackUtil.performAttack(tool, ToolAttackContext.attacker(player).target(target).cooldown(1).toolAttributes(tool).build());
  }

  /** Runs the hit, tracks each target's peak horizontal speed for {@link #TICKS} ticks and compares it with the reference */
  private static void measure(GameTestHelper helper, Map<String, LivingEntity> targets, Runnable hit) {
    Map<String, Double> peak = new LinkedHashMap<>();
    hit.run();
    // the speed right after the hit, then each tick for projectiles hitting later; the arena walls stop a target but never cap its peak speed
    Runnable track = () -> targets.forEach((key, entity) -> peak.merge(key, entity.getDeltaMovement().horizontalDistance(), Math::max));
    track.run();
    for (int tick = 1; tick < TICKS; tick++) {
      helper.runAfterDelay(tick, track);
    }
    helper.runAfterDelay(TICKS, () -> {
      List<String> measured = new ArrayList<>();
      List<String> problems = new ArrayList<>();
      for (Map.Entry<String, LivingEntity> entry : targets.entrySet()) {
        double distance = peak.getOrDefault(entry.getKey(), 0d);
        String value = String.format(Locale.ROOT, "%s=%.3f", entry.getKey(), distance);
        measured.add(value);
        Double expected = PEAK_SPEED.get(entry.getKey());
        if (expected == null) {
          problems.add(value + " has no reference");
        } else if (Math.abs(expected - distance) > TOLERANCE) {
          problems.add(String.format(Locale.ROOT, "%s, expected %.3f", value, expected));
        }
      }
      TConstruct.LOG.info("Knockback peak speeds: {}", measured);
      if (problems.isEmpty()) {
        helper.succeed();
      } else {
        helper.fail(Component.literal("knockback speed differs from the 26.1.2 reference: " + String.join("; ", problems)));
      }
    });
  }

  /** A sword attack without and with the knockback modifier ({@code ToolAttackUtil#performAttack} adds the knockback above vanilla's 0.4) */
  @TinkerGameTest(template = "gametest/empty_9x9x9", timeoutTicks = 100)
  public static void knockback_reference_tool(GameTestHelper helper) {
    floor(helper);
    Cow plain = cow(helper, 1.5, 3.5);
    Cow knockback = cow(helper, 6.5, 3.5);
    Player plainAttacker = attacker(helper, 1.5, 2);
    Player knockbackAttacker = attacker(helper, 6.5, 2);
    measure(helper, Map.of("sword", plain, "sword_knockback_2", knockback), () -> {
      attack(plainAttacker, tool(TinkerTools.sword.get(), null, 0), plain);
      attack(knockbackAttacker, tool(TinkerTools.sword.get(), ModifierIds.knockback, 2), knockback);
    });
  }

  /** The sword sweep ({@code SweepWeaponAttack}) and the kama circle attack ({@code CircleWeaponAttack}) knock back the target's neighbour by 0.4 */
  @TinkerGameTest(template = "gametest/empty_9x9x9", timeoutTicks = 100)
  public static void knockback_reference_aoe(GameTestHelper helper) {
    floor(helper);
    Cow sweepTarget = cow(helper, 1.5, 3.5);
    Cow sweepNeighbour = cow(helper, 2.5, 3.5);
    Cow circleTarget = cow(helper, 6, 3.5);
    Cow circleNeighbour = cow(helper, 6.95, 3.5);
    Player sweeper = attacker(helper, 1.5, 2);
    Player circler = attacker(helper, 6, 2);
    measure(helper, Map.of("sweep_neighbour", sweepNeighbour, "circle_neighbour", circleNeighbour), () -> {
      attack(sweeper, tool(TinkerTools.sword.get(), null, 0), sweepTarget);
      attack(circler, tool(TinkerTools.kama.get(), null, 0), circleTarget);
    });
  }

  /** A flint shuriken shot at a cow hits it ({@code ShurikenEntityBase#onHitEntity}: damage, then knockback along its motion) */
  @TinkerGameTest(template = "gametest/empty_9x9x9", timeoutTicks = 100)
  public static void knockback_reference_shuriken(GameTestHelper helper) {
    floor(helper);
    Cow target = cow(helper, 4.5, 5.5);
    Player thrower = attacker(helper, 4.5, 2.5);
    measure(helper, Map.of("shuriken", target), () -> {
      FlintShurikenEntity shuriken = new FlintShurikenEntity(helper.getLevel(), thrower);
      Vec3 aim = target.position().add(0, target.getBbHeight() / 2, 0).subtract(shuriken.position());
      shuriken.shoot(aim.x, aim.y, aim.z, 1.5f, 0);
      // hit right away instead of flying, so the hit tick (and the speed read after it) does not depend on the entity tick order
      try {
        Method onHit = ShurikenEntityBase.class.getDeclaredMethod("onHitEntity", EntityHitResult.class);
        onHit.setAccessible(true);
        onHit.invoke(shuriken, new EntityHitResult(target));
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("cannot hit with the shuriken", e);
      }
    });
  }

  /**
   * The springy modifier on worn armor pushes the attacker back ({@code KnockbackCounterModule}). Level 7 makes the chance certain; the random part of
   * the strength comes from {@code TConstruct.RANDOM}, seeded right before the hit.
   */
  @TinkerGameTest(template = "gametest/empty_9x9x9", timeoutTicks = 100)
  public static void knockback_reference_counter(GameTestHelper helper) {
    floor(helper);
    Husk defender = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(4.5, 1, 5.5));
    defender.setItemSlot(EquipmentSlot.CHEST, tool(TinkerTools.travelersGear.get(ArmorType.CHESTPLATE), ModifierIds.springy, 7).createStack());
    Husk attacker = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(4.5, 1, 4));
    attacker.setYRot(0);
    attacker.setYHeadRot(0);
    attacker.setYBodyRot(0);
    // the armor's tinker data (slot in charge) is set when the husk ticks the equipment change
    helper.runAfterDelay(2, () -> measure(helper, Map.of("springy_counter", attacker), () -> {
      TConstruct.RANDOM.setSeed(8);
      defender.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 1);
    }));
  }

  /**
   * The bonking sling modifier launches the target the player looks at ({@code SlingKnockbackModule#sling}), fully charged, inaccuracy seeded. The
   * battlesign has bonking and knockback as traits; its sling force comes from the knockback modifier's {@code SlingForceModule}.
   */
  @TinkerGameTest(template = "gametest/empty_9x9x9", timeoutTicks = 100)
  public static void knockback_reference_sling(GameTestHelper helper) {
    floor(helper);
    Cow target = cow(helper, 4.5, 4.5);
    Player player = attacker(helper, 4.5, 2.5);
    ToolStack tool = tool(TinkerTools.battlesign.get(), null, 0);
    player.setItemSlot(EquipmentSlot.MAINHAND, tool.createStack());
    ModifierEntry bonking = tool.getModifier(ModifierIds.bonking);
    helper.assertTrue(bonking.getLevel() > 0, Component.literal("the battlesign has no bonking modifier"));
    measure(helper, Map.of("bonking", target), () -> {
      tool.getPersistentData().putInt(GeneralInteractionModifierHook.KEY_DRAWTIME, 20);
      player.getRandom().setSeed(8);
      int duration = bonking.getHook(ModifierHooks.GENERAL_INTERACT).getUseDuration(tool, bonking);
      bonking.getHook(ModifierHooks.TOOL_USING).beforeReleaseUsing(tool, bonking, player, duration, duration - 20, bonking);
    });
  }
}
