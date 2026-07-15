package slimeknights.tconstruct.library.json.predicate.tool;

import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link ToolStackItemPredicate}. Note {@link net.minecraft.tags.TagKey} data (the {@code MODIFIABLE} tag
 * gating {@link ToolStackItemPredicate#matches}) is not populated in a bare unit test (no datapack reload), so only
 * the "definitely not a tool" path is exercised here - that is a real assertion regardless of tag state, since a
 * plain vanilla item is never in a Tinkers' tag either way. The "true" path needs a gametest/integration test.
 */
class ToolStackItemPredicateTest extends BaseMcTest {
  @Test
  void matches_falseForANonModifiableItem() {
    ToolStackItemPredicate predicate = ToolStackItemPredicate.ofContext(ToolContextPredicate.ANY);
    assertThat(predicate.matches(new ItemStack(Items.DIAMOND))).isFalse();
  }

  @Test
  void matches_falseForAnEmptyStack() {
    ToolStackItemPredicate predicate = ToolStackItemPredicate.ofContext(ToolContextPredicate.ANY);
    assertThat(predicate.matches(ItemStack.EMPTY)).isFalse();
  }

  @Test
  void serializeToJson_includesTheTypeAndThePredicate() {
    ToolStackItemPredicate predicate = ToolStackItemPredicate.ofContext(ToolContextPredicate.ANY);
    JsonObject json = predicate.serializeToJson().getAsJsonObject();
    assertThat(json.get("type").getAsString()).isEqualTo(ToolStackItemPredicate.ID.toString());
    assertThat(json.has("predicate")).isTrue();
  }
}
