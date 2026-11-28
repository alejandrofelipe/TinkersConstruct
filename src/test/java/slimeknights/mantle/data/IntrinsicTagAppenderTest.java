package slimeknights.mantle.data;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link IntrinsicTagAppender}: objects and keys end up in the tag file in call order, as with the removed intrinsic appender (MC 26.2, D-TAGS) */
class IntrinsicTagAppenderTest extends BaseMcTest {
  private static final ResourceKey<Registry<String>> REGISTRY = ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("test", "words"));

  private static ResourceKey<String> key(String path) {
    return ResourceKey.create(REGISTRY, Identifier.fromNamespaceAndPath("test", path));
  }

  private static TagKey<String> tag(String path) {
    return TagKey.create(REGISTRY, Identifier.fromNamespaceAndPath("test", path));
  }

  /** Appender over a fresh builder; objects are words whose key is test:word */
  private static IntrinsicTagAppender<String> appender(TagBuilder builder) {
    return IntrinsicTagAppender.of(TagAppender.forBuilder(builder), IntrinsicTagAppenderTest::key);
  }

  private static List<String> entries(TagBuilder builder) {
    return builder.build().stream().map(TagEntry::toString).toList();
  }

  @Test
  void objectsKeysAndTags_keepCallOrder() {
    TagBuilder builder = TagBuilder.create();
    appender(builder)
      .add("a")
      .addTag(tag("t"))
      .add(key("b"))
      .add("c", "d")
      .addOptional("e")
      .addOptional(key("f"))
      .addOptionalTag(tag("u"))
      .addTags(tag("v"), tag("w"))
      .addOptionalTags(tag("x"))
      .add(key("g"), key("h"))
      .addAll(List.of(key("i")));
    assertThat(entries(builder)).containsExactly(
      "test:a", "#test:t", "test:b", "test:c", "test:d", "test:e?", "test:f?", "#test:u?",
      "#test:v", "#test:w", "#test:x?", "test:g", "test:h", "test:i");
    assertThat(builder.shouldReplace()).isFalse();
  }

  @Test
  void replace_setsTheFlag() {
    TagBuilder builder = TagBuilder.create();
    appender(builder).add("a").replace();
    assertThat(builder.shouldReplace()).isTrue();
    appender(builder).replace(false);
    assertThat(builder.shouldReplace()).isFalse();
  }

  @Test
  void remove_objectsKeysAndTags() {
    TagBuilder builder = TagBuilder.create();
    appender(builder)
      .remove("a")
      .remove("b", "c")
      .remove(key("d"))
      .remove(tag("t"), tag("u"));
    assertThat(builder.getRemoveEntries().map(TagEntry::toString).toList())
      .containsExactly("test:a", "test:b", "test:c", "test:d", "#test:t", "#test:u");
    assertThat(builder.build()).isEmpty();
  }

  @Test
  void registry_looksKeysUp() {
    TagBuilder builder = TagBuilder.create();
    IntrinsicTagAppender.of(TagAppender.forBuilder(builder), BuiltInRegistries.FLUID).add(Fluids.WATER, Fluids.FLOWING_WATER).addOptional(Fluids.LAVA);
    assertThat(entries(builder)).containsExactly("minecraft:water", "minecraft:flowing_water", "minecraft:lava?");
  }

  @Test
  void keyExtractorFailure_propagates() {
    IntrinsicTagAppender<String> appender = IntrinsicTagAppender.of(TagAppender.forBuilder(TagBuilder.create()), word -> {
      throw new IllegalArgumentException("unregistered " + word);
    });
    assertThatThrownBy(() -> appender.add("nope")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nope");
  }
}
