package slimeknights.tconstruct.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.neoforge.common.loot.LootModifierManager;
import net.neoforged.neoforge.resource.NeoForgeReloadListeners;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.common.recipe.TinkerRecipeAccess;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.ModifierManager;
import slimeknights.tconstruct.library.tools.layout.StationSlotLayout;
import slimeknights.tconstruct.library.tools.layout.StationSlotLayoutLoader;
import slimeknights.tconstruct.tools.TinkerTools;

import java.util.ArrayList;
import java.util.List;

/**
 * Regression guard for datapack entries that 1.21.4 silently skipped at load because a referenced tag was missing:
 * slime embellishment recipes, the smithing-template diamond recipes, the raw-steel melting recipes, and the
 * tag-backed ore loot modifiers (lustrous cobalt, chrysophilite) whose {@code mantle:contains_item} condition is
 * decoded through Mantle's GSON codec.
 */
public class DatapackTagGapGameTests {
  /** Slime embellishment recipes, all gated on {@code #tconstruct:modifiable/embellishment/slime} */
  private static final List<String> SLIME_EMBELLISHMENTS = List.of(
    "earth", "sky", "ichor", "ender", "greenheart", "skyroot", "bloodshroom", "enderbark", "clay", "magma", "honey");

  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
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
      Identifier id = TConstruct.getResource("smeltery/melting/metal/steel/" + raw);
      if (TinkerRecipeAccess.byKey(helper.getLevel(), id).isPresent()) {
        unexpected.add(id.toString());
      }
    }
    if (!missing.isEmpty() || !unexpected.isEmpty()) {
      helper.fail(Component.literal("missing recipes " + missing + "; raw steel recipes loaded without c:raw_materials/steel " + unexpected));
    } else {
      helper.succeed();
    }
  }

  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
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
      helper.fail(Component.literal(String.join("; ", failures)));
    }
  }

  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void ore_loot_modifiers_load(GameTestHelper helper) {
    // 26.1: the loot modifier manager is a reload listener of the server resources, with a public lookup by id
    LootModifierManager loaded = helper.getLevel().getServer().getServerResources().managers().getListener(NeoForgeReloadListeners.LOOT_MODIFIERS_KEY);
    List<String> missing = new ArrayList<>();
    for (String id : List.of("lustrous/cobalt", "chrysophilite_modifier")) {
      if (loaded.getModifier(TConstruct.getResource(id)) == null) {
        missing.add(TConstruct.getResource(id).toString());
      }
    }
    if (missing.isEmpty()) {
      helper.succeed();
    } else {
      helper.fail(Component.literal("tag-backed loot modifiers failed to decode: " + missing));
    }
  }

  /**
   * Runtime JSON loaders must decode tag ingredients against the reload's staged tags: the built-in item registry only
   * gets its tags bound after the reload finishes, so on the first boot (dedicated server, gametest server) a loader
   * using {@code BuiltInRegistries} ops dropped every entry with a tag ingredient as "Missing tag".
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tag_ingredient_runtime_data_loads_on_first_boot(GameTestHelper helper) {
    List<String> missing = new ArrayList<>();
    for (String layout : List.of("tinker_station", "tinkers_anvil", "scorched_anvil")) {
      if (StationSlotLayoutLoader.getInstance().get(TConstruct.getResource(layout)) == StationSlotLayout.EMPTY) {
        missing.add("station layout " + layout);
      }
    }
    // 26.1: item icons decode during the reload, before default components are bound (stack codecs fail there, D-TPL)
    StationSlotLayout pickaxe = StationSlotLayoutLoader.getInstance().get(TConstruct.getResource("pickaxe"));
    ItemStack pickaxeIcon = pickaxe.getIcon().getValue(ItemStack.class);
    if (pickaxe == StationSlotLayout.EMPTY || pickaxeIcon == null || !pickaxeIcon.is(TinkerTools.pickaxe.get())) {
      missing.add("station layout pickaxe with its item icon (got " + pickaxeIcon + ")");
    }
    for (String modifier : List.of("stoneshield", "slimeball")) {
      if (!ModifierManager.INSTANCE.contains(new ModifierId(TConstruct.getResource(modifier)))) {
        missing.add("modifier " + modifier);
      }
    }
    if (missing.isEmpty()) {
      helper.succeed();
    } else {
      helper.fail(Component.literal("tag-ingredient data failed to load: " + missing));
    }
  }

  /** Records the recipe as missing if the recipe manager does not have it */
  private static void requireRecipe(GameTestHelper helper, String path, List<String> missing) {
    Identifier id = TConstruct.getResource(path);
    if (TinkerRecipeAccess.byKey(helper.getLevel(), id).isEmpty()) {
      missing.add(id.toString());
    }
  }
}
