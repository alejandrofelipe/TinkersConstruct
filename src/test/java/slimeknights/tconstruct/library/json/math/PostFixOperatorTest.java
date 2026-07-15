package slimeknights.tconstruct.library.json.math;

import com.google.gson.JsonSyntaxException;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.network.FriendlyByteBuf;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Tests each post fix operator directly against a float stack. Pure math + Gson + a plain FriendlyByteBuf, no MC bootstrap required. */
class PostFixOperatorTest {
  private static final String[] NO_VARIABLE_NAMES = new String[0];
  private static final float[] NO_VARIABLES = new float[0];
  private static final Offset<Float> TOLERANCE = within(0.0001f);

  /** Builds a stack with the given values pushed in order (the last value ends up on top) */
  private static FloatArrayList stackOf(float... values) {
    FloatArrayList stack = new FloatArrayList();
    for (float value : values) {
      stack.push(value);
    }
    return stack;
  }

  /** Runs the operator against a stack seeded with the given operands, returning the single value left behind */
  private static float apply(PostFixOperator operator, float... operands) {
    FloatArrayList stack = stackOf(operands);
    operator.perform(stack, NO_VARIABLES);
    assertThat(stack.size()).withFailMessage("Expected exactly 1 value left on the stack after %s", operator).isEqualTo(1);
    return stack.popFloat();
  }


  /* Basic math */

  @Test
  void add_sumsBothOperands() {
    assertThat(apply(PostFixOperator.ADD, 2, 3)).isEqualTo(5f);
    assertThat(apply(PostFixOperator.ADD, -2, 3)).isEqualTo(1f);
  }

  @Test
  void subtract_subtractsRightFromLeft() {
    assertThat(apply(PostFixOperator.SUBTRACT, 5, 3)).isEqualTo(2f);
    assertThat(apply(PostFixOperator.SUBTRACT, 3, 5)).isEqualTo(-2f);
  }

  @Test
  void subtractFlipped_subtractsLeftFromRight() {
    assertThat(apply(PostFixOperator.SUBTRACT_FLIPPED, 5, 3)).isEqualTo(-2f);
    assertThat(apply(PostFixOperator.SUBTRACT_FLIPPED, 3, 5)).isEqualTo(2f);
  }

  @Test
  void multiply_multipliesOperands() {
    assertThat(apply(PostFixOperator.MULTIPLY, 4, 3)).isEqualTo(12f);
    assertThat(apply(PostFixOperator.MULTIPLY, -2, 3)).isEqualTo(-6f);
  }

  @Test
  void negate_flipsSign() {
    assertThat(apply(PostFixOperator.NEGATE, 5)).isEqualTo(-5f);
    assertThat(apply(PostFixOperator.NEGATE, -5)).isEqualTo(5f);
    assertThat(apply(PostFixOperator.NEGATE, 0)).isEqualTo(0f);
  }

  @Test
  void divide_dividesLeftByRight() {
    assertThat(apply(PostFixOperator.DIVIDE, 10, 2)).isEqualTo(5f);
  }

  @Test
  void divide_byZero_returnsZeroInsteadOfInfinity() {
    assertThat(apply(PostFixOperator.DIVIDE, 10, 0)).isEqualTo(0f);
  }

  @Test
  void divideFlipped_dividesRightByLeft() {
    assertThat(apply(PostFixOperator.DIVIDE_FLIPPED, 2, 10)).isEqualTo(5f);
  }

  @Test
  void divideFlipped_leftZero_returnsZeroInsteadOfInfinity() {
    assertThat(apply(PostFixOperator.DIVIDE_FLIPPED, 0, 10)).isEqualTo(0f);
  }

  @Test
  void power_raisesLeftToRight() {
    assertThat(apply(PostFixOperator.POWER, 2, 3)).isEqualTo(8f);
    assertThat(apply(PostFixOperator.POWER, 5, 0)).isEqualTo(1f);
  }

  @Test
  void powerFlipped_raisesRightToLeft() {
    assertThat(apply(PostFixOperator.POWER_FLIPPED, 2, 3)).isEqualTo(9f); // 3^2
  }

  @Test
  void sqrt_takesSquareRootOfTop() {
    assertThat(apply(PostFixOperator.SQRT, 9)).isEqualTo(3f, TOLERANCE);
    assertThat(apply(PostFixOperator.SQRT, 2)).isEqualTo(1.41421356f, TOLERANCE);
  }


  /* Logical operators: 1 for true, 0 for false */

  @Test
  void equal_comparesOperands() {
    assertThat(apply(PostFixOperator.EQUAL, 5, 5)).isEqualTo(1f);
    assertThat(apply(PostFixOperator.EQUAL, 5, 6)).isEqualTo(0f);
  }

  @Test
  void notEqual_comparesOperands() {
    assertThat(apply(PostFixOperator.NOT_EQUAL, 5, 6)).isEqualTo(1f);
    assertThat(apply(PostFixOperator.NOT_EQUAL, 5, 5)).isEqualTo(0f);
  }

  @Test
  void lessThan_comparesOperands() {
    assertThat(apply(PostFixOperator.LESS_THAN, 2, 3)).isEqualTo(1f);
    assertThat(apply(PostFixOperator.LESS_THAN, 3, 3)).isEqualTo(0f);
    assertThat(apply(PostFixOperator.LESS_THAN, 4, 3)).isEqualTo(0f);
  }

  @Test
  void lessThanOrEqual_comparesOperands() {
    assertThat(apply(PostFixOperator.LESS_THAN_EQUAL, 2, 3)).isEqualTo(1f);
    assertThat(apply(PostFixOperator.LESS_THAN_EQUAL, 3, 3)).isEqualTo(1f);
    assertThat(apply(PostFixOperator.LESS_THAN_EQUAL, 4, 3)).isEqualTo(0f);
  }

