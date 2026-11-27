package slimeknights.mantle.loot.condition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.test.BaseMcTest;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M7: a {@code mantle:contains_item} ingredient naming a tag no datapack defines (the compat {@code #c:ores/<metal>} post conditions of the
 * lustrous loot modifiers) must decode, as it did up to 1.21.11, instead of failing the whole loot modifier with "Missing tag".
 */
class ContainsItemModifierLootConditionTest extends BaseMcTest {
  private static final String MISSING = "c:ores/mantle_test_missing";

  private static ContainsItemModifierLootCondition parse(String json) {
    return ContainsItemModifierLootCondition.deserialize(JsonParser.parseString(json), ContainsItemModifierLootCondition.class, null);
  }

  /** Binds the tags of an item's holder like a tag reload does ({@code Holder.Reference#bindTags} is package-private) */
  private static void bindTags(Item item, Collection<TagKey<Item>> tags) throws ReflectiveOperationException {
    Method bindTags = Holder.Reference.class.getDeclaredMethod("bindTags", Collection.class);
    bindTags.setAccessible(true);
    bindTags.invoke(item.builtInRegistryHolder(), tags);
  }

  @Test
  void missingTag_decodesAndMatchesNothing() {
    ContainsItemModifierLootCondition condition = parse("{\"type\":\"mantle:contains_item\",\"ingredient\":\"#" + MISSING + "\"}");
    assertThat(condition.test(List.of(new ItemStack(Items.IRON_ORE), new ItemStack(Items.STONE)), null)).isFalse();
    assertThat(condition.test(List.of(), null)).isFalse();
  }

  @Test
  void missingTag_serializesAsTheTag() {
    ContainsItemModifierLootCondition condition = parse("{\"type\":\"mantle:contains_item\",\"ingredient\":\"#" + MISSING + "\",\"needed\":2}");
    JsonObject json = condition.serialize(null);
    assertThat(json.get("ingredient").getAsString()).isEqualTo("#" + MISSING);
    assertThat(json.get("needed").getAsInt()).isEqualTo(2);
    assertThat(json.get("type").getAsString()).isEqualTo("mantle:contains_item");
  }

  @Test
  void missingTag_matchesOnceTheTagIsDefined() throws ReflectiveOperationException {
    ContainsItemModifierLootCondition condition = parse("{\"type\":\"mantle:contains_item\",\"ingredient\":\"#" + MISSING + "\",\"needed\":2}");
    TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(MISSING));
    List<TagKey<Item>> previous = Items.IRON_ORE.builtInRegistryHolder().tags().toList();
    try {
      // a later reload where a mod adds iron ore to the tag
      bindTags(Items.IRON_ORE, Set.of(tag));
      assertThat(condition.test(List.of(new ItemStack(Items.IRON_ORE)), null)).as("one of two needed").isFalse();
      assertThat(condition.test(List.of(new ItemStack(Items.STONE), new ItemStack(Items.IRON_ORE, 2)), null)).isTrue();
    } finally {
      bindTags(Items.IRON_ORE, previous);
    }
    assertThat(condition.test(List.of(new ItemStack(Items.IRON_ORE, 2)), null)).isFalse();
  }

  @Test
  void itemIngredient_unchanged() {
    ContainsItemModifierLootCondition condition = parse("{\"type\":\"mantle:contains_item\",\"ingredient\":\"minecraft:iron_ore\"}");
    assertThat(condition.test(List.of(new ItemStack(Items.IRON_ORE)), null)).isTrue();
    assertThat(condition.test(List.of(new ItemStack(Items.STONE)), null)).isFalse();
    JsonElement ingredient = condition.serialize(null).get("ingredient");
    assertThat(ingredient.toString()).contains("minecraft:iron_ore");
  }
}
