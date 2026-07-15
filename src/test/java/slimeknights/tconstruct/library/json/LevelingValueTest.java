package slimeknights.tconstruct.library.json;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests {@link LevelingValue}: a float value with a flat part and a part that scales linearly with modifier level.
 * Directly consumed by {@code VolatileFloatModule}/{@code StatBoostModule}/{@code StatCopyModule} (this task's
 * primary cluster); needs no MC bootstrap, matching {@code PostFixFormula}'s precedent of pure-logic loadables.
 */
class LevelingValueTest {
  @Test
  void compute_addsFlatPlusScaled() {
    LevelingValue value = new LevelingValue(1.5f, 0.5f);
    assertThat(value.compute(4f)).isCloseTo(3.5f, within(0.0001f)); // 1.5 + 0.5*4
  }

  @Test
  void compute_modifierEntry_usesEffectiveLevel() {
    LevelingValue value = new LevelingValue(0f, 2f);
    ModifierEntry entry = new ModifierEntry(new ModifierId("test", "foo"), 3);
    assertThat(value.compute(entry)).isCloseTo(6f, within(0.0001f));
  }

  @Test
  void computeForLevel_zeroAtNonPositiveLevel() {
    LevelingValue value = new LevelingValue(10f, 1f);
    assertThat(value.computeForLevel(0f)).isZero();
    assertThat(value.computeForLevel(-1f)).isZero();
    assertThat(value.computeForLevel(2f)).isCloseTo(12f, within(0.0001f));
  }

  @Test
  void computeForScale_belowOneLevel_scalesFlatAndEachLevelTogether() {
    // documented: "when not using a full level, scale flat alongside each level"
    LevelingValue value = new LevelingValue(2f, 4f);
    // (2+4) * 0.5 = 3
    assertThat(value.computeForScale(0.5f)).isCloseTo(3f, within(0.0001f));
  }

  @Test
  void computeForScale_atOrAboveOneLevel_usesNormalCompute() {
    LevelingValue value = new LevelingValue(2f, 4f);
    assertThat(value.computeForScale(1f)).isCloseTo(6f, within(0.0001f)); // 2 + 4*1
    assertThat(value.computeForScale(2f)).isCloseTo(10f, within(0.0001f)); // 2 + 4*2
  }

  @Test
  void isFlat_trueOnlyWhenEachLevelIsZero() {
    assertThat(new LevelingValue(5f, 0f).isFlat()).isTrue();
    assertThat(new LevelingValue(5f, 0.01f).isFlat()).isFalse();
  }

  @Test
  void flat_ignoresLevel() {
    LevelingValue value = LevelingValue.flat(3f);
    assertThat(value.compute(1f)).isCloseTo(3f, within(0.0001f));
    assertThat(value.compute(99f)).isCloseTo(3f, within(0.0001f));
  }

  @Test
  void eachLevel_scalesLinearlyFromZero() {
    LevelingValue value = LevelingValue.eachLevel(1.5f);
    assertThat(value.compute(0f)).isZero();
    assertThat(value.compute(2f)).isCloseTo(3f, within(0.0001f));
  }

  @Test
  void constants_matchDocumentedBehavior() {
    assertThat(LevelingValue.ZERO.compute(10f)).isZero();
    assertThat(LevelingValue.ONE.compute(10f)).isCloseTo(1f, within(0.0001f));
    assertThat(LevelingValue.LEVEL.compute(10f)).isCloseTo(10f, within(0.0001f));
  }

  @Test
  void applyRandom_zeroRandomComponent_isExactlyFlatValue() {
    float result = LevelingValue.applyRandom(3f, new LevelingValue(2f, 1f), LevelingValue.ZERO);
    assertThat(result).isCloseTo(5f, within(0.0001f)); // flat.compute(3) = 2+1*3=5, random contributes 0
  }

  @Test
  void applyRandom_positiveRandomComponent_staysWithinDocumentedBounds() {
    LevelingValue flat = LevelingValue.flat(10f);
    LevelingValue random = LevelingValue.flat(4f);
    // "the random amount is scaled uniformly between 0 and the value" -> result always in [10, 14]
    for (int i = 0; i < 50; i++) {
      float result = LevelingValue.applyRandom(1f, flat, random);
      assertThat(result).isGreaterThanOrEqualTo(10f).isLessThanOrEqualTo(14f);
    }
  }

  @Test
  void loadable_jsonRoundTrips() {
    LevelingValue original = new LevelingValue(3.25f, 1.5f);
    JsonObject json = new JsonObject();
    LevelingValue.LOADABLE.serialize(original, json);
    LevelingValue parsed = LevelingValue.LOADABLE.deserialize(json);
    assertThat(parsed).isEqualTo(original);
  }

  @Test
  void loadable_missingFields_defaultToZero() {
    JsonObject json = new JsonObject();
    LevelingValue parsed = LevelingValue.LOADABLE.deserialize(json);
    assertThat(parsed).isEqualTo(LevelingValue.ZERO);
  }

  @Test
  void loadable_networkRoundTrips() {
    LevelingValue original = new LevelingValue(3.25f, 1.5f);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    LevelingValue.LOADABLE.encode(buffer, original);
    LevelingValue decoded = LevelingValue.LOADABLE.decode(buffer);
    assertThat(decoded).isEqualTo(original);
  }

  @Test
  void deprecatedJsonHelpers_roundTripThroughLoadable() {
    LevelingValue original = new LevelingValue(2f, 3f);
    JsonObject json = original.serialize(new JsonObject());
    assertThat(LevelingValue.deserialize(json)).isEqualTo(original);
  }

  @Test
  void deprecatedNetworkHelpers_roundTripThroughLoadable() {
    LevelingValue original = new LevelingValue(2f, 3f);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    original.toNetwork(buffer);
    assertThat(LevelingValue.fromNetwork(buffer)).isEqualTo(original);
  }
}
