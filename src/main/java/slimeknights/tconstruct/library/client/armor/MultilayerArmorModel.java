package slimeknights.tconstruct.library.client.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.client.armor.ArmorModelManager.ArmorModel;
import slimeknights.tconstruct.library.client.armor.TinkerArmorLayer.Context;
import slimeknights.tconstruct.library.client.armor.texture.ArmorTextureSupplier;
import slimeknights.tconstruct.library.client.armor.texture.ArmorTextureSupplier.ArmorTexture;
import slimeknights.tconstruct.library.client.armor.texture.ArmorTextureSupplier.TextureType;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.item.armor.ModifiableArmorItem;

/**
 * Renderer for worn armor that just applies the list of textures from its {@link ArmorModel}, called by {@link TinkerArmorLayer}.
 * 1.21.4: was a {@link Model} subclass returned from the custom armor model hook; models are now final and stateless, so this
 * submits each texture layer on the vanilla armor model instead.
 */
public class MultilayerArmorModel {
  public static final MultilayerArmorModel INSTANCE = new MultilayerArmorModel();
  /** Order of the first armor layer, matching vanilla's {@code HumanoidArmorLayer}. The entity itself uses order 0. */
  protected static final int FIRST_ORDER = 1;

  protected MultilayerArmorModel() {}

  /**
   * Submits the armor worn in the given slot.
   * @param model       Armor model with the texture layers
   * @param stack       Worn stack
   * @param slot        Slot the stack is worn in
   * @param armorModel  Vanilla armor model for the slot
   * @param context     Entity being rendered
   */
  public <S extends HumanoidRenderState> void submit(ArmorModel model, ItemStack stack, EquipmentSlot slot, HumanoidModel<S> armorModel, Context<S> context) {
    submitLayers(model, stack, TextureType.fromSlot(slot), armorModel, context, FIRST_ORDER);
    // elytra modifier adds wings, drawn from the wings variant of the same layers
    if (slot == EquipmentSlot.CHEST && ModifierUtil.checkVolatileFlag(stack, ModifiableArmorItem.ELYTRA)) {
      PoseStack matrices = context.matrices();
      matrices.pushPose();
      // same offset and first order as vanilla's wings layer
      matrices.translate(0.0F, 0.0F, 0.125F);
      submitLayers(model, stack, TextureType.WINGS, context.wings(), context, 0);
      matrices.popPose();
    }
  }

  /**
   * Submits each texture layer of the armor model, glint going on the first layer that renders.
   * @return  Next free order
   */
  protected static <S extends HumanoidRenderState> int submitLayers(ArmorModel model, ItemStack stack, TextureType textureType, Model<? super S> target, Context<S> context, int order) {
    boolean glint = stack.hasFoil();
    for (ArmorTextureSupplier textureSupplier : model.layers()) {
      ArmorTexture texture = textureSupplier.getArmorTexture(stack, textureType, context.registryAccess());
      if (texture != ArmorTexture.EMPTY) {
        order = texture.submit(target, context.state(), context.matrices(), context.collector(), order, context.packedLight(), context.state().outlineColor, glint);
        glint = false;
      }
    }
    return order;
  }
}
