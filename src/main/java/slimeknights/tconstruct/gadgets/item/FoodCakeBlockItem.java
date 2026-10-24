package slimeknights.tconstruct.gadgets.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import slimeknights.mantle.item.BlockTooltipItem;
import slimeknights.tconstruct.gadgets.block.FoodCakeBlock;

import java.util.function.Consumer;

/** Block item for food cakes, showing the cake effects. 1.21.5: Block#appendHoverText was removed, so the tooltip moved to the item */
public class FoodCakeBlockItem extends BlockTooltipItem {
  public FoodCakeBlockItem(Block block, Properties builder) {
    super(block, builder);
  }

  @Override
  public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
    super.appendHoverText(stack, context, display, tooltip, flag);
    if (getBlock() instanceof FoodCakeBlock cake) {
      cake.appendHoverText(tooltip);
    }
  }
}
