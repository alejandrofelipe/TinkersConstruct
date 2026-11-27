package slimeknights.mantle.loot.function;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
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

  private static ItemStack run(ItemStack stack, FluidStackTemplate fluid) {
    LootItemFunction function = SetFluidLootFunction.builder(fluid).build();
    // no conditions, so the context is never read
    return function.apply(stack, null);
  }

  @Test
  void fillsTheItem() {
    ItemStack stack = new ItemStack(Items.BUCKET);
    ItemStack result = run(stack, new FluidStackTemplate(Fluids.WATER, 1000));
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
    ItemStack result = run(stack, new FluidStackTemplate(Fluids.WATER, 1000));
    assertThat(result).isNotSameAs(stack);
    assertThat(result.is(Items.BUCKET)).isTrue();
    assertThat(result.getCount()).isEqualTo(3);
  }

  @Test
  void noCapability_returnsTheStack() {
    // 26.1: the function holds a FluidStackTemplate, which cannot be empty, so the old empty fluid case is gone
    ItemStack stick = new ItemStack(Items.STICK);
    assertThat(run(stick, new FluidStackTemplate(Fluids.WATER, 1000))).isSameAs(stick);
  }

  /** Parses the function JSON body (no conditions) through the function codec */
  private static DataResult<SetFluidLootFunction> parse(String json) {
    return SetFluidLootFunction.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json));
  }

  @Test
  void codec_parsesFluidObject() {
    assertThat(parse("{\"fluid\": {\"id\": \"minecraft:water\", \"amount\": 250}}").isSuccess()).isTrue();
  }

  @Test
  void codec_parsesBareFluidIdAsABucket() {
    // FluidStackTemplate.CODEC accepts a bare id as 1000 mB
    SetFluidLootFunction function = parse("{\"fluid\": \"minecraft:lava\"}").getOrThrow();
    assertThat(function.apply(new ItemStack(Items.BUCKET), null).is(Items.LAVA_BUCKET)).isTrue();
  }

  /**
   * An empty or missing fluid (or a zero amount) fails to parse, so the loot table using it fails to load. Same JSON behaviour as the 1.21.11
   * {@code FluidStack.CODEC} (non-empty fluid, positive amount); 26.1 only dropped the programmatic empty fluid ({@link FluidStackTemplate} cannot
   * be empty) and added the bare id form.
   */
  @Test
  void codec_rejectsEmptyOrMissingFluid() {
    assertThat(parse("{\"fluid\": {\"id\": \"minecraft:empty\", \"amount\": 1000}}").isError()).isTrue();
    assertThat(parse("{\"fluid\": \"minecraft:empty\"}").isError()).isTrue();
    assertThat(parse("{\"fluid\": {\"id\": \"minecraft:water\", \"amount\": 0}}").isError()).isTrue();
    assertThat(parse("{}").isError()).isTrue();
  }

  @Test
  void insideTransaction_doesNotThrow() {
    try (Transaction ignored = Transaction.openRoot()) {
      assertThat(run(new ItemStack(Items.BUCKET), new FluidStackTemplate(Fluids.LAVA, 1000)).is(Items.LAVA_BUCKET)).isTrue();
    }
  }
}
