package slimeknights.tconstruct.library.json.predicate.tool;

import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link ToolStackPredicate}.
 *
 * <p>NOTE: {@code ToolStackPredicate#NONE} is deliberately not exercised - same copy/paste bug as
 * {@link ToolContextPredicate#NONE} (wired to {@code simple(tool -> true)} instead of {@code false}); see that
 * class's javadoc and the P2-T2 report for details.
 */
class ToolStackPredicateTest extends BaseMcTest {
  @Test
  void any_alwaysMatches() {
    assertThat(ToolStackPredicate.ANY.matches(mock(IToolStackView.class))).isTrue();
  }

  @Test
  void notBroken_trueOnlyWhenToolIsNotBroken() {
    IToolStackView tool = mock(IToolStackView.class);
    when(tool.isBroken()).thenReturn(false);
    assertThat(ToolStackPredicate.NOT_BROKEN.matches(tool)).isTrue();

    when(tool.isBroken()).thenReturn(true);
    assertThat(ToolStackPredicate.NOT_BROKEN.matches(tool)).isFalse();
  }

  @Test
  void inverted_notBroken_flipsTheResult() {
    IJsonPredicate<IToolStackView> inverted = ToolStackPredicate.NOT_BROKEN.inverted();
    IToolStackView tool = mock(IToolStackView.class);
    when(tool.isBroken()).thenReturn(false);
    assertThat(inverted.matches(tool)).isFalse();

    when(tool.isBroken()).thenReturn(true);
    assertThat(inverted.matches(tool)).isTrue();
  }

  @Test
  void context_bridgesAToolContextPredicateOverAToolStackView() {
    IJsonPredicate<IToolStackView> predicate = ToolStackPredicate.context(ToolContextPredicate.HAS_UPGRADES);
    IToolStackView tool = mock(IToolStackView.class);
    when(tool.getUpgrades()).thenReturn(ModifierNBT.EMPTY);
    assertThat(predicate.matches(tool)).isFalse();
  }

  @Test
  void set_matchesAnyListedItem() {
    IToolStackView tool = mock(IToolStackView.class);
    when(tool.getItem()).thenReturn(Items.DIAMOND);
    IJsonPredicate<IToolStackView> predicate = ToolStackPredicate.set(Items.DIAMOND, Items.EMERALD);
    assertThat(predicate.matches(tool)).isTrue();

    when(tool.getItem()).thenReturn(Items.IRON_INGOT);
    assertThat(predicate.matches(tool)).isFalse();
  }

  @Test
  void and_trueOnlyWhenBothMatch() {
    IJsonPredicate<IToolStackView> alwaysTrue = ToolStackPredicate.simple(t -> true);
    IJsonPredicate<IToolStackView> alwaysFalse = ToolStackPredicate.simple(t -> false);
    IToolStackView tool = mock(IToolStackView.class);

    assertThat(ToolStackPredicate.and(alwaysTrue, alwaysTrue).matches(tool)).isTrue();
    assertThat(ToolStackPredicate.and(alwaysTrue, alwaysFalse).matches(tool)).isFalse();
  }

  @Test
  void or_trueWhenEitherMatches() {
    IJsonPredicate<IToolStackView> alwaysTrue = ToolStackPredicate.simple(t -> true);
    IJsonPredicate<IToolStackView> alwaysFalse = ToolStackPredicate.simple(t -> false);
    IToolStackView tool = mock(IToolStackView.class);

    assertThat(ToolStackPredicate.or(alwaysFalse, alwaysTrue).matches(tool)).isTrue();
    assertThat(ToolStackPredicate.or(alwaysFalse, alwaysFalse).matches(tool)).isFalse();
  }
}
