package slimeknights.tconstruct.library.client.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.client.armor.ArmorModelManager.ArmorModel;
import slimeknights.tconstruct.library.client.armor.texture.ArmorTextureSupplier;
import slimeknights.tconstruct.library.client.armor.texture.ArmorTextureSupplier.ArmorTexture;
import slimeknights.tconstruct.library.client.armor.texture.ArmorTextureSupplier.TextureType;

/**
 * Armor model that just applies the list of textures.
 *
 * <p>PORT 1.21.4: see {@link AbstractArmorModel} - this is no longer a {@code Model} subclass (final
 * {@code renderToBuffer}) and is not wired into the render path (the custom-armor-model hook is vestigial). The dynamic
 * layer-compositing logic is preserved in {@link #renderArmor} as Phase-4 scaffolding for the EquipmentClientInfo
 * re-seat; {@code base}/{@link #setup} capture the state the re-seat will need.
 */
public class MultilayerArmorModel extends AbstractArmorModel {
  public static final MultilayerArmorModel INSTANCE = new MultilayerArmorModel();

  protected ItemStack armorStack = ItemStack.EMPTY;
  protected ArmorModel model = ArmorModel.EMPTY;
  protected RegistryAccess registryAccess = RegistryAccess.EMPTY;

  protected MultilayerArmorModel() {}

  /** Prepares this model */
  public MultilayerArmorModel setup(LivingEntity living, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> base, ArmorModel model) {
    this.model = model;
    this.registryAccess = living.level().registryAccess();
    if (!model.layers().isEmpty()) {
      setup(living, stack, slot, base);
      this.armorStack = stack;
    } else {
      this.armorStack = ItemStack.EMPTY;
    }
    return this;
  }

  /**
   * Composites the dynamic texture layers over the base humanoid model.
   * PORT 1.21.4: previously the {@code Model#renderToBuffer} override invoked by the custom-armor-model hook; kept as
   * scaffolding (unwired) for the Phase-4 EquipmentClientInfo re-seat.
   */
  public void renderArmor(PoseStack matrices, VertexConsumer bufferIn, int packedLightIn, int packedOverlayIn, int color) {
    if (this.base != null && buffer != null) {
      // 1.21 passes a single packed ARGB color; unpack to feed the float-based texture render chain
      float alpha = ARGB.alpha(color) / 255.0F;
      float red = ARGB.red(color) / 255.0F;
      float green = ARGB.green(color) / 255.0F;
      float blue = ARGB.blue(color) / 255.0F;
      boolean armorGlint = hasGlint;
      for (ArmorTextureSupplier textureSupplier : model.layers()) {
        ArmorTexture texture = textureSupplier.getArmorTexture(armorStack, textureType, registryAccess);
        if (texture != ArmorTexture.EMPTY) {
          texture.renderTexture(base, matrices, buffer, packedLightIn, packedOverlayIn, red, green, blue, alpha, armorGlint);
          armorGlint = false;
        }
      }
    }
  }
}
