package slimeknights.tconstruct.fluids.util;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Represents a capability handler for a container with a constant fluid, drained whole into its empty container.
 * <p>
 * PORT 1.21.10 (transfer step 1): the legacy {@code IFluidHandlerItem} implementation was ported natively in Mantle; this class keeps the
 * Tinkers name for addons and delegates to {@link slimeknights.mantle.item.ConstantFluidContainerWrapper} (stateless, the item swap goes
 * through the {@link ItemAccess}, amounts are totals over the accessed items).
 */
public class ConstantFluidContainerWrapper extends slimeknights.mantle.item.ConstantFluidContainerWrapper {
  /**
   * @param fluid           Fluid in a single container
   * @param access          Access to the filled container item
   * @param emptyContainer  Item left after draining, {@link ItemResource#EMPTY} to consume the container
   */
  public ConstantFluidContainerWrapper(FluidStack fluid, ItemAccess access, ItemResource emptyContainer) {
    super(fluid, access, emptyContainer);
  }

  /** Creates a handler using the crafting remainder of the item as the empty container */
  public ConstantFluidContainerWrapper(FluidStack fluid, ItemAccess access) {
    super(fluid, access);
  }
}
