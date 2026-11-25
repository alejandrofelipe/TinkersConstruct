package slimeknights.tconstruct.library.transfer;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.IndexModifier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.Optional;

/**
 * NeoForge's native menu slot, reporting its handler index as the slot index like the legacy {@code SlotItemHandler} did.
 * {@link ResourceHandlerSlot} sits on an empty dummy container at index 0, but our screens ({@code SideInventoryScreen},
 * {@code ScalingChestScreen}, {@code GuiMeltingModule}) and Mantle's {@code WrapperSlot} read the inventory index from the slot.
 * <p>
 * Two safety nets on top of NeoForge's slot:
 * <ul>
 *   <li>The handler may shrink while the menu is open (a smeltery re-forming smaller, a tool losing slots): an index past the handler's
 *   size reads as empty, takes nothing and gives nothing, like the legacy slot's {@code validSlot} guard, instead of throwing.</li>
 *   <li>Over a {@linkplain ModifiableItemHandler#transactional transactional setter} (inventories exposing only a native handler) a set can be
 *   refused, while vanilla menu logic takes the items from the cursor or source slot before setting. So placing is bounded by what a
 *   simulated transfer accepts ({@link #mayPlace}, {@link #getMaxStackSize(ItemStack)}, like the legacy slot's simulated insert) and taking
 *   by what a simulated extract gives ({@link #mayPickup} needs the whole stack to be extractable, as swaps and quick moves clear the slot;
 *   {@link #tryRemove} is clamped), so every set vanilla then makes is one the handler accepts.</li>
 * </ul>
 */
public class ModifiableItemHandlerSlot extends ResourceHandlerSlot {
  private final ResourceHandler<ItemResource> handler;
  private final int index;
  /** If true, sets go through the handler and may be refused */
  private final boolean transactional;

  public ModifiableItemHandlerSlot(ResourceHandler<ItemResource> handler, IndexModifier<ItemResource> modifier, int index, int x, int y) {
    super(handler, modifier, index, x, y);
    this.handler = handler;
    this.index = index;
    this.transactional = modifier instanceof ModifiableItemHandler.TransactionalModifier;
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

  @Override
  public boolean mayPlace(ItemStack stack) {
    if (!inRange() || !super.mayPlace(stack)) {
      return false;
    }
    return !transactional || simulateInsert(stack) > 0;
  }

  @Override
  public int getMaxStackSize(ItemStack stack) {
    if (!inRange()) {
      return 0;
    }
    if (!transactional) {
      return super.getMaxStackSize(stack);
    }
    ItemStack current = super.getStackCopy();
    int insertable = simulateInsert(stack);
    return !current.isEmpty() && ItemStack.isSameItemSameComponents(current, stack) ? current.getCount() + insertable : insertable;
  }

  /**
   * Simulates how much of the stack's item the handler takes into this slot, after taking out a different item it holds (a swap).
   * Menus run outside transactions, but nest just in case; nothing is ever committed.
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
        if (handler.extract(index, current, amount, tx) != amount) {
          return 0;
        }
      }
      int capacity = handler.getCapacityAsInt(index, resource);
      return capacity <= 0 ? 0 : handler.insert(index, resource, capacity, tx);
    }
  }


  /* Taking */

  /** Simulates extracting up to the given amount of the slot's item */
  private int simulateExtract(int amount) {
    ItemResource current = handler.getResource(index);
    if (current.isEmpty() || amount <= 0) {
      return 0;
    }
    try (Transaction tx = TransferUtil.openNestedOrRoot()) {
      return handler.extract(index, current, amount, tx);
    }
  }

  @Override
  public boolean mayPickup(Player player) {
    if (!inRange()) {
      return false;
    }
    if (!transactional) {
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
    if (transactional) {
      // remove(count) sets the remainder, so only take what the handler gives
      count = simulateExtract(Math.min(count, decrement));
      if (count <= 0) {
        return Optional.empty();
      }
    }
    return super.tryRemove(count, decrement, player);
  }
}
