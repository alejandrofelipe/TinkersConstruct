package slimeknights.tconstruct.tools.client.material;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import slimeknights.tconstruct.tools.client.material.ThrownShurikenRenderer.ShurikenRenderState;
import slimeknights.tconstruct.tools.entity.ThrownShuriken;
import slimeknights.tconstruct.tools.entity.ToolProjectile;

/** Renderer for {@link ThrownShuriken} */
public class ThrownShurikenRenderer<T extends Projectile & ToolProjectile> extends EntityRenderer<T, ShurikenRenderState> {
  private final ItemRenderer itemRenderer;
  public ThrownShurikenRenderer(EntityRendererProvider.Context context) {
    super(context);
    this.itemRenderer = Minecraft.getInstance().getItemRenderer();
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
    state.tool = entity.getDisplayTool();
    state.level = entity.level();
    state.seed = entity.getId();
  }

  @Override
  public void render(ShurikenRenderState state, PoseStack matrixStackIn, MultiBufferSource bufferIn, int packedLightIn) {
    if (state.tickCount >= 2 || state.distanceToCameraSq >= 12.25D) {
      matrixStackIn.pushPose();
      matrixStackIn.mulPose(Axis.YP.rotationDegrees(state.yRot + 90));
      matrixStackIn.mulPose(Axis.ZP.rotationDegrees((state.tickCount + state.partialTick) * 30 % 360));
      matrixStackIn.translate(-0.03125, -0.09375, 0);
      // TODO: custom display properties?
      this.itemRenderer.renderStatic(state.tool, ItemDisplayContext.GROUND, packedLightIn, OverlayTexture.NO_OVERLAY, matrixStackIn, bufferIn, state.level, state.seed);
      matrixStackIn.popPose();
      super.render(state, matrixStackIn, bufferIn, packedLightIn);
    }
  }

  /** Render state carrying the shuriken's spin and display item */
  public static class ShurikenRenderState extends EntityRenderState {
    public float yRot;
    public int tickCount;
    public ItemStack tool = ItemStack.EMPTY;
    public Level level;
    public int seed;
  }
}
