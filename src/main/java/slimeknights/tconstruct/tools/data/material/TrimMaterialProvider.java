package slimeknights.tconstruct.tools.data.material;

import net.minecraft.Util;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.component.ProvidesTrimMaterial;
import net.minecraft.world.item.equipment.trim.MaterialAssetGroup;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import slimeknights.mantle.registration.object.MetalItemObject;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.shared.TinkerMaterials;
import slimeknights.tconstruct.world.TinkerWorld;

/** Provider for trim materials */
public class TrimMaterialProvider {
  private static final String TRIM_FORMAT = TConstruct.makeDescriptionId("trim_material", "format");

  /** Registers all providers */
  public static void register(RegistrySetBuilder builder) {
    builder.add(Registries.TRIM_MATERIAL, TrimMaterialProvider::registerTrimMaterials);
  }

  /** Registers all trim materials with the context */
  private static void registerTrimMaterials(BootstrapContext<TrimMaterial> context) {
    materials((material, ingredient, color, modelIndex) -> context.register(
      key(material),
      // PORT 1.21.5: trim materials no longer store their ingredient (the item provides the material through a component,
      // see addIngredientComponents), only the asset group and description; modelIndex no longer feeds the material
      new TrimMaterial(MaterialAssetGroup.create(material.getSuffix()),
        Component.translatable(TRIM_FORMAT, Component.translatable(Util.makeDescriptionId("material", material.getLocation()))).withStyle(style -> style.withColor(color)))
    ));
  }

  /**
   * Makes the ingredient items provide their trim material.
   * 1.21.5: vanilla and the armor trim modifier find the material from the {@link DataComponents#PROVIDES_TRIM_MATERIAL} component of the ingredient.
   */
  public static void addIngredientComponents(ModifyDefaultComponentsEvent event) {
    materials((material, ingredient, color, modelIndex) -> event.modify(ingredient, builder -> builder.set(DataComponents.PROVIDES_TRIM_MATERIAL, new ProvidesTrimMaterial(key(material)))));
  }

  /** Lists all trim materials */
  private static void materials(MaterialConsumer consumer) {
    // we set model indexes as fallbacks for when trimmed is not installed so you have at least something on vanilla models
    consumer.accept(MaterialIds.slimesteel,     TinkerMaterials.slimesteel,     0x27C6C6, 0.8f); // diamond
    consumer.accept(MaterialIds.amethystBronze, TinkerMaterials.amethystBronze, 0xC687BD, 1.0f); // amethyst
    consumer.accept(MaterialIds.pigIron,        TinkerMaterials.pigIron,        0xF0A8A4, 0.5f); // copper
    consumer.accept(MaterialIds.roseGold,       TinkerMaterials.roseGold,       0xF7CDBB, 0.1f); // quartz

    consumer.accept(MaterialIds.cobalt,      TinkerMaterials.cobalt,      0x2376dd, 0.9f); // lapis
    consumer.accept(MaterialIds.steel,       TinkerMaterials.steel,       0x959595, 0.2F); // iron
    consumer.accept(MaterialIds.manyullyn,   TinkerMaterials.manyullyn,   0x9261cc, 1.0f); // amethyst
    consumer.accept(MaterialIds.hepatizon,   TinkerMaterials.hepatizon,   0x60496b, 0.3f); // netherite
    consumer.accept(MaterialIds.cinderslime, TinkerMaterials.cinderslime, 0xB80000, 0.4F); // redstone
    consumer.accept(MaterialIds.queensSlime, TinkerMaterials.queensSlime, 0x236c45, 0.7f); // emerald
    consumer.accept(MaterialIds.knightmetal, TinkerMaterials.knightmetal, 0xC4D6AE, 0.1f); // quartz
    consumer.accept(MaterialIds.knightslime, TinkerMaterials.knightslime, 0xB771FF, 0.3F); // netherite

    consumer.accept(MaterialIds.earthslime, TinkerWorld.earthGeode, 0x01cd4e, 0.7f); // emerald
    consumer.accept(MaterialIds.skyslime,   TinkerWorld.skyGeode,   0x01cbcd, 0.8f); // diamond
    consumer.accept(MaterialIds.ichor,      TinkerWorld.ichorGeode, 0xff970d, 0.5f); // copper
    consumer.accept(MaterialIds.enderslime, TinkerWorld.enderGeode, 0xaf4cf6, 1.0f); // amethyst
  }

  /** Gets the trim material key for the given material */
  private static ResourceKey<TrimMaterial> key(MaterialId material) {
    return ResourceKey.create(Registries.TRIM_MATERIAL, material.getLocation());
  }

  /** Consumer for a trim material definition */
  @FunctionalInterface
  private interface MaterialConsumer {
    void accept(MaterialId material, ItemLike ingredient, int color, float modelIndex);

    /** Accepts a trim material using the ingot */
    default void accept(MaterialId material, MetalItemObject ingredient, int color, float modelIndex) {
      accept(material, ingredient.getIngot(), color, modelIndex);
    }
  }
}