package slimeknights.tconstruct.fixture;

import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import slimeknights.tconstruct.tools.stats.HandleMaterialStats;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;
import slimeknights.tconstruct.tools.stats.StatlessMaterialStats;

public class MaterialItemFixture {

  public static ToolPartItem MATERIAL_ITEM, MATERIAL_ITEM_2, MATERIAL_ITEM_HEAD, MATERIAL_ITEM_HANDLE, MATERIAL_ITEM_EXTRA;

  private MaterialItemFixture() {
  }

  private static boolean init = false;
  @SuppressWarnings("unchecked")  // its correct, and if it were to fail this is tests
  public static void init() {
    if (init) {
      return;
    }
    init = true;
    ((MappedRegistry<Item>)BuiltInRegistries.ITEM).unfreeze(false); // yes, I know this is bad, but this is testing so we do bad things sometimes
    MATERIAL_ITEM = register("test_material", MaterialStatsFixture.STATS_TYPE);
    MATERIAL_ITEM_2 = register("test_material_2", MaterialStatsFixture.STATS_TYPE_2);
    MATERIAL_ITEM_HEAD = register("test_head", HeadMaterialStats.ID);
    MATERIAL_ITEM_HANDLE = register("test_handle", HandleMaterialStats.ID);
    MATERIAL_ITEM_EXTRA = register("test_extra", StatlessMaterialStats.BINDING.getIdentifier());
  }

  // PORT 1.21.4: Item.Properties now requires an id (Item#<init> reads it); outside the registration
  // framework a bare unit test must set it explicitly, matching the registry key.
  private static ToolPartItem register(String name, MaterialStatsId statsId) {
    ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", name);
    ToolPartItem item = new ToolPartItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)), statsId);
    Registry.register(BuiltInRegistries.ITEM, id, item);
    return item;
  }
}
