package slimeknights.tconstruct.smeltery.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.client.render.FluidCuboid;
import slimeknights.mantle.client.render.FluidRenderInfo;
import slimeknights.mantle.client.render.FluidRenderer;
import slimeknights.mantle.client.render.RenderItem;
import slimeknights.mantle.client.render.RenderingHelper;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.tank.CastingFluidHandler;
import slimeknights.tconstruct.smeltery.client.render.CastingBlockEntityRenderer.CastingRenderState;
import slimeknights.tconstruct.smeltery.client.util.TintedItemRenderState;

import org.jspecify.annotations.Nullable;
import java.util.List;

public class CastingBlockEntityRenderer implements BlockEntityRenderer<CastingBlockEntity, CastingRenderState> {
  private final ItemModelResolver itemModelResolver;
  public CastingBlockEntityRenderer(Context context) {
    this.itemModelResolver = context.itemModelResolver();
  }

  @Override
  public CastingRenderState createRenderState() {
    return new CastingRenderState();
  }

  @Override
  public void extractRenderState(CastingBlockEntity casting, CastingRenderState state, float partialTicks, Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
    BlockEntityRenderer.super.extractRenderState(casting, state, partialTicks, cameraPos, breakProgress);
    state.fluids = FluidCuboid.REGISTRY.get(state.blockState, List.of());
    state.renderItems = RenderItem.STATE_REGISTRY.get(state.blockState, List.of());
    state.input.clear();
    state.output.clear();
    state.fluid = FluidStack.EMPTY;
    state.fadingFluid = null;
    state.capacity = 0;
    if (state.fluids.isEmpty() && state.renderItems.isEmpty()) {
      return;
    }

    // if the recipe is in progress, start fading the item away
    int timer = casting.getTimer();
    int totalTime = casting.getCoolingTime();
    int itemOpacity = 0;
    int fluidOpacity = 0xFF;
    if (timer > 0 && totalTime > 0) {
      int opacity = (4 * 0xFF) * timer / totalTime;
      // fade item in
      itemOpacity = opacity / 4;

      // fade fluid and temperature out during last 10%
      if (opacity > 3 * 0xFF) {
        fluidOpacity = (4 * 0xFF) - opacity;
      }
    }
    state.fluidOpacity = fluidOpacity;

    // fluids
    if (!state.fluids.isEmpty()) {
      CastingFluidHandler tank = casting.getTank();
      state.fluid = tank.getFluid().copy();
      state.capacity = tank.getCapacity();
      // if full, the fluid fades out with the cooling progress
      if (state.capacity > 0 && state.fluid.getAmount() == state.capacity && fluidOpacity >= 0) {
        FluidRenderInfo info = FluidRenderInfo.of(state.fluid);
        state.fadingFluid = info == null ? null : info.withOpacity(fluidOpacity);
      }
    }

    // items
    if (!state.renderItems.isEmpty()) {
      // input is normal
      RenderingHelper.extractItem(itemModelResolver, state.input, casting.getItem(0), state.renderItems.get(0), casting.getLevel());

      // output may be the recipe output instead of the current item
      if (state.renderItems.size() >= 2) {
        RenderItem outputModel = state.renderItems.get(1);
        if (!outputModel.isHidden()) {
          // get output stack
          ItemStack output = casting.getItem(1);
          boolean cooling = itemOpacity > 0 && output.isEmpty();
          if (cooling) {
            output = casting.getRecipeOutput();
          }
          RenderingHelper.extractItem(itemModelResolver, state.output, output, outputModel, casting.getLevel());
          // tint and add opacity to the cooling recipe output
          if (cooling) {
            state.output.tint(TintedItemRenderState.coolingTint(itemOpacity, fluidOpacity));
          }
        }
      }
    }
  }

  @Override
  public void submit(CastingRenderState state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera) {
    boolean hasFluid = !state.fluid.isEmpty() && state.capacity > 0;
    if (!hasFluid && state.input.isEmpty() && state.output.isEmpty()) {
      return;
    }
    // rotate the matrix
    boolean isRotated = RenderingHelper.applyRotation(matrices, state.blockState);

    // render fluids
    if (hasFluid) {
      FluidStack fluidStack = state.fluid;
      int light = state.lightCoords;
      // if full, start rendering with opacity for progress
      if (fluidStack.getAmount() == state.capacity) {
        if (state.fadingFluid != null) {
          for (FluidCuboid fluid : state.fluids) {
            FluidRenderer.submitCuboid(matrices, collector, fluid, state.fadingFluid, light);
          }
        }
      } else {
        // not strictly useful to scale the fluids down, but who knows what the modeler does
        for (FluidCuboid fluid : state.fluids) {
          FluidRenderer.submitScaledCuboid(matrices, collector, fluid, fluidStack, 0, state.capacity, light, false);
        }
      }
    }

    // render items
    if (!state.renderItems.isEmpty()) {
      RenderingHelper.submitItem(matrices, collector, state.input, state.renderItems.get(0), state.lightCoords);
      if (state.renderItems.size() >= 2) {
        RenderingHelper.submitItem(matrices, collector, state.output, state.renderItems.get(1), state.lightCoords);
      }
    }

    // pop back rotation
    if (isRotated) {
      matrices.popPose();
    }
  }

  /** Render state for casting tables and basins */
  public static class CastingRenderState extends BlockEntityRenderState {
    public List<FluidCuboid> fluids = List.of();
    public List<RenderItem> renderItems = List.of();
    /** Copy of the tank fluid */
    public FluidStack fluid = FluidStack.EMPTY;
    public int capacity = 0;
    /** Opacity of the fluid, fades out at the end of cooling */
    public int fluidOpacity = 0xFF;
    /** Fluid of a full tank faded by {@link #fluidOpacity}, null unless full */
    @Nullable
    public FluidRenderInfo fadingFluid;
    /** Cast or input item */
    public final ItemStackRenderState input = new ItemStackRenderState();
    /** Output item, or the cooling recipe output */
    public final TintedItemRenderState output = new TintedItemRenderState();
  }
}
