package slimeknights.tconstruct.library.transfer;

import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.EmptyResourceHandler;
import net.neoforged.neoforge.transfer.IndexModifier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import slimeknights.mantle.inventory.SingleItemHandler;

import javax.annotation.Nullable;

/**
 * A native item handler paired with a setter that puts a stack into one of its slots directly, which menu slots (NeoForge's
 * {@link net.neoforged.neoforge.transfer.item.ResourceHandlerSlot}) and client slot sync packets need. The setter is a plain change outside
 * of any transaction, it bypasses the handler's validation where the owner allows it (like the legacy {@code setStackInSlot}).
 * <p>
 * PORT 1.21.10 (transfer phase 6): replaces the legacy {@code IItemHandlerModifiable} used by menus and slot sync.
 * @param handler   Native handler, used for reading, validity and capacity
 * @param modifier  Sets the contents of a slot, outside transactions
 */
public record ModifiableItemHandler(ResourceHandler<ItemResource> handler, IndexModifier<ItemResource> modifier) {
  /** Handler with no slots */
  public static final ModifiableItemHandler EMPTY = new ModifiableItemHandler(EmptyResourceHandler.instance(), (index, resource, amount) -> {});

  /** Gets the number of slots */
  public int size() {
    return handler.size();
  }

  /** Gets a copy of the stack in the given slot, empty if the slot does not exist */
  public ItemStack getStack(int index) {
    return index >= 0 && index < handler.size() ? ItemUtil.getStack(handler, index) : ItemStack.EMPTY;
  }

  /** Sets the stack in the given slot, ignored if the slot does not exist */
  public void setStack(int index, ItemStack stack) {
    if (index >= 0 && index < handler.size()) {
      modifier.set(index, ItemResource.of(stack), stack.getCount());
    }
  }

  /** Creates a menu slot for the given index */
  public Slot createSlot(int index, int x, int y) {
    return new ModifiableItemHandlerSlot(handler, modifier, index, x, y);
  }


  /* Factories */

  /** Converts a resource and amount from an {@link IndexModifier} into a stack */
  public static ItemStack toStack(ItemResource resource, int amount) {
    return resource.isEmpty() || amount <= 0 ? ItemStack.EMPTY : resource.toStack(amount);
  }

  /** Creates a handler from a native handler and a stack setter */
  public static ModifiableItemHandler of(ResourceHandler<ItemResource> handler, StackSetter setter) {
    return new ModifiableItemHandler(handler, (index, resource, amount) -> setter.set(index, toStack(resource, amount)));
  }

  /** Creates a handler over slot storage, set through {@link SlotStorageItemHandler#setStackInSlot(int, ItemStack)} */
  public static ModifiableItemHandler ofSlots(SlotStorageItemHandler handler) {
    return of(handler, handler::setStackInSlot);
  }

  /** Creates a handler over a single item handler, set through {@link SingleItemHandler#setStack(ItemStack)} */
  public static ModifiableItemHandler ofSingle(SingleItemHandler<?> handler) {
    return of(handler, (index, stack) -> handler.setStack(stack));
  }

  /** Creates a handler over a plain native stack handler, set through {@code ItemStacksResourceHandler#set} */
  public static ModifiableItemHandler ofStacks(ItemStacksResourceHandler handler) {
    return new ModifiableItemHandler(handler, handler::set);
  }

  /**
   * Creates a handler over a vanilla container, like the legacy {@code InvWrapper}/{@code SidedInvWrapper}.
   * @param container  Container
   * @param side       Side accessed, limits the slots for a {@link WorldlyContainer}, null for all slots
   */
  public static ModifiableItemHandler ofContainer(Container container, @Nullable Direction side) {
    if (side != null && container instanceof WorldlyContainer worldly) {
      return of(new WorldlyContainerWrapper(worldly, side), (index, stack) -> {
        worldly.setItem(worldly.getSlotsForFace(side)[index], stack);
        worldly.setChanged();
      });
    }
    return of(VanillaContainerWrapper.of(container), (index, stack) -> {
      container.setItem(index, stack);
      container.setChanged();
    });
  }

  /**
   * Creates a handler over any native handler, for inventories that only expose the native capability. The setter moves the
   * difference through the handler in a root transaction, committed only if every move went through completely and the slot then holds
   * exactly the given stack, so it is subject to the handler's validation and may refuse a set. Menu slots created from it
   * ({@link ModifiableItemHandlerSlot}) bound what vanilla places and takes by simulated transfers, so vanilla never takes items from the
   * cursor for a set the handler would refuse.
   */
  public static ModifiableItemHandler transactional(ResourceHandler<ItemResource> handler) {
    return new ModifiableItemHandler(handler, new TransactionalModifier(handler));
  }

  /** Setter of {@link #transactional(ResourceHandler)}, recognized by {@link ModifiableItemHandlerSlot} */
  record TransactionalModifier(ResourceHandler<ItemResource> handler) implements IndexModifier<ItemResource> {
    @Override
    public void set(int index, ItemResource resource, int amount) {
      ItemResource current = handler.getResource(index);
      int currentAmount = current.isEmpty() ? 0 : handler.getAmountAsInt(index);
      try (Transaction tx = Transaction.openRoot()) {
        boolean success;
        if (!current.isEmpty() && current.equals(resource)) {
          // same item: move just the difference
          int difference = amount - currentAmount;
          if (difference > 0) {
            success = handler.insert(index, resource, difference, tx) == difference;
          } else if (difference < 0) {
            success = handler.extract(index, resource, -difference, tx) == -difference;
          } else {
            success = true;
          }
        } else {
          // different item: take out the old one, put in the new one
          success = currentAmount == 0 || handler.extract(index, current, currentAmount, tx) == currentAmount;
          if (success && !resource.isEmpty() && amount > 0) {
            success = handler.insert(index, resource, amount, tx) == amount;
          }
        }
        // the handler may have put the items elsewhere (e.g. merged into another slot): the slot itself must hold the stack
        if (success) {
          boolean empty = resource.isEmpty() || amount <= 0;
          success = empty ? handler.getResource(index).isEmpty() || handler.getAmountAsInt(index) == 0
                          : handler.getResource(index).equals(resource) && handler.getAmountAsInt(index) == amount;
        }
        if (success) {
          tx.commit();
        }
      }
    }
  }

  /** Sets a stack in a slot */
  @FunctionalInterface
  public interface StackSetter {
    void set(int index, ItemStack stack);
  }
}
