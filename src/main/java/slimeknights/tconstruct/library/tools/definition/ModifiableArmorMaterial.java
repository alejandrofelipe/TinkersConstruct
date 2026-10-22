package slimeknights.tconstruct.library.tools.definition;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.equipment.ArmorType;
import slimeknights.tconstruct.library.tools.item.armor.DummyArmorMaterial;

import javax.annotation.Nullable;

/** Armor material that doubles as a container for tool definitions for each armor slot */
public class ModifiableArmorMaterial extends DummyArmorMaterial {
  /** Array of all four armor equipment slots */
  public static final EquipmentSlot[] EQUIPMENT_SLOTS = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

  /** The four humanoid armor slots; excludes ArmorType.BODY (1.21 animal-armor slot, unused by Tinkers). */
  public static final ArmorType[] ARMOR_SLOTS = { ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS };

  /** Array of slot index to tool definition for the slot */
  private final ToolDefinition[] armorDefinitions;

  private ModifiableArmorMaterial(ResourceLocation id, SoundEvent equipSound, ToolDefinition... armorDefinitions) {
    super(id, equipSound);
    if (armorDefinitions.length != ArmorType.values().length) {
      throw new IllegalArgumentException("Must have an armor definition slot for each ArmorType");
    }
    this.armorDefinitions = armorDefinitions;
  }

  /** Creates a modifiable armor material, creates tool definition for the selected slots */
  public static ModifiableArmorMaterial create(ResourceLocation id, SoundEvent equipSound, ArmorType... slots) {
    ToolDefinition[] definitions = new ToolDefinition[ArmorType.values().length];
    for (ArmorType slot : slots) {
      definitions[slot.ordinal()] = ToolDefinition.create(id.withSuffix("_" + slot.getName()));
    }
    return new ModifiableArmorMaterial(id, equipSound, definitions);
  }

  /** Creates a modifiable armor material, creates tool definition for all four armor slots */
  public static ModifiableArmorMaterial create(ResourceLocation id, SoundEvent equipSound) {
    // Tinkers armor is humanoid-only; ARMOR_SLOTS excludes ArmorType.BODY (added in 1.21 for wolf/animal armor)
    return create(id, equipSound, ARMOR_SLOTS);
  }

  /**
   * Gets the armor definition for the given armor slot, used in item construction
   * @param slotType  Slot type
   * @return  Armor definition
   */
  @Nullable
  public ToolDefinition getArmorDefinition(ArmorType slotType) {
    return armorDefinitions[slotType.ordinal()];
  }
}
