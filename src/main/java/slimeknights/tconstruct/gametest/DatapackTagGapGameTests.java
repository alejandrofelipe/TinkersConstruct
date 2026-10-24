package slimeknights.tconstruct.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.neoforge.common.loot.LootModifierManager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.common.recipe.TinkerRecipeAccess;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.ModifierManager;
import slimeknights.tconstruct.library.tools.layout.StationSlotLayout;
import slimeknights.tconstruct.library.tools.layout.StationSlotLayoutLoader;
import slimeknights.tconstruct.tools.TinkerTools;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Regression guard for datapack entries that 1.21.4 silently skipped at load because a referenced tag was missing:
 * slime embellishment recipes, the smithing-template diamond recipes, the raw-steel melting recipes, and the
 * tag-backed ore loot modifiers (lustrous cobalt, chrysophilite) whose {@code mantle:contains_item} condition is
 * decoded through Mantle's GSON codec.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(TConstruct.MOD_ID)
public class DatapackTagGapGameTests {
  /** Slime embellishment recipes, all gated on {@code #tconstruct:modifiable/embellishment/slime} */
  private static final List<String> SLIME_EMBELLISHMENTS = List.of(
    "earth", "sky", "ichor", "ender", "greenheart", "skyroot", "bloodshroom", "enderbark", "clay", "magma", "honey");

  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void previously_skipped_recipes_load(GameTestHelper helper) {
    List<String> missing = new ArrayList<>();
    for (String slime : SLIME_EMBELLISHMENTS) {
      requireRecipe(helper, "tools/modifiers/slotless/embellishment/slime/" + slime, missing);
    }
    requireRecipe(helper, "smeltery/melting/diamond/smithing_template", missing);
    requireRecipe(helper, "smeltery/casting/diamond/smithing_template", missing);
    // raw steel has no item in this pack, so the condition-gated raw-ore recipes must stay out rather than error
    List<String> unexpected = new ArrayList<>();
    for (String raw : List.of("raw", "raw_block")) {
      ResourceLocation id = TConstruct.getResource("smeltery/melting/metal/steel/" + raw);
      if (TinkerRecipeAccess.byKey(helper.getLevel(), id).isPresent()) {
        unexpected.add(id.toString());
      }
    }
    if (!missing.isEmpty() || !unexpected.isEmpty()) {
      helper.fail("missing recipes " + missing + "; raw steel recipes loaded without c:raw_materials/steel " + unexpected);
    } else {
      helper.succeed();
    }
  }

  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void port_tags_populated(GameTestHelper helper) {
    List<String> failures = new ArrayList<>();
    if (!new ItemStack(TinkerTools.slimesuit.get(ArmorType.HELMET)).is(TinkerTags.Items.EMBELLISHMENT_SLIME)) {
      failures.add("slime helmet not in " + TinkerTags.Items.EMBELLISHMENT_SLIME.location());
    }
    if (!new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE).is(TinkerTags.Items.SMITHING_TEMPLATES)) {
      failures.add("coast trim template not in " + TinkerTags.Items.SMITHING_TEMPLATES.location());
    }
    if (!new ItemStack(Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE).is(TinkerTags.Items.SMITHING_TEMPLATES)) {
      failures.add("bolt trim template not in " + TinkerTags.Items.SMITHING_TEMPLATES.location());
    }
    if (failures.isEmpty()) {
      helper.succeed();
    } else {
      helper.fail(String.join("; ", failures));
    }
  }

  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void ore_loot_modifiers_load(GameTestHelper helper) {
    // NeoForge exposes no public accessor for the loaded modifiers by id, so read them reflectively
    Map<ResourceLocation, ?> loaded;
    try {
      Method getter = Class.forName("net.neoforged.neoforge.common.NeoForgeEventHandler").getDeclaredMethod("getLootModifierManager");
      getter.setAccessible(true);
      Field mods = LootModifierManager.class.getDeclaredField("registeredLootModifiers");
      mods.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<ResourceLocation, ?> map = (Map<ResourceLocation, ?>) mods.get(getter.invoke(null));
      loaded = map;
    } catch (ReflectiveOperationException | RuntimeException e) {
      helper.fail("could not read NeoForge's loaded loot modifiers: " + e);
      return;
    }
    List<String> missing = new ArrayList<>();
    for (String id : List.of("lustrous/cobalt", "chrysophilite_modifier")) {
      if (!loaded.containsKey(TConstruct.getResource(id))) {
        missing.add(TConstruct.getResource(id).toString());
      }
    }
    if (missing.isEmpty()) {
      helper.succeed();
    } else {
      helper.fail("tag-backed loot modifiers failed to decode: " + missing);
    }
  }

  /**
   * Runtime JSON loaders must decode tag ingredients against the reload's staged tags: the built-in item registry only
   * gets its tags bound after the reload finishes, so on the first boot (dedicated server, gametest server) a loader
   * using {@code BuiltInRegistries} ops dropped every entry with a tag ingredient as "Missing tag".
   */
  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tag_ingredient_runtime_data_loads_on_first_boot(GameTestHelper helper) {
    List<String> missing = new ArrayList<>();
    for (String layout : List.of("tinker_station", "tinkers_anvil", "scorched_anvil")) {
      if (StationSlotLayoutLoader.getInstance().get(TConstruct.getResource(layout)) == StationSlotLayout.EMPTY) {
        missing.add("station layout " + layout);
      }
    }
    for (String modifier : List.of("stoneshield", "slimeball")) {
      if (!ModifierManager.INSTANCE.contains(new ModifierId(TConstruct.getResource(modifier)))) {
        missing.add("modifier " + modifier);
      }
    }
    if (missing.isEmpty()) {
      helper.succeed();
    } else {
      helper.fail("tag-ingredient data failed to load: " + missing);
    }
  }

  /** Records the recipe as missing if the recipe manager does not have it */
  private static void requireRecipe(GameTestHelper helper, String path, List<String> missing) {
    ResourceLocation id = TConstruct.getResource(path);
    if (TinkerRecipeAccess.byKey(helper.getLevel(), id).isEmpty()) {
      missing.add(id.toString());
    }
  }
}
