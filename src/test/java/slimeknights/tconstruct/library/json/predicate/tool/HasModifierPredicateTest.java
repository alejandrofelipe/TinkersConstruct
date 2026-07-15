package slimeknights.tconstruct.library.json.predicate.tool;

import org.junit.jupiter.api.Test;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.tconstruct.library.json.IntRange;
import slimeknights.tconstruct.library.json.predicate.modifier.ModifierPredicate;
import slimeknights.tconstruct.library.json.predicate.modifier.SingleModifierPredicate;
import slimeknights.tconstruct.library.json.predicate.tool.HasModifierPredicate.ModifierCheck;
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
 * Tests {@link HasModifierPredicate}: presence/level-range check for a modifier (or set of modifiers) on a tool,
 * including its {@link ModifierCheck} dispatch (upgrades vs. all modifiers) and {@link HasModifierPredicate#inverted()}.
 */
class HasModifierPredicateTest extends BaseMcTest {
  private static final ModifierId FOO = new ModifierId("test", "foo");
  private static final ModifierId BAR = new ModifierId("test", "bar");

  // both accessors are always stubbed to a real (possibly empty) ModifierNBT, matching a real tool stack - an
  // un-stubbed Mockito mock method returns null, which NPEs inside ModifierNBT#has/getModifiers
  private static IToolContext toolWithUpgrades(ModifierEntry... entries) {
    IToolContext tool = mock(IToolContext.class);
    when(tool.getUpgrades()).thenReturn(new ModifierNBT(List.of(entries)));
    when(tool.getModifiers()).thenReturn(ModifierNBT.EMPTY);
    return tool;
  }

  private static IToolContext toolWithModifiers(ModifierEntry... entries) {
    IToolContext tool = mock(IToolContext.class);
    when(tool.getUpgrades()).thenReturn(ModifierNBT.EMPTY);
    when(tool.getModifiers()).thenReturn(new ModifierNBT(List.of(entries)));
    return tool;
  }


  /* matches() */

  @Test
  void matches_levelExactlyZero_trueOnlyWhenModifierAbsent() {
    HasModifierPredicate predicate = new HasModifierPredicate(new SingleModifierPredicate(FOO), new IntRange(0, 0), ModifierCheck.ALL);
    assertThat(predicate.matches(toolWithModifiers())).isTrue();
    assertThat(predicate.matches(toolWithModifiers(new ModifierEntry(FOO, 1)))).isFalse();
  }

  @Test
  void matches_validLevel_trueWhenModifierPresentAtAnyLevel() {
    HasModifierPredicate predicate = new HasModifierPredicate(new SingleModifierPredicate(FOO), ModifierEntry.VALID_LEVEL, ModifierCheck.ALL);
    assertThat(predicate.matches(toolWithModifiers(new ModifierEntry(FOO, 1)))).isTrue();
    assertThat(predicate.matches(toolWithModifiers(new ModifierEntry(FOO, 99)))).isTrue();
    assertThat(predicate.matches(toolWithModifiers())).isFalse();
  }

  @Test
  void matches_customRange_sumsLevelsOfEveryMatchingModifier() {
    // predicate matching either FOO or BAR; javadoc: "if multiple modifiers match, their levels will be summed"
    IJsonPredicate<ModifierId> fooOrBar = ModifierPredicate.or(new SingleModifierPredicate(FOO), new SingleModifierPredicate(BAR));
    HasModifierPredicate predicate = new HasModifierPredicate(fooOrBar, new IntRange(3, 5), ModifierCheck.ALL);

    // 2 + 2 = 4, within [3,5]
    assertThat(predicate.matches(toolWithModifiers(new ModifierEntry(FOO, 2), new ModifierEntry(BAR, 2)))).isTrue();
    // just FOO at 2, sum is 2, below the range
    assertThat(predicate.matches(toolWithModifiers(new ModifierEntry(FOO, 2)))).isFalse();
    // 3 + 3 = 6, above the range
    assertThat(predicate.matches(toolWithModifiers(new ModifierEntry(FOO, 3), new ModifierEntry(BAR, 3)))).isFalse();
  }

  @Test
  void matches_modifierCheckUpgrades_onlyLooksAtUpgrades() {
    HasModifierPredicate predicate = new HasModifierPredicate(new SingleModifierPredicate(FOO), ModifierEntry.VALID_LEVEL, ModifierCheck.UPGRADES);
    assertThat(predicate.matches(toolWithUpgrades(new ModifierEntry(FOO, 1)))).isTrue();
    // same modifier, but reported via getModifiers() instead of getUpgrades() -> UPGRADES check should ignore it
    assertThat(predicate.matches(toolWithModifiers(new ModifierEntry(FOO, 1)))).isFalse();
  }

  @Test
  void matches_modifierCheckAll_looksAtAllModifiers() {
    HasModifierPredicate predicate = new HasModifierPredicate(new SingleModifierPredicate(FOO), ModifierEntry.VALID_LEVEL, ModifierCheck.ALL);
    assertThat(predicate.matches(toolWithModifiers(new ModifierEntry(FOO, 1)))).isTrue();
    // ALL check should not look at getUpgrades()
    assertThat(predicate.matches(toolWithUpgrades(new ModifierEntry(FOO, 1)))).isFalse();
  }


  /* inverted() */

  @Test
  void inverted_rangeTouchingMaxBound_shiftsToZeroThroughMinMinusOne() {
    HasModifierPredicate predicate = new HasModifierPredicate(new SingleModifierPredicate(FOO), ModifierEntry.ANY_LEVEL.min(5), ModifierCheck.ALL);
    IJsonPredicate<IToolContext> inverted = predicate.inverted();
    assertThat(inverted).isInstanceOf(HasModifierPredicate.class);
    assertThat(((HasModifierPredicate) inverted).level()).isEqualTo(new IntRange(0, 4));
  }

  @Test
  void inverted_rangeTouchingMinBound_shiftsToMaxPlusOneThroughMax() {
    HasModifierPredicate predicate = new HasModifierPredicate(new SingleModifierPredicate(FOO), ModifierEntry.ANY_LEVEL.max(3), ModifierCheck.ALL);
    IJsonPredicate<IToolContext> inverted = predicate.inverted();
    assertThat(inverted).isInstanceOf(HasModifierPredicate.class);
    assertThat(((HasModifierPredicate) inverted).level()).isEqualTo(new IntRange(4, ModifierEntry.ANY_LEVEL.max()));
  }

  @Test
  void inverted_rangeTouchingNeitherBound_fallsBackToGenericInvert() {
    HasModifierPredicate predicate = new HasModifierPredicate(new SingleModifierPredicate(FOO), new IntRange(2, 5), ModifierCheck.ALL);
    IJsonPredicate<IToolContext> inverted = predicate.inverted();

    // in range -> original true, inverted false
    assertThat(predicate.matches(toolWithModifiers(new ModifierEntry(FOO, 3)))).isTrue();
    assertThat(inverted.matches(toolWithModifiers(new ModifierEntry(FOO, 3)))).isFalse();
    // out of range -> original false, inverted true
    assertThat(predicate.matches(toolWithModifiers(new ModifierEntry(FOO, 1)))).isFalse();
    assertThat(inverted.matches(toolWithModifiers(new ModifierEntry(FOO, 1)))).isTrue();
  }


  /* Static constructors */

  @Test
  void hasUpgrade_withModifierId_checksUpgradesOnly() {
    HasModifierPredicate predicate = HasModifierPredicate.hasUpgrade(FOO, 2);
    assertThat(predicate.check()).isEqualTo(ModifierCheck.UPGRADES);
    assertThat(predicate.matches(toolWithUpgrades(new ModifierEntry(FOO, 2)))).isTrue();
    assertThat(predicate.matches(toolWithUpgrades(new ModifierEntry(FOO, 1)))).isFalse();
    assertThat(predicate.matches(toolWithModifiers(new ModifierEntry(FOO, 2)))).isFalse();
  }

  @Test
  void hasModifier_withPredicate_checksAllModifiers() {
    HasModifierPredicate predicate = HasModifierPredicate.hasModifier(new SingleModifierPredicate(FOO), 2);
    assertThat(predicate.check()).isEqualTo(ModifierCheck.ALL);
    assertThat(predicate.matches(toolWithModifiers(new ModifierEntry(FOO, 2)))).isTrue();
    assertThat(predicate.matches(toolWithModifiers(new ModifierEntry(FOO, 1)))).isFalse();
  }
}
