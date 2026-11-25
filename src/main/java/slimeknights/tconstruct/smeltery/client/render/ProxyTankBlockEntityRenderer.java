package slimeknights.tconstruct.smeltery.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import slimeknights.tconstruct.library.transfer.TransferUtil;
import slimeknights.mantle.client.render.FluidCuboid;
import slimeknights.mantle.client.render.FluidRenderer;
import slimeknights.mantle.client.render.InventoryRenderState;
import slimeknights.mantle.client.render.RenderItem;
import slimeknights.mantle.client.render.RenderingHelper;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.smeltery.block.entity.ProxyTankBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.tank.ProxyItemTank;
import slimeknights.tconstruct.smeltery.client.render.ProxyTankBlockEntityRenderer.ProxyTankRenderState;

import javax.annotation.Nullable;
import java.util.List;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

/** Renderer for {@link ProxyTankBlockEntity}. Unlike {@link TankInventoryBlockEntityRenderer}, does not use a {@link slimeknights.tconstruct.library.fluid.FluidTankAnimated} */
public class ProxyTankBlockEntityRenderer implements BlockEntityRenderer<ProxyTankBlockEntity, ProxyTankRenderState> {
  private final ItemModelResolver itemModelResolver;
  public ProxyTankBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    this.itemModelResolver = context.itemModelResolver();
  }

  @Override
  public ProxyTankRenderState createRenderState() {
    return new ProxyTankRenderState();
  }

  @Override
  public void extractRenderState(ProxyTankBlockEntity proxyTank, ProxyTankRenderState state, float partialTicks, Vec3 cameraPos, @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {
    BlockEntityRenderer.super.extractRenderState(proxyTank, state, partialTicks, cameraPos, breakProgress);
    state.facing = state.blockState.getValue(HORIZONTAL_FACING);
    state.fluids = Config.CLIENT.tankFluidModel.get() ? List.of() : FluidCuboid.REGISTRY.get(state.blockState, List.of());
    ProxyItemTank<?> itemTank = proxyTank.getItemTank();
    if (state.fluids.isEmpty()) {
      state.fluid = FluidStack.EMPTY;
      state.capacity = 0;
    } else {
      ResourceHandler<FluidResource> fluidHandler = itemTank.getFluidCapability();
      state.fluid = TransferUtil.getFluid(fluidHandler, 0).copy();
      state.capacity = TransferUtil.getCapacity(fluidHandler, 0);
    }
    state.extractItems(itemModelResolver, RenderItem.STATE_REGISTRY.get(state.blockState, List.of()), itemTank::getStackInSlot, proxyTank.getLevel());
  }

  @Override
  public void submit(ProxyTankRenderState state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera) {
    boolean hasFluid = !state.fluid.isEmpty() && state.capacity > 0;
    if (hasFluid || !state.isEmpty()) {
      // rotate the matrix
      boolean isRotated = RenderingHelper.applyRotation(matrices, state.facing);

      // render fluids
      if (hasFluid) {
        for (FluidCuboid cube : state.fluids) {
          FluidRenderer.submitScaledCuboid(matrices, collector, cube, state.fluid, 0, state.capacity, state.lightCoords, true);
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

  /** Render state for the proxy tank */
  public static class ProxyTankRenderState extends InventoryRenderState {
    public Direction facing = Direction.SOUTH;
    public List<FluidCuboid> fluids = List.of();
    /** Copy of the fluid in the held item */
    public FluidStack fluid = FluidStack.EMPTY;
    public int capacity = 0;
  }
}
