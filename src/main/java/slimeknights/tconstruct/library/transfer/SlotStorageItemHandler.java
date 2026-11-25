package slimeknights.tconstruct.library.transfer;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStackResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Native item handler over indexed stack storage owned by someone else (a block entity, a legacy inventory, a melting module),
 * in the style of NeoForge's {@code VanillaContainerWrapper}: each slot is a NeoForge {@link ItemStackResourceHandler}, a
 * {@code SnapshotJournal} that snapshots the stack on the first change in a transaction and puts it back if the transaction is aborted.
 * <p>
 * Changes inside a transaction go through {@link #setStackSilently(int, ItemStack)}, which must have <strong>no side effects</strong>
 * (no packets, block updates, recipe refreshes). Once the root transaction commits, the slot puts the original stack back silently and
 * replays the final stack through {@link #setStackWithSideEffects(int, ItemStack)}, so the owner reacts exactly once, as if the change
 * had been made outside a transaction. Simulations (aborted transactions) therefore never reach the owner's side effects.
 * <p>
 * PORT 1.21.10 (transfer step 1): replaces the legacy {@code IItemHandler}s we registered as capabilities. Since phase 6 it is also the base of
 * inventories that own their stacks (chests, see {@code ChestItemHandler}); menus set slots through {@link #setStackInSlot(int, ItemStack)}.
 */
public abstract class SlotStorageItemHandler implements ResourceHandler<ItemResource> {
  private final List<Slot> slots = new ArrayList<>();

  /** Gets the live stack in the given slot */
  protected abstract ItemStack getStack(int slot);

  /** Sets the stack in the given slot without any side effect, used inside transactions and to revert them */
  protected abstract void setStackSilently(int slot, ItemStack stack);

  /** Sets the stack in the given slot as a normal change, running the owner's side effects. Called once per changed slot on root commit. */
  protected abstract void setStackWithSideEffects(int slot, ItemStack stack);

  /** Gets the maximum amount of the given resource the slot holds, {@code resource} may be empty to ask for the general limit */
  protected abstract int getSlotCapacity(int slot, ItemResource resource);

  /** Checks if the given resource is valid in the slot, the resource is never empty */
  protected boolean isItemValid(int slot, ItemResource resource) {
    return true;
  }

  /** Checks if the given resource may be inserted into the slot right now, in addition to {@link #isItemValid(int, ItemResource)} */
  protected boolean canInsert(int slot, ItemResource resource) {
    return true;
  }

  /** Checks if the given resource may be extracted from the slot right now */
  protected boolean canExtract(int slot, ItemResource resource) {
    return true;
  }

  /**
   * Sets the stack in the slot directly, outside of any transaction, with side effects. Equivalent to the legacy {@code setStackInSlot},
   * used for instance by client slot sync packets.
   */
  public void setStackInSlot(int slot, ItemStack stack) {
    Objects.checkIndex(slot, size());
    setStackWithSideEffects(slot, stack);
  }

  /** Gets the journal of the given slot */
  private Slot slot(int index) {
    Objects.checkIndex(index, size());
    while (slots.size() <= index) {
      slots.add(new Slot(slots.size()));
    }
    return slots.get(index);
  }

  @Override
  public ItemResource getResource(int index) {
    Objects.checkIndex(index, size());
    return ItemResource.of(getStack(index));
  }

  @Override
  public long getAmountAsLong(int index) {
    Objects.checkIndex(index, size());
    return getStack(index).getCount();
  }

  @Override
  public long getCapacityAsLong(int index, ItemResource resource) {
    return slot(index).getCapacityAsLong(0, resource);
  }

  @Override
  public boolean isValid(int index, ItemResource resource) {
    return slot(index).isValid(0, resource);
  }

  @Override
  public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
    TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
    if (!canInsert(index, resource)) {
      return 0;
    }
    return slot(index).insert(0, resource, amount, transaction);
  }

  @Override
  public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
    TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
    if (!canExtract(index, resource)) {
      return 0;
    }
    return slot(index).extract(0, resource, amount, transaction);
  }

  @Override
  public String toString() {
    return getClass().getName() + "[size=" + size() + "]";
  }

  /** Journal of a single slot */
  private class Slot extends ItemStackResourceHandler {
    private final int index;

    private Slot(int index) {
      this.index = index;
    }

    @Override
    protected ItemStack getStack() {
      return SlotStorageItemHandler.this.getStack(index);
    }

    @Override
    protected void setStack(ItemStack stack) {
      setStackSilently(index, stack);
    }

    @Override
    protected boolean isValid(ItemResource resource) {
      return isItemValid(index, resource);
    }

    @Override
    protected int getCapacity(ItemResource resource) {
      return getSlotCapacity(index, resource);
    }

    @Override
    protected void onRootCommit(ItemStack original) {
      // replay the final stack over the original, so the owner sees a single normal change
      ItemStack current = getStack();
      setStackSilently(index, original);
      setStackWithSideEffects(index, current);
    }
  }
}
