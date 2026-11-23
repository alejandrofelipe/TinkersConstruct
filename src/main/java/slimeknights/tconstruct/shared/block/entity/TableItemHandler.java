package slimeknights.tconstruct.shared.block.entity;

import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import slimeknights.tconstruct.library.transfer.SlotStorageItemHandler;

/**
 * Native item handler of a {@link TableBlockEntity} that is a {@link WorldlyContainer}, seen from a fixed side, like the legacy
 * {@code SidedInvWrapper} it replaces (slots from {@link WorldlyContainer#getSlotsForFace(Direction)}, insertion and extraction through
 * {@code canPlaceItemThroughFace}/{@code canTakeItemThroughFace}).
 * <p>
 * PORT 1.21.10 (transfer step 1): unlike NeoForge's {@code WorldlyContainerWrapper}, which calls {@code Container#setItem} during the
 * transaction, slot changes are silent ({@link TableBlockEntity#setItemSilently(int, ItemStack)}) until the root transaction commits, then
 * replayed through {@link TableBlockEntity#setItem(int, ItemStack)}, so casting tables only react (block state, comparators, client sync,
 * fluid processing) to committed transfers.
 */
public class TableItemHandler<T extends TableBlockEntity & WorldlyContainer> extends SlotStorageItemHandler {
  private final T table;
  private final Direction side;

  public TableItemHandler(T table, Direction side) {
    this.table = table;
    this.side = side;
  }

  /** Maps a handler index to a container slot */
  private int slot(int index) {
    return table.getSlotsForFace(side)[index];
  }

  @Override
  public int size() {
    return table.getSlotsForFace(side).length;
  }

  @Override
  protected ItemStack getStack(int index) {
    return table.getItem(slot(index));
  }

  @Override
  protected void setStackSilently(int index, ItemStack stack) {
    table.setItemSilently(slot(index), stack);
  }

  @Override
  protected void setStackWithSideEffects(int index, ItemStack stack) {
    table.setItem(slot(index), stack);
  }

  @Override
  protected int getSlotCapacity(int index, ItemResource resource) {
    int limit = table.getMaxStackSize();
    return resource.isEmpty() ? limit : Math.min(limit, resource.getMaxStackSize());
  }

  @Override
  protected boolean isItemValid(int index, ItemResource resource) {
    return table.canPlaceItem(slot(index), resource.toStack());
  }

  @Override
  protected boolean canInsert(int index, ItemResource resource) {
    return table.canPlaceItemThroughFace(slot(index), resource.toStack(), side);
  }

  @Override
  protected boolean canExtract(int index, ItemResource resource) {
    return table.canTakeItemThroughFace(slot(index), resource.toStack(), side);
  }
}
