package slimeknights.tconstruct.gadgets.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import slimeknights.tconstruct.fluids.item.ContainerFoodItem;
import slimeknights.tconstruct.shared.TinkerFood;

import java.util.List;

/**
 * Extension of cake that utilizes a food instance for properties
 */
public class FoodCakeBlock extends CakeBlock {
  // PORT 1.21.4: status effects moved from FoodProperties into the Consumable component, so the cake carries both:
  // FoodProperties supplies nutrition/saturation/canAlwaysEat, Consumable supplies the effects applied per slice.
  private final FoodProperties food;
  private final Consumable consumable;
  private final EffectCombination combination;

  public FoodCakeBlock(Properties properties, TinkerFood.Meal meal, EffectCombination combination) {
    super(properties);
    this.food = meal.food();
    this.consumable = meal.consumable();
    this.combination = combination;
  }

  @Override
  public void appendHoverText(ItemStack pStack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag pFlag) {
    ContainerFoodItem.addEffectTooltip(consumable, tooltip);
  }

  @Override
  protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
    InteractionResult result = this.eatSlice(world, pos, state, player);
    if (result.consumesAction()) {
      return result;
    }
    if (world.isClientSide()) {
      return InteractionResult.CONSUME;
    }
    return InteractionResult.PASS;
  }

  /** Checks if the given player has all potion effects from the food */
  private boolean hasAllEffects(Player player) {
    for (ConsumeEffect consumeEffect : consumable.onConsumeEffects()) {
      if (consumeEffect instanceof ApplyStatusEffectsConsumeEffect applyEffects) {
        for (MobEffectInstance effect : applyEffects.effects()) {
          MobEffectInstance current = player.getEffect(effect.getEffect());
          if (current == null || current.getDuration() < 100) {
            return false;
          }
        }
      }
    }
    return true;
  }

  /** Eats a single slice of cake if possible */
  private InteractionResult eatSlice(LevelAccessor world, BlockPos pos, BlockState state, Player player) {
    if (!player.canEat(false) && !food.canAlwaysEat()) {
      return InteractionResult.PASS;
    }
    // repurpose fast eating, will mean no eating if we have the effect
    if (combination == EffectCombination.BLOCK && hasAllEffects(player)) {
      return InteractionResult.PASS;
    }
    player.awardStat(Stats.EAT_CAKE_SLICE);
    // apply food stats
    player.getFoodData().eat(food.nutrition(), food.saturation());
    for (ConsumeEffect consumeEffect : consumable.onConsumeEffects()) {
      if (consumeEffect instanceof ApplyStatusEffectsConsumeEffect applyEffects) {
        for (MobEffectInstance possible : applyEffects.effects()) {
          if (!world.isClientSide() && world.getRandom().nextFloat() < applyEffects.probability()) {
            MobEffectInstance effect = new MobEffectInstance(possible);
            // if adding, increase duration by current duration, provided its an exact level match
            if (combination == EffectCombination.ADD) {
              MobEffectInstance current = player.getEffect(effect.getEffect());
              if (current != null && current.getAmplifier() == effect.getAmplifier()) {
                effect.duration += current.getDuration();
              }
            }
            player.addEffect(effect);
          }
        }
      }
    }
    // remove one bite from the cake
    int i = state.getValue(BITES);
    if (i < 6) {
      world.setBlock(pos, state.setValue(BITES, i + 1), 3);
    } else {
      world.removeBlock(pos, false);
    }
    return InteractionResult.SUCCESS;
  }

  public enum EffectCombination {
    /** New effect will update time on existing, like potions */
    SET,
    /** New effect will increase duration of existing */
    ADD,
    /** Cake cannot be eaten if effect is present  */
    BLOCK
  }
}
