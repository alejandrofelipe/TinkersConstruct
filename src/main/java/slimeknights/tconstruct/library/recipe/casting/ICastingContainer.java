package slimeknights.tconstruct.library.recipe.casting;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.level.material.Fluid;
import slimeknights.mantle.recipe.container.ISingleStackContainer;

/**
 * Inventory containing a single item and a fluid
 */
public interface ICastingContainer extends ISingleStackContainer {
  /**
   * Gets the contained fluid in this inventory
   * @return  Contained fluid
   */
  Fluid getFluid();

  /**
   * Gets the data components for the contained fluid.
   * @return  Fluid's data component patch, {@link DataComponentPatch#EMPTY} if none
   */
  default DataComponentPatch getFluidComponents() {
    return DataComponentPatch.EMPTY;
  }
}
