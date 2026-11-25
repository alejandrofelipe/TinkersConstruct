package slimeknights.tconstruct.smeltery.item;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import slimeknights.mantle.transfer.SingleFluidTank;

/**
 * Fluid tank representing a stack of multiple fluid tanks. All operations must affect every tack in the stack at the same time, so must in increments of the scale.
 * Internally works the same as a fluid tank with {@code capacity * scale}, except operations are truncated to the nearest scale (e.g. if scale is 4, we must fill in 4mb increments).
 * <p>
 * PORT 1.21.10 (transfer): native, on Mantle's {@link SingleFluidTank}; inserts and extracts are truncated to the scale.
 */
public class ScaledFluidTank extends SingleFluidTank {
  private final int scale;
  private ScaledFluidTank(int capacity, int scale) {
    super(capacity * scale);
    this.scale = scale;
  }

  /** Creates a new instance */
  public static SingleFluidTank create(int capacity, int scale) {
    if (scale == 1) {
      return new SingleFluidTank(capacity);
    }
    return new ScaledFluidTank(capacity, scale);
  }

  /* Helpers */

  /** enforces the amount matches the scale */
  private int enforceScale(int amount) {
    // no working with fluids of partial amounts
    int remainder = amount % scale;
    if (remainder != 0) {
      amount -= remainder;
    }
    return amount;
  }

  /** enforces the fluid matches the scale */
  private FluidStack enforceScale(FluidStack stack) {
    // no working with fluids of partial amounts
    int remainder = stack.getAmount() % scale;
    if (remainder != 0) {
      stack.shrink(remainder);
    }
    return stack;
  }


  /* Fluid tank methods */

  @Override
  public SingleFluidTank setCapacity(int capacity) {
    return super.setCapacity(enforceScale(capacity));
  }

  @Override
  public void setFluid(FluidStack stack) {
    super.setFluid(enforceScale(stack));
  }

  @Override
  public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
    return super.insert(index, resource, enforceScale(amount), transaction);
  }

  @Override
  public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
    return super.extract(index, resource, enforceScale(amount), transaction);
  }


  /* NBT */

  // 1.21.8: the fluid is still stored inline (no "Fluid" key) like the old readFromNBT/writeToNBT overrides, hence the map codec
  @SuppressWarnings("deprecation")
  @Override
  public void deserialize(ValueInput input) {
    // scale the fluid on reading from NBT; as each instance should store the fluid relative to stack size 1
    // never scale the shared FluidStack.EMPTY, setAmount mutates it
    FluidStack fluid = input.read(FluidStack.MAP_CODEC).filter(stack -> !stack.isEmpty()).orElse(FluidStack.EMPTY);
    if (!fluid.isEmpty()) {
      fluid.setAmount(fluid.getAmount() * scale);
    }
    setFluid(fluid);
  }

  @SuppressWarnings("deprecation")
  @Override
  public void serialize(ValueOutput output) {
    // scale the fluid on writing to NBT; as each instance should store the fluid relative to stack size 1
    if (!this.fluid.isEmpty()) {
      FluidStack fluid = this.fluid.copy();
      fluid.setAmount(fluid.getAmount() / scale);
      output.store(FluidStack.MAP_CODEC, fluid);
    }
  }
}
