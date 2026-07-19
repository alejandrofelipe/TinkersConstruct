package slimeknights.mantle.fluid.transfer;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.recipe.helper.ItemOutput;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fill-side mirror of {@link EmptyPotionTransferTest}: filling a potion fluid back into an item must carry the
 * fluid's {@link DataComponents#POTION_CONTENTS} onto the result (the 1.21 replacement for the old CompoundTag
 * round-trip). Without it, a potion emptied into a tank and filled back becomes a blank potion. {@code getFilled}
 * is {@code protected}, so this test lives in the same package.
 */
class FillFluidWithNBTTransferTest extends BaseMcTest {
  @Test
  void getFilled_potionFluid_carriesPotionContentsOntoItem() {
    FillFluidWithNBTTransfer transfer = new FillFluidWithNBTTransfer(
      Ingredient.of(Items.GLASS_BOTTLE), ItemOutput.fromItem(Items.POTION),
      FluidIngredient.of(new FluidStack(Fluids.LAVA, 1000)));

    DataComponentPatch patch = DataComponentPatch.builder()
      .set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HEALING)).build();
    FluidStack potionFluid = new FluidStack(Fluids.LAVA.builtInRegistryHolder(), 1000, patch);

    ItemStack filled = transfer.getFilled(potionFluid);

    PotionContents contents = filled.get(DataComponents.POTION_CONTENTS);
    assertThat(contents).isNotNull();
    assertThat(contents.is(Potions.HEALING)).isTrue();
  }
}
