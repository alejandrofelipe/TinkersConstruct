package slimeknights.tconstruct.smeltery.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.client.render.ChannelFluids;
import slimeknights.mantle.client.render.FluidCuboid;
import slimeknights.mantle.client.render.FluidRenderer;
import slimeknights.mantle.client.render.MantleRenderTypes;
import slimeknights.mantle.client.render.RenderingHelper;
import slimeknights.tconstruct.smeltery.block.ChannelBlock;
import slimeknights.tconstruct.smeltery.block.ChannelBlock.ChannelConnection;
import slimeknights.tconstruct.smeltery.block.entity.ChannelBlockEntity;
import slimeknights.tconstruct.smeltery.client.render.ChannelBlockEntityRenderer.ChannelRenderState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class ChannelBlockEntityRenderer implements BlockEntityRenderer<ChannelBlockEntity, ChannelRenderState> {
  public ChannelBlockEntityRenderer(Context context) {}

  @Override
  public ChannelRenderState createRenderState() {
    return new ChannelRenderState();
  }

  @Override
  public void extractRenderState(ChannelBlockEntity te, ChannelRenderState renderState, float partialTicks, Vec3 cameraPos, @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {
    BlockEntityRenderer.super.extractRenderState(te, renderState, partialTicks, cameraPos, breakProgress);
    renderState.fluid = FluidStack.EMPTY;
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
    renderState.fluid = fluid.copy();

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
    FluidStack fluid = state.fluid;
    if (fluid.isEmpty()) {
      return;
    }
    List<RotatedCube> cubes = state.cubes;
    List<List<FluidCuboid>> below = state.below;
    int combinedLight = state.lightCoords;
    collector.submitCustomGeometry(matrices, MantleRenderTypes.FLUID, (pose, builder) -> {
      // fluid attributes
      IClientFluidTypeExtensions attributes = IClientFluidTypeExtensions.of(fluid.getFluid());
      TextureAtlasSprite still = FluidRenderer.getBlockSprite(attributes.getStillTexture(fluid));
      TextureAtlasSprite flowing = FluidRenderer.getBlockSprite(attributes.getFlowingTexture(fluid));
      int color = attributes.getTintColor(fluid);
      int light = FluidRenderer.withBlockLight(combinedLight, fluid.getFluid().getFluidType().getLightLevel(fluid));

      for (RotatedCube cube : cubes) {
        PoseStack.Pose cubePose = pose;
        // apply rotation for the side, same as RenderingHelper#applyRotation
        Direction rotation = cube.rotation;
        if (rotation != null && rotation.getAxis().isHorizontal() && rotation != Direction.SOUTH) {
          cubePose = pose.copy();
          cubePose.rotateAround(Axis.YP.rotationDegrees(-90f * rotation.get2DDataValue()), 0.5f, 0, 0.5f);
        }
        FluidRenderer.renderCuboid(cubePose, builder, cube.cube, 0, still, flowing, color, light, false);
      }
      // render into the block(s) below
      if (!below.isEmpty()) {
        RenderingHelper.renderFaucetFluids(pose, builder, below, still, flowing, color, light);
      }
    });
  }

  @Override
  public AABB getRenderBoundingBox(ChannelBlockEntity be) {
    return be.getRenderBoundingBox();
  }

  /** Fluid cuboid with the horizontal rotation to apply, null for none */
  private record RotatedCube(FluidCuboid cube, @Nullable Direction rotation) {}

  /** Render state for the channel */
  public static class ChannelRenderState extends BlockEntityRenderState {
    /** Copy of the channel fluid */
    public FluidStack fluid = FluidStack.EMPTY;
    /** Cubes to render with their rotation */
    private List<RotatedCube> cubes = List.of();
    /** Fluid cuboids in the blocks below, from {@link RenderingHelper#getFaucetFluids} */
    public List<List<FluidCuboid>> below = List.of();
  }
}
