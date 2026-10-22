package slimeknights.tconstruct.tools.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.client.render.FluidCuboid;
import slimeknights.mantle.client.render.FluidRenderer;
import slimeknights.mantle.client.render.MantleRenderTypes;
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
    state.fluid = entity.getFluid();
  }

  @Override
  public void render(FluidProjectileRenderState state, PoseStack pMatrixStack, MultiBufferSource pBuffer, int pPackedLight) {
    pMatrixStack.pushPose();
    pMatrixStack.translate(0.0D, 0.15F, 0.0D);
    pMatrixStack.mulPose(Axis.YP.rotationDegrees(state.yRot - 90.0F));
    pMatrixStack.mulPose(Axis.ZP.rotationDegrees(state.xRot));
    FluidRenderer.renderCuboids(pMatrixStack, pBuffer.getBuffer(MantleRenderTypes.FLUID), fluids, state.fluid, pPackedLight);
    pMatrixStack.popPose();
    super.render(state, pMatrixStack, pBuffer, pPackedLight);
  }

  /** Render state carrying the projectile rotation and fluid */
  public static class FluidProjectileRenderState extends EntityRenderState {
    public float yRot;
    public float xRot;
    public FluidStack fluid = FluidStack.EMPTY;
  }
}
