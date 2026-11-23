package slimeknights.tconstruct.library.transfer;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import slimeknights.mantle.inventory.SingleItemHandler;

import java.util.function.Consumer;

/**
 * Native item handler over the single slot of a Mantle {@link SingleItemHandler}, see {@link SlotStorageItemHandler} for the transaction
 * semantics: changes are silent until the root transaction commits, then replayed through {@link SingleItemHandler#setStack(ItemStack)}.
 * <p>
 * PORT 1.21.10 (transfer step 1): replaces registering the legacy handler as the block's item capability. The capacity is the slot limit,
 * capped to the item's max stack size (the legacy handler ignored the max stack size, which let automation over-stack unstackable items).
 */
@SuppressWarnings("removal")  // SingleItemHandler is the legacy storage until phase 6
public class SingleItemResourceHandler extends SlotStorageItemHandler {
  private final SingleItemHandler<?> handler;
  private final Consumer<ItemStack> silentSetter;

  /**
   * @param handler       Legacy handler holding the stack
   * @param silentSetter  Sets the stack without the side effects of {@code handler.setStack} overrides, typically {@code super.setStack}
   */
  public SingleItemResourceHandler(SingleItemHandler<?> handler, Consumer<ItemStack> silentSetter) {
    this.handler = handler;
    this.silentSetter = silentSetter;
  }

  @Override
  public int size() {
    return 1;
  }

  @Override
  protected ItemStack getStack(int slot) {
    return handler.getStack();
  }

  @Override
  protected void setStackSilently(int slot, ItemStack stack) {
    silentSetter.accept(stack);
  }

  @Override
  protected void setStackWithSideEffects(int slot, ItemStack stack) {
    handler.setStack(stack);
  }

  @Override
  protected int getSlotCapacity(int slot, ItemResource resource) {
    int limit = handler.getSlotLimit(0);
    return resource.isEmpty() ? limit : Math.min(limit, resource.getMaxStackSize());
  }

  @Override
  protected boolean isItemValid(int slot, ItemResource resource) {
    return handler.isItemValid(0, resource.toStack());
  }
}
