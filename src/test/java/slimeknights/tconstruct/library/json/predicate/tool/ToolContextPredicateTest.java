package slimeknights.tconstruct.library.json.predicate.tool;

import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link ToolContextPredicate}.
 */
class ToolContextPredicateTest extends BaseMcTest {
  private static final ModifierId FOO = new ModifierId("test", "foo");

  private static IToolContext toolWithUpgrades(ModifierEntry... entries) {
    IToolContext tool = mock(IToolContext.class);
    when(tool.getUpgrades()).thenReturn(new ModifierNBT(List.of(entries)));
    return tool;
  }

  @Test
  void any_alwaysMatches() {
    assertThat(ToolContextPredicate.ANY.matches(mock(IToolContext.class))).isTrue();
  }

  @Test
  void none_neverMatches() {
    assertThat(ToolContextPredicate.NONE.matches(mock(IToolContext.class))).isFalse();
  }

  @Test
  void hasUpgrades_trueOnlyWhenUpgradesPresent() {
    assertThat(ToolContextPredicate.HAS_UPGRADES.matches(toolWithUpgrades())).isFalse();
    assertThat(ToolContextPredicate.HAS_UPGRADES.matches(toolWithUpgrades(new ModifierEntry(FOO, 1)))).isTrue();
  }

  @Test
  void simple_wrapsAnArbitraryPredicate() {
    IToolContext tool = mock(IToolContext.class);
    when(tool.getItem()).thenReturn(Items.DIAMOND_PICKAXE);
    ToolContextPredicate predicate = ToolContextPredicate.simple(t -> t.getItem() == Items.DIAMOND_PICKAXE);
    assertThat(predicate.matches(tool)).isTrue();

    when(tool.getItem()).thenReturn(Items.STICK);
    assertThat(predicate.matches(tool)).isFalse();
  }

  @Test
  void inverted_hasUpgrades_flipsTheResult() {
    IJsonPredicate<IToolContext> inverted = ToolContextPredicate.HAS_UPGRADES.inverted();
    assertThat(inverted.matches(toolWithUpgrades())).isTrue();
    assertThat(inverted.matches(toolWithUpgrades(new ModifierEntry(FOO, 1)))).isFalse();
  }

  @Test
  void set_matchesAnyListedItem() {
    IToolContext tool = mock(IToolContext.class);
    when(tool.getItem()).thenReturn(Items.DIAMOND);
    IJsonPredicate<IToolContext> predicate = ToolContextPredicate.set(Items.DIAMOND, Items.EMERALD);
    assertThat(predicate.matches(tool)).isTrue();

    when(tool.getItem()).thenReturn(Items.IRON_INGOT);
    assertThat(predicate.matches(tool)).isFalse();
  }

  @Test
  void and_trueOnlyWhenBothMatch() {
    IJsonPredicate<IToolContext> alwaysTrue = ToolContextPredicate.simple(t -> true);
    IJsonPredicate<IToolContext> alwaysFalse = ToolContextPredicate.simple(t -> false);
    IToolContext tool = mock(IToolContext.class);

    assertThat(ToolContextPredicate.and(alwaysTrue, alwaysTrue).matches(tool)).isTrue();
    assertThat(ToolContextPredicate.and(alwaysTrue, alwaysFalse).matches(tool)).isFalse();
  }

  @Test
  void or_trueWhenEitherMatches() {
    IJsonPredicate<IToolContext> alwaysTrue = ToolContextPredicate.simple(t -> true);
    IJsonPredicate<IToolContext> alwaysFalse = ToolContextPredicate.simple(t -> false);
    IToolContext tool = mock(IToolContext.class);

    assertThat(ToolContextPredicate.or(alwaysFalse, alwaysTrue).matches(tool)).isTrue();
    assertThat(ToolContextPredicate.or(alwaysFalse, alwaysFalse).matches(tool)).isFalse();
  }
}
