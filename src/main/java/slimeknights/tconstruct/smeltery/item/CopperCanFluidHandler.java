package slimeknights.tconstruct.smeltery.item;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import slimeknights.tconstruct.library.recipe.FluidValues;

import javax.annotation.Nonnull;

/** Capability handler instance for the copper can item */
@AllArgsConstructor
public class CopperCanFluidHandler implements IFluidHandlerItem {
  @Getter
  private final ItemStack container;


  /* Tank properties */

  @Override
  public int getTanks() {
    return 1;
  }

  @Override
  public boolean isFluidValid(int tank, FluidStack stack) {
    return true;
  }

  /** Gets the stack size sensitive capacity of the container */
  private int getCapacity() {
    // scale up by the stack size to prevent dupes with people trying to fill a stack of containers
    return FluidValues.INGOT * container.getCount();
  }

  @Override
  public int getTankCapacity(int tank) {
    return getCapacity();
  }

  /** Gets the contained fluid (1 ingot), or empty */
  private FluidStack getContained() {
    return CopperCanItem.getFluidStack(container);
  }

  @Nonnull
  @Override
  public FluidStack getFluidInTank(int tank) {
    FluidStack contained = getContained();
    if (contained.isEmpty()) {
      return FluidStack.EMPTY;
    }
    return contained.copyWithAmount(getCapacity());
  }


  /* Interaction */

  @Override
  public int fill(FluidStack resource, FluidAction action) {
    // must not be filled, must have enough
    int capacity = getCapacity();
    if (!getContained().isEmpty() || resource.getAmount() < capacity) {
      return 0;
    }
    // update fluid and return
    if (action.execute()) {
      // this is not size sensitive so no need to shrink resource for stack size
      CopperCanItem.setFluid(container, resource);
    }
    return capacity;
  }

  @Nonnull
  @Override
  public FluidStack drain(FluidStack resource, FluidAction action) {
    // must be draining at least an ingot
    int capacity = getCapacity();
    if (resource.isEmpty() || resource.getAmount() < capacity) {
      return FluidStack.EMPTY;
    }
    // must have a fluid, must match what they are draining (fluid and components)
    FluidStack contained = getContained();
    if (contained.isEmpty()) {
      return FluidStack.EMPTY;
    }
    FluidStack output = contained.copyWithAmount(capacity);
    if (!FluidStack.isSameFluidSameComponents(resource, output)) {
      return FluidStack.EMPTY;
    }
    // output 1 ingot times stack size
    if (action.execute()) {
      CopperCanItem.setFluid(container, FluidStack.EMPTY);
    }
    return output;
  }

  @Nonnull
  @Override
  public FluidStack drain(int maxDrain, FluidAction action) {
    // must be draining at least an ingot
    int capacity = getCapacity();
    if (maxDrain < capacity) {
      return FluidStack.EMPTY;
    }
    // must have a fluid
    FluidStack contained = getContained();
    if (contained.isEmpty()) {
      return FluidStack.EMPTY;
    }
    // output 1 ingot
    FluidStack output = contained.copyWithAmount(capacity);
    if (action.execute()) {
      CopperCanItem.setFluid(container, FluidStack.EMPTY);
    }
    return output;
  }
}
