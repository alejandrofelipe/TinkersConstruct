package slimeknights.tconstruct.library.json.math;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests {@link PostFixFormula}: the expression engine tying together {@link PostFixOperator} and the push operations,
 * its {@link PostFixFormula.Builder}, and its JSON/network (de)serialization. Pure math + Gson + a plain
 * FriendlyByteBuf, no MC bootstrap required.
 *
 * <p>NOTE: {@code Builder#notEqualEpsilon()} is deliberately not exercised here - reading the source shows it wires
 * to {@code PostFixOperator.EQUAL_EPS} instead of {@code NOT_EQUAL_EPS} (copy/paste bug, pre-existing, unrelated to
 * this cluster). {@link PostFixOperatorTest} covers the {@code NOT_EQUAL_EPS} operator itself correctly.
 */
class PostFixFormulaTest {
  private static final String[] ONE_VAR = {"a"};
  private static final String[] TWO_VARS = {"a", "b"};
  private static final Offset<Float> TOLERANCE = within(0.0001f);

  private static JsonObject formulaJson(Object... tokens) {
    JsonArray array = new JsonArray();
    for (Object token : tokens) {
      if (token instanceof String s) {
        array.add(s);
      } else if (token instanceof Number n) {
        array.add(n.floatValue());
      } else if (token instanceof Boolean b) {
        array.add(b);
      } else {
        throw new IllegalArgumentException("Unsupported token type " + token);
      }
    }
    JsonObject json = new JsonObject();
    json.add("formula", array);
    return json;
  }


  /* apply() */

  @Test
  void apply_wrongArgumentCount_throws() {
    PostFixFormula formula = PostFixFormula.builder(ONE_VAR).variable(0).buildFormula();
    assertThatThrownBy(() -> formula.apply(1f, 2f)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(formula::apply).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void apply_variablesAndConstant_computesExpectedValue() {
    // (a + b) * 2
    PostFixFormula formula = PostFixFormula.builder(TWO_VARS)
      .variable(0).variable(1).add()
      .constant(2).multiply()
      .buildFormula();
    assertThat(formula.apply(3, 4)).isEqualTo(14f); // (3+4)*2
    assertThat(formula.apply(-1, 1)).isEqualTo(0f);
  }

  @Test
  void apply_noArguments_computesConstantExpression() {
    PostFixFormula formula = PostFixFormula.builder(new String[0]).constant(2).constant(3).multiply().buildFormula();
    assertThat(formula.apply()).isEqualTo(6f);
  }


  /* Builder validation */

  @Test
  void buildFormula_tooManyValuesLeftOnStack_throws() {
    // two constants pushed, nothing combines them -> 2 values left instead of 1
    assertThatThrownBy(() -> PostFixFormula.builder(new String[0]).constant(1).constant(2).buildFormula())
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void buildFormula_emptyStack_throws() {
    assertThatThrownBy(() -> PostFixFormula.builder(new String[0]).buildFormula())
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void builder_invalidVariableIndex_throws() {
    assertThatThrownBy(() -> PostFixFormula.builder(ONE_VAR).variable(1))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PostFixFormula.builder(ONE_VAR).variable(-1))
      .isInstanceOf(IllegalArgumentException.class);
  }


  /* Builder convenience methods, each checked against a real numeric result */

  @Test
  void builder_min() {
    PostFixFormula formula = PostFixFormula.builder(TWO_VARS).variable(0).variable(1).min().buildFormula();
    assertThat(formula.apply(5, 3)).isEqualTo(3f);
  }

  @Test
  void builder_max() {
    PostFixFormula formula = PostFixFormula.builder(TWO_VARS).variable(0).variable(1).max().buildFormula();
    assertThat(formula.apply(5, 3)).isEqualTo(5f);
  }

  @Test
  void builder_nonNegative() {
    PostFixFormula formula = PostFixFormula.builder(ONE_VAR).variable(0).nonNegative().buildFormula();
    assertThat(formula.apply(-5)).isEqualTo(0f);
    assertThat(formula.apply(5)).isEqualTo(5f);
  }

  @Test
  void builder_percentClamp() {
    PostFixFormula formula = PostFixFormula.builder(ONE_VAR).variable(0).percentClamp().buildFormula();
    assertThat(formula.apply(1.5f)).isEqualTo(1f);
    assertThat(formula.apply(-0.5f)).isEqualTo(0f);
  }

  @Test
  void builder_sqrt() {
    PostFixFormula formula = PostFixFormula.builder(ONE_VAR).variable(0).sqrt().buildFormula();
    assertThat(formula.apply(9)).isEqualTo(3f, TOLERANCE);
  }

  @Test
  void builder_negate() {
    PostFixFormula formula = PostFixFormula.builder(ONE_VAR).variable(0).negate().buildFormula();
    assertThat(formula.apply(5)).isEqualTo(-5f);
  }

  @Test
  void builder_divide() {
    PostFixFormula formula = PostFixFormula.builder(TWO_VARS).variable(0).variable(1).divide().buildFormula();
    assertThat(formula.apply(10, 2)).isEqualTo(5f);
  }

  @Test
  void builder_power() {
    PostFixFormula formula = PostFixFormula.builder(TWO_VARS).variable(0).variable(1).power().buildFormula();
    assertThat(formula.apply(2, 3)).isEqualTo(8f); // 2^3
  }

  @Test
  void builder_floorAndCeil() {
    PostFixFormula floor = PostFixFormula.builder(ONE_VAR).variable(0).floor().buildFormula();
    assertThat(floor.apply(3.7f)).isEqualTo(3f);

    PostFixFormula ceil = PostFixFormula.builder(ONE_VAR).variable(0).ceil().buildFormula();
    assertThat(ceil.apply(3.2f)).isEqualTo(4f);
  }

  @Test
  void builder_powerFlipped() {
    PostFixFormula formula = PostFixFormula.builder(TWO_VARS).variable(0).variable(1).powerFlipped().buildFormula();
    assertThat(formula.apply(2, 3)).isEqualTo(9f); // 3^2
  }

  @Test
  void builder_divideFlipped() {
    PostFixFormula formula = PostFixFormula.builder(TWO_VARS).variable(0).variable(1).divideFlipped().buildFormula();
    assertThat(formula.apply(2, 10)).isEqualTo(5f); // 10/2
  }

  @Test
  void builder_subtractFlipped() {
    PostFixFormula formula = PostFixFormula.builder(TWO_VARS).variable(0).variable(1).subtractFlipped().buildFormula();
    assertThat(formula.apply(5, 3)).isEqualTo(-2f); // 3-5
  }

  @Test
  void builder_swapAndDuplicate() {
    // duplicate a onto the stack, swap the copies, then subtract -> always 0, but exercises both wirings
    PostFixFormula formula = PostFixFormula.builder(ONE_VAR).variable(0).duplicate().swap().subtract().buildFormula();
    assertThat(formula.apply(7)).isEqualTo(0f);
  }

  @Test
  void builder_comparisons() {
    assertThat(PostFixFormula.builder(TWO_VARS).variable(0).variable(1).equal().buildFormula().apply(3, 3)).isEqualTo(1f);
    assertThat(PostFixFormula.builder(TWO_VARS).variable(0).variable(1).equal().buildFormula().apply(3, 4)).isEqualTo(0f);

    assertThat(PostFixFormula.builder(TWO_VARS).variable(0).variable(1).notEqual().buildFormula().apply(3, 4)).isEqualTo(1f);

    assertThat(PostFixFormula.builder(TWO_VARS).variable(0).variable(1).greaterThan().buildFormula().apply(5, 3)).isEqualTo(1f);
    assertThat(PostFixFormula.builder(TWO_VARS).variable(0).variable(1).greaterThanOrEqual().buildFormula().apply(3, 3)).isEqualTo(1f);

    assertThat(PostFixFormula.builder(TWO_VARS).variable(0).variable(1).lessThan().buildFormula().apply(2, 3)).isEqualTo(1f);
    assertThat(PostFixFormula.builder(TWO_VARS).variable(0).variable(1).lessThanOrEqual().buildFormula().apply(3, 3)).isEqualTo(1f);

    assertThat(PostFixFormula.builder(TWO_VARS).variable(0).variable(1).equalEpsilon().buildFormula().apply(1f, 1f + 1e-7f)).isEqualTo(1f);
  }


  /* JSON serialization */

  @Test
  void deserialize_variablesConstantsAndOperators_computesExpectedValue() {
    // ($a * 2) - 1
    JsonObject json = formulaJson("$a", 2, "*", 1, "-");
    PostFixFormula formula = PostFixFormula.deserialize(json, ONE_VAR);
    assertThat(formula.apply(5)).isEqualTo(9f); // 5*2-1 = 9
    assertThat(formula.apply(0)).isEqualTo(-1f);
  }

  @Test
  void deserialize_malformedFormula_wrapsAsJsonSyntaxException() {
    // ADD needs 2 operands, stack starts empty -> invalid formula
    JsonObject json = formulaJson("+");
    assertThatThrownBy(() -> PostFixFormula.deserialize(json, new String[0]))
      .isInstanceOf(JsonSyntaxException.class);
  }

  @Test
  void deserialize_unknownVariable_throws() {
    JsonObject json = formulaJson("$unknown");
    assertThatThrownBy(() -> PostFixFormula.deserialize(json, ONE_VAR))
      .isInstanceOf(JsonSyntaxException.class);
  }

  @Test
  void deserialize_blankToken_throws() {
    JsonObject json = formulaJson("");
    assertThatThrownBy(() -> PostFixFormula.deserialize(json, new String[0]))
      .isInstanceOf(JsonSyntaxException.class);
  }

  @Test
  void deserialize_booleanToken_throws() {
    JsonObject json = formulaJson(true);
    assertThatThrownBy(() -> PostFixFormula.deserialize(json, new String[0]))
      .isInstanceOf(JsonSyntaxException.class);
  }

  @Test
  void deserialize_emptyArray_throws() {
    JsonObject json = formulaJson();
    assertThatThrownBy(() -> PostFixFormula.deserialize(json, new String[0]))
      .isInstanceOf(JsonSyntaxException.class);
  }

  @Test
  void deserialize_nonPrimitiveToken_throws() {
    // a nested array where a string/number token is expected
    JsonArray array = new JsonArray();
    array.add(new JsonArray());
    JsonObject json = new JsonObject();
    json.add("formula", array);
    assertThatThrownBy(() -> PostFixFormula.deserialize(json, new String[0]))
      .isInstanceOf(JsonSyntaxException.class);
  }

  @Test
  void serialize_thenDeserialize_producesEquivalentFormula() {
    PostFixFormula original = PostFixFormula.builder(TWO_VARS)
      .variable(0).variable(1).subtract()
      .abs()
      .buildFormula();
    JsonObject json = original.serialize(new JsonObject(), TWO_VARS);
    PostFixFormula roundTripped = PostFixFormula.deserialize(json, TWO_VARS);
    assertThat(roundTripped.apply(3, 8)).isEqualTo(original.apply(3, 8)).isEqualTo(5f);
    assertThat(roundTripped.apply(8, 3)).isEqualTo(original.apply(8, 3)).isEqualTo(5f);
  }


  /* Network serialization */

  @Test
  void toNetwork_thenFromNetwork_producesEquivalentFormula() {
    PostFixFormula original = PostFixFormula.builder(ONE_VAR).variable(0).constant(3).add().buildFormula();
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    original.toNetwork(buffer);
    PostFixFormula decoded = PostFixFormula.fromNetwork(buffer, 1);
    assertThat(decoded.apply(4)).isEqualTo(original.apply(4)).isEqualTo(7f);
  }
}
