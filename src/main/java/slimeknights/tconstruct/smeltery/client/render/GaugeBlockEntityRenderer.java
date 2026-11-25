package slimeknights.tconstruct.smeltery.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.client.render.FluidCuboid;
import slimeknights.mantle.client.render.FluidRenderer;
import slimeknights.tconstruct.library.transfer.TransferUtil;
import slimeknights.tconstruct.smeltery.block.entity.GaugeBlockEntity;
import slimeknights.tconstruct.smeltery.client.render.GaugeBlockEntityRenderer.GaugeRenderState;

import javax.annotation.Nullable;
import java.util.List;

/** Renderer for the obisidian gauge block */
public class GaugeBlockEntityRenderer implements BlockEntityRenderer<GaugeBlockEntity, GaugeRenderState> {
  public GaugeBlockEntityRenderer(Context context) {}

  @Override
  public GaugeRenderState createRenderState() {
    return new GaugeRenderState();
  }

  @Override
  public void extractRenderState(GaugeBlockEntity tile, GaugeRenderState state, float partialTick, Vec3 cameraPos, @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {
    BlockEntityRenderer.super.extractRenderState(tile, state, partialTick, cameraPos, breakProgress);
    state.fluids = FluidCuboid.REGISTRY.get(state.blockState, List.of());
    state.fluid = FluidStack.EMPTY;
    if (!state.fluids.isEmpty()) {
      state.fluid = TransferUtil.getFluid(tile.getTank(), 0);
    }
  }

  @Override
  public void submit(GaugeRenderState state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera) {
    FluidRenderer.submitCuboids(matrices, collector, state.fluids, state.fluid, state.lightCoords);
  }

  /** Render state for the gauge */
  public static class GaugeRenderState extends BlockEntityRenderState {
    public List<FluidCuboid> fluids = List.of();
    /** Copy of the displayed fluid */
    public FluidStack fluid = FluidStack.EMPTY;
  }
}
