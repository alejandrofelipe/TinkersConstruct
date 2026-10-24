package slimeknights.tconstruct.fluids.item;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.fluids.util.ConstantFluidContainerWrapper;
import slimeknights.tconstruct.library.recipe.FluidValues;

import java.util.List;
import java.util.function.Consumer;

/** Magma bottle instance, which lights the drinker on fire */
public class MagmaBottleItem extends Item {
  private final int fireTime;
  public MagmaBottleItem(Properties props, int fireTime) {
    super(props);
    this.fireTime = fireTime;
  }

  @Override
  public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flagIn) {
    super.appendHoverText(stack, context, display, tooltip, flagIn);
    tooltip.accept(Component.translatable(
      "potion.withDuration",
      Blocks.FIRE.getName(),
      StringUtil.formatTickDuration(fireTime * 20, 20f)
    ).withStyle(MobEffectCategory.HARMFUL.getTooltipFormatting()));
  }

  @Override
  public InteractionResult use(Level level, Player player, InteractionHand hand) {
    player.startUsingItem(hand);
    return InteractionResult.CONSUME;
  }

  @Override
  public int getUseDuration(ItemStack pStack, LivingEntity entity) {
    return 32;
  }

  @Override
  public ItemUseAnimation getUseAnimation(ItemStack pStack) {
    return ItemUseAnimation.DRINK;
  }

  @Override
  public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity living) {
    living.igniteForSeconds(fireTime);
    ItemStack container = stack.getCraftingRemainder();
    Player player = living instanceof Player p ? p : null;
    if (player == null || !player.getAbilities().instabuild) {
      stack.shrink(1);
      container = container.copy();
      if (stack.isEmpty()) {
        return container;
      }
      if (player != null) {
        if (!player.getInventory().add(container)) {
          player.drop(container, false);
        }
      }
    }
    return stack;
  }

  /** Creates the fluid handler for the given stack; registered centrally on {@code RegisterCapabilitiesEvent}. */
  public IFluidHandlerItem getFluidHandler(ItemStack stack) {
    return new ConstantFluidContainerWrapper(new FluidStack(TinkerFluids.magma.get(), FluidValues.BOTTLE), stack);
  }
}
