package slimeknights.tconstruct.smeltery.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.client.render.FluidCuboid;
import slimeknights.mantle.client.render.FluidRenderInfo;
import slimeknights.mantle.client.render.FluidRenderer;
import slimeknights.mantle.client.render.RenderingHelper;
import slimeknights.tconstruct.smeltery.block.FaucetBlock;
import slimeknights.tconstruct.smeltery.block.entity.FaucetBlockEntity;
import slimeknights.tconstruct.smeltery.client.render.FaucetBlockEntityRenderer.FaucetRenderState;

import javax.annotation.Nullable;
import java.util.List;

public class FaucetBlockEntityRenderer implements BlockEntityRenderer<FaucetBlockEntity, FaucetRenderState> {
  public FaucetBlockEntityRenderer(Context context) {}

  @Override
  public FaucetRenderState createRenderState() {
    return new FaucetRenderState();
  }

  @Override
  public void extractRenderState(FaucetBlockEntity tileEntity, FaucetRenderState state, float partialTicks, Vec3 cameraPos, @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {
    BlockEntityRenderer.super.extractRenderState(tileEntity, state, partialTicks, cameraPos, breakProgress);
    state.fluid = null;
    state.fluids = List.of();
    state.below = List.of();
    FluidStack renderFluid = tileEntity.getRenderFluid();
    Level world = tileEntity.getLevel();
    if (!tileEntity.isPouring() || renderFluid.isEmpty() || world == null) {
      return;
    }
    // fetch faucet model to determine where to render fluids
    state.fluids = FluidCuboid.REGISTRY.get(state.blockState, List.of());
    if (!state.fluids.isEmpty()) {
      state.fluid = FluidRenderInfo.of(renderFluid);
      state.direction = state.blockState.getValue(FaucetBlock.FACING);
      // fluids in the block(s) below
      state.below = RenderingHelper.getFaucetFluids(world, tileEntity.getBlockPos(), state.direction);
    }
  }

  @Override
  public void submit(FaucetRenderState state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera) {
    FluidRenderInfo fluid = state.fluid;
    if (fluid == null || state.fluids.isEmpty()) {
      return;
    }
    // if side, rotate fluid model, including the fluid in the block(s) below
    boolean isRotated = RenderingHelper.applyRotation(matrices, state.direction);
    FluidRenderer.submitPouring(matrices, collector, state.fluids, state.below, fluid, state.lightCoords);
    // if rotated, pop back rotation
    if (isRotated) {
      matrices.popPose();
    }
  }

  @Override
  public AABB getRenderBoundingBox(FaucetBlockEntity be) {
    return be.getRenderBoundingBox();
  }

  /** Render state for the faucet */
  public static class FaucetRenderState extends BlockEntityRenderState {
    /** Poured fluid, null when not pouring */
    @Nullable
    public FluidRenderInfo fluid;
    public Direction direction = Direction.DOWN;
    public List<FluidCuboid> fluids = List.of();
    /** Fluid cuboids in the blocks below, from {@link RenderingHelper#getFaucetFluids} */
    public List<List<FluidCuboid>> below = List.of();
  }
}
