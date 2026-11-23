package slimeknights.tconstruct.library.fluid;

import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import slimeknights.mantle.block.entity.MantleBlockEntity;
import slimeknights.tconstruct.common.network.TinkerNetwork;
import slimeknights.tconstruct.smeltery.network.FluidUpdatePacket;

import java.util.Objects;

/**
 * Fluid tank owned by a block entity, syncing changes to the client.
 * <p>
 * PORT 1.21.10 (transfer step 1): also a native {@link ResourceHandler} (one tank), the form registered as the block's fluid capability.
 * Native inserts and extracts change {@link #fluid} silently inside the transaction, journaled by a {@link SnapshotJournal}, and run
 * {@link #onContentsChanged()} (block entity update, packets, light, comparators) only once the root transaction commits, so a simulation
 * never notifies the block entity. The legacy {@link #fill(FluidStack, FluidAction)}/{@code drain} methods stay for internal use and
 * notify right away, as before; they must not be mixed with an open transaction that touched this tank.
 */
@SuppressWarnings("removal")  // FluidTank is the legacy base and storage until phase 6
public class FluidTankBase<T extends MantleBlockEntity> extends FluidTank implements ResourceHandler<FluidResource> {

  protected T parent;
  /** Journal of {@link #fluid} for native transfers */
  private final SnapshotJournal<FluidStack> journal = new SnapshotJournal<>() {
    @Override
    protected FluidStack createSnapshot() {
      return fluid.copy();
    }

    @Override
    protected void revertToSnapshot(FluidStack snapshot) {
      fluid = snapshot;
    }

    @Override
    protected void onRootCommit(FluidStack original) {
      if (!FluidStack.matches(original, fluid)) {
        onContentsChanged();
      }
    }
  };

  public FluidTankBase(int capacity, T parent) {
    super(capacity);
    this.parent = parent;
  }

  /* Native transfer */

  @Override
  public int size() {
    return 1;
  }

  @Override
  public FluidResource getResource(int index) {
    Objects.checkIndex(index, 1);
    return FluidResource.of(fluid);
  }

  @Override
  public long getAmountAsLong(int index) {
    Objects.checkIndex(index, 1);
    return fluid.getAmount();
  }

  @Override
  public long getCapacityAsLong(int index, FluidResource resource) {
    Objects.checkIndex(index, 1);
    return resource.isEmpty() || isFluidValid(resource.toStack(1)) ? capacity : 0;
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
    if (!isFluidValid(resource.toStack(1)) || (!fluid.isEmpty() && !resource.matches(fluid))) {
      return 0;
    }
    int inserted = Math.min(amount, capacity - fluid.getAmount());
    if (inserted <= 0) {
      return 0;
    }
    journal.updateSnapshots(transaction);
    fluid = resource.toStack(fluid.getAmount() + inserted);
    return inserted;
  }

  @Override
  public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
    Objects.checkIndex(index, 1);
    TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
    if (fluid.isEmpty() || !resource.matches(fluid)) {
      return 0;
    }
    int extracted = Math.min(amount, fluid.getAmount());
    if (extracted <= 0) {
      return 0;
    }
    journal.updateSnapshots(transaction);
    fluid = fluid.copyWithAmount(fluid.getAmount() - extracted);
    if (fluid.isEmpty()) {
      fluid = FluidStack.EMPTY;
    }
    return extracted;
  }

  /* Legacy transfer */

  // override to fix bug with onContentsChanged during fill
  @Override
  public int fill(FluidStack resource, FluidAction action) {
    if (resource.isEmpty() || !isFluidValid(resource)) {
      return 0;
    }
    if (action.simulate()) {
      if (fluid.isEmpty()) {
        return Math.min(capacity, resource.getAmount());
      }
      if (!FluidStack.isSameFluidSameComponents(fluid, resource)) {
        return 0;
      }
      return Math.min(capacity - fluid.getAmount(), resource.getAmount());
    }
    if (fluid.isEmpty()) {
      // FIX: the Forge implementation returns fluid.getAmount() here, which may be wrong if the fluid gets changed during onContentsChanged()
      // we instead use a local variable for the amount filled to guarantee its accurate
      int filled = Math.min(capacity, resource.getAmount());
      fluid = resource.copyWithAmount(filled);
      onContentsChanged();
      return filled;
    }
    if (!FluidStack.isSameFluidSameComponents(fluid, resource)) {
      return 0;
    }
    int filled = capacity - fluid.getAmount();

    if (resource.getAmount() < filled) {
      fluid.grow(resource.getAmount());
      filled = resource.getAmount();
    } else {
      fluid.setAmount(capacity);
    }
    if (filled > 0) {
      onContentsChanged();
    }
    return filled;
  }

  @Override
  public void onContentsChanged() {
    if (parent instanceof IFluidTankUpdater) {
      ((IFluidTankUpdater) parent).onTankContentsChanged();
    }

    parent.setChanged();
    Level level = parent.getLevel();
    if(level != null && !level.isClientSide()) {
      TinkerNetwork.getInstance().sendToClientsAround(new FluidUpdatePacket(parent.getBlockPos(), this.getFluid()), level, parent.getBlockPos());
    }
  }
}