  @Test
  void greaterThan_comparesOperands() {
    assertThat(apply(PostFixOperator.GREATER_THAN, 3, 2)).isEqualTo(1f);
    assertThat(apply(PostFixOperator.GREATER_THAN, 3, 3)).isEqualTo(0f);
    assertThat(apply(PostFixOperator.GREATER_THAN, 2, 3)).isEqualTo(0f);
  }

  @Test
  void greaterThanOrEqual_comparesOperands() {
    assertThat(apply(PostFixOperator.GREATER_THAN_EQUAL, 3, 2)).isEqualTo(1f);
    assertThat(apply(PostFixOperator.GREATER_THAN_EQUAL, 3, 3)).isEqualTo(1f);
    assertThat(apply(PostFixOperator.GREATER_THAN_EQUAL, 2, 3)).isEqualTo(0f);
  }

  @Test
  void equalEpsilon_treatsNearlyEqualValuesAsEqual() {
    assertThat(apply(PostFixOperator.EQUAL_EPS, 1.0f, 1.0f + 1e-7f)).isEqualTo(1f);
    assertThat(apply(PostFixOperator.EQUAL_EPS, 1.0f, 1.5f)).isEqualTo(0f);
  }

  @Test
  void notEqualEpsilon_treatsNearlyEqualValuesAsEqual() {
    assertThat(apply(PostFixOperator.NOT_EQUAL_EPS, 1.0f, 1.0f + 1e-7f)).isEqualTo(0f);
    assertThat(apply(PostFixOperator.NOT_EQUAL_EPS, 1.0f, 1.5f)).isEqualTo(1f);
  }


  /* Piecewise */

  @Test
  void min_returnsSmallerOperand() {
    assertThat(apply(PostFixOperator.MIN, 5, 3)).isEqualTo(3f);
    assertThat(apply(PostFixOperator.MIN, 3, 5)).isEqualTo(3f);
  }

  @Test
  void max_returnsLargerOperand() {
    assertThat(apply(PostFixOperator.MAX, 5, 3)).isEqualTo(5f);
    assertThat(apply(PostFixOperator.MAX, 3, 5)).isEqualTo(5f);
  }

  @Test
  void nonNegative_clampsNegativeToZero() {
    assertThat(apply(PostFixOperator.NON_NEGATIVE, -5)).isEqualTo(0f);
    assertThat(apply(PostFixOperator.NON_NEGATIVE, 5)).isEqualTo(5f);
    assertThat(apply(PostFixOperator.NON_NEGATIVE, 0)).isEqualTo(0f);
  }

  @Test
  void percentClamp_clampsBetweenZeroAndOne() {
    assertThat(apply(PostFixOperator.PERCENT_CLAMP, -0.5f)).isEqualTo(0f);
    assertThat(apply(PostFixOperator.PERCENT_CLAMP, 1.5f)).isEqualTo(1f);
    assertThat(apply(PostFixOperator.PERCENT_CLAMP, 0.5f)).isEqualTo(0.5f);
  }

  @Test
  void abs_makesNegativeValuesPositive() {
    assertThat(apply(PostFixOperator.ABS, -3)).isEqualTo(3f);
    assertThat(apply(PostFixOperator.ABS, 3)).isEqualTo(3f);
  }

  @Test
  void floor_roundsTowardsNegativeInfinity() {
    assertThat(apply(PostFixOperator.FLOOR, 3.7f)).isEqualTo(3f);
    assertThat(apply(PostFixOperator.FLOOR, -3.2f)).isEqualTo(-4f);
  }

  @Test
  void ceil_roundsTowardsPositiveInfinity() {
    assertThat(apply(PostFixOperator.CEIL, 3.2f)).isEqualTo(4f);
    assertThat(apply(PostFixOperator.CEIL, -3.7f)).isEqualTo(-3f);
  }


  /* Stack operators */

  @Test
  void swap_swapsTopTwoElements() {
    FloatArrayList stack = stackOf(1, 2);
    PostFixOperator.SWAP.perform(stack, NO_VARIABLES);
    assertThat(stack.size()).isEqualTo(2);
    assertThat(stack.popFloat()).isEqualTo(1f); // now on top
    assertThat(stack.popFloat()).isEqualTo(2f); // now on bottom
  }

  @Test
  void duplicate_copiesTopElement() {
    FloatArrayList stack = stackOf(5);
    PostFixOperator.DUPLICATE.perform(stack, NO_VARIABLES);
    assertThat(stack.size()).isEqualTo(2);
    assertThat(stack.popFloat()).isEqualTo(5f);
    assertThat(stack.popFloat()).isEqualTo(5f);
  }


  /* JSON serialization */

  @Test
  void deserialize_unknownToken_throws() {
    assertThatThrownBy(() -> PostFixOperator.deserialize("nonexistent"))
      .isInstanceOf(JsonSyntaxException.class);
  }

  @Test
  void serialize_thenDeserialize_isSymmetricForEveryOperator() {
    for (PostFixOperator operator : PostFixOperator.values()) {
      String token = operator.serialize(NO_VARIABLE_NAMES).getAsString();
      assertThat(PostFixOperator.deserialize(token))
        .withFailMessage("Round trip failed for %s (token '%s')", operator, token)
        .isEqualTo(operator);
    }
  }


  /* Network serialization */

  @Test
  void toNetwork_thenFromNetwork_isSymmetricForEveryOperator() {
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    for (PostFixOperator operator : PostFixOperator.values()) {
      operator.toNetwork(buffer);
    }
    for (PostFixOperator operator : PostFixOperator.values()) {
      assertThat(StackOperation.fromNetwork(buffer)).isEqualTo(operator);
    }
  }
}
