package slimeknights.tconstruct.library.transfer;

import net.neoforged.neoforge.transfer.IndexModifier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

/**
 * NeoForge's native menu slot, reporting its handler index as the slot index like the legacy {@code SlotItemHandler} did.
 * {@link ResourceHandlerSlot} sits on an empty dummy container at index 0, but our screens ({@code SideInventoryScreen},
 * {@code ScalingChestScreen}, {@code GuiMeltingModule}) and Mantle's {@code WrapperSlot} read the inventory index from the slot.
 */
public class ModifiableItemHandlerSlot extends ResourceHandlerSlot {
  private final int index;

  public ModifiableItemHandlerSlot(ResourceHandler<ItemResource> handler, IndexModifier<ItemResource> modifier, int index, int x, int y) {
    super(handler, modifier, index, x, y);
    this.index = index;
  }

  @Override
  public int getSlotIndex() {
    return index;
  }

  @Override
  public int getContainerSlot() {
    return index;
  }
}
