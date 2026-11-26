package slimeknights.tconstruct.library.json.condition;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The deprecated tag conditions serialize a tag as its registry id ({@code ResourceKey#identifier()} of the registry key, the 1.21.11 name of
 * {@code location()}) plus the tag id ({@code TagKey#location()}, unchanged). The receiver-dependent rename could swap these for
 * {@code ResourceKey#registry()} (always {@code minecraft:root} for a registry key) or the wrong key and still compile, so the encoded JSON is
 * pinned to the exact ids, and decoding it must give the same JSON back.
 */
@SuppressWarnings("removal")
class TagConditionCodecTest extends BaseMcTest {
  /** Encodes, checks the expected JSON, then decodes and re-encodes to the same JSON */
  private static <T> void assertJson(MapCodec<T> codec, T condition, String expected) {
    JsonElement encoded = codec.codec().encodeStart(JsonOps.INSTANCE, condition).getOrThrow();
    assertThat(encoded).isEqualTo(JsonParser.parseString(expected));
    T decoded = codec.codec().parse(JsonOps.INSTANCE, encoded).getOrThrow();
    assertThat(codec.codec().encodeStart(JsonOps.INSTANCE, decoded).getOrThrow()).isEqualTo(encoded);
  }

  @Test
  void tagNotEmpty_registryAndTagIds() {
    assertJson(TagNotEmptyCondition.CODEC, new TagNotEmptyCondition<>(ItemTags.PLANKS),
      "{\"registry\":\"minecraft:item\",\"tag\":\"minecraft:planks\"}");
  }

  @Test
  void tagDifference_registryAndTagIds() {
    assertJson(TagDifferencePresentCondition.CODEC, TagDifferencePresentCondition.ofKeys(BlockTags.LOGS, BlockTags.OAK_LOGS, BlockTags.BIRCH_LOGS),
      "{\"registry\":\"minecraft:block\",\"base\":\"minecraft:logs\",\"subtracted\":[\"minecraft:oak_logs\",\"minecraft:birch_logs\"]}");
  }

  @Test
  void tagIntersection_registryAndTagIds() {
    assertJson(TagIntersectionPresentCondition.CODEC, TagIntersectionPresentCondition.ofKeys(FluidTags.WATER, FluidTags.LAVA),
      "{\"registry\":\"minecraft:fluid\",\"tags\":[\"minecraft:water\",\"minecraft:lava\"]}");
  }
}
