package slimeknights.tconstruct.smeltery.block.entity.component;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import slimeknights.tconstruct.common.multiblock.IMasterLogic;
import slimeknights.tconstruct.common.multiblock.ServantTileEntity;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;

/** Mostly extended to make type validaton easier, and the servant base class is not registered */
public class SmelteryComponentBlockEntity extends ServantTileEntity {

  public SmelteryComponentBlockEntity(BlockPos pos, BlockState state) {
    this(TinkerSmeltery.smelteryComponent.get(), pos, state);
  }

  protected SmelteryComponentBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
    super(type, pos, state);
  }

  /**
   * PORT 1.21.5: replaces the block's {@code onRemove}, which notified the master when the block changed. The chunk removes the
   * block entity before {@code affectNeighborsAfterRemoval} runs, so this has to happen here. The level already holds the new state.
   */
  @Override
  public void preRemoveSideEffects(BlockPos pos, BlockState state) {
    if (level != null) {
      notifyMasterOfChange(pos, level.getBlockState(pos));
    }
    // drops the inventory for inventory blocks (ducts)
    super.preRemoveSideEffects(pos, state);
  }

  /**
   * Block method to update neighbors of a smeltery component when a new one is placed
   * @param world  World instance
   * @param pos    Location of new smeltery component
   */
  public static void updateNeighbors(Level world, BlockPos pos, BlockState state) {
    for (Direction direction : Direction.values()) {
      // if the neighbor is a master, notify it we exist
      BlockEntity tileEntity = world.getBlockEntity(pos.relative(direction));
      if (tileEntity instanceof IMasterLogic master) {
        master.notifyChange(pos, state);
        break;
        // if the neighbor is a servant, notify its master we exist
      } else if (tileEntity instanceof SmelteryComponentBlockEntity component && component.hasMaster()) {
        component.notifyMasterOfChange(pos, state);
        break;
      }
    }
  }
}
