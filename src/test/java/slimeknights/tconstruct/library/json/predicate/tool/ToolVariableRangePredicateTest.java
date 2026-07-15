package slimeknights.tconstruct.library.json.predicate.tool;

import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.json.predicate.VariableRangePredicate.IntervalType;
import slimeknights.tconstruct.library.json.variable.tool.ToolVariable;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link ToolVariableRangePredicate}: resolves a {@link ToolVariable} from the tool and checks it against an
 * interval. Also exercises the shared {@link IntervalType} boundary math it delegates to.
 */
class ToolVariableRangePredicateTest extends BaseMcTest {
  private static IToolStackView toolWithDurability(int durability) {
    IToolStackView tool = mock(IToolStackView.class);
    when(tool.getCurrentDurability()).thenReturn(durability);
    return tool;
  }

  @Test
  void matches_resolvesVariableThenTestsRange() {
    ToolVariableRangePredicate predicate = new ToolVariableRangePredicate(ToolVariable.CURRENT_DURABILITY, 5, 10, IntervalType.CLOSED);
    assertThat(predicate.matches(toolWithDurability(7))).isTrue();
    assertThat(predicate.matches(toolWithDurability(2))).isFalse();
  }

  @Test
  void min_open_excludesTheBoundary() {
    ToolVariableRangePredicate predicate = ToolVariableRangePredicate.min(ToolVariable.CURRENT_DURABILITY, 5, true);
    assertThat(predicate.interval()).isEqualTo(IntervalType.OPEN);
    assertThat(predicate.matches(toolWithDurability(5))).isFalse();
    assertThat(predicate.matches(toolWithDurability(6))).isTrue();
  }

  @Test
  void min_closed_includesTheBoundaryAndHasNoUpperBound() {
    ToolVariableRangePredicate predicate = ToolVariableRangePredicate.min(ToolVariable.CURRENT_DURABILITY, 5, false);
    assertThat(predicate.interval()).isEqualTo(IntervalType.RIGHT_OPEN);
    assertThat(predicate.matches(toolWithDurability(5))).isTrue();
    assertThat(predicate.matches(toolWithDurability(Integer.MAX_VALUE / 2))).isTrue();
  }

  @Test
  void max_open_excludesTheBoundary() {
    ToolVariableRangePredicate predicate = ToolVariableRangePredicate.max(ToolVariable.CURRENT_DURABILITY, 10, true);
    assertThat(predicate.interval()).isEqualTo(IntervalType.OPEN);
    assertThat(predicate.matches(toolWithDurability(10))).isFalse();
    assertThat(predicate.matches(toolWithDurability(9))).isTrue();
  }

  @Test
  void max_closed_includesTheBoundaryAndHasNoLowerBound() {
    ToolVariableRangePredicate predicate = ToolVariableRangePredicate.max(ToolVariable.CURRENT_DURABILITY, 10, false);
    assertThat(predicate.interval()).isEqualTo(IntervalType.LEFT_OPEN);
    assertThat(predicate.matches(toolWithDurability(10))).isTrue();
    assertThat(predicate.matches(toolWithDurability(0))).isTrue();
  }


  /* IntervalType: shared boundary math, exercised directly for full branch coverage */

  @Test
  void intervalType_open_excludesBothBoundaries() {
    assertThat(IntervalType.OPEN.test(5, 5, 10)).isFalse();
    assertThat(IntervalType.OPEN.test(10, 5, 10)).isFalse();
    assertThat(IntervalType.OPEN.test(7, 5, 10)).isTrue();
  }

  @Test
  void intervalType_closed_includesBothBoundaries() {
    assertThat(IntervalType.CLOSED.test(5, 5, 10)).isTrue();
    assertThat(IntervalType.CLOSED.test(10, 5, 10)).isTrue();
    assertThat(IntervalType.CLOSED.test(4, 5, 10)).isFalse();
    assertThat(IntervalType.CLOSED.test(11, 5, 10)).isFalse();
  }

  @Test
  void intervalType_leftOpen_excludesMinIncludesMax() {
    assertThat(IntervalType.LEFT_OPEN.test(5, 5, 10)).isFalse();
    assertThat(IntervalType.LEFT_OPEN.test(10, 5, 10)).isTrue();
  }

  @Test
  void intervalType_rightOpen_includesMinExcludesMax() {
    assertThat(IntervalType.RIGHT_OPEN.test(5, 5, 10)).isTrue();
    assertThat(IntervalType.RIGHT_OPEN.test(10, 5, 10)).isFalse();
  }
}
