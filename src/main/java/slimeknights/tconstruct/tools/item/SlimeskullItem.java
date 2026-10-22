package slimeknights.tconstruct.tools.item;

import net.minecraft.client.model.Model;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.armor.ArmorModelManager.ArmorModelDispatcher;
import slimeknights.tconstruct.library.tools.definition.ModifiableArmorMaterial;
import slimeknights.tconstruct.library.tools.helper.ArmorUtil;
import slimeknights.tconstruct.library.tools.item.armor.ModifiableArmorItem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;

/** This item is mainly to return the proper model for a slimeskull */
public class SlimeskullItem extends ModifiableArmorItem {
  /** Model ID for our slimeskull. You may want your own for a custom slimeskull */
  public static final ResourceLocation MODEL_LOCATION = TConstruct.getResource("slimeskull");

  private final ResourceLocation name;

  public SlimeskullItem(ModifiableArmorMaterial material, ResourceLocation name, Properties properties) {
    super(material, ArmorType.HELMET, properties);
    this.name = name;
  }

  public SlimeskullItem(ModifiableArmorMaterial material, Properties properties) {
    this(material, material.getId(), properties);
  }

  // PORT M3 (armor layers): IItemExtension#getArmorTexture(ItemStack, Entity, EquipmentSlot, String) was removed
  // in 1.21; armor textures are now resolved via ArmorMaterial layers / IClientItemExtensions. Deferred to the
  // armor-layer port (mirrors MultilayerArmorItem).
  @Nullable
  public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
    return ArmorUtil.getDummyArmorTexture(slot);
  }

  // PORT 1.21.4: IItemExtension#initializeClient was removed (client extensions now register via
  // RegisterClientExtensionsEvent). Kept as a plain helper until the client phase rewires it.
  public void initializeClient(Consumer<IClientItemExtensions> consumer) {
    consumer.accept(new ArmorModelDispatcher() {
      @Override
      protected ResourceLocation getName() {
        return name;
      }

      // PORT 1.21.4: the custom-armor-model hook (now (ItemStack, LayerType, Model)) is no longer invoked by NeoForge
      // 21.4, so the embedded-skull slimeskull model ({@link SlimeskullArmorModel}) can no longer be injected this way.
      // Returns the vanilla model; the slimeskull's dynamic armor layers + embedded skull need a dedicated render-state
      // hook (no EquipmentClientInfo analog for the skull) in Phase-4. getModel(stack) keeps the model cache warm.
      @Nonnull
      @Override
      public Model getHumanoidArmorModel(ItemStack stack, EquipmentClientInfo.LayerType layerType, Model original) {
        getModel(stack);
        return original;
      }
    });
  }
}
