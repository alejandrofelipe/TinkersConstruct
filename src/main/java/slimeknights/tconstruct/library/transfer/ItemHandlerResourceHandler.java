package slimeknights.tconstruct.library.transfer;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Native item handler over the stack storage of one of our legacy {@link IItemHandlerModifiable}s (such as an {@code ItemStackHandler}
 * subclass), see {@link SlotStorageItemHandler} for the transaction semantics: changes are silent until the root transaction commits,
 * then replayed through {@link IItemHandlerModifiable#setStackInSlot(int, ItemStack)}.
 * <p>
 * This is not a general legacy-to-native adapter: it needs direct, side effect free access to the storage ({@link SilentSetter}), and
 * only reads the legacy handler's slot limits and validity, matching {@code ItemStackHandler#insertItem} (limit capped by the max stack size).
 */
@SuppressWarnings("removal")  // legacy storage until phase 6
public class ItemHandlerResourceHandler extends SlotStorageItemHandler {
  private final IItemHandlerModifiable handler;
  private final SilentSetter silentSetter;

  /**
   * @param handler       Legacy handler owning the stacks
   * @param silentSetter  Sets a stack in the storage without side effects (no {@code onContentsChanged})
   */
  public ItemHandlerResourceHandler(IItemHandlerModifiable handler, SilentSetter silentSetter) {
    this.handler = handler;
    this.silentSetter = silentSetter;
  }

  @Override
  public int size() {
    return handler.getSlots();
  }

  @Override
  protected ItemStack getStack(int slot) {
    return handler.getStackInSlot(slot);
  }

  @Override
  protected void setStackSilently(int slot, ItemStack stack) {
    silentSetter.set(slot, stack);
  }

  @Override
  protected void setStackWithSideEffects(int slot, ItemStack stack) {
    handler.setStackInSlot(slot, stack);
  }

  @Override
  protected int getSlotCapacity(int slot, ItemResource resource) {
    int limit = handler.getSlotLimit(slot);
    return resource.isEmpty() ? limit : Math.min(limit, resource.getMaxStackSize());
  }

  @Override
  protected boolean isItemValid(int slot, ItemResource resource) {
    return handler.isItemValid(slot, resource.toStack());
  }

  /** Sets a stack in the storage without side effects */
  @FunctionalInterface
  public interface SilentSetter {
    void set(int slot, ItemStack stack);
  }
}
