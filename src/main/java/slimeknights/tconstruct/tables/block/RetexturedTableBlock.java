package slimeknights.tconstruct.tables.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import slimeknights.mantle.block.RetexturedBlock;

import javax.annotation.Nullable;

public abstract class RetexturedTableBlock extends TabbedTableBlock {
  public RetexturedTableBlock(Properties builder) {
    super(builder);
  }

  // PORT 1.21.5: Block#appendHoverText was removed, the texture tooltip comes from the RetexturedBlockItem registered for this block

  @Override
  public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
    super.setPlacedBy(world, pos, state, placer, stack);
    RetexturedBlock.updateTextureBlock(world, pos, stack);
  }

  @Override
  public ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state, boolean includeData) {
    return RetexturedBlock.getPickBlock(world, pos, state);
  }
}
