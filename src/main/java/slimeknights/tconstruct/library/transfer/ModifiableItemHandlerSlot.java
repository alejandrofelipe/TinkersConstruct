package slimeknights.tconstruct.library.transfer;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.IndexModifier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.transfer.ModifiableItemHandler.TransactionalModifier;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * NeoForge's native menu slot, reporting its handler index as the slot index like the legacy {@code SlotItemHandler} did.
 * {@link ResourceHandlerSlot} sits on an empty dummy container at index 0, but our screens ({@code SideInventoryScreen},
 * {@code ScalingChestScreen}, {@code GuiMeltingModule}) and Mantle's {@code WrapperSlot} read the inventory index from the slot.
 * <p>
 * Safety nets on top of NeoForge's slot:
 * <ul>
 *   <li>The handler may shrink while the menu is open (a smeltery re-forming smaller, a tool losing slots): an index past the handler's
 *   size reads as empty, takes nothing and gives nothing, like the legacy slot's {@code validSlot} guard, instead of throwing.</li>
 *   <li>Over a {@linkplain ModifiableItemHandler#transactional transactional setter} (inventories exposing only a native handler) a set can be
 *   refused, while vanilla menu logic takes the items from the cursor or source slot before setting. So placing is bounded by what a
 *   simulated transfer accepts ({@link #getMaxStackSize(ItemStack)}, like the legacy slot's simulated insert; {@link #mayPlace} also
 *   accepts the item a full slot already holds, like a chest slot) and taking
 *   by what a simulated extract gives ({@link #mayPickup} needs the whole stack to be extractable, as swaps and quick moves clear the slot;
 *   {@link #tryRemove} is clamped), so the sets vanilla then makes are ones the handler normally accepts.</li>
 *   <li>Simulations cannot predict every handler (one spreading an insert over several slots non-linearly accepts a large simulated insert
 *   but no exact set), so the final write is checked too: {@link #safeInsert(ItemStack, int)} sets the slot before shrinking the cursor,
 *   and a refused {@link #setByPlayer(ItemStack, ItemStack)} (swaps, drags, number key swaps: vanilla already took the new stack) gives the
 *   items the slot did not take back to the player. The set is all or nothing, so the handler keeps none of what is given back.</li>
 * </ul>
 */
public class ModifiableItemHandlerSlot extends ResourceHandlerSlot {
  private final ResourceHandler<ItemResource> handler;
  private final int index;
  /** Setter that may refuse a set, null if sets always apply */
  @Nullable
  private final TransactionalModifier transactional;
  /** Player receiving the items of refused player sets, null if unknown */
  @Nullable
  private final Player player;

  public ModifiableItemHandlerSlot(ResourceHandler<ItemResource> handler, IndexModifier<ItemResource> modifier, @Nullable Player player, int index, int x, int y) {
    super(handler, modifier, index, x, y);
    this.handler = handler;
    this.index = index;
    this.transactional = modifier instanceof TransactionalModifier setter ? setter : null;
    this.player = player;
  }

  @Override
  public int getSlotIndex() {
    return index;
  }

  @Override
  public int getContainerSlot() {
    return index;
  }

  /** Checks if the slot still exists in the handler */
  private boolean inRange() {
    return index < handler.size();
  }


  /* Size guard */

  @Override
  protected ItemStack getStackCopy() {
    return inRange() ? super.getStackCopy() : ItemStack.EMPTY;
  }

  @Override
  protected void setStackCopy(ItemStack stack) {
    if (inRange()) {
      super.setStackCopy(stack);
    }
  }

  @Override
  public int getMaxStackSize() {
    return inRange() ? super.getMaxStackSize() : 0;
  }


  /* Placing */

  /**
   * Over a transactional setter, the slot accepts the item if a simulated transfer puts some of it in, or, when it already holds that item,
   * if its whole stack could be taken out and put back. Like a full chest slot, a full slot still accepts its own item, so vanilla does not
   * treat clicking it with more as picking up ({@code doClick} pulls the stack onto the cursor when {@code mayPlace} fails), and
   * {@link #allowModification} ({@code mayPickup && mayPlace(getItem())}) holds for a full slot whose contents can be replaced, such as a
   * tool, which stacks to 1. How much goes in is bounded by {@link #getMaxStackSize(ItemStack)}.
   */
  @Override
  public boolean mayPlace(ItemStack stack) {
    if (!inRange() || !super.mayPlace(stack)) {
      return false;
    }
    return transactional == null || simulateInsert(stack) > 0 || simulateReplace(stack);
  }

  /** Simulates taking out the slot's whole stack and putting the same item back, true if the stack's item is the slot's item and some goes back in */
  private boolean simulateReplace(ItemStack stack) {
    ItemResource resource = ItemResource.of(stack);
    ItemResource current = handler.getResource(index);
    if (current.isEmpty() || !current.equals(resource)) {
      return false;
    }
    try (Transaction tx = TransferUtil.openNestedOrRoot()) {
      int amount = handler.getAmountAsInt(index);
      if (handler.extract(index, current, amount, tx) != amount || amountInSlot(current) != 0) {
        return false;
      }
      return handler.insert(index, resource, amount, tx) > 0 && amountInSlot(resource) > 0;
    }
  }

  @Override
  public int getMaxStackSize(ItemStack stack) {
    if (!inRange()) {
      return 0;
    }
    if (transactional == null) {
      return super.getMaxStackSize(stack);
    }
    ItemStack current = super.getStackCopy();
    int insertable = simulateInsert(stack);
    return !current.isEmpty() && ItemStack.isSameItemSameComponents(current, stack) ? current.getCount() + insertable : insertable;
  }

  /** Gets the amount of the given item held in this slot, 0 if it holds another item */
  private int amountInSlot(ItemResource resource) {
    return handler.getResource(index).equals(resource) ? handler.getAmountAsInt(index) : 0;
  }

  /**
   * Simulates how much of the stack's item the handler takes into this slot, after taking out a different item it holds (a swap).
   * Menus run outside transactions, but nest just in case; nothing is ever committed.
   * <p>
   * Measures the change of the slot itself rather than trusting the returned amounts: a handler may ignore the requested index (a
   * compacting storage putting items in another slot, a void slot keeping nothing), while the transactional setter only commits if the
   * slot ends up holding exactly the requested stack. Counting items that did not reach the slot would let vanilla take them from the
   * cursor or source for a set the setter then refuses, losing them.
   */
  private int simulateInsert(ItemStack stack) {
    if (stack.isEmpty()) {
      return 0;
    }
    ItemResource resource = ItemResource.of(stack);
    ItemResource current = handler.getResource(index);
    try (Transaction tx = TransferUtil.openNestedOrRoot()) {
      if (!current.isEmpty() && !current.equals(resource)) {
        int amount = handler.getAmountAsInt(index);
        // the old item must really leave this slot
        if (handler.extract(index, current, amount, tx) != amount || amountInSlot(current) != 0) {
          return 0;
        }
      }
      int capacity = handler.getCapacityAsInt(index, resource);
      if (capacity <= 0) {
        return 0;
      }
      int before = amountInSlot(resource);
      int inserted = handler.insert(index, resource, capacity, tx);
      return Math.max(0, Math.min(inserted, amountInSlot(resource) - before));
    }
  }


  /* Final write */

  /**
   * Same amounts as vanilla, but over a transactional setter the slot is set first and the stack only shrinks if the set went through, so
   * a refused set leaves everything on the cursor.
   */
  @Override
  public ItemStack safeInsert(ItemStack stack, int increment) {
    if (transactional == null || !inRange()) {
      return super.safeInsert(stack, increment);
    }
    if (stack.isEmpty() || !mayPlace(stack)) {
      return stack;
    }
    ItemStack current = super.getStackCopy();
    int amount = Math.min(Math.min(increment, stack.getCount()), getMaxStackSize(stack) - current.getCount());
    if (amount > 0 && (current.isEmpty() || ItemStack.isSameItemSameComponents(current, stack))) {
      if (transactional.trySet(index, ItemResource.of(stack), current.getCount() + amount)) {
        stack.shrink(amount);
      }
      // the menu may write back the last stack handed out by getItem, so refresh it to the real contents
      getItem();
    }
    return stack;
  }

  /**
   * Over a transactional setter, vanilla calls this after already taking the new stack from the cursor or another slot (and, for a swap,
   * handing out the old one), so a refused set would lose those items: the part of the new stack the slot did not take goes back to the
   * player instead (server side; the client's prediction is corrected by the server). For a swap, whose old stack vanilla already handed
   * out, the slot is still emptied when the handler allows it, so the old stack is not duplicated; when it refuses that too (contradicting
   * what {@link #mayPickup} simulated), the slot keeps the old stack and the copy vanilla handed out is taken back from the player.
   * <p>
   * A new stack of the slot's own item is not always "the slot's stack plus what the player gave": a number key swap from a smaller hotbar
   * stack of the same item hands the whole old stack to the hotbar and sets the slot to the smaller stack, so a new stack smaller than the
   * old one is handled as a swap. A larger one nets out to the difference whichever way vanilla got there (placing more, a drag, or a
   * number key swap moving more than the slot held, which also hands the old stack out).
   * <p>
   * Programmatic {@link #set(ItemStack)} calls (client sync, {@link TransferUtil#trySetSlot}, Mantle's stack merging, which all check the
   * result themselves) never give anything back.
   */
  @Override
  public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
    if (transactional == null || !inRange()) {
      super.setByPlayer(newStack, oldStack);
      return;
    }
    ItemStack current = super.getStackCopy();
    if (!transactional.trySet(index, ItemResource.of(newStack), newStack.getCount()) && !newStack.isEmpty()) {
      int refused;
      if (ItemStack.isSameItemSameComponents(current, newStack) && newStack.getCount() >= current.getCount()) {
        // the player gave just the difference
        refused = newStack.getCount() - current.getCount();
      } else {
        refused = newStack.getCount();
        // a swap (a smaller stack of the same item included): vanilla already handed out the old stack
        if (!current.isEmpty() && !transactional.trySet(index, ItemResource.EMPTY, 0)) {
          // nor can the slot be emptied, although mayPickup simulated it: the slot keeps the old stack, so take back the copy vanilla handed out
          takeBack(current);
        }
      }
      if (refused > 0 && isServerPlayer()) {
        TransferUtil.giveItemToPlayer(player, newStack.copyWithCount(refused));
      }
    }
    // the menu may write back the last stack handed out by getItem, so refresh it to the real contents
    getItem();
  }

  /** Checks if items can be given to or taken from the player: server side only, the client's prediction is corrected by the server */
  private boolean isServerPlayer() {
    return player != null && !player.level().isClientSide();
  }

  /**
   * Takes back a stack vanilla handed to the player before the slot refused to give it up: from the cursor (a cursor swap puts it there)
   * and the player's inventory (a number key swap). The same item with the same components is interchangeable, so any matching stack will
   * do. Logs an error if the player no longer holds all of it.
   */
  private void takeBack(ItemStack stack) {
    if (!isServerPlayer()) {
      return;
    }
    int remaining = stack.getCount();
    AbstractContainerMenu menu = player.containerMenu;
    ItemStack carried = menu.getCarried();
    if (ItemStack.isSameItemSameComponents(carried, stack)) {
      int taken = Math.min(remaining, carried.getCount());
      menu.setCarried(taken == carried.getCount() ? ItemStack.EMPTY : carried.copyWithCount(carried.getCount() - taken));
      remaining -= taken;
    }
    Inventory inventory = player.getInventory();
    for (int i = 0; remaining > 0 && i < inventory.getContainerSize(); i++) {
      ItemStack item = inventory.getItem(i);
      if (ItemStack.isSameItemSameComponents(item, stack)) {
        int taken = Math.min(remaining, item.getCount());
        inventory.removeItem(i, taken);
        remaining -= taken;
      }
    }
    if (remaining > 0) {
      TConstruct.LOG.error("Menu slot {} kept {} after handing it out, but {} of it could not be taken back from {}", index, stack, remaining, player.getName().getString());
    }
  }


  /* Taking */

  /**
   * Simulates extracting up to the given amount of the slot's item. Like {@link #simulateInsert(ItemStack)}, counts only what really left
   * this slot, as the setter checks the slot holds exactly the remainder.
   */
  private int simulateExtract(int amount) {
    ItemResource current = handler.getResource(index);
    if (current.isEmpty() || amount <= 0) {
      return 0;
    }
    try (Transaction tx = TransferUtil.openNestedOrRoot()) {
      int before = handler.getAmountAsInt(index);
      int extracted = handler.extract(index, current, amount, tx);
      return Math.max(0, Math.min(extracted, before - amountInSlot(current)));
    }
  }

  /**
   * Over a transactional setter, requires the whole stack to be extractable, not just one item: vanilla checks only this before clearing
   * the slot or setting a remainder computed without the handler (QUICK_MOVE moves the whole stack then sets the rest, SWAP clears the
   * slot, Mantle's {@code MultiModuleContainerMenu#notifySlotAfterTransfer} sets what is left), so a slot giving less than its whole stack must not be picked up from.
   */
  @Override
  public boolean mayPickup(Player player) {
    if (!inRange()) {
      return false;
    }
    if (transactional == null) {
      return super.mayPickup(player);
    }
    int amount = handler.getAmountAsInt(index);
    return amount > 0 && simulateExtract(amount) == amount;
  }

  @Override
  public Optional<ItemStack> tryRemove(int count, int decrement, Player player) {
    if (!inRange()) {
      return Optional.empty();
    }
    if (transactional != null) {
      // remove(count) sets the remainder, so only take what the handler gives
      count = simulateExtract(Math.min(count, decrement));
      if (count <= 0) {
        return Optional.empty();
      }
    }
    return super.tryRemove(count, decrement, player);
  }
}
