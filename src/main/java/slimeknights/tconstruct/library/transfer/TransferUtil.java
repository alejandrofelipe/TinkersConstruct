package slimeknights.tconstruct.library.transfer;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import slimeknights.mantle.transfer.TransferHelper;

import javax.annotation.Nullable;

/**
 * Transaction safe helpers for querying and filling the fluid handler of a single container item, and for immediate fills and drains of
 * block and internal fluid handlers (the replacement of the legacy {@code fill}/{@code drain} calls).
 * <p>
 * PORT 1.21.10 (transfer step 1): Mantle's legacy item lookups ({@code TransferHelper.getLegacyFluidHandler(ItemStack)}) open a root
 * transaction on every call, so they throw when used inside a transaction. Code that may run inside one, such as validity checks of our
 * native handlers or recipe matching started from a native fill (casting tables), uses these helpers instead: every method works on an
 * isolated one item copy of the stack, so the passed stack is never modified, and opens a nested transaction when one is already open.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TransferUtil {
  /**
   * Opens a transaction nested in the currently open transaction, or a root transaction if none is open.
   * Only use it for changes to isolated copies (such as the slot of {@link #isolate(ItemStack)}), as committing a nested transaction
   * leaves the final outcome to the outer transaction.
   * @see TransferHelper#openNestedOrRoot()
   */
  public static Transaction openNestedOrRoot() {
    return TransferHelper.openNestedOrRoot();
  }

  /* Block and internal fluid handlers */

  /** Gets the fluid at the given index of the handler, empty if the index is out of range. Read only, safe anywhere. */
  public static FluidStack getFluid(ResourceHandler<FluidResource> handler, int index) {
    if (index < 0 || index >= handler.size()) {
      return FluidStack.EMPTY;
    }
    return FluidUtil.getStack(handler, index);
  }

  /** Gets the capacity of the given index of the handler for any fluid, like the legacy {@code getTankCapacity}; 0 if the index is out of range */
  public static int getCapacity(ResourceHandler<FluidResource> handler, int index) {
    if (index < 0 || index >= handler.size()) {
      return 0;
    }
    return handler.getCapacityAsInt(index, FluidResource.EMPTY);
  }

  /**
   * Fills the handler with the given fluid, like the legacy {@code fill(fluid, action)}. Opens a transaction nested in the open one, or a
   * root transaction if none is open, so it is safe anywhere; inside an open transaction the outer transaction decides the outcome.
   * @param handler   Handler to fill
   * @param fluid     Fluid to fill, not modified
   * @param simulate  If true, nothing changes
   * @return  Amount filled
   */
  public static int fill(ResourceHandler<FluidResource> handler, FluidStack fluid, boolean simulate) {
    if (fluid.isEmpty()) {
      return 0;
    }
    try (Transaction tx = openNestedOrRoot()) {
      int filled = handler.insert(FluidResource.of(fluid), fluid.getAmount(), tx);
      if (!simulate && filled > 0) {
        tx.commit();
      }
      return filled;
    }
  }

  /**
   * Drains the given fluid from the handler, like the legacy {@code drain(fluid, action)}. Same transaction rules as {@link #fill(ResourceHandler, FluidStack, boolean)}.
   * @param handler   Handler to drain
   * @param fluid     Fluid to drain, its amount is the maximum; not modified
   * @param simulate  If true, nothing changes
   * @return  Fluid drained, empty if none
   */
  public static FluidStack drain(ResourceHandler<FluidResource> handler, FluidStack fluid, boolean simulate) {
    if (fluid.isEmpty()) {
      return FluidStack.EMPTY;
    }
    FluidResource resource = FluidResource.of(fluid);
    try (Transaction tx = openNestedOrRoot()) {
      int drained = handler.extract(resource, fluid.getAmount(), tx);
      if (drained <= 0) {
        return FluidStack.EMPTY;
      }
      if (!simulate) {
        tx.commit();
      }
      return resource.toStack(drained);
    }
  }

  /** Puts a one item copy of the stack in a new single slot handler, giving it an isolated item access */
  public static ItemStacksResourceHandler isolate(ItemStack stack) {
    ItemStacksResourceHandler slot = new ItemStacksResourceHandler(1);
    if (!stack.isEmpty()) {
      slot.set(0, ItemResource.of(stack), 1);
    }
    return slot;
  }

  /** Gets the stack in the first slot of the handler */
  public static ItemStack getStack(ResourceHandler<ItemResource> handler) {
    return handler.getResource(0).toStack(handler.getAmountAsInt(0));
  }

  /** Gets the native fluid handler of a one item copy of the stack, null if none. Safe to query inside a transaction, but changes go to the copy. */
  @Nullable
  public static ResourceHandler<FluidResource> getFluidHandler(ItemStack stack) {
    if (stack.isEmpty()) {
      return null;
    }
    return ItemAccess.forHandlerIndex(isolate(stack), 0).getCapability(Capabilities.Fluid.ITEM);
  }

  /** Checks if a single item of the stack exposes a fluid handler. Safe inside a transaction. */
  public static boolean hasFluidHandler(ItemStack stack) {
    return getFluidHandler(stack) != null;
  }

  /** Checks if a single item of the stack exposes an item handler. Safe inside a transaction. */
  public static boolean hasItemHandler(ItemStack stack) {
    return !stack.isEmpty() && ItemAccess.forHandlerIndex(isolate(stack), 0).getCapability(Capabilities.Item.ITEM) != null;
  }

  /** Gets the fluid in the first tank of a single item of the stack, empty if none. Safe inside a transaction. */
  public static FluidStack getFirstFluid(ItemStack stack) {
    ResourceHandler<FluidResource> handler = getFluidHandler(stack);
    if (handler == null || handler.size() == 0) {
      return FluidStack.EMPTY;
    }
    return FluidUtil.getStack(handler, 0);
  }

  /**
   * Simulates filling a single item of the stack with the given fluid, like the legacy {@code fill(fluid, SIMULATE)} on one container.
   * Safe inside a transaction.
   * @return  Amount that would be filled, 0 if the item has no fluid handler
   */
  public static int simulateFill(ItemStack stack, FluidStack fluid) {
    if (stack.isEmpty() || fluid.isEmpty()) {
      return 0;
    }
    ItemStacksResourceHandler slot = isolate(stack);
    ResourceHandler<FluidResource> handler = ItemAccess.forHandlerIndex(slot, 0).getCapability(Capabilities.Fluid.ITEM);
    if (handler == null) {
      return 0;
    }
    try (Transaction tx = openNestedOrRoot()) {
      return handler.insert(FluidResource.of(fluid), fluid.getAmount(), tx);
    }
  }

  /**
   * Fills a single item of the stack with the given fluid, like the legacy {@code fill(fluid, EXECUTE)} followed by {@code getContainer()}
   * on one container. The passed stack is not modified. Safe inside a transaction.
   * @return  Resulting container, or a one item copy of the stack if it has no fluid handler or nothing was filled
   */
  public static ItemStack fill(ItemStack stack, FluidStack fluid) {
    ItemStacksResourceHandler slot = isolate(stack);
    if (stack.isEmpty() || fluid.isEmpty()) {
      return getStack(slot);
    }
    ResourceHandler<FluidResource> handler = ItemAccess.forHandlerIndex(slot, 0).getCapability(Capabilities.Fluid.ITEM);
    if (handler != null) {
      try (Transaction tx = openNestedOrRoot()) {
        if (handler.insert(FluidResource.of(fluid), fluid.getAmount(), tx) > 0) {
          tx.commit();
        }
      }
    }
    // read the slot right away, an outer transaction may still revert the isolated copy later
    return getStack(slot);
  }
}
