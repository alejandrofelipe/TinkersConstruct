package slimeknights.tconstruct.smeltery.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import slimeknights.mantle.client.render.FluidCuboid;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.library.client.RenderUtils;
import slimeknights.tconstruct.library.client.RenderUtils.TankRenderData;
import slimeknights.tconstruct.smeltery.block.entity.ITankBlockEntity;
import slimeknights.tconstruct.smeltery.client.render.TankBlockEntityRenderer.TankRenderState;

import org.jspecify.annotations.Nullable;
import java.util.List;

public class TankBlockEntityRenderer<T extends BlockEntity & ITankBlockEntity> implements BlockEntityRenderer<T, TankRenderState> {
  public TankBlockEntityRenderer(Context context) {}

  @Override
  public TankRenderState createRenderState() {
    return new TankRenderState();
  }

  @Override
  public void extractRenderState(T tile, TankRenderState state, float partialTicks, Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
    BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPos, breakProgress);
    state.fluids = Config.CLIENT.tankFluidModel.get() ? List.of() : FluidCuboid.REGISTRY.get(state.blockState, List.of());
    state.tank = state.fluids.isEmpty() ? TankRenderData.EMPTY : RenderUtils.extractFluidTank(tile.getTank(), partialTicks);
  }

  @Override
  public void submit(TankRenderState state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera) {
    // render the fluid
    if (!state.tank.isEmpty()) {
      for (FluidCuboid fluid : state.fluids) {
        RenderUtils.submitFluidTank(matrices, collector, fluid, state.tank, state.lightCoords, true);
      }
    }
  }

  /** Render state for a block with a single fluid tank */
  public static class TankRenderState extends BlockEntityRenderState {
    public List<FluidCuboid> fluids = List.of();
    public TankRenderData tank = TankRenderData.EMPTY;
  }
}
