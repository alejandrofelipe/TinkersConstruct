package slimeknights.tconstruct.tools.client.material;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import slimeknights.tconstruct.library.TinkerItemDisplays;
import slimeknights.tconstruct.tools.client.material.ThrownToolRenderer.ToolProjectileRenderState;
import slimeknights.tconstruct.tools.entity.ThrownTool;
import slimeknights.tconstruct.tools.entity.ToolProjectile;

/** Renderer for {@link ThrownTool} */
public class ThrownToolRenderer<T extends AbstractArrow & ToolProjectile> extends EntityRenderer<T, ToolProjectileRenderState> {
  protected final ItemRenderer itemRenderer;
  public ThrownToolRenderer(Context context) {
    super(context);
    this.itemRenderer = Minecraft.getInstance().getItemRenderer();
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
    state.tool = entity.getDisplayTool();
    state.level = entity.level();
    state.seed = entity.getId();
  }

  @Override
  public void render(ToolProjectileRenderState state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
    poseStack.pushPose();
    poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot - 90));
    poseStack.mulPose(Axis.ZP.rotationDegrees(state.xRot + 225));
    poseStack.translate(0.2, -0.2, 0);
    this.itemRenderer.renderStatic(state.tool, TinkerItemDisplays.THROWN, packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffer, state.level, state.seed);
    poseStack.popPose();
    super.render(state, poseStack, buffer, packedLight);
  }

  /** Render state carrying the thrown tool's rotation and display item */
  public static class ToolProjectileRenderState extends EntityRenderState {
    public float yRot;
    public float xRot;
    public ItemStack tool = ItemStack.EMPTY;
    public Level level;
    public int seed;
  }
}
