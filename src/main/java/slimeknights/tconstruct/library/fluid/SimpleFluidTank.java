package slimeknights.tconstruct.library.fluid;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.Objects;

/**
 * Native single tank over fluid storage owned by someone else (e.g. the tank of a tool), with more control over the storage than
 * Mantle's {@code SingleFluidTank}.
 * <p>
 * PORT 1.21.10 (transfer): was an {@code IFluidTank}/{@code IFluidHandler} interface. Inserts and extracts write the new fluid through
 * {@link #setFluid(FluidStack)} right away (which must have no side effects), journaled: an aborted transaction writes the original
 * fluid back, and {@link #onContentsChanged(FluidStack)} runs once the root transaction commits with a different fluid.
 */
public abstract class SimpleFluidTank implements ResourceHandler<FluidResource> {
  /** Journal of the fluid for native transfers */
  private final SnapshotJournal<FluidStack> journal = new SnapshotJournal<>() {
    @Override
    protected FluidStack createSnapshot() {
      return getFluid().copy();
    }

    @Override
    protected void revertToSnapshot(FluidStack snapshot) {
      setFluid(snapshot);
    }

    @Override
    protected void onRootCommit(FluidStack original) {
      FluidStack fluid = getFluid();
      if (!FluidStack.matches(original, fluid)) {
        onContentsChanged(fluid);
      }
    }
  };

  /** Gets the contained fluid */
  public abstract FluidStack getFluid();

  /** Stores the fluid, without side effects as it is also called inside transactions and to revert them */
  public abstract void setFluid(FluidStack fluid);

  /** Gets the capacity of the tank */
  public abstract int getCapacity();

  /** Checks if the fluid is valid for the tank */
  public boolean isFluidValid(FluidStack stack) {
    return true;
  }

  /**
   * Called once the root transaction of a native change commits, if the fluid changed
   * @param updated  New fluid
   */
  protected void onContentsChanged(FluidStack updated) {}


  /* Native transfer */

  @Override
  public int size() {
    return 1;
  }

  @Override
  public FluidResource getResource(int index) {
    Objects.checkIndex(index, 1);
    return FluidResource.of(getFluid());
  }

  @Override
  public long getAmountAsLong(int index) {
    Objects.checkIndex(index, 1);
    return getFluid().getAmount();
  }

  @Override
  public long getCapacityAsLong(int index, FluidResource resource) {
    Objects.checkIndex(index, 1);
    return resource.isEmpty() || isFluidValid(resource.toStack(1)) ? getCapacity() : 0;
  }

  @Override
  public boolean isValid(int index, FluidResource resource) {
    Objects.checkIndex(index, 1);
    return !resource.isEmpty() && isFluidValid(resource.toStack(1));
  }

  @Override
  public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
    Objects.checkIndex(index, 1);
    TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
    if (amount == 0 || !isFluidValid(resource.toStack(amount))) {
      return 0;
    }
    FluidStack fluid = getFluid();
    if (!fluid.isEmpty() && !resource.matches(fluid)) {
      return 0;
    }
    int filled = Math.min(getCapacity() - fluid.getAmount(), amount);
    if (filled <= 0) {
      return 0;
    }
    journal.updateSnapshots(transaction);
    setFluid(resource.toStack(fluid.getAmount() + filled));
    return filled;
  }

  @Override
  public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
    Objects.checkIndex(index, 1);
    TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
    FluidStack fluid = getFluid();
    if (amount == 0 || fluid.isEmpty() || !resource.matches(fluid)) {
      return 0;
    }
    int drained = Math.min(amount, fluid.getAmount());
    journal.updateSnapshots(transaction);
    setFluid(drained == fluid.getAmount() ? FluidStack.EMPTY : fluid.copyWithAmount(fluid.getAmount() - drained));
    return drained;
  }
}
