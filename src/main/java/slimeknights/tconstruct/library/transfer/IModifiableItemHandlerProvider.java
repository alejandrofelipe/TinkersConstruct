package slimeknights.tconstruct.library.transfer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import slimeknights.mantle.transfer.TransferHelper;

import org.jspecify.annotations.Nullable;

/**
 * Implemented by our block entities to give menus and slot sync packets direct access to the real stacks of their inventory.
 * <p>
 * PORT 1.21.10 (transfer phase 6): was {@code ILegacyItemHandlerProvider}, handing out a legacy {@code IItemHandlerModifiable}.
 */
public interface IModifiableItemHandlerProvider {
  /**
   * Gets the modifiable item handler over the real stacks of this block.
   * @param side  Side accessed, null for internal access
   * @return  Item handler, or null if this block has no inventory on that side
   */
  @Nullable
  ModifiableItemHandler getModifiableItemHandler(@Nullable Direction side);

  /**
   * Finds a modifiable item handler over the real stacks of the block at the given position, for menus and slot sync packets.
   * Covers our block entities, vanilla chests (both halves of a double chest, like the capability), any {@link Container}, and
   * any other block exposing the native item capability (set through transactions, see {@link ModifiableItemHandler#transactional(ResourceHandler)}).
   * @param level  Level
   * @param pos    Block position
   * @param side   Side accessed, null for internal access
   * @return  Item handler, or null if none
   */
  @Nullable
  static ModifiableItemHandler find(Level level, BlockPos pos, @Nullable Direction side) {
    BlockEntity be = level.getBlockEntity(pos);
    if (be instanceof IModifiableItemHandlerProvider provider) {
      return provider.getModifiableItemHandler(side);
    }
    if (be instanceof ChestBlockEntity) {
      BlockState state = be.getBlockState();
      if (state.getBlock() instanceof ChestBlock chest) {
        Container container = ChestBlock.getContainer(chest, state, level, pos, true);
        if (container != null) {
          return ModifiableItemHandler.ofContainer(container, null);
        }
      }
    }
    if (be instanceof Container container) {
      return ModifiableItemHandler.ofContainer(container, side);
    }
    // inventories exposing only the native capability
    if (be != null) {
      ResourceHandler<ItemResource> handler = TransferHelper.getItemResourceHandler(level, pos, be.getBlockState(), be, side);
      if (handler != null) {
        return ModifiableItemHandler.transactional(handler);
      }
    }
    return null;
  }
}
