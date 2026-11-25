package slimeknights.tconstruct.tables.block.entity.inventory;

import lombok.Getter;
import net.minecraft.world.item.ItemStack;

/** Base logic for scaling chest inventories */
public abstract class ScalingChestItemHandler extends ChestItemHandler {
  /** Default maximum size */
  protected static final int DEFAULT_MAX = 256;
  /** Current size for display in containers */
  @Getter
  private int visualSize = 1;

  public ScalingChestItemHandler(int size) {
    super(size);
  }

  public ScalingChestItemHandler() {
    this(DEFAULT_MAX);
  }

  @Override
  public abstract boolean isItemValid(int slot, ItemStack stack);

  @Override
  protected void onLoad() {
    int newLimit = size();
    if (newLimit > 1 && this.getStackInSlot(newLimit - 1).isEmpty()) {
      while (newLimit > 1 && this.getStackInSlot(newLimit - 2).isEmpty()) {
        newLimit--;
      }
    }
    this.visualSize = newLimit;
  }

  /** Updates the visual size of the inventory */
  private void updateVisualSize(int slotChanged, ItemStack stack) {
    // if the slot is too large, nothing to do
    int maxSlots = size();
    if (slotChanged >= maxSlots) {
      return;
    }
    // if the slot is past the current one, update to there
    if (stack.isEmpty()) {
      // if the current index was the last slot, decrease size
      if (slotChanged + 1 == visualSize || (slotChanged + 2 == visualSize && this.getStackInSlot(visualSize - 1).isEmpty())) {
        while (visualSize > 1 && this.getStackInSlot(visualSize - 2).isEmpty()) {
          visualSize--;
        }
      }
    } else {
      // if the current index is past the max, increase visual size to this plus 1
      if (visualSize < maxSlots && visualSize < slotChanged + 2) {
        visualSize = slotChanged + 2;
      }
    }
  }

  /* Hook in visual size update, also runs once per slot when a transaction commits */

  @Override
  protected void setStackWithSideEffects(int slot, ItemStack stack) {
    super.setStackWithSideEffects(slot, stack);
    updateVisualSize(slot, stack);
  }
}
