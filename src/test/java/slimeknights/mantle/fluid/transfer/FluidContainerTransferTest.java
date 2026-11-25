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
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.minecraft.tags.TagKey;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.datagen.MantleTags;
import slimeknights.mantle.fluid.transfer.IFluidContainerTransfer.TransferDirection;
import slimeknights.mantle.fluid.transfer.IFluidContainerTransfer.TransferResult;
import slimeknights.mantle.recipe.helper.FluidOutput;
import slimeknights.mantle.recipe.helper.ItemOutput;
import slimeknights.mantle.recipe.helper.TagPreference;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;
import slimeknights.tconstruct.test.BaseMcTest;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Native transfers of the {@code mantle:fluid_transfer} types (M5 phase 6a): each type moves its fluid through a {@link net.neoforged.neoforge.transfer.ResourceHandler}
 * in the given transaction, leaves no change when it fails, and gives the same result as its legacy {@link net.neoforged.neoforge.fluids.capability.IFluidHandler} path.
 */
class FluidContainerTransferTest extends BaseMcTest {
  private static final int BUCKET = 1000;
  private static final int BOTTLE = 250;

  private static EmptyFluidContainerTransfer emptyBucket() {
    return new EmptyFluidContainerTransfer(Ingredient.of(Items.WATER_BUCKET), ItemOutput.fromItem(Items.BUCKET), FluidOutput.fromFluid(Fluids.WATER, BUCKET));
  }

  private static FillFluidContainerTransfer fillBucket() {
    return new FillFluidContainerTransfer(Ingredient.of(Items.BUCKET), ItemOutput.fromItem(Items.WATER_BUCKET), FluidIngredient.of(Fluids.WATER, BUCKET));
  }

  private static FillFluidWithNBTTransfer fillPotion() {
    return new FillFluidWithNBTTransfer(Ingredient.of(Items.GLASS_BOTTLE), ItemOutput.fromItem(Items.POTION), FluidIngredient.of(Fluids.WATER, BOTTLE));
  }

  private static EmptyPotionTransfer emptyPotion() {
    return new EmptyPotionTransfer(Ingredient.of(Items.POTION), ItemOutput.fromItem(Items.GLASS_BOTTLE), BOTTLE);
  }

