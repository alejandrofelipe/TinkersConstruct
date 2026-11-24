package slimeknights.tconstruct.library.client.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.client.armor.texture.ArmorTextureSupplier.TextureType;

import javax.annotation.Nullable;

/**
 * Common shared logic for material armor models.
 *
 * <p>PORT 1.21.4: this used to extend {@link Model} and be returned from the NeoForge custom-armor-model hook
 * ({@code IClientItemExtensions#getGenericArmorModel}) so it could composite multiple dynamic texture layers inside an
 * overridden {@code renderToBuffer}. In 1.21.4 {@code Model#renderToBuffer} is final (it only renders the model's root
 * {@code ModelPart}) and NeoForge no longer invokes the custom-armor-model hook at all — equipped armor renders through
 * {@code EquipmentLayerRenderer} using static {@code EquipmentClientInfo} JSON layers. This class therefore no longer
 * extends {@code Model}; the dynamic per-material/modifier compositing is preserved here as scaffolding but is currently
 * unwired. Phase-4 must re-seat it onto {@code EquipmentClientInfo} (register custom equipment assets) plus
 * {@code IClientItemExtensions#getArmorTexture}/{@code getArmorLayerTintColor} for the dynamic texture/tint swaps; the
 * embedded skull (slimeskull) has no equipment-layer analog and needs a dedicated render-state hook.
 */
public abstract class AbstractArmorModel {
  /** Base model instance for rendering */
  @Nullable
  protected HumanoidModel<?> base;
  /** If true, applies the enchantment glint to extra layers */
  protected boolean hasGlint = false;
  /** If true, uses the legs texture */
  protected TextureType textureType = TextureType.ARMOR;

  protected AbstractArmorModel() {}

  /** Sets up the model given the passed arguments */
  protected void setup(LivingEntity living, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> base) {
    this.base = base;
    this.hasGlint = stack.hasFoil();
    this.textureType = TextureType.fromSlot(slot);
    // PORT 1.21.4: the elytra/wings extra layer relied on the dead custom-model hook and the old ElytraModel
    // (non-generic now, state-driven setupAnim). Dropped pending the Phase-4 EquipmentClientInfo re-seat.
  }

  /** Renders a colored model */
  public static void renderColored(Model model, PoseStack matrices, VertexConsumer buffer, int packedLightIn, int packedOverlayIn, int color, float red, float green, float blue, float alpha) {
    if (color != -1) {
      alpha *= (float)(color >> 24 & 255) / 255.0F;
      red *= (float)(color >> 16 & 255) / 255.0F;
      green *= (float)(color >> 8 & 255) / 255.0F;
      blue *= (float)(color & 255) / 255.0F;
    }
    // 1.21 renderToBuffer takes a single packed ARGB int rather than four floats
    model.renderToBuffer(matrices, buffer, packedLightIn, packedOverlayIn, ARGB.colorFromFloat(alpha, red, green, blue));
  }


  /* Helpers */

  /**
   * Buffer from the render living event, stored as we lose access to it later.
   * 1.21.9: always null, {@code RenderLivingEvent} only exposes a {@code SubmitNodeCollector} now (two-phase rendering);
   * the unwired armor scaffolding must move to submits when it is re-seated.
   */
  @Nullable
  public static MultiBufferSource buffer;

  /**
   * Initializes the wrapper.
   * 1.21.9: no-op, the render living event no longer has a buffer source to capture, see {@link #buffer}.
   */
  public static void init() {}

  /** Replacement for the removed {@code ItemRenderer#getArmorFoilBuffer}, adding the armor glint when requested */
  public static VertexConsumer getArmorFoilBuffer(MultiBufferSource bufferSource, RenderType renderType, boolean hasGlint) {
    return hasGlint ? VertexMultiConsumer.create(bufferSource.getBuffer(RenderType.armorEntityGlint()), bufferSource.getBuffer(renderType)) : bufferSource.getBuffer(renderType);
  }
}
