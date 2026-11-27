package slimeknights.tconstruct.smeltery.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import slimeknights.mantle.client.render.FluidCuboid;
import slimeknights.mantle.client.render.InventoryRenderState;
import slimeknights.mantle.client.render.RenderItem;
import slimeknights.mantle.client.render.RenderingHelper;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.library.client.RenderUtils;
import slimeknights.tconstruct.library.client.RenderUtils.TankRenderData;
import slimeknights.tconstruct.library.transfer.TransferUtil;
import slimeknights.tconstruct.smeltery.block.entity.ITankBlockEntity.ITankInventoryBlockEntity;
import slimeknights.tconstruct.smeltery.client.render.TankInventoryBlockEntityRenderer.TankInventoryRenderState;

import org.jspecify.annotations.Nullable;
import java.util.List;

public class TankInventoryBlockEntityRenderer<T extends BlockEntity & ITankInventoryBlockEntity> implements BlockEntityRenderer<T, TankInventoryRenderState> {
  private final EnumProperty<Direction> directionProperty;
  private final ItemModelResolver itemModelResolver;
  public TankInventoryBlockEntityRenderer(Context context, EnumProperty<Direction> directionProperty) {
    this.directionProperty = directionProperty;
    this.itemModelResolver = context.itemModelResolver();
  }

  @Override
  public TankInventoryRenderState createRenderState() {
    return new TankInventoryRenderState();
  }

  @Override
  public void extractRenderState(T melter, TankInventoryRenderState state, float partialTicks, Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
    BlockEntityRenderer.super.extractRenderState(melter, state, partialTicks, cameraPos, breakProgress);
    state.facing = state.blockState.getValue(directionProperty);
    state.fluids = Config.CLIENT.tankFluidModel.get() ? List.of() : FluidCuboid.REGISTRY.get(state.blockState, List.of());
    state.tank = state.fluids.isEmpty() ? TankRenderData.EMPTY : RenderUtils.extractFluidTank(melter.getTank(), partialTicks);
    // TODO: can we show count somehow?
    ResourceHandler<ItemResource> inventory = melter.getDisplayInventory();
    state.extractItems(itemModelResolver, RenderItem.STATE_REGISTRY.get(state.blockState, List.of()), index -> TransferUtil.getStack(inventory, index), melter.getLevel());
  }

  @Override
  public void submit(TankInventoryRenderState state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera) {
    if (!state.tank.isEmpty() || !state.isEmpty()) {
      // rotate the matrix
      boolean isRotated = RenderingHelper.applyRotation(matrices, state.facing);

      // render fluids
      if (!state.tank.isEmpty()) {
        for (FluidCuboid fluid : state.fluids) {
          RenderUtils.submitFluidTank(matrices, collector, fluid, state.tank, state.lightCoords, true);
        }
      }

      // render items
      state.submitItems(matrices, collector);

      // pop back rotation
      if (isRotated) {
        matrices.popPose();
      }
    }
  }

  /** Render state for a block with a fluid tank and an item display */
  public static class TankInventoryRenderState extends InventoryRenderState {
    public Direction facing = Direction.SOUTH;
    public List<FluidCuboid> fluids = List.of();
    public TankRenderData tank = TankRenderData.EMPTY;
  }
}
