package slimeknights.tconstruct.smeltery.block.entity.tank;

import com.google.common.collect.Lists;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import slimeknights.mantle.block.entity.MantleBlockEntity;
import slimeknights.tconstruct.common.network.TinkerNetwork;
import slimeknights.tconstruct.library.fluid.IMultitankListChange;
import slimeknights.tconstruct.library.utils.WeakListenerList;
import slimeknights.tconstruct.smeltery.block.entity.tank.ISmelteryTankHandler.FluidChange;
import slimeknights.tconstruct.smeltery.network.SmelteryTankUpdatePacket;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Consumer;

/**
 * Fluid handler implementation for the smeltery.
 * <p>
 * PORT 1.21.10 (transfer step 1): also a native {@link ResourceHandler}, the form exposed through drains and ducts. Native changes to the
 * fluid list are journaled and the parent and list listeners are notified once the root transaction commits, never on a simulation.
 * The legacy methods stay for internal use (melting, alloying, faucets, GUI) and notify right away, as before.
 */
@SuppressWarnings("removal")  // legacy IFluidHandler for internal use until phase 6
public class SmelteryTank<T extends MantleBlockEntity & ISmelteryTankHandler> implements IFluidHandler, ResourceHandler<FluidResource>, IMultitankListChange {
  private final T parent;
  /** Fluids actually contained in the tank */
  @Getter
  private final List<FluidStack> fluids;
  /** Maximum capacity of the smeltery */
  private int capacity;
  /** Current amount of fluid in the tank */
  @Getter
  private int contained;
  /** Listener for the tank list changing */ // TODO: does this replace ISmelteryTankHandler?
  private final WeakListenerList tankListChange = new WeakListenerList();

  public SmelteryTank(T parent) {
    fluids = Lists.newArrayList();
    capacity = 0;
    contained = 0;
    this.parent = parent;
  }

  /**
   * Called when the fluids change to sync to client
   */
  public void syncFluids() {
    Level world = parent.getLevel();
    if (world != null && !world.isClientSide()) {
      BlockPos pos = parent.getBlockPos();
      TinkerNetwork.getInstance().sendToClientsAround(new SmelteryTankUpdatePacket(pos, fluids), world, pos);
    }
  }


  /* Capacity and space */

  /**
   * Updates the maximum tank capacity
   * @param maxCapacity  New max capacity
   */
  public void setCapacity(int maxCapacity) {
    this.capacity = maxCapacity;
  }

  /**
   * Gets the maximum amount of space in the smeltery tank
   * @return  Tank capacity
   */
  public int getCapacity() {
    return capacity;
  }

  /**
   * Gets the amount of empty space in the tank
   * @return  Remaining space in the tank
   */
  public int getRemainingSpace() {
    if (contained >= capacity) {
      return 0;
    }
    return capacity - contained;
  }


  /* Fluids */

  @Override
  public boolean isFluidValid(int tank, FluidStack stack) {
    return true;
  }

  @Override
  public int getTanks() {
    if (contained < capacity) {
      return fluids.size() + 1;
    }
    return fluids.size();
  }

  @Nonnull
  @Override
  public FluidStack getFluidInTank(int tank) {
    if (tank < 0 || tank >= fluids.size()) {
      return FluidStack.EMPTY;
    }
    return fluids.get(tank);
  }

  @Override
  public int getTankCapacity(int tank) {
    if (tank < 0) {
      return 0;
    }
    // index of the tank size means the "empty" segment
    int remaining = capacity - contained;
    if (tank == fluids.size()) {
      return remaining;
    }
    // any valid index, return the amount contained and the extra space
    return fluids.get(tank).getAmount() + remaining;
  }

  /**
   * Moves the fluid with the passed index to the beginning/bottom of the fluid tank stack
   * @param index  Index to move
   */
  public void moveFluidToBottom(int index) {
    if (index < fluids.size()) {
      FluidStack fluid = fluids.get(index);
      fluids.remove(index);
      fluids.add(0, fluid);
      parent.notifyFluidsChanged(FluidChange.CHANGED, FluidStack.EMPTY);
      tankListChange.run();
    }
  }


  /* Filling and draining */

