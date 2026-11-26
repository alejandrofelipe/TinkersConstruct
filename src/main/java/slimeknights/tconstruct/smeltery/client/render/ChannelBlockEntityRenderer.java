package slimeknights.tconstruct.smeltery.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.client.render.ChannelFluids;
import slimeknights.mantle.client.render.FluidCuboid;
import slimeknights.mantle.client.render.FluidRenderInfo;
import slimeknights.mantle.client.render.FluidRenderer;
import slimeknights.mantle.client.render.RenderingHelper;
import slimeknights.tconstruct.smeltery.block.ChannelBlock;
import slimeknights.tconstruct.smeltery.block.ChannelBlock.ChannelConnection;
import slimeknights.tconstruct.smeltery.block.entity.ChannelBlockEntity;
import slimeknights.tconstruct.smeltery.client.render.ChannelBlockEntityRenderer.ChannelRenderState;

import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

public class ChannelBlockEntityRenderer implements BlockEntityRenderer<ChannelBlockEntity, ChannelRenderState> {
  public ChannelBlockEntityRenderer(Context context) {}

  @Override
  public ChannelRenderState createRenderState() {
    return new ChannelRenderState();
  }

  @Override
  public void extractRenderState(ChannelBlockEntity te, ChannelRenderState renderState, float partialTicks, Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
    BlockEntityRenderer.super.extractRenderState(te, renderState, partialTicks, cameraPos, breakProgress);
    renderState.fluid = null;
    renderState.cubes = List.of();
    renderState.below = List.of();
    FluidStack fluid = te.getFluid();
    if (fluid.isEmpty()) {
      return;
    }

    // fetch model properties
    Level world = te.getLevel();
    if (world == null) {
      return;
    }
    BlockPos pos = te.getBlockPos();
    BlockState state = renderState.blockState;
    ChannelFluids model = ChannelFluids.REGISTRY.get(state.getBlock());
    if (model == null) {
      return;
    }
    renderState.fluid = FluidRenderInfo.of(fluid);

    // determine the cubes to render: sides first, while doing so we will determine center "flow"
    List<RotatedCube> cubes = new ArrayList<>();
    Direction centerFlow = Direction.UP;
    for (Direction direction : Plane.HORIZONTAL) {
      // check if we have that side on the block
      ChannelConnection connection = state.getValue(ChannelBlock.DIRECTION_MAP.get(direction));
      if (connection.canFlow()) {
        // get the relevant fluid model
        FluidCuboid cube;
        if (te.isFlowing(direction)) {
          cube = model.side().flow(connection == ChannelConnection.OUT);

          // add to center direction
          if (connection == ChannelConnection.OUT) {
            // if unset (up), use this direction
            if (centerFlow == Direction.UP) {
              centerFlow = direction;
              // if set and it disagrees, set the fail state (down)
            } else if (centerFlow != direction) {
              centerFlow = Direction.DOWN;
            }
          }
          // render the extra edge against other blocks
          if (!world.getBlockState(pos.relative(direction)).is(state.getBlock())) {
            cubes.add(new RotatedCube(model.side().edge(), direction));
          }
        } else {
          cube = model.side().still();
        }
        cubes.add(new RotatedCube(cube, direction));
      }
    }

    // render center
    if (centerFlow.getAxis().isVertical()) {
      cubes.add(new RotatedCube(model.center(false), null));
    } else {
      cubes.add(new RotatedCube(model.center(true), centerFlow));
    }

    // render flow downwards
    if (state.getValue(ChannelBlock.DOWN) && te.isFlowing(Direction.DOWN)) {
      cubes.add(new RotatedCube(model.down(), null));
      // render into the block(s) below
      renderState.below = RenderingHelper.getFaucetFluids(world, pos, Direction.DOWN);
    }
    renderState.cubes = cubes;
  }

  @Override
  public void submit(ChannelRenderState state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera) {
    FluidRenderInfo fluid = state.fluid;
    if (fluid == null) {
      return;
    }
    int light = state.lightCoords;
    for (RotatedCube cube : state.cubes) {
      // apply rotation for the side
      boolean isRotated = cube.rotation != null && RenderingHelper.applyRotation(matrices, cube.rotation);
      FluidRenderer.submitPouring(matrices, collector, List.of(cube.cube), List.of(), fluid, light);
      if (isRotated) {
        matrices.popPose();
      }
    }
    // render into the block(s) below
    FluidRenderer.submitPouring(matrices, collector, List.of(), state.below, fluid, light);
  }

  @Override
  public AABB getRenderBoundingBox(ChannelBlockEntity be) {
    return be.getRenderBoundingBox();
  }

  /** Fluid cuboid with the horizontal rotation to apply, null for none */
  private record RotatedCube(FluidCuboid cube, @Nullable Direction rotation) {}

  /** Render state for the channel */
  public static class ChannelRenderState extends BlockEntityRenderState {
    /** Channel fluid, null when empty */
    @Nullable
    public FluidRenderInfo fluid;
    /** Cubes to render with their rotation */
    private List<RotatedCube> cubes = List.of();
    /** Fluid cuboids in the blocks below, from {@link RenderingHelper#getFaucetFluids} */
    public List<List<FluidCuboid>> below = List.of();
  }
}
