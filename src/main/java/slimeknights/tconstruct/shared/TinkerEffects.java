package slimeknights.tconstruct.shared;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import slimeknights.mantle.registration.deferred.PotionDeferredRegister;
import slimeknights.mantle.registration.deferred.PotionDeferredRegister.PotionType;
import slimeknights.mantle.registration.object.EnumObject;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerEffect;
import slimeknights.tconstruct.common.TinkerModule;
import slimeknights.tconstruct.shared.effect.AntigravityEffect;
import slimeknights.tconstruct.shared.effect.ReturningEffect;
import slimeknights.tconstruct.tools.modifiers.effect.BleedingEffect;
import slimeknights.tconstruct.tools.modifiers.effect.MagneticEffect;
import slimeknights.tconstruct.tools.modifiers.effect.RepulsiveEffect;
import slimeknights.tconstruct.tools.modifiers.traits.skull.SelfDestructiveModifier.SelfDestructiveEffect;

import javax.annotation.Nullable;

/** Handles registration for all status effects and potions in the mod */
public class TinkerEffects extends TinkerModule {
  private static final PotionDeferredRegister POTIONS = new PotionDeferredRegister(TConstruct.MOD_ID);

  // slimy potions
  public static final DeferredHolder<MobEffect, TinkerEffect> experienced = MOB_EFFECTS.register("experienced", () -> new TinkerEffect(MobEffectCategory.BENEFICIAL, 0x82c873, true).addAttributeModifier(TinkerAttributes.EXPERIENCE_MULTIPLIER, TConstruct.getResource("effect.experienced"), 0.25f, Operation.ADD_MULTIPLIED_BASE));
  public static final DeferredHolder<MobEffect, TinkerEffect> ricochet = MOB_EFFECTS.register("ricochet", () -> new TinkerEffect(MobEffectCategory.NEUTRAL, 0x01cbcd, true).addAttributeModifier(TinkerAttributes.KNOCKBACK_MULTIPLIER, TConstruct.getResource("effect.ricochet"), 0.5f, Operation.ADD_MULTIPLIED_BASE));
  public static final DeferredHolder<MobEffect, TinkerEffect> enderference = MOB_EFFECTS.register("enderference", () -> new TinkerEffect(MobEffectCategory.HARMFUL, 0xD37CFF, true));
  /** Projectile persistent data key to allow ranged modifiers to hit endermen. */
  public static final ResourceLocation ENDERFERENCE_KEY = enderference.getId();

  // slimy cakes
  public static final DeferredHolder<MobEffect, TinkerEffect> bouncy = MOB_EFFECTS.register("bouncy", () -> new TinkerEffect(MobEffectCategory.BENEFICIAL, 0x71AC63, true).addAttributeModifier(TinkerAttributes.BOUNCY, TConstruct.getResource("effect.bouncy"), 1, Operation.ADD_VALUE));
  public static final DeferredHolder<MobEffect, TinkerEffect> doubleJump = MOB_EFFECTS.register("double_jump", () -> new TinkerEffect(MobEffectCategory.BENEFICIAL, 0xA99B87, true).addAttributeModifier(TinkerAttributes.JUMP_COUNT, TConstruct.getResource("effect.double_jump"), 1, Operation.ADD_VALUE));
  public static final DeferredHolder<MobEffect, AntigravityEffect> antigravity = MOB_EFFECTS.register("antigravity", AntigravityEffect::new);
  public static final DeferredHolder<MobEffect, ReturningEffect> returning = MOB_EFFECTS.register("returning", ReturningEffect::new);

  // modifier effects
  public static final DeferredHolder<MobEffect, BleedingEffect> bleeding = MOB_EFFECTS.register("bleeding", BleedingEffect::new);
  public static final DeferredHolder<MobEffect, MagneticEffect> magnetic = MOB_EFFECTS.register("magnetic", MagneticEffect::new);
  public static final DeferredHolder<MobEffect, TinkerEffect> selfDestructing = MOB_EFFECTS.register("self_destructing", SelfDestructiveEffect::new);
  public static final DeferredHolder<MobEffect, RepulsiveEffect> repulsive = MOB_EFFECTS.register("repulsive", RepulsiveEffect::new);
  public static final DeferredHolder<MobEffect, TinkerEffect> pierce = MOB_EFFECTS.register("pierce", () -> new TinkerEffect(MobEffectCategory.HARMFUL, 0xD1D37A, true).addAttributeModifier(Attributes.ARMOR, TConstruct.getResource("effect.pierce"), -1, Operation.ADD_VALUE));
  // damage boost
  public static final DeferredHolder<MobEffect, TinkerEffect> conductive = MOB_EFFECTS.register("conductive", () -> new TinkerEffect(MobEffectCategory.HARMFUL, 0xF2D500, true));
  public static final DeferredHolder<MobEffect, TinkerEffect> venom = MOB_EFFECTS.register("venom", () -> new TinkerEffect(MobEffectCategory.HARMFUL, 0xA2935E, true));

  // potions
  public static final EnumObject<PotionType,Potion> experiencedPotion = POTIONS.registerTypes(experienced).withStrong().withLong().build();
  public static final EnumObject<PotionType,Potion> ricochetPotion = POTIONS.registerTypes(ricochet).withStrong().withLong().build();
  public static final EnumObject<PotionType,Potion> levitationPotion = POTIONS.registerTypes("levitation", () -> MobEffects.LEVITATION.value(), 15 * 20, 0).withStrong().withLong(40 * 20, 0).build();
  public static final EnumObject<PotionType,Potion> enderferencePotion = POTIONS.registerTypes(enderference, 90 * 20, 0).withLong().build();

  public TinkerEffects() {
    POTIONS.register(TConstruct.modBus);
  }

  // PORT M3: 1.21 reworked brewing. PotionBrewing is no longer a set of static mutable maps
  //  (PotionBrewing.POTION_MIXES / Mix / addMix are gone); brewing recipes are registered through
  //  net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent on the game bus, and potions are
  //  now Holder<Potion>. The brewing registration below must move to that event in the entrypoint (M3).
  @SubscribeEvent
  void commonSetup(FMLCommonSetupEvent event) {
    // brewing registration moved to RegisterBrewingRecipesEvent (see PORT M3 note above)
  }

  /** Checks if the given entity can be hit considering enderman enderference */
  public static boolean canHitWithProjectile(@Nullable LivingEntity living) {
    return living == null || living.getType() != EntityType.ENDERMAN || living.hasEffect(enderference);
  }

  /** Checks if the given entity needs special casing for enderference */
  public static boolean needsEnderferenceOverride(@Nullable Entity entity) {
    return entity != null && entity.getType() == EntityType.ENDERMAN && entity instanceof LivingEntity living && living.hasEffect(enderference);
  }

  /** Checks if the given entity needs special casing for enderference */
  public static boolean needsEnderferenceOverride(@Nullable LivingEntity living) {
    return living != null && living.getType() == EntityType.ENDERMAN && living.hasEffect(enderference);
  }
}
