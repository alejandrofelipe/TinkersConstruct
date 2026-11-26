package slimeknights.tconstruct.smeltery.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Vector3f;
import slimeknights.mantle.client.render.FluidRenderInfo;
import slimeknights.mantle.client.render.FluidRenderer;
import slimeknights.tconstruct.smeltery.block.entity.tank.SmelteryTank;
import slimeknights.tconstruct.smeltery.client.screen.module.GuiSmelteryTank;

import java.util.ArrayList;
import java.util.List;

/** Helper class to render the smeltery tank */
public class SmelteryTankRenderer {
  /** Distance between the liquid and the edge of the block */
  private static final float FLUID_OFFSET = 0.005f;
  /** Amount to subtract from the height for fluid offset */
  private static final int HEIGHT_OFFSET = (int) (FLUID_OFFSET * 2000d);

  /**
   * One fluid of the tank, stacked from the bottom up
   * @param fluid  Fluid to draw
   * @param from   Layer start relative to the min inside position
   * @param to     Layer end
   */
  public record FluidLayer(FluidRenderInfo fluid, Vector3f from, Vector3f to) {}

  /**
   * Computes the fluid layers of the smeltery tank, relative to tankMinPos. Call during render state extraction.
   * @param tank        Smeltery tank
   * @param tankMinPos  Min position for fluid rendering
   * @param tankMaxPos  Max position for fluid rendering
   * @return  Layers to draw from the bottom up, empty layers skipped
   */
  public static List<FluidLayer> extractLayers(SmelteryTank<?> tank, BlockPos tankMinPos, BlockPos tankMaxPos) {
    // empty smeltery :(
    List<FluidStack> fluids = tank.getFluids();
    if (fluids.isEmpty()) {
      return List.of();
    }
    // determine x and z bounds, constant
    int xd = tankMaxPos.getX() - tankMinPos.getX();
    int zd = tankMaxPos.getZ() - tankMinPos.getZ();
    // somehow people are getting a rendering crash with these being negative, no idea how but easy to catch
    if (xd < 0 || zd < 0) {
      return List.of();
    }
    float xMax = xd + 1f - FLUID_OFFSET;
    float zMax = zd + 1f - FLUID_OFFSET;

    // calc heights, we use mB capacities and then convert it over to blockheights during rendering
    int yd = 1 + Math.max(0, tankMaxPos.getY() - tankMinPos.getY());
    // one block height = 1000 mb
    int[] heights = GuiSmelteryTank.calcLiquidHeights(fluids, tank.getCapacity(), yd * 1000 - HEIGHT_OFFSET, 100);

    List<FluidLayer> layers = new ArrayList<>(fluids.size());
    float curY = FLUID_OFFSET;
    for (int i = 0; i < fluids.size(); i++) {
      float h = (float) heights[i] / 1000f;
      float yMax = curY + h;
      FluidRenderInfo fluid = FluidRenderInfo.of(fluids.get(i));
      if (fluid != null && curY < yMax) {
        layers.add(new FluidLayer(fluid, new Vector3f(FLUID_OFFSET, curY, FLUID_OFFSET), new Vector3f(xMax, yMax, zMax)));
      }
      curY += h;
    }
    return layers;
  }

  /**
   * Submits the smeltery tank fluids, relative to tankMinPos
   * @param matrices    Matrix stack instance
   * @param collector   Node collector
   * @param layers      Fluid layers from {@link #extractLayers(SmelteryTank, BlockPos, BlockPos)}
   * @param brightness  Packed lighting values
   */
  public static void submitFluids(PoseStack matrices, SubmitNodeCollector collector, List<FluidLayer> layers, int brightness) {
    for (FluidLayer layer : layers) {
      FluidRenderer.submitTiledBox(matrices, collector, layer.fluid(), layer.from(), layer.to(), brightness);
    }
  }
}
