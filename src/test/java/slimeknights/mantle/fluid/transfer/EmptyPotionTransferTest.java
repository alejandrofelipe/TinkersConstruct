package slimeknights.mantle.fluid.transfer;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.datagen.MantleTags;
import slimeknights.mantle.recipe.helper.ItemOutput;
import slimeknights.mantle.recipe.helper.TagPreference;
import slimeknights.tconstruct.test.BaseMcTest;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code TagPreference} resolves {@link MantleTags.Fluids#POTION} from datapack tags, which are never loaded in a
 * bare unit test - {@link TagPreference#getPreference} would just return an empty optional and the potion-contents
 * copy under test would never run. Reflectively seed {@code TagPreference}'s private preference cache with a
 * registered vanilla fluid instead; the assertions only care about the carried {@code PotionContents} component,
 * not which fluid backs it.
 */
class EmptyPotionTransferTest extends BaseMcTest {
  @AfterEach
  void clearPreferenceCache() throws ReflectiveOperationException {
    preferenceCache().clear();
  }

  @Test
  void getFluid_nonWaterPotion_carriesPotionContents() throws ReflectiveOperationException {
    preferenceCache().put(MantleTags.Fluids.POTION, Optional.of(Fluids.LAVA));
    EmptyPotionTransfer transfer = new EmptyPotionTransfer(Ingredient.of(Items.POTION), ItemOutput.fromItem(Items.GLASS_BOTTLE), 1000);
    ItemStack stack = new ItemStack(Items.POTION);
    stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HEALING));

    FluidStack result = transfer.getFluid(stack);

    PotionContents contents = result.get(DataComponents.POTION_CONTENTS);
    assertThat(contents).isNotNull();
    assertThat(contents.is(Potions.HEALING)).isTrue();
  }

  @Test
  void getFluid_waterPotion_returnsWater() {
    EmptyPotionTransfer transfer = new EmptyPotionTransfer(Ingredient.of(Items.POTION), ItemOutput.fromItem(Items.GLASS_BOTTLE), 1000);
    ItemStack stack = new ItemStack(Items.POTION);
    stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER));

    FluidStack result = transfer.getFluid(stack);

    assertThat(result.getFluid()).isEqualTo(Fluids.WATER);
  }

  @SuppressWarnings("unchecked")
  private static Map<TagKey<?>, Optional<?>> preferenceCache() throws ReflectiveOperationException {
    Field field = TagPreference.class.getDeclaredField("PREFERENCE_CACHE");
    field.setAccessible(true);
    return (Map<TagKey<?>, Optional<?>>) field.get(null);
  }
}
