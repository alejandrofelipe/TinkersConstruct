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
}