  /** Native single tank with the given contents */
  private static FluidStacksResourceHandler tank(int capacity, FluidStack contents) {
    FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, capacity);
    if (!contents.isEmpty()) {
      tank.set(0, FluidResource.of(contents), contents.getAmount());
    }
    return tank;
  }

  /** Legacy single tank with the given contents */
  private static FluidTank legacyTank(int capacity, FluidStack contents) {
    FluidTank tank = new FluidTank(capacity);
    tank.setFluid(contents.copy());
    return tank;
  }

  /** Water carrying the given potion contents, like a potion fluid */
  private static FluidStack potionWater(int amount) {
    return new FluidStack(Fluids.WATER.builtInRegistryHolder(), amount, DataComponentPatch.builder().set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.SWIFTNESS)).build());
  }

  private static TransferResult commit(IFluidContainerTransfer transfer, ItemStack stack, FluidStack fluid, FluidStacksResourceHandler tank, TransferDirection direction) {
    try (Transaction tx = Transaction.openRoot()) {
      TransferResult result = transfer.transfer(stack, fluid, tank, direction, tx);
      tx.commit();
      return result;
    }
  }

  private static void assertTank(FluidStacksResourceHandler tank, FluidStack expected) {
    assertThat(tank.getResource(0)).isEqualTo(expected.isEmpty() ? FluidResource.EMPTY : FluidResource.of(expected));
    assertThat(tank.getAmountAsInt(0)).isEqualTo(expected.getAmount());
  }


  /* empty_item */

  @Test
  void emptyItem_fillsHandler() {
    FluidStacksResourceHandler tank = tank(4000, FluidStack.EMPTY);
    ItemStack stack = new ItemStack(Items.WATER_BUCKET);
    TransferResult result = commit(emptyBucket(), stack, FluidStack.EMPTY, tank, TransferDirection.AUTO);
    assertThat(result).isNotNull();
    assertThat(result.stack().is(Items.BUCKET)).isTrue();
    assertThat(result.didFill()).isFalse();
    assertThat(FluidStack.matches(result.fluid(), new FluidStack(Fluids.WATER, BUCKET))).isTrue();
    assertTank(tank, new FluidStack(Fluids.WATER, BUCKET));
    // the passed stack is not touched
    assertThat(stack.is(Items.WATER_BUCKET)).isTrue();
  }

  @Test
  void emptyItem_doesNotFit_leavesNoChange() {
    FluidStacksResourceHandler tank = tank(1500, new FluidStack(Fluids.WATER, 1000));
    assertThat(commit(emptyBucket(), new ItemStack(Items.WATER_BUCKET), FluidStack.EMPTY, tank, TransferDirection.EMPTY_ITEM)).isNull();
    assertTank(tank, new FluidStack(Fluids.WATER, 1000));
    // wrong fluid in the tank
    FluidStacksResourceHandler lava = tank(4000, new FluidStack(Fluids.LAVA, 1000));
    assertThat(commit(emptyBucket(), new ItemStack(Items.WATER_BUCKET), FluidStack.EMPTY, lava, TransferDirection.AUTO)).isNull();
    assertTank(lava, new FluidStack(Fluids.LAVA, 1000));
  }

  @Test
  void emptyItem_fillOnlyDirection_doesNothing() {
    FluidStacksResourceHandler tank = tank(4000, FluidStack.EMPTY);
    assertThat(commit(emptyBucket(), new ItemStack(Items.WATER_BUCKET), FluidStack.EMPTY, tank, TransferDirection.FILL_ITEM)).isNull();
    assertTank(tank, FluidStack.EMPTY);
  }

  @Test
  void emptyItem_abortedTransaction_leavesNoChange() {
    FluidStacksResourceHandler tank = tank(4000, FluidStack.EMPTY);
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(emptyBucket().transfer(new ItemStack(Items.WATER_BUCKET), FluidStack.EMPTY, tank, TransferDirection.AUTO, tx)).isNotNull();
      // not committed
    }
    assertTank(tank, FluidStack.EMPTY);
  }


  /* fill_item */

  @Test
  void fillItem_drainsHandler() {
    FluidStacksResourceHandler tank = tank(4000, new FluidStack(Fluids.WATER, 1500));
    FluidStack current = new FluidStack(Fluids.WATER, 1500);
    TransferResult result = commit(fillBucket(), new ItemStack(Items.BUCKET), current, tank, TransferDirection.AUTO);
    assertThat(result).isNotNull();
    assertThat(result.stack().is(Items.WATER_BUCKET)).isTrue();
    assertThat(result.didFill()).isTrue();
    assertThat(FluidStack.matches(result.fluid(), new FluidStack(Fluids.WATER, BUCKET))).isTrue();
    assertTank(tank, new FluidStack(Fluids.WATER, 500));
  }

  @Test
  void fillItem_notEnough_leavesNoChange() {
    FluidStacksResourceHandler tank = tank(4000, new FluidStack(Fluids.WATER, 600));
    assertThat(commit(fillBucket(), new ItemStack(Items.BUCKET), new FluidStack(Fluids.WATER, 600), tank, TransferDirection.FILL_ITEM)).isNull();
    assertTank(tank, new FluidStack(Fluids.WATER, 600));
  }

  @Test
  void fillItem_emptyOnlyDirection_doesNothing() {
    FluidStacksResourceHandler tank = tank(4000, new FluidStack(Fluids.WATER, 1500));
    assertThat(commit(fillBucket(), new ItemStack(Items.BUCKET), new FluidStack(Fluids.WATER, 1500), tank, TransferDirection.EMPTY_ITEM)).isNull();
    assertTank(tank, new FluidStack(Fluids.WATER, 1500));
  }


  /* fill_nbt */

  @Test
  void fillNbt_carriesPotionContentsOntoItem() {
    FluidStacksResourceHandler tank = tank(4000, potionWater(1000));
    TransferResult result = commit(fillPotion(), new ItemStack(Items.GLASS_BOTTLE), potionWater(1000), tank, TransferDirection.AUTO);
    assertThat(result).isNotNull();
    assertThat(result.stack().is(Items.POTION)).isTrue();
    PotionContents contents = result.stack().get(DataComponents.POTION_CONTENTS);
    assertThat(contents).isNotNull();
    assertThat(contents.is(Potions.SWIFTNESS)).isTrue();
    assertTank(tank, potionWater(750));
  }

  @Test
  void fillNbt_otherComponents_doNotDrain() {
    // plain water is a different resource than water carrying potion contents
    FluidStacksResourceHandler tank = tank(4000, new FluidStack(Fluids.WATER, 1000));
    assertThat(commit(fillPotion(), new ItemStack(Items.GLASS_BOTTLE), potionWater(1000), tank, TransferDirection.AUTO)).isNull();
    assertTank(tank, new FluidStack(Fluids.WATER, 1000));
  }


  /* empty_potion */

  @Test
  void emptyPotion_waterPotion_emptiesWater() {
    FluidStacksResourceHandler tank = tank(4000, FluidStack.EMPTY);
    ItemStack water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
    TransferResult result = commit(emptyPotion(), water, FluidStack.EMPTY, tank, TransferDirection.EMPTY_ITEM);
    assertThat(result).isNotNull();
    assertThat(result.stack().is(Items.GLASS_BOTTLE)).isTrue();
    assertTank(tank, new FluidStack(Fluids.WATER, BOTTLE));
  }

  @Test
  void emptyPotion_withoutPotionFluid_leavesNoChange() {
    // no fluid is tagged as the potion fluid in unit tests, so a non-water potion has nothing to empty into
    FluidStacksResourceHandler tank = tank(4000, FluidStack.EMPTY);
    ItemStack swiftness = PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS);
    assertThat(commit(emptyPotion(), swiftness, FluidStack.EMPTY, tank, TransferDirection.AUTO)).isNull();
    assertTank(tank, FluidStack.EMPTY);
  }


  @Test
  void potionRoundTrip_emptyThenFill_keepsContents() throws ReflectiveOperationException {
    // seed the potion fluid preference like EmptyPotionTransferTest, as no datapack tags load in unit tests
    Field cacheField = TagPreference.class.getDeclaredField("PREFERENCE_CACHE");
    cacheField.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<TagKey<?>, Optional<?>> cache = (Map<TagKey<?>, Optional<?>>) cacheField.get(null);
    cache.put(MantleTags.Fluids.POTION, Optional.of(Fluids.LAVA));
    try {
      FluidStacksResourceHandler tank = tank(4000, FluidStack.EMPTY);
      TransferResult emptied = commit(emptyPotion(), PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS), FluidStack.EMPTY, tank, TransferDirection.AUTO);
      assertThat(emptied).isNotNull();
      assertThat(emptied.stack().is(Items.GLASS_BOTTLE)).isTrue();
      FluidResource potionFluid = tank.getResource(0);
      assertThat(potionFluid.getFluid()).isEqualTo(Fluids.LAVA);
      assertThat(potionFluid.getComponents().get(DataComponents.POTION_CONTENTS)).isEqualTo(new PotionContents(Potions.SWIFTNESS));
      assertThat(tank.getAmountAsInt(0)).isEqualTo(BOTTLE);

      FillFluidWithNBTTransfer fill = new FillFluidWithNBTTransfer(Ingredient.of(Items.GLASS_BOTTLE), ItemOutput.fromItem(Items.POTION), FluidIngredient.of(Fluids.LAVA, BOTTLE));
      TransferResult filled = commit(fill, new ItemStack(Items.GLASS_BOTTLE), potionFluid.toStack(BOTTLE), tank, TransferDirection.AUTO);
      assertThat(filled).isNotNull();
      assertThat(filled.stack().is(Items.POTION)).isTrue();
      assertThat(filled.stack().get(DataComponents.POTION_CONTENTS)).isEqualTo(new PotionContents(Potions.SWIFTNESS));
      assertTank(tank, FluidStack.EMPTY);
    } finally {
      cache.clear();
    }
  }


  /* Legacy path parity (kept until phase 6b) */

  @SuppressWarnings({"removal", "deprecation"})
  @Test
  void legacyPath_sameResults() {
    // empty
    FluidTank legacy = legacyTank(4000, FluidStack.EMPTY);
    TransferResult result = emptyBucket().transfer(new ItemStack(Items.WATER_BUCKET), FluidStack.EMPTY, legacy, TransferDirection.AUTO);
    assertThat(result).isNotNull();
    assertThat(result.stack().is(Items.BUCKET)).isTrue();
    assertThat(legacy.getFluidAmount()).isEqualTo(BUCKET);
    // fill
    result = fillBucket().transfer(new ItemStack(Items.BUCKET), legacy.getFluid().copy(), legacy, TransferDirection.AUTO);
    assertThat(result).isNotNull();
    assertThat(result.stack().is(Items.WATER_BUCKET)).isTrue();
    assertThat(legacy.isEmpty()).isTrue();
    // fill with potion contents
    FluidTank potion = legacyTank(4000, potionWater(1000));
    result = fillPotion().transfer(new ItemStack(Items.GLASS_BOTTLE), potion.getFluid().copy(), potion, TransferDirection.AUTO);
    assertThat(result).isNotNull();
    assertThat(result.stack().get(DataComponents.POTION_CONTENTS)).isEqualTo(new PotionContents(Potions.SWIFTNESS));
    assertThat(potion.getFluidAmount()).isEqualTo(750);
    // a failed transfer leaves the tank alone
    FluidTank small = legacyTank(1500, new FluidStack(Fluids.WATER, 1000));
    assertThat(emptyBucket().transfer(new ItemStack(Items.WATER_BUCKET), FluidStack.EMPTY, small, TransferDirection.AUTO)).isNull();
    assertThat(small.getFluidAmount()).isEqualTo(1000);
  }
}
