package slimeknights.tconstruct.library.json;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link LevelingInt}: an int value with a flat part and a part that scales linearly with modifier level.
 * Directly consumed by {@code VolatileIntModule} (this task's primary cluster); needs no MC bootstrap, matching
 * {@code PostFixFormula}'s precedent of pure-logic Mantle loadables.
 */
class LevelingIntTest {
  @Test
  void compute_intLevel_addsFlatPlusScaled() {
    LevelingInt value = new LevelingInt(2, 3);
    assertThat(value.compute(4)).isEqualTo(14); // 2 + 3*4
  }

  @Test
  void compute_floatLevel_truncatesTowardZero() {
    LevelingInt value = new LevelingInt(1, 2);
    assertThat(value.compute(2.9f)).isEqualTo(6); // (int)(1 + 2*2.9) = (int) 6.8 = 6
  }

  @Test
  void compute_modifierEntry_usesEffectiveLevel() {
    LevelingInt value = new LevelingInt(0, 5);
    ModifierEntry entry = new ModifierEntry(new ModifierId("test", "foo"), 3);
    assertThat(value.compute(entry)).isEqualTo(15);
  }

  @Test
  void computeForLevel_int_zeroAtNonPositiveLevel() {
    LevelingInt value = new LevelingInt(10, 1);
    assertThat(value.computeForLevel(0)).isZero();
    assertThat(value.computeForLevel(-1)).isZero();
    assertThat(value.computeForLevel(2)).isEqualTo(12);
  }

  @Test
  void computeForLevel_float_zeroAtNonPositiveLevel() {
    LevelingInt value = new LevelingInt(10, 2);
    assertThat(value.computeForLevel(0f)).isZero();
    assertThat(value.computeForLevel(2.5f)).isEqualTo(15); // (int)(10 + 2*2.5) = 15
  }

  @Test
  void flat_ignoresLevel() {
    LevelingInt value = LevelingInt.flat(7);
    assertThat(value.compute(1)).isEqualTo(7);
    assertThat(value.compute(99)).isEqualTo(7);
  }

  @Test
  void eachLevel_scalesLinearlyFromZero() {
    LevelingInt value = LevelingInt.eachLevel(4);
    assertThat(value.compute(0)).isZero();
    assertThat(value.compute(3)).isEqualTo(12);
  }

  @Test
  void constants_matchDocumentedBehavior() {
    assertThat(LevelingInt.ZERO.compute(10)).isZero();
    assertThat(LevelingInt.ONE.compute(10)).isEqualTo(1);
    assertThat(LevelingInt.LEVEL.compute(10)).isEqualTo(10);
  }

  @Test
  void loadable_jsonRoundTrips() {
    LevelingInt original = new LevelingInt(3, 5);
    JsonObject json = new JsonObject();
    LevelingInt.LOADABLE.serialize(original, json);
    LevelingInt parsed = LevelingInt.LOADABLE.deserialize(json);
    assertThat(parsed).isEqualTo(original);
    assertThat(parsed.compute(2)).isEqualTo(13);
  }

  @Test
  void loadable_missingFields_defaultToZero() {
    JsonObject json = new JsonObject();
    LevelingInt parsed = LevelingInt.LOADABLE.deserialize(json);
    assertThat(parsed).isEqualTo(LevelingInt.ZERO);
  }

  @Test
  void loadable_networkRoundTrips() {
    LevelingInt original = new LevelingInt(3, 5);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    LevelingInt.LOADABLE.encode(buffer, original);
    LevelingInt decoded = LevelingInt.LOADABLE.decode(buffer);
    assertThat(decoded).isEqualTo(original);
  }
}
