package slimeknights.tconstruct.tools.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;

public class ModifiableSwordItem extends ModifiableItem {
  public ModifiableSwordItem(Properties properties, ToolDefinition toolDefinition) {
    super(properties, toolDefinition);
  }

  public ModifiableSwordItem(Properties properties, ToolDefinition toolDefinition, int maxStackSize) {
    super(properties, toolDefinition, maxStackSize);
  }

  /** 1.21.5: replaces {@code canAttackBlock} */
  @Override
  public boolean canDestroyBlock(ItemStack stack, BlockState state, Level worldIn, BlockPos pos, LivingEntity entity) {
    return !(entity instanceof Player player) || !player.isCreative();
  }
}
