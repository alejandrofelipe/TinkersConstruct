package slimeknights.tconstruct.smeltery.block.entity.inventory;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import slimeknights.mantle.transfer.TransferHelper;
import slimeknights.tconstruct.library.fluid.IMultitankListChange;

import java.util.function.Consumer;

/**
 * Fluid handler of a duct, exposing only the smeltery fluid matching the duct's filter item.
 * <p>
 * PORT 1.21.10 (transfer step 1): native {@link ResourceHandler} over the smeltery's native handler; inserts and extracts are forwarded
 * with the caller's transaction, so the smeltery journals them. Like the legacy wrapper they go to the whole tank (the smeltery merges by
 * fluid), so the index is only used to read; the index mapping is refreshed by the tank list listeners, which run when a commit or a revert
 * changes the list layout, and is never cached while a transaction is open.
 */
public class DuctTankWrapper implements ResourceHandler<FluidResource> {
  private final ResourceHandler<FluidResource> parent;
  private final DuctItemHandler itemHandler;
  private int[] tankMapping;

  public DuctTankWrapper(ResourceHandler<FluidResource> parent, DuctItemHandler itemHandler) {
    this.parent = parent;
    this.itemHandler = itemHandler;
    // clear cache when the fluid changes or the smeltery list changes
    Consumer<DuctTankWrapper> consumer = self -> self.tankMapping = null;
    itemHandler.addListener(this, consumer);
    if (parent instanceof IMultitankListChange notifier) {
      notifier.addTankListListener(this, consumer);
    }
  }

  /**
   * Gets the mapping from index to matching tank. While a transaction is open the parent's list may hold uncommitted changes that are
   * reverted later, so the mapping is computed fresh and not cached; outside transactions it is cached until the tank list changes.
   */
  private int[] getTankMapping() {
    if (TransferHelper.isTransactionOpen()) {
      return computeTankMapping();
    }
    if (tankMapping == null) {
      tankMapping = computeTankMapping();
    }
    return tankMapping;
  }

  /** Computes the mapping from index to matching tank */
  private int[] computeTankMapping() {
    FluidStack filter = itemHandler.getFluid();
    int count = parent.size();
    if (filter.isEmpty()) {
      if (count > 0 && parent.getResource(count - 1).isEmpty()) {
        return new int[] { count - 1 };
      }
      return new int[0];
    }
    IntList list = new IntArrayList(count);
    for (int i = 0; i < count; i++) {
      FluidResource contained = parent.getResource(i);
      if (contained.isEmpty() || contained.matches(filter)) {
        list.add(i);
      }
    }
    return list.toIntArray();
  }

  /** Maps the index to a parent index, -1 if out of range or stale */
  private int map(int index) {
    int[] mapping = getTankMapping();
    if (index < 0 || index >= mapping.length || mapping[index] >= parent.size()) {
      return -1;
    }
    return mapping[index];
  }

  /** Checks if the resource matches the duct filter */
  private boolean matchesFilter(FluidResource resource) {
    FluidStack filter = itemHandler.getFluid();
    return !filter.isEmpty() && resource.matches(filter);
  }

  /* Properties */

  @Override
  public int size() {
    return getTankMapping().length;
  }

  @Override
  public FluidResource getResource(int index) {
    int mapped = map(index);
    return mapped < 0 ? FluidResource.EMPTY : parent.getResource(mapped);
  }

  @Override
  public long getAmountAsLong(int index) {
    int mapped = map(index);
    return mapped < 0 ? 0 : parent.getAmountAsLong(mapped);
  }

  @Override
  public long getCapacityAsLong(int index, FluidResource resource) {
    int mapped = map(index);
    return mapped < 0 ? 0 : parent.getCapacityAsLong(mapped, resource);
  }

  @Override
  public boolean isValid(int index, FluidResource resource) {
    return matchesFilter(resource);
  }

  /* Interactions */

  @Override
  public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
    return insert(resource, amount, transaction);
  }

  @Override
  public int insert(FluidResource resource, int amount, TransactionContext transaction) {
    TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
    if (!matchesFilter(resource)) {
      return 0;
    }
    return parent.insert(resource, amount, transaction);
  }

  @Override
  public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
    return extract(resource, amount, transaction);
  }

  @Override
  public int extract(FluidResource resource, int amount, TransactionContext transaction) {
    TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
    if (!matchesFilter(resource)) {
      return 0;
    }
    return parent.extract(resource, amount, transaction);
  }
}
