package slimeknights.tconstruct.library.json.math;

import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests {@link StackOperation#deserialize} (the JSON element -> operation dispatch shared by every formula) and the
 * push operations' own serialize/network logic. Same package as {@link PushConstantOperation}/{@link PushVariableOperation}
 * so their package-private constructors are reachable directly.
 */
class StackOperationTest {
  private static final String[] VARIABLE_NAMES = {"foo", "bar"};

  @Test
  void deserialize_operatorToken_returnsMatchingOperator() {
    StackOperation operation = StackOperation.deserialize(new JsonPrimitive("+"), VARIABLE_NAMES);
    assertThat(operation).isEqualTo(PostFixOperator.ADD);
  }

  @Test
  void deserialize_dollarPrefixed_pushesTheNamedVariable() {
    // "bar" is index 1 in VARIABLE_NAMES
    StackOperation operation = StackOperation.deserialize(new JsonPrimitive("$bar"), VARIABLE_NAMES);
    assertThat(operation).isEqualTo(new PushVariableOperation(1));

    FloatArrayList stack = new FloatArrayList();
    operation.perform(stack, new float[]{10f, 20f});
    assertThat(stack.popFloat()).isEqualTo(20f);
  }

  @Test
  void deserialize_unknownVariable_throws() {
    assertThatThrownBy(() -> StackOperation.deserialize(new JsonPrimitive("$missing"), VARIABLE_NAMES))
      .isInstanceOf(JsonSyntaxException.class);
  }

  @Test
  void deserialize_blankString_throws() {
    assertThatThrownBy(() -> StackOperation.deserialize(new JsonPrimitive(""), VARIABLE_NAMES))
      .isInstanceOf(JsonSyntaxException.class);
  }

  @Test
  void deserialize_number_returnsConstantPush() {
    StackOperation operation = StackOperation.deserialize(new JsonPrimitive(3.5), VARIABLE_NAMES);
    assertThat(operation).isEqualTo(new PushConstantOperation(3.5f));
  }

  @Test
  void deserialize_boolean_throws() {
    assertThatThrownBy(() -> StackOperation.deserialize(new JsonPrimitive(true), VARIABLE_NAMES))
      .isInstanceOf(JsonSyntaxException.class);
  }


  /* PushConstantOperation */

  @Test
  void constantOperation_pushesItsValue() {
    FloatArrayList stack = new FloatArrayList();
    new PushConstantOperation(4.5f).perform(stack, new float[0]);
    assertThat(stack.popFloat()).isEqualTo(4.5f);
  }

  @Test
  void constantOperation_serializesAsRawNumber() {
    JsonPrimitive json = new PushConstantOperation(2.5f).serialize(VARIABLE_NAMES);
    assertThat(json.getAsFloat()).isEqualTo(2.5f);
  }

  @Test
  void constantOperation_networkRoundTrips() {
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    new PushConstantOperation(4.5f).toNetwork(buffer);
    assertThat(StackOperation.fromNetwork(buffer)).isEqualTo(new PushConstantOperation(4.5f));
  }


  /* PushVariableOperation */

  @Test
  void variableOperation_pushesTheIndexedArgument() {
    FloatArrayList stack = new FloatArrayList();
    new PushVariableOperation(1).perform(stack, new float[]{10f, 20f, 30f});
    assertThat(stack.popFloat()).isEqualTo(20f);
  }

  @Test
  void variableOperation_serializesWithDollarPrefix() {
    JsonPrimitive json = new PushVariableOperation(1).serialize(VARIABLE_NAMES);
    assertThat(json.getAsString()).isEqualTo("$bar");
  }

  @Test
  void variableOperation_networkRoundTrips() {
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    new PushVariableOperation(1).toNetwork(buffer);
    assertThat(StackOperation.fromNetwork(buffer)).isEqualTo(new PushVariableOperation(1));
  }
}
