package slimeknights.tconstruct.tools.client.material;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import slimeknights.tconstruct.library.TinkerItemDisplays;
import slimeknights.tconstruct.tools.client.material.ThrownToolRenderer.ToolProjectileRenderState;
import slimeknights.tconstruct.tools.entity.ThrownTool;
import slimeknights.tconstruct.tools.entity.ToolProjectile;

/** Renderer for {@link ThrownTool} */
public class ThrownToolRenderer<T extends AbstractArrow & ToolProjectile> extends EntityRenderer<T, ToolProjectileRenderState> {
  protected final ItemModelResolver itemModelResolver;
  public ThrownToolRenderer(Context context) {
    super(context);
    this.itemModelResolver = context.getItemModelResolver();
  }

  @Override
  public ToolProjectileRenderState createRenderState() {
    return new ToolProjectileRenderState();
  }

  @Override
  public void extractRenderState(T entity, ToolProjectileRenderState state, float partialTicks) {
    super.extractRenderState(entity, state, partialTicks);
    state.yRot = Mth.lerp(partialTicks, entity.yRotO, entity.getYRot());
    state.xRot = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
    // same seed as the old renderStatic call (entity ID)
    itemModelResolver.updateForNonLiving(state.tool, entity.getDisplayTool(), TinkerItemDisplays.THROWN, entity);
  }

  @Override
  public void submit(ToolProjectileRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
    poseStack.pushPose();
    poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot - 90));
    poseStack.mulPose(Axis.ZP.rotationDegrees(state.xRot + 225));
    poseStack.translate(0.2, -0.2, 0);
    state.tool.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
    poseStack.popPose();
    super.submit(state, poseStack, collector, camera);
  }

  /** Render state carrying the thrown tool's rotation and display item */
  public static class ToolProjectileRenderState extends EntityRenderState {
    public float yRot;
    public float xRot;
    public final ItemStackRenderState tool = new ItemStackRenderState();
  }
}
