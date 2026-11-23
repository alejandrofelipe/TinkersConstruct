package slimeknights.mantle.recipe.ingredient;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.item.ConstantFluidContainerWrapper;
import slimeknights.tconstruct.test.BaseMcTest;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Caller-level check of the per-container item lookup (M5 transfer step 1): {@link FluidContainerIngredient} must match a stack of
 * several filled containers by the contents of one container, as the legacy capability did.
 * <p>
 * No mod loading happens in unit tests, so the honey bottle gets a temporary constant water capability through a reflectively
 * created {@link RegisterCapabilitiesEvent}, removed again after the class.
 * Runs in the Gradle {@code test} task once Tinkers compiles on 1.21.10 (phase 2E); verified earlier in a scratch harness.
 */
class FluidContainerIngredientTest extends BaseMcTest {
  private static final int AMOUNT = 250;

  @BeforeAll
  static void registerHoneyCapability() throws ReflectiveOperationException {
    Constructor<RegisterCapabilitiesEvent> constructor = RegisterCapabilitiesEvent.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    constructor.newInstance().registerItem(Capabilities.Fluid.ITEM, (stack, access) -> new ConstantFluidContainerWrapper(new FluidStack(Fluids.WATER, AMOUNT), access, ItemResource.of(Items.GLASS_BOTTLE)), Items.HONEY_BOTTLE);
  }

  @AfterAll
  @SuppressWarnings("unchecked")
  static void removeHoneyCapability() throws ReflectiveOperationException {
    Field providers = ItemCapability.class.getDeclaredField("providers");
    providers.setAccessible(true);
    ((Map<Item, ?>) providers.get(Capabilities.Fluid.ITEM)).remove(Items.HONEY_BOTTLE);
  }

  private static FluidContainerIngredient water(int amount) {
    return FluidContainerIngredient.fromIngredient(FluidIngredient.of(new FluidStack(Fluids.WATER, amount)));
  }

  @Test
  void test_singleContainer_matches() {
    assertThat(water(AMOUNT).test(new ItemStack(Items.HONEY_BOTTLE))).isTrue();
  }

  @Test
  void test_stackOfContainers_matchesPerContainer() {
    ItemStack stack = new ItemStack(Items.HONEY_BOTTLE, 2);
    assertThat(water(AMOUNT).test(stack)).isTrue();
    // the stack total must not be what is matched
    assertThat(water(2 * AMOUNT).test(stack)).isFalse();
    // testing never changes the stack
    assertThat(stack.is(Items.HONEY_BOTTLE)).isTrue();
    assertThat(stack.getCount()).isEqualTo(2);
  }

  @Test
  void test_noCapability_doesNotMatch() {
    assertThat(water(AMOUNT).test(new ItemStack(Items.STICK))).isFalse();
  }
}
