package slimeknights.tconstruct.smeltery.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import slimeknights.mantle.client.render.FluidCuboid;
import slimeknights.mantle.client.render.FluidRenderer;
import slimeknights.mantle.client.render.MantleRenderTypes;
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
    state.fluid = FluidStack.EMPTY;
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
      state.fluid = renderFluid.copy();
      state.direction = state.blockState.getValue(FaucetBlock.FACING);
      // fluids in the block(s) below
      state.below = RenderingHelper.getFaucetFluids(world, tileEntity.getBlockPos(), state.direction);
    }
  }

  @Override
  public void submit(FaucetRenderState state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera) {
    FluidStack renderFluid = state.fluid;
    if (renderFluid.isEmpty() || state.fluids.isEmpty()) {
      return;
    }
    // if side, rotate fluid model
    boolean isRotated = RenderingHelper.applyRotation(matrices, state.direction);

    List<FluidCuboid> fluids = state.fluids;
    List<List<FluidCuboid>> below = state.below;
    int light = state.lightCoords;
    collector.submitCustomGeometry(matrices, MantleRenderTypes.FLUID, (pose, buffer) -> {
      // fluid props
      IClientFluidTypeExtensions attributes = IClientFluidTypeExtensions.of(renderFluid.getFluid());
      int color = attributes.getTintColor(renderFluid);
      TextureAtlasSprite still = FluidRenderer.getBlockSprite(attributes.getStillTexture(renderFluid));
      TextureAtlasSprite flowing = FluidRenderer.getBlockSprite(attributes.getFlowingTexture(renderFluid));
      FluidType fluidType = renderFluid.getFluid().getFluidType();
      int fluidLight = FluidRenderer.withBlockLight(light, fluidType.getLightLevel(renderFluid));

      // render all cubes in the model
      for (FluidCuboid cube : fluids) {
        FluidRenderer.renderCuboid(pose, buffer, cube, 0, still, flowing, color, fluidLight, false);
      }

      // render into the block(s) below
      RenderingHelper.renderFaucetFluids(pose, buffer, below, still, flowing, color, fluidLight);
    });

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
    /** Copy of the poured fluid, empty when not pouring */
    public FluidStack fluid = FluidStack.EMPTY;
    public Direction direction = Direction.DOWN;
    public List<FluidCuboid> fluids = List.of();
    /** Fluid cuboids in the blocks below, from {@link RenderingHelper#getFaucetFluids} */
    public List<List<FluidCuboid>> below = List.of();
  }
}
