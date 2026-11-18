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
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
public class CastingFluidHandler implements IFluidHandler {
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
