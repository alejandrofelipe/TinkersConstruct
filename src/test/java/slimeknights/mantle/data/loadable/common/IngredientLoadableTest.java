package slimeknights.mantle.data.loadable.common;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
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
}
