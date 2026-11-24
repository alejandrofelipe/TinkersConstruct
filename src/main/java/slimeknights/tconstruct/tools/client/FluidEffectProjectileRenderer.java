package slimeknights.tconstruct.tools.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.client.render.FluidCuboid;
import slimeknights.mantle.client.render.FluidRenderer;
import slimeknights.tconstruct.tools.client.FluidEffectProjectileRenderer.FluidProjectileRenderState;
import slimeknights.tconstruct.tools.entity.FluidEffectProjectile;

import java.util.List;

public class FluidEffectProjectileRenderer extends EntityRenderer<FluidEffectProjectile, FluidProjectileRenderState> {
  private final List<FluidCuboid> fluids;
  public FluidEffectProjectileRenderer(Context context) {
    super(context);
    this.fluids = List.of(
      FluidCuboid.builder().from(-4,  0,  0).to(-2,  2,  2).build(),
      FluidCuboid.builder().from( 0, -4,  0).to( 2, -2,  2).build(),
      FluidCuboid.builder().from( 0,  0, -4).to( 2,  2, -2).build(),
      FluidCuboid.builder().from( 2,  0,  0).to( 4,  2,  2).build(),
      FluidCuboid.builder().from( 0,  0,  0).to( 2,  4,  2).build(),
      FluidCuboid.builder().from( 0,  0,  2).to( 2,  2,  4).build());
  }

  @Override
  public FluidProjectileRenderState createRenderState() {
    return new FluidProjectileRenderState();
  }

  @Override
  public void extractRenderState(FluidEffectProjectile entity, FluidProjectileRenderState state, float partialTicks) {
    super.extractRenderState(entity, state, partialTicks);
    state.yRot = Mth.lerp(partialTicks, entity.yRotO, entity.getYRot());
    state.xRot = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
    state.fluid = entity.getFluid().copy();
  }

  @Override
  public void submit(FluidProjectileRenderState state, PoseStack pMatrixStack, SubmitNodeCollector collector, CameraRenderState camera) {
    pMatrixStack.pushPose();
    pMatrixStack.translate(0.0D, 0.15F, 0.0D);
    pMatrixStack.mulPose(Axis.YP.rotationDegrees(state.yRot - 90.0F));
    pMatrixStack.mulPose(Axis.ZP.rotationDegrees(state.xRot));
    FluidRenderer.submitCuboids(pMatrixStack, collector, fluids, state.fluid, state.lightCoords);
    pMatrixStack.popPose();
    super.submit(state, pMatrixStack, collector, camera);
  }

  /** Render state carrying the projectile rotation and fluid */
  public static class FluidProjectileRenderState extends EntityRenderState {
    public float yRot;
    public float xRot;
    /** Copy of the projectile fluid */
    public FluidStack fluid = FluidStack.EMPTY;
  }
}
