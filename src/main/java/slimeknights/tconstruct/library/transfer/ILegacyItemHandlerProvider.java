package slimeknights.tconstruct.library.transfer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;

import javax.annotation.Nullable;

/**
 * Implemented by our block entities that keep a legacy, modifiable item handler over their real stacks.
 * <p>
 * PORT 1.21.10 (transfer step 1): the registered item capability is a native {@code ResourceHandler} now, and its legacy bridge hands out
 * copies and is not modifiable, which breaks menu slots and client slot sync. Menus and packets that need the real stacks of a block use
 * {@link #find(Level, BlockPos, Direction)} instead of the capability. Removed with the legacy interfaces in the full migration (phase 6).
 */
@SuppressWarnings("removal")  // legacy item handlers until phase 6
public interface ILegacyItemHandlerProvider {
  /**
   * Gets the legacy item handler over the real stacks of this block.
   * @param side  Side accessed, null for internal access
   * @return  Item handler, or null if this block has no inventory on that side
   */
  @Nullable
  IItemHandlerModifiable getLegacyItemHandler(@Nullable Direction side);

  /**
   * Finds a modifiable legacy item handler over the real stacks of the block at the given position, for menus and slot sync packets.
   * Covers our block entities, vanilla chests (both halves of a double chest, like the old capability) and any {@link Container};
   * other inventories only expose a native capability and are not supported until the full migration.
   * @param level  Level
   * @param pos    Block position
   * @param side   Side accessed, null for internal access
   * @return  Item handler, or null if none
   */
  @Nullable
  static IItemHandlerModifiable find(Level level, BlockPos pos, @Nullable Direction side) {
    BlockEntity be = level.getBlockEntity(pos);
    if (be instanceof ILegacyItemHandlerProvider provider) {
      return provider.getLegacyItemHandler(side);
    }
    if (be instanceof ChestBlockEntity) {
      BlockState state = be.getBlockState();
      if (state.getBlock() instanceof ChestBlock chest) {
        Container container = ChestBlock.getContainer(chest, state, level, pos, true);
        if (container != null) {
          return new InvWrapper(container);
        }
      }
    }
    if (be instanceof WorldlyContainer worldly && side != null) {
      return new SidedInvWrapper(worldly, side);
    }
    if (be instanceof Container container) {
      return new InvWrapper(container);
    }
    return null;
  }
}
