package slimeknights.tconstruct.library.client;

import com.mojang.blaze3d.vertex.PoseStack;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.client.render.FluidCuboid;
import slimeknights.mantle.client.render.FluidRenderer;
import slimeknights.tconstruct.library.fluid.FluidTankAnimated;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RenderUtils {
  /**
   * Fluid tank contents copied during render state extraction.
   * @param fluid     Copy of the tank fluid
   * @param capacity  Tank capacity
   * @param offset    Animated render offset for this frame
   */
  public record TankRenderData(FluidStack fluid, int capacity, float offset) {
    public static final TankRenderData EMPTY = new TankRenderData(FluidStack.EMPTY, 0, 0);

    /** Returns true if there is no fluid to render */
    public boolean isEmpty() {
      return fluid.isEmpty() || capacity <= 0;
    }
  }

  /**
   * Extracts the fluid of an animated tank for rendering, advancing the tank's render offset animation.
   * Call during render state extraction.
   * @param tank          Fluid tank animated to render
   * @param partialTicks  Partial ticks
   * @return  Render data with a copy of the fluid
   */
  public static TankRenderData extractFluidTank(FluidTankAnimated tank, float partialTicks) {
    // render liquid if present
    FluidStack liquid = tank.getFluid();
    int capacity = tank.getCapacity();
    if (!liquid.isEmpty() && capacity > 0) {
      // update render offset
      float offset = tank.getRenderOffset();
      if (offset > 1.2f || offset < -1.2f) {
        offset = offset - ((offset / 12f + 0.1f) * partialTicks);
        tank.setRenderOffset(offset);
      } else {
        tank.setRenderOffset(0);
      }
      return new TankRenderData(liquid.copy(), capacity, offset);
    }
    // clear render offet if no liquid
    tank.setRenderOffset(0);
    return TankRenderData.EMPTY;
  }

  /**
   * Submits textured quads for a fluid tank
   * @param matrices   Matrix stack instance
   * @param collector  Node collector
   * @param cube       Fluid cuboid instance
   * @param tank       Tank data from {@link #extractFluidTank(FluidTankAnimated, float)}
   * @param light      Quad lighting
   * @param flipGas    If true, flips gas cubes
   */
  public static void submitFluidTank(PoseStack matrices, SubmitNodeCollector collector, FluidCuboid cube, TankRenderData tank, int light, boolean flipGas) {
    if (!tank.isEmpty()) {
      FluidRenderer.submitScaledCuboid(matrices, collector, cube, tank.fluid(), tank.offset(), tank.capacity(), light, flipGas);
    }
  }

  public static int alpha(int c) {
    return (c >> 24) & 0xFF;
  }

  public static int red(int c) {
    return (c >> 16) & 0xFF;
  }

  public static int green(int c) {
    return (c >> 8) & 0xFF;
  }

  public static int blue(int c) {
    return (c) & 0xFF;
  }
}
