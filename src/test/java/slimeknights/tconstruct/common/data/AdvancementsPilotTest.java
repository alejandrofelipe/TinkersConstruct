package slimeknights.tconstruct.common.data;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.test.JsonFileLoader;

import static org.assertj.core.api.Assertions.assertThat;

/** Validates the generated tinkering-path advancement JSONs (singular {@code advancement/} path). */
class AdvancementsPilotTest {
  private final JsonFileLoader loader = new JsonFileLoader(new Gson(), "advancement");

  @Test
  void root_materialsAndYou_generated() {
    // loadJson throws if the file is missing, so reaching the asserts proves the singular advancement/ path
    JsonObject json = loader.loadJson("tconstruct", "tools/materials_and_you");
    assertThat(json.has("criteria")).isTrue();
    JsonObject display = json.getAsJsonObject("display");
    // show_toast defaults to true and is omitted from JSON; assert on fields the codec actually emits
    assertThat(display.getAsJsonObject("icon").get("id").getAsString()).isEqualTo("tconstruct:materials_and_you");
    assertThat(display.get("announce_to_chat").getAsBoolean()).isFalse();
  }

  @Test
  void netheriteTier_usesToolSubPredicate() {
    JsonObject json = loader.loadJson("tconstruct", "tools/netherite_tier");
    JsonObject criteria = json.getAsJsonObject("criteria");
    // the harvest_level criterion must carry an item predicate with a tconstruct:tool sub-predicate
    assertThat(criteria.toString()).contains("tconstruct:tool");
    assertThat(json.get("parent").getAsString()).isEqualTo("tconstruct:tools/tinker_tool");
  }

  @Test
  void treeParentsResolve() {
    for (String id : new String[]{"tools/part_builder", "tools/make_part", "tools/tinker_station",
        "tools/tinker_tool", "tools/perfect_aim", "tools/one_shot", "tools/material_master",
        "tools/travelers_gear", "tools/tool_smith", "tools/modified", "tools/upgrade_slots"}) {
      assertThat(loader.loadJson("tconstruct", id).has("parent")).as(id).isTrue();
    }
  }

  @Test
  void startingBook_isConfigGatedHidden() {
    JsonObject json = loader.loadJson("tconstruct", "internal/starting_book");
    // config-gated via neoforge:conditions (replaces the removed ConditionalAdvancement)
    assertThat(json.has("neoforge:conditions")).isTrue();
    assertThat(json.getAsJsonArray("neoforge:conditions").toString()).contains("tconstruct:config");
    // hidden internal advancement: no display, has the tick criterion + a loot reward
    assertThat(json.has("display")).isFalse();
    assertThat(json.getAsJsonObject("criteria").has("tick")).isTrue();
    assertThat(json.getAsJsonObject("rewards").has("loot")).isTrue();
  }
}
