package slimeknights.tconstruct.smeltery.block.entity.tank;

import com.google.common.collect.Lists;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fluid handler implementation for the smeltery.
 * <p>
 * PORT 1.21.10 (transfer): a native {@link ResourceHandler}, the form exposed through drains and ducts and used internally (melting,
 * alloying, faucets, GUI). Changes to the fluid list are journaled and the parent and list listeners are notified once the root transaction
 * commits, never on a simulation: {@link FluidChange#ADDED} if a new fluid entered the tank (alloying recipes may match now),
 * {@link FluidChange#REMOVED} if one left, else {@link FluidChange#CHANGED}. Indices are the fluids from the bottom up, followed by an
 * "empty" segment while the tank has space.
 */
public class SmelteryTank<T extends MantleBlockEntity & ISmelteryTankHandler> implements ResourceHandler<FluidResource>, IMultitankListChange {
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

  /**
   * Gets the fluid at the given index, from the bottom up
   * @param tank  Fluid index
   * @return  Fluid, empty for the "empty" segment or an invalid index. Do not modify.
   */
  public FluidStack getFluidInTank(int tank) {
    if (tank < 0 || tank >= fluids.size()) {
      return FluidStack.EMPTY;
    }
    return fluids.get(tank);
  }

  /**
   * Gets the capacity of the given index: the fluid amount plus the free space, or the free space for the "empty" segment
   * @param tank  Fluid index
   * @return  Capacity, may be negative if the smeltery shrunk
   */
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


  /* Native transfer */

  /** Snapshot of the native state */
  private record State(List<FluidStack> fluids, int contained) {}

  /** Checks if the list contains the given fluid */
  private static boolean containsFluid(List<FluidStack> list, FluidStack fluid) {
    for (FluidStack other : list) {
      if (FluidStack.isSameFluidSameComponents(other, fluid)) {
        return true;
      }
    }
    return false;
  }

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
      // notify like the legacy fill and drain: a new fluid means alloy recipes might match now
      FluidChange change = FluidChange.CHANGED;
      FluidStack changed = getFluidInTank(0);
      for (FluidStack fluid : fluids) {
        if (!containsFluid(original.fluids(), fluid)) {
          change = FluidChange.ADDED;
          changed = fluid;
          break;
        }
      }
      if (change == FluidChange.CHANGED) {
        for (FluidStack fluid : original.fluids()) {
          if (!containsFluid(fluids, fluid)) {
            change = FluidChange.REMOVED;
            changed = fluid;
            break;
          }
        }
      }
      parent.notifyFluidsChanged(change, changed);
      // list listeners need to know about added/removed fluids and the "empty" segment appearing or disappearing
      boolean listChanged = change != FluidChange.CHANGED || original.fluids().size() != fluids.size()
                            || (original.contained() >= capacity) != (contained >= capacity);
      if (listChanged) {
        tankListChange.run();
      }
    }
  };

  @Override
  public int size() {
    if (contained < capacity) {
      return fluids.size() + 1;
    }
    return fluids.size();
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
   * Inserts into the tank. Any fluid fits as long as there is space: the index must hold the same fluid, or be the
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
    // the empty segment index merges into the existing entry, so a single call fills the whole tank
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
