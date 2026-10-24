package slimeknights.tconstruct.fluids.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import slimeknights.tconstruct.fluids.util.ConstantFluidContainerWrapper;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ContainerFoodItem extends Item {
  public ContainerFoodItem(Properties props) {
    super(props);
  }

  @Override
  public int getUseDuration(ItemStack pStack, LivingEntity entity) {
    return 32;
  }

  @Override
  public ItemUseAnimation getUseAnimation(ItemStack pStack) {
    return ItemUseAnimation.DRINK;
  }

  /** Adds effects to the tooltip. PORT 1.21.4: status effects moved from FoodProperties to the Consumable component. */
  public static void addEffectTooltip(Consumable consumable, List<Component> tooltip) {
    addEffectTooltip(consumable, (Consumer<Component>) tooltip::add);
  }

  /** Adds effects to the tooltip. 1.21.5: tooltips are built through a consumer. */
  public static void addEffectTooltip(Consumable consumable, Consumer<Component> tooltip) {
    // add effects to the tooltip, code based on potion items
    for (ConsumeEffect consumeEffect : consumable.onConsumeEffects()) {
      if (consumeEffect instanceof ApplyStatusEffectsConsumeEffect applyEffects) {
        for (MobEffectInstance effect : applyEffects.effects()) {
          MutableComponent mutable = Component.translatable(effect.getDescriptionId());
          if (effect.getAmplifier() > 0) {
            mutable = Component.translatable("potion.withAmplifier", mutable, Component.translatable("potion.potency." + effect.getAmplifier()));
          }
          if (effect.getDuration() > 20) {
            mutable = Component.translatable("potion.withDuration", mutable, MobEffectUtil.formatDuration(effect, 1.0f, 20f));
          }
          tooltip.accept(mutable.withStyle(effect.getEffect().value().getCategory().getTooltipFormatting()));
        }
      }
    }
  }

  @Override
  public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flagIn) {
    Consumable consumable = stack.get(DataComponents.CONSUMABLE);
    if (consumable != null) {
      addEffectTooltip(consumable, tooltip);
    }
  }

  @Override
  public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity living) {
    ItemStack container = stack.getCraftingRemainder();
    ItemStack result = super.finishUsingItem(stack, level, living);
    Player player = living instanceof Player p ? p : null;
    if (player == null || !player.getAbilities().instabuild) {
      container = container.copy();
      if (result.isEmpty()) {
        return container;
      }
      if (player != null) {
        if (!player.getInventory().add(container)) {
          player.drop(container, false);
        }
      }
    }
    return result;
  }

  public static class FluidContainerFoodItem extends ContainerFoodItem {
    private final Supplier<FluidStack> fluid;
    public FluidContainerFoodItem(Properties props, Supplier<FluidStack> fluid) {
      super(props);
      this.fluid = fluid;
    }

    /** Creates the fluid handler for the given stack; registered centrally on {@code RegisterCapabilitiesEvent}. */
    public IFluidHandlerItem getFluidHandler(ItemStack stack) {
      return new ConstantFluidContainerWrapper(fluid.get(), stack);
    }
  }
}