  @Override
  public int fill(FluidStack resource, FluidAction action) {
    // if full or nothing being filled, do nothing
    if (contained >= capacity || resource.isEmpty()) {
      return 0;
    }

    // determine how much we can fill
    int usable = Math.min(capacity - contained, resource.getAmount());
    // could be negative if the smeltery size changes then you try filling it
    if (usable <= 0) {
      return 0;
    }

    // done here if just simulating
    if (action.simulate()) {
      return usable;
    }

    // add contained fluid amount
    contained += usable;

    // check if we already have the given liquid
    for (FluidStack fluid : fluids) {
      if (FluidStack.isSameFluidSameComponents(fluid, resource)) {
        // yup. add it
        fluid.grow(usable);
        parent.notifyFluidsChanged(FluidChange.CHANGED, fluid);
        // notify as we lost the "empty tank"
        if (contained >= capacity) {
          tankListChange.run();
        }
        return usable;
      }
    }

    // not present yet, add it
    resource = resource.copy();
    resource.setAmount(usable);
    fluids.add(resource);
    parent.notifyFluidsChanged(FluidChange.ADDED, resource);
    // notify as we added a new fluid
    tankListChange.run();
    return usable;
  }

  @Nonnull
  @Override
  public FluidStack drain(int maxDrain, FluidAction action) {
    if (fluids.isEmpty()) {
      return FluidStack.EMPTY;
    }
    boolean wasFull = contained >= capacity;

    // simply drain the first one
    FluidStack fluid = fluids.get(0);
    int drainable = Math.min(maxDrain, fluid.getAmount());

    // copy contained fluid to return for accuracy
    FluidStack ret = fluid.copy();
    ret.setAmount(drainable);

    // remove the fluid from the tank
    if (action.execute()) {
      fluid.shrink(drainable);
      contained -= drainable;
      // if now empty, remove from the list
      if (fluid.getAmount() <= 0) {
        fluids.remove(fluid);
        parent.notifyFluidsChanged(FluidChange.REMOVED, fluid);
        tankListChange.run();
      } else {
        parent.notifyFluidsChanged(FluidChange.CHANGED, fluid);

        // need to notify if we were full but are no longer as its adds an empty tank
        if (wasFull && contained < capacity) {
          tankListChange.run();
        }
      }
    }

    // return drained fluid
    return ret;
  }

  @Nonnull
  @Override
  public FluidStack drain(FluidStack toDrain, FluidAction action) {
    boolean wasFull = contained >= capacity;
    // search for the resource
    ListIterator<FluidStack> iter = fluids.listIterator();
    while (iter.hasNext()) {
      FluidStack fluid = iter.next();
      if (FluidStack.isSameFluidSameComponents(fluid, toDrain)) {
        // if found, determine how much we can drain
        int drainable = Math.min(toDrain.getAmount(), fluid.getAmount());

        // copy contained fluid to return for accuracy
        FluidStack ret = fluid.copy();
        ret.setAmount(drainable);

        // update tank if executing
        if (action.execute()) {
          fluid.shrink(drainable);
          contained -= drainable;
          // if now empty, remove from the list
          if (fluid.getAmount() <= 0) {
            iter.remove();
            parent.notifyFluidsChanged(FluidChange.REMOVED, fluid);
            tankListChange.run();
          } else {
            parent.notifyFluidsChanged(FluidChange.CHANGED, fluid);

            // need to notify if we were full but are no longer as its adds an empty tank
            if (wasFull && contained < capacity) {
              tankListChange.run();
            }
          }
        }

        return ret;
      }
    }

    // nothing drained
    return FluidStack.EMPTY;
  }

  /* Native transfer */

  /** Snapshot of the native state */
  private record State(List<FluidStack> fluids, int contained) {}

  /** Journal of the fluid list for native transfers */
  private final SnapshotJournal<State> journal = new SnapshotJournal<>() {
    @Override
    protected State createSnapshot() {
      List<FluidStack> copy = new ArrayList<>(fluids.size());
      for (FluidStack fluid : fluids) {
        copy.add(fluid.copy());
      }
      return new State(copy, contained);
    }

    @Override
    protected void revertToSnapshot(State snapshot) {
      fluids.clear();
      fluids.addAll(snapshot.fluids());
      contained = snapshot.contained();
    }

    @Override
    protected void onRootCommit(State original) {
      // the parent only cares about the order, which native transfers keep; list listeners need to know about added/removed/full changes
      parent.notifyFluidsChanged(FluidChange.CHANGED, getFluidInTank(0));
      tankListChange.run();
    }
  };

  @Override
  public int size() {
    return getTanks();
  }

  @Override
  public FluidResource getResource(int index) {
    return FluidResource.of(getFluidInTank(index));
  }

