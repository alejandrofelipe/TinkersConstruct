package slimeknights.tconstruct.smeltery.block.entity.tank;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;

/**
 * Fluid handler of casting tables and basins, whose capacity comes from the casting recipe started by the first fill.
 * <p>
 * PORT 1.21.10 (transfer step 1): also a native {@link ResourceHandler}, the form registered as the block's fluid capability. A native
 * fill only <em>simulates</em> starting the recipe ({@link CastingBlockEntity#initNewCasting(FluidStack, FluidAction)} with
 * {@code SIMULATE}) to size the tank, and changes the fluid, capacity and filter silently, journaled. Once the root transaction commits,
 * the recipe is started for real and the block entity notified (cooling timer, comparators, client sync); draining to empty resets the
 * block entity on commit. Simulations (aborted transactions) therefore never start a recipe or touch the block entity.
 * The legacy methods stay for internal use and react right away, as before.
 */
@SuppressWarnings("removal")  // legacy IFluidHandler for internal use until phase 6
@RequiredArgsConstructor
public class CastingFluidHandler implements IFluidHandler, ResourceHandler<FluidResource> {
  private final CastingBlockEntity tile;
  @Getter @Setter
  private FluidStack fluid = FluidStack.EMPTY;
  @Setter
  private int capacity = 0;
  private Fluid filter = Fluids.EMPTY;

  /** Checks if the given fluid is valid */
  public boolean isFluidValid(FluidStack stack) {
    return !stack.isEmpty() && (filter == Fluids.EMPTY || stack.getFluid() == filter);
  }

  /** Checks if the fluid is empty */
  public boolean isEmpty() {
    return fluid.isEmpty();
  }

  /** Gets the current capacity of this fluid handler */
  public int getCapacity() {
    if (capacity == 0) {
      return fluid.getAmount();
    }
    return capacity;
  }

  /** Resets the tanks filter */
  public void reset() {
    capacity = 0;
    fluid = FluidStack.EMPTY;
    filter = Fluids.EMPTY;
  }

  @Override
  public int fill(FluidStack resource, FluidAction action) {
    if (resource.isEmpty() || !isFluidValid(resource)) {
      return 0;
    }

    // update filter and capacity
    int capacity = this.capacity;
    if (filter == null || this.capacity == 0) {
      Fluid fluid = resource.getFluid();
      capacity = tile.initNewCasting(resource, action);
      if (capacity <= 0) {
        return 0;
      }
      if (action.execute()) {
        this.capacity = capacity;
        this.filter = fluid;
      }
    }

    // if no fluid yet, copy it in
    if (fluid.isEmpty()) {
      int amount = Math.min(capacity, resource.getAmount());
      if (action.execute()) {
        fluid = resource.copyWithAmount(amount);
        tile.onContentsChanged();
      }
      return amount;
    }

    // safety: should never be false, but good to check
    if (!FluidStack.isSameFluidSameComponents(resource, fluid)) {
      return 0;
    }

    // if full, nothing to do
    int space = capacity - fluid.getAmount();
    if (space <= 0) {
      return 0;
    }
    // if it fits, it grows
    int amount = resource.getAmount();
    if (amount < space) {
      if (action.execute()) {
        fluid.grow(amount);
        tile.onContentsChanged();
      }
      return amount;
    } else {
      // too much? set to max
      if (action.execute()) {
        fluid.setAmount(capacity);
        tile.onContentsChanged();
      }
      return space;
    }
  }

  @Nonnull
  @Override
  public FluidStack drain(FluidStack resource, FluidAction action) {
    if (resource.isEmpty() || !FluidStack.isSameFluidSameComponents(resource, fluid)) {
      return FluidStack.EMPTY;
    }
    return this.drain(resource.getAmount(), action);
  }

  @Nonnull
  @Override
  public FluidStack drain(int maxDrain, FluidAction action) {
    int drained = Math.min(fluid.getAmount(), maxDrain);
    if (drained <= 0) {
      return FluidStack.EMPTY;
    }

    FluidStack stack = fluid.copyWithAmount(drained);
    if (action.execute()) {
      fluid.shrink(drained);
      if (fluid.isEmpty()) {
        // since empty, assume the current recipe is invalid now
        // fixes some odd behavior with capacity and recipes going out of sync
        tile.reset();
      } else {
        // called in reset
        tile.onContentsChanged();
      }
    }
    return stack;
  }

  /* Native transfer */

  /** Snapshot of the native state */
  private record State(FluidStack fluid, int capacity, Fluid filter) {}

