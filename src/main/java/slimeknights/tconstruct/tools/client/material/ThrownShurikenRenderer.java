package slimeknights.tconstruct.tools.client.material;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemDisplayContext;
import slimeknights.tconstruct.tools.client.material.ThrownShurikenRenderer.ShurikenRenderState;
import slimeknights.tconstruct.tools.entity.ThrownShuriken;
import slimeknights.tconstruct.tools.entity.ToolProjectile;

/** Renderer for {@link ThrownShuriken} */
public class ThrownShurikenRenderer<T extends Projectile & ToolProjectile> extends EntityRenderer<T, ShurikenRenderState> {
  private final ItemModelResolver itemModelResolver;
  public ThrownShurikenRenderer(EntityRendererProvider.Context context) {
    super(context);
    this.itemModelResolver = context.getItemModelResolver();
  }

  @Override
  public ShurikenRenderState createRenderState() {
    return new ShurikenRenderState();
  }

  @Override
  public void extractRenderState(T entity, ShurikenRenderState state, float partialTicks) {
    super.extractRenderState(entity, state, partialTicks);
    state.yRot = Mth.lerp(partialTicks, entity.yRotO, entity.getYRot());
    state.tickCount = entity.tickCount;
    // TODO: custom display properties?
    // same seed as the old renderStatic call (entity ID)
    itemModelResolver.updateForNonLiving(state.tool, entity.getDisplayTool(), ItemDisplayContext.GROUND, entity);
  }

  @Override
  public void submit(ShurikenRenderState state, PoseStack matrixStackIn, SubmitNodeCollector collector, CameraRenderState camera) {
    if (state.tickCount >= 2 || state.distanceToCameraSq >= 12.25D) {
      matrixStackIn.pushPose();
      matrixStackIn.mulPose(Axis.YP.rotationDegrees(state.yRot + 90));
      matrixStackIn.mulPose(Axis.ZP.rotationDegrees((state.tickCount + state.partialTick) * 30 % 360));
      matrixStackIn.translate(-0.03125, -0.09375, 0);
      state.tool.submit(matrixStackIn, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
      matrixStackIn.popPose();
      super.submit(state, matrixStackIn, collector, camera);
    }
  }

  /** Render state carrying the shuriken's spin and display item */
  public static class ShurikenRenderState extends EntityRenderState {
    public float yRot;
    public int tickCount;
    public final ItemStackRenderState tool = new ItemStackRenderState();
  }
}