  @Override
  public long getAmountAsLong(int index) {
    return getFluidInTank(index).getAmount();
  }

  @Override
  public long getCapacityAsLong(int index, FluidResource resource) {
    // a stale index (e.g. a cached mapping during a transaction) has no capacity rather than throwing
    if (index < 0 || index > fluids.size()) {
      return 0;
    }
    return Math.max(0, getTankCapacity(index));
  }

  @Override
  public boolean isValid(int index, FluidResource resource) {
    return !resource.isEmpty();
  }

  /** Finds the index of the given fluid in the list, -1 if missing */
  private int indexOf(FluidResource resource) {
    for (int i = 0; i < fluids.size(); i++) {
      if (resource.matches(fluids.get(i))) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Inserts into the tank. Like the legacy fill, any fluid fits as long as there is space: the index must hold the same fluid, or be the
   * "empty" segment after the fluids, in which case the fluid merges into its existing entry if present.
   */
  @Override
  public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
    TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
    if (index < 0 || index > fluids.size() || (index < fluids.size() && !resource.matches(fluids.get(index)))) {
      return 0;
    }
    int usable = Math.min(capacity - contained, amount);
    // could be negative if the smeltery size changes then you try filling it
    if (usable <= 0) {
      return 0;
    }
    journal.updateSnapshots(transaction);
    contained += usable;
    int existing = indexOf(resource);
    if (existing >= 0) {
      fluids.get(existing).grow(usable);
    } else {
      fluids.add(resource.toStack(usable));
    }
    return usable;
  }

  @Override
  public int insert(FluidResource resource, int amount, TransactionContext transaction) {
    TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
    // the empty segment index merges into the existing entry, so a single call fills like the legacy fill
    int existing = indexOf(resource);
    return insert(existing >= 0 ? existing : fluids.size(), resource, amount, transaction);
  }

  @Override
  public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
    TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
    if (index < 0 || index >= fluids.size()) {
      return 0;
    }
    FluidStack fluid = fluids.get(index);
    if (!resource.matches(fluid)) {
      return 0;
    }
    int drained = Math.min(amount, fluid.getAmount());
    if (drained <= 0) {
      return 0;
    }
    journal.updateSnapshots(transaction);
    // the snapshot copied the list entries, so the entry may change in place
    fluid = fluids.get(index);
    fluid.shrink(drained);
    contained -= drained;
    if (fluid.isEmpty()) {
      fluids.remove(index);
    }
    return drained;
  }

  /* Saving and loading */

  private static final String TAG_FLUIDS = "fluids";
  private static final String TAG_CAPACITY = "capacity";

  /**
   * Updates fluids in the tank, typically from a packet
   * @param fluids  List of fluids
   */
  public void setFluids(List<FluidStack> fluids) {
    FluidStack oldFirst = getFluidInTank(0);
    this.fluids.clear();
    this.fluids.addAll(fluids);
    contained = fluids.stream().mapToInt(FluidStack::getAmount).reduce(0, Integer::sum);
    FluidStack newFirst = getFluidInTank(0);
    if (!FluidStack.isSameFluidSameComponents(oldFirst, newFirst)) {
      parent.notifyFluidsChanged(FluidChange.ORDER_CHANGED, newFirst);
      tankListChange.run();
    }
  }

  /** Writes the tank to NBT */
  public void write(ValueOutput nbt) {
    // list is always written, even when empty
    ValueOutput.TypedOutputList<FluidStack> list = nbt.list(TAG_FLUIDS, FluidStack.CODEC);
    for (FluidStack liquid : fluids) {
      list.add(liquid);
    }
    nbt.putInt(TAG_CAPACITY, capacity);
  }

  /** Reads the tank from NBT */
  public void read(ValueInput tag) {
    fluids.clear();
    contained = 0;
    // OPTIONAL_CODEC treats an empty compound as an empty fluid like the old parseOptional
    for (FluidStack fluid : tag.listOrEmpty(TAG_FLUIDS, FluidStack.OPTIONAL_CODEC)) {
      if (!fluid.isEmpty()) {
        fluids.add(fluid);
        contained += fluid.getAmount();
      }
    }
    capacity = tag.getIntOr(TAG_CAPACITY, 0);
  }


  /* Listeners */

  @Override
  public <TE> void addTankListListener(TE parent, Consumer<TE> listener) {
    tankListChange.addListener(parent, listener);
  }

  @Override
  public void removeTankListListeners(Object parent) {
    tankListChange.removeListeners(parent);
  }
}
