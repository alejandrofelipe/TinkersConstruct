package slimeknights.tconstruct.smeltery.client.screen;

import net.minecraft.client.renderer.Rect2i;
import net.neoforged.neoforge.fluids.FluidStack;

import javax.annotation.Nullable;

/**
 * Interface for JEI support to determine the ingredient under the mouse
 */
public interface IScreenWithFluidTank {
  /**
   * Gets the ingredient under the mouse, typically a fluid. Screens (what JEI sees) take the mouse and return the area in
   * screen coordinates. The GUI modules implementing this ({@code GuiTankModule}, {@code GuiFuelModule},
   * {@code GuiSmelteryTank}) instead take the mouse relative to the GUI corner and return a GUI relative area; the screen
   * owning them subtracts its corner from the mouse and adds it back to the area with {@link FluidLocation#offset}.
   * @param mouseX Mouse X, in screen coordinates for screens or GUI relative for modules
   * @param mouseY Mouse Y, in screen coordinates for screens or GUI relative for modules
   * @return Fluid under mouse with its area in the same coordinates as the mouse, or null if no fluid.
   */
  @Nullable
  FluidLocation getFluidUnderMouse(int mouseX, int mouseY);

  /** Return from the fluid under mouse, maps to a JEI fluid */
  record FluidLocation(FluidStack fluid, Rect2i location) {
    /**
     * Moves the location by the given offset, used by screens to turn the GUI relative areas of their modules into screen coordinates
     * @param location  Location to move, may be null
     * @param x         X offset, typically the left of the GUI
     * @param y         Y offset, typically the top of the GUI
     * @return Moved location, or null if the location is null
     */
    @Nullable
    public static FluidLocation offset(@Nullable FluidLocation location, int x, int y) {
      if (location == null) {
        return null;
      }
      Rect2i area = location.location;
      return new FluidLocation(location.fluid, new Rect2i(area.getX() + x, area.getY() + y, area.getWidth(), area.getHeight()));
    }
  }
}
