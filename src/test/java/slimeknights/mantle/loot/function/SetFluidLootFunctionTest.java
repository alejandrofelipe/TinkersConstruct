package slimeknights.mantle.loot.function;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.transfer.TestFluidItemCapabilities;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link SetFluidLootFunction} fills every item of the dropped stack through the native item capability (M5 phase 6a). */
class SetFluidLootFunctionTest extends BaseMcTest {
  @BeforeAll
  static void registerCapabilities() throws ReflectiveOperationException {
    TestFluidItemCapabilities.register();
  }

  @AfterAll
  static void unregisterCapabilities() throws ReflectiveOperationException {
    TestFluidItemCapabilities.unregister();
  }

  private static ItemStack run(ItemStack stack, FluidStack fluid) {
    LootItemFunction function = SetFluidLootFunction.builder(fluid).build();
    // no conditions, so the context is never read
    return function.apply(stack, null);
  }

  @Test
  void fillsTheItem() {
    ItemStack stack = new ItemStack(Items.BUCKET);
    ItemStack result = run(stack, new FluidStack(Fluids.WATER, 1000));
    assertThat(result.is(Items.WATER_BUCKET)).isTrue();
    assertThat(result.getCount()).isEqualTo(1);
    // the passed stack is not modified
    assertThat(stack.is(Items.BUCKET)).isTrue();
  }

  @Test
  void stack_isFilledAsAWhole() {
    // the fill is split between all items of the stack: three buckets would need a slot holding three water buckets, which do not stack,
    // so nothing is filled (same as the legacy whole stack bridge) and a copy of the stack comes back
    ItemStack stack = new ItemStack(Items.BUCKET, 3);
    ItemStack result = run(stack, new FluidStack(Fluids.WATER, 1000));
    assertThat(result).isNotSameAs(stack);
    assertThat(result.is(Items.BUCKET)).isTrue();
    assertThat(result.getCount()).isEqualTo(3);
  }

  @Test
  void noCapability_orEmptyFluid_returnsTheStack() {
    ItemStack stick = new ItemStack(Items.STICK);
    assertThat(run(stick, new FluidStack(Fluids.WATER, 1000))).isSameAs(stick);
    ItemStack bucket = new ItemStack(Items.BUCKET);
    assertThat(run(bucket, FluidStack.EMPTY)).isSameAs(bucket);
  }

  @Test
  void insideTransaction_doesNotThrow() {
    try (Transaction ignored = Transaction.openRoot()) {
      assertThat(run(new ItemStack(Items.BUCKET), new FluidStack(Fluids.LAVA, 1000)).is(Items.LAVA_BUCKET)).isTrue();
    }
  }
}