  /** Journal of the tank state for native transfers */
  private final SnapshotJournal<State> journal = new SnapshotJournal<>() {
    @Override
    protected State createSnapshot() {
      return new State(fluid.copy(), capacity, filter);
    }

    @Override
    protected void revertToSnapshot(State snapshot) {
      fluid = snapshot.fluid();
      capacity = snapshot.capacity();
      filter = snapshot.filter();
    }

    @Override
    protected void onRootCommit(State original) {
      if (fluid.isEmpty()) {
        // drained to empty: like the legacy drain, assume the current recipe is invalid now
        if (!original.fluid().isEmpty()) {
          tile.reset();
        }
        return;
      }
      // a new casting was sized by a simulation, start it for real now
      if (original.capacity() == 0 && capacity != 0) {
        int newCapacity = tile.initNewCasting(fluid, FluidAction.EXECUTE);
        if (newCapacity > 0) {
          capacity = newCapacity;
        }
      }
      tile.onContentsChanged();
    }
  };

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
    return resource.isEmpty() || isFluidValid(resource.toStack(1)) ? getCapacity() : 0;
  }

  @Override
  public boolean isValid(int index, FluidResource resource) {
    Objects.checkIndex(index, 1);
    return isFluidValid(resource.toStack(1));
  }

  @Override
  public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
    Objects.checkIndex(index, 1);
    TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
    if (!isFluidValid(resource.toStack(1))) {
      return 0;
    }
    // size a new casting by simulating the recipe start, the real start happens on commit
    int capacity = this.capacity;
    boolean newCasting = this.capacity == 0;
    if (newCasting) {
      capacity = tile.initNewCasting(resource.toStack(amount), FluidAction.SIMULATE);
      if (capacity <= 0) {
        return 0;
      }
    }
    // safety: should never be false, but good to check
    if (!fluid.isEmpty() && !resource.matches(fluid)) {
      return 0;
    }
    int inserted = Math.min(amount, capacity - fluid.getAmount());
    if (inserted <= 0) {
      return 0;
    }
    journal.updateSnapshots(transaction);
    if (newCasting) {
      this.capacity = capacity;
      this.filter = resource.getFluid();
    }
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
    int drained = Math.min(amount, fluid.getAmount());
    if (drained <= 0) {
      return 0;
    }
    journal.updateSnapshots(transaction);
    if (drained == fluid.getAmount()) {
      // like the legacy drain, emptying clears the tank's recipe state; the block entity resets on commit
      fluid = FluidStack.EMPTY;
      capacity = 0;
      filter = Fluids.EMPTY;
    } else {
      fluid = fluid.copyWithAmount(fluid.getAmount() - drained);
    }
    return drained;
  }

  /* Required */

  @Nonnull
  @Override
  public FluidStack getFluidInTank(int tank) {
    if (tank == 0) {
      return fluid;
    }
    return FluidStack.EMPTY;
  }

  @Override
  public int getTanks() {
    return 1;
  }

  @Override
  public int getTankCapacity(int tank) {
    return getCapacity();
  }

  @Override
  public boolean isFluidValid(int tank, FluidStack stack) {
    return tank == 0 && isFluidValid(stack);
  }

  /* Tag */
  private static final String TAG_FLUID = "fluid";
  private static final String TAG_FILTER = "filter";
  private static final String TAG_CAPACITY = "capacity";

  /** Reads the tank from Tag */
  public void read(ValueInput nbt) {
    capacity = nbt.getIntOr(TAG_CAPACITY, 0);
    // only replace the fluid if the key holds a compound, OPTIONAL_CODEC treats an empty compound as empty like the old parseOptional
    if (nbt.child(TAG_FLUID).isPresent()) {
      setFluid(nbt.read(TAG_FLUID, FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY));
    }
    Optional<String> filterName = nbt.getString(TAG_FILTER);
    if (filterName.isPresent()) {
      Fluid fluid = BuiltInRegistries.FLUID.getValue(Objects.requireNonNull(ResourceLocation.tryParse(filterName.get())));
      if (fluid != null) {
        filter = fluid;
      }
    }
  }

  /** Write the tank from NBT */
  public void write(ValueOutput nbt) {
    nbt.putInt(TAG_CAPACITY, capacity);
    if (!fluid.isEmpty()) {
      nbt.store(TAG_FLUID, FluidStack.CODEC, fluid);
    }
    if (filter != Fluids.EMPTY) {
      nbt.putString(TAG_FILTER, BuiltInRegistries.FLUID.getKey(filter).toString());
    }
  }
}
