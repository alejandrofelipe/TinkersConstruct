package slimeknights.mantle.data.loadable.common;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.util.typed.TypedMap;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the 1.21.4 registry-aware ops fix: an {@link Ingredient} is a {@code HolderSet<Item>}, so parsing a plain
 * item reference requires a registry-aware {@link com.mojang.serialization.DynamicOps}. With the pre-fix
 * {@code JsonOps.INSTANCE} this threw "Can't decode element without registry". {@link IngredientLoadable} now resolves
 * through {@link slimeknights.mantle.data.loadable.RegistryJsonOps}.
 */
class IngredientLoadableTest extends BaseMcTest {
  @Test
  void convert_plainItemString_resolvesAgainstRegistry() {
    JsonElement json = new JsonPrimitive("minecraft:rabbit_stew");
    Ingredient ingredient = IngredientLoadable.DISALLOW_EMPTY.convert(json, "test", TypedMap.EMPTY);
    assertThat(ingredient.test(new ItemStack(Items.RABBIT_STEW))).isTrue();
    assertThat(ingredient.test(new ItemStack(Items.STONE))).isFalse();
  }

  @Test
  void roundTrip_plainItem() {
    Ingredient original = Ingredient.of(Items.RABBIT_STEW);
    JsonElement json = IngredientLoadable.DISALLOW_EMPTY.serialize(original);
    Ingredient parsed = IngredientLoadable.DISALLOW_EMPTY.convert(json, "test", TypedMap.EMPTY);
    assertThat(parsed.test(new ItemStack(Items.RABBIT_STEW))).isTrue();
    assertThat(parsed.test(new ItemStack(Items.STONE))).isFalse();
  }

  /**
   * Regression for the client recipe-sync decode crash (Connection Lost on joining any world). NeoForge reconstructs a
   * custom ingredient from its {@link com.mojang.serialization.Codec} over a {@code RegistryOps<Tag>} (NbtOps-backed).
   * {@link slimeknights.mantle.data.loadable.LoadableCodec#decode} converts the {@link Tag} input to a {@link JsonElement},
   * so the ops it threads into the loadable context must be re-parented to {@link com.mojang.serialization.JsonOps}. Before
   * the fix the original {@code RegistryOps<Tag>} leaked through and {@link IngredientLoadable} parsed the JsonElement with
   * NbtOps, throwing "JsonPrimitive cannot be cast to Tag" and making the whole {@code recipe_content} packet un-decodable.
   */
  @Test
  void codec_decodesOverRegistryNbtOps_clientSyncPath() {
    RegistryOps<Tag> nbtOps = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).createSerializationContext(NbtOps.INSTANCE);
    Codec<Ingredient> codec = IngredientLoadable.DISALLOW_EMPTY.codec();
    Ingredient parsed = codec.parse(nbtOps, StringTag.valueOf("minecraft:rabbit_stew")).getOrThrow();
    assertThat(parsed.test(new ItemStack(Items.RABBIT_STEW))).isTrue();
    assertThat(parsed.test(new ItemStack(Items.STONE))).isFalse();
  }
}
