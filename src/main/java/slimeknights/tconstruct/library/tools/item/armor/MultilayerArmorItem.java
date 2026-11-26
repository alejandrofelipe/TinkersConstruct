package slimeknights.tconstruct.library.tools.item.armor;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import slimeknights.tconstruct.library.client.armor.ArmorModelManager.ArmorModelDispatcher;
import slimeknights.tconstruct.library.tools.definition.ModifiableArmorMaterial;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.helper.ArmorUtil;

import org.jspecify.annotations.Nullable;
import java.util.function.Consumer;

/** Armor model that applies multiple texture layers in order */
public class MultilayerArmorItem extends ModifiableArmorItem {
  private final Identifier name;
  public MultilayerArmorItem(ModifiableArmorMaterial material, ArmorType slot, Properties properties) {
    this(material, slot, properties, material.getId());
  }

  public MultilayerArmorItem(ModifiableArmorMaterial material, ArmorType slot, Properties properties, Identifier name) {
    super(material, slot, properties, EQUIPMENT_ASSET);
    this.name = name;
  }

  public MultilayerArmorItem(DummyArmorMaterial material, ArmorType slot, Properties properties, ToolDefinition toolDefinition) {
    this(material, slot, properties, toolDefinition, Identifier.parse(material.getName()));
  }

  public MultilayerArmorItem(DummyArmorMaterial material, ArmorType slot, Properties properties, ToolDefinition toolDefinition, Identifier name) {
    this(material, slot, properties, toolDefinition, name, EQUIPMENT_ASSET);
  }

  /**
   * Creates armor drawn from an armor model.
   * @param equipmentAsset  {@link #EQUIPMENT_ASSET}, or {@link #WINGS_EQUIPMENT_ASSET} for a chestplate that always has wings
   */
  public MultilayerArmorItem(DummyArmorMaterial material, ArmorType slot, Properties properties, ToolDefinition toolDefinition, Identifier name, ResourceKey<EquipmentAsset> equipmentAsset) {
    super(material, slot, properties, toolDefinition, equipmentAsset);
    this.name = name;
  }

  // PORT M3 (armor layers): IItemExtension#getArmorTexture(ItemStack, Entity, EquipmentSlot, String) was removed
  // in 1.21; armor textures are now resolved via ArmorMaterial layers / IClientItemExtensions. Deferred to the
  // armor-layer port.
  @Nullable
  public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
    return ArmorUtil.getDummyArmorTexture(slot);
  }

  /**
   * Creates the client extensions drawing this armor when worn, see {@link slimeknights.tconstruct.library.client.armor.TinkerArmorLayer}.
   * 1.21.4: {@code IItemExtension#initializeClient} was removed, called from {@code ToolClientEvents} on {@code RegisterClientExtensionsEvent}.
   */
  public void initializeClient(Consumer<IClientItemExtensions> consumer) {
    consumer.accept(new ArmorModelDispatcher() {
      @Override
      protected Identifier getName() {
        return name;
      }
    });
  }
}
