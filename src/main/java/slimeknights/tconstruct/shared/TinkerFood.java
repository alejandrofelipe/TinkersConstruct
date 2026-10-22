package slimeknights.tconstruct.shared;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import slimeknights.tconstruct.shared.block.SlimeType;
import slimeknights.tconstruct.world.block.FoliageType;

@SuppressWarnings("WeakerAccess")
public final class TinkerFood {
  private TinkerFood() {}

  /**
   * Pairs food properties with its consumable component.
   * PORT 1.21.4: status effects moved out of {@link FoodProperties} into the {@link Consumable} component, so a food
   * that applies effects must carry both.
   */
  public record Meal(FoodProperties food, Consumable consumable) {
    /** Applies this meal's food properties and consumable (effects) to the given item properties. */
    public Item.Properties apply(Item.Properties properties) {
      return properties.food(food, consumable);
    }
  }

  /**
   * Builds a consumable carrying the given status effects, each always applied (probability 1.0), mirroring the
   * 1.20 {@code FoodProperties.Builder#effect} calls that all used probability 1.0.
   */
  private static Consumable effectConsumable(ItemUseAnimation animation, MobEffectInstance... effects) {
    Consumable.Builder builder = Consumable.builder().animation(animation);
    for (MobEffectInstance effect : effects) {
      builder.onConsume(new ApplyStatusEffectsConsumeEffect(effect, 1.0f));
    }
    return builder.build();
  }

  /** Bacon. What more is there to say? */
  public static final FoodProperties BACON = new FoodProperties.Builder().nutrition(4).saturationModifier(0.6F).build();

  /** Cheese is used for both the block and the ingot, eating the block returns 3 ingots */
  public static final FoodProperties CHEESE = new FoodProperties.Builder().nutrition(3).saturationModifier(0.4F).build();

  /** For the modifier */
  public static final Meal JEWELED_APPLE = new Meal(
    new FoodProperties.Builder().nutrition(4).saturationModifier(1.2F).alwaysEdible().build(),
    effectConsumable(ItemUseAnimation.EAT,
      new MobEffectInstance(MobEffects.DIG_SPEED, 1200, 0),
      new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 2400, 0)));

  /* Cake block is set up to take food as a parameter */
  public static final Meal EARTH_CAKE = new Meal(new FoodProperties.Builder().nutrition(1).saturationModifier(0.3f).alwaysEdible().build(), effectConsumable(ItemUseAnimation.EAT, new MobEffectInstance(TinkerEffects.bouncy,      30 * 20, 0)));
  public static final Meal SKY_CAKE   = new Meal(new FoodProperties.Builder().nutrition(1).saturationModifier(0.2f).alwaysEdible().build(), effectConsumable(ItemUseAnimation.EAT, new MobEffectInstance(TinkerEffects.doubleJump,  30 * 20, 0)));
  public static final Meal ICHOR_CAKE = new Meal(new FoodProperties.Builder().nutrition(1).saturationModifier(0.3f).alwaysEdible().build(), effectConsumable(ItemUseAnimation.EAT, new MobEffectInstance(TinkerEffects.antigravity, 30 * 20, 0)));
  public static final Meal ENDER_CAKE = new Meal(new FoodProperties.Builder().nutrition(1).saturationModifier(0.4f).alwaysEdible().build(), effectConsumable(ItemUseAnimation.EAT, new MobEffectInstance(TinkerEffects.returning,   30 * 20, 0)));
  public static final Meal MAGMA_CAKE = new Meal(new FoodProperties.Builder().nutrition(2).saturationModifier(0.2f).alwaysEdible().build(), effectConsumable(ItemUseAnimation.EAT, new MobEffectInstance(MobEffects.FIRE_RESISTANCE,      30 * 20, 0)));
  // regen is 50 ticks per half heart, so this heals 3 per slice
  public static final Meal BLOOD_CAKE = new Meal(new FoodProperties.Builder().nutrition(2).saturationModifier(0.2f).alwaysEdible().build(), effectConsumable(ItemUseAnimation.EAT, new MobEffectInstance(MobEffects.REGENERATION, 3 * 50, 0)));

  public static final Meal EARTH_BOTTLE = new Meal(new FoodProperties.Builder().alwaysEdible().build(), effectConsumable(ItemUseAnimation.DRINK, new MobEffectInstance(TinkerEffects.experienced,  120 * 20), new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120 * 20, 1)));
  public static final Meal SKY_BOTTLE   = new Meal(new FoodProperties.Builder().alwaysEdible().build(), effectConsumable(ItemUseAnimation.DRINK, new MobEffectInstance(TinkerEffects.ricochet,     120 * 20), new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120 * 20, 1)));
  public static final Meal ICHOR_BOTTLE = new Meal(new FoodProperties.Builder().alwaysEdible().build(), effectConsumable(ItemUseAnimation.DRINK, new MobEffectInstance(MobEffects.LEVITATION,             10 * 20), new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,  10 * 20, 1)));
  public static final Meal ENDER_BOTTLE = new Meal(new FoodProperties.Builder().alwaysEdible().build(), effectConsumable(ItemUseAnimation.DRINK, new MobEffectInstance(TinkerEffects.enderference,  60 * 20), new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,  60 * 20, 1)));
  // 250 is 10 poison damage
  public static final Meal VENOM_BOTTLE = new Meal(new FoodProperties.Builder().alwaysEdible().build(), effectConsumable(ItemUseAnimation.DRINK, new MobEffectInstance(MobEffects.DAMAGE_BOOST, 30 * 20), new MobEffectInstance(MobEffects.POISON, 250)));
  /** @deprecated no longer used */
  @Deprecated(forRemoval = true)
  public static final FoodProperties MAGMA_BOTTLE = new FoodProperties.Builder().alwaysEdible().build();

  public static final FoodProperties MEAT_SOUP = new FoodProperties.Builder().nutrition(8).saturationModifier(0.6f).build();

  /**
   * Gets the cake for the given slime type
   * @param slime  Slime type
   * @return  Cake food
   */
  public static Meal getCake(FoliageType slime) {
    return switch (slime) {
      default -> EARTH_CAKE;
      case SKY -> SKY_CAKE;
      case ICHOR -> ICHOR_CAKE;
      case BLOOD -> BLOOD_CAKE;
      case ENDER -> ENDER_CAKE;
    };
  }

  /**
   * Gets the cake for the given slime type
   * @param slime  Slime type
   * @return  Cake food
   */
  public static Meal getBottle(SlimeType slime) {
    return switch (slime) {
      default -> EARTH_BOTTLE;
      case SKY -> SKY_BOTTLE;
      case ICHOR -> ICHOR_BOTTLE;
      case ENDER -> ENDER_BOTTLE;
    };
  }
}
