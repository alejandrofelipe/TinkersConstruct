package slimeknights.tconstruct.tables.block.entity.inventory;

import lombok.Setter;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;
import net.neoforged.neoforge.transfer.item.ItemResource;
import slimeknights.mantle.block.entity.MantleBlockEntity;
import slimeknights.tconstruct.library.transfer.SlotStorageItemHandler;

import org.jspecify.annotations.Nullable;
import java.util.Objects;

/**
 * Base inventory of tinker chests, registered as their item capability: native, changes made through a transaction are silent until the
 * root transaction commits, then replayed through {@link #setStackInSlot(int, ItemStack)} (see {@link SlotStorageItemHandler}).
 * <p>
 * PORT 1.21.10 (transfer phase 6): was the {@code IChestItemHandler} interface over NeoForge's legacy {@code ItemStackHandler}. Serializes
 * exactly like it: {@code Items} (list of {@link ItemStackWithSlot}, empty slots skipped) and {@code Size}; reading resizes to {@code Size},
 * defaulting to the current size, and clears the slots first.
 */
public abstract class ChestItemHandler extends SlotStorageItemHandler implements ValueIOSerializable, IScalingContainer {
  private static final String KEY_ITEMS = "Items";
  private static final String KEY_SIZE = "Size";

  /** Stacks in this inventory */
  private NonNullList<ItemStack> stacks;
  /** TE owning this inventory */
  @Setter @Nullable
  private MantleBlockEntity parent;

  protected ChestItemHandler(int size) {
    this.stacks = NonNullList.withSize(size, ItemStack.EMPTY);
  }

  /** Gets the maximum number of items in the given slot, before capping to the item's max stack size */
  public abstract int getSlotLimit(int slot);

  /** Checks if the given stack is valid in the slot, also called inside transactions so no side effects */
  public boolean isItemValid(int slot, ItemStack stack) {
    return true;
  }

  /** Called after a slot changed outside of a transaction, or once per slot when a root transaction commits */
  protected void onContentsChanged(int slot) {
    if (parent != null) {
      parent.setChangedFast();
    }
  }

  /** Called after the inventory was read */
  protected void onLoad() {}

  @Override
  public int size() {
    return stacks.size();
  }

  /** Gets the live stack in the given slot */
  public ItemStack getStackInSlot(int slot) {
    Objects.checkIndex(slot, size());
    return stacks.get(slot);
  }


  /* Slot storage */

  @Override
  protected ItemStack getStack(int slot) {
    return getStackInSlot(slot);
  }

  @Override
  protected void setStackSilently(int slot, ItemStack stack) {
    Objects.checkIndex(slot, size());
    stacks.set(slot, stack);
  }

  @Override
  protected void setStackWithSideEffects(int slot, ItemStack stack) {
    setStackSilently(slot, stack);
    onContentsChanged(slot);
  }

  @Override
  protected int getSlotCapacity(int slot, ItemResource resource) {
    int limit = getSlotLimit(slot);
    return resource.isEmpty() ? limit : Math.min(limit, resource.getMaxStackSize());
  }

  @Override
  protected boolean isItemValid(int slot, ItemResource resource) {
    return isItemValid(slot, resource.toStack());
  }


  /* Serializing */

  @Override
  public void serialize(ValueOutput output) {
    ValueOutput.TypedOutputList<ItemStackWithSlot> items = output.list(KEY_ITEMS, ItemStackWithSlot.CODEC);
    for (int i = 0; i < stacks.size(); i++) {
      ItemStack stack = stacks.get(i);
      if (!stack.isEmpty()) {
        items.add(new ItemStackWithSlot(i, stack));
      }
    }
    output.putInt(KEY_SIZE, stacks.size());
  }

  @Override
  public void deserialize(ValueInput input) {
    stacks = NonNullList.withSize(input.getIntOr(KEY_SIZE, stacks.size()), ItemStack.EMPTY);
    input.listOrEmpty(KEY_ITEMS, ItemStackWithSlot.CODEC).forEach(slot -> {
      if (slot.isValidInContainer(stacks.size())) {
        stacks.set(slot.slot(), slot.stack());
      }
    });
    onLoad();
  }
}
