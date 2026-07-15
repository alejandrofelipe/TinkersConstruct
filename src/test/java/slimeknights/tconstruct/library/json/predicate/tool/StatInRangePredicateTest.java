package slimeknights.tconstruct.library.json.predicate.tool;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.StatsNBT;
import slimeknights.tconstruct.library.tools.stat.ModifierStatsBuilder;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests {@link StatInRangePredicate}: min/max (inclusive) range check on a numeric tool stat. */
class StatInRangePredicateTest extends BaseMcTest {
  /** Builds stats with the given attack damage value (base 0 + update = value) */
  private static StatsNBT statsWithAttackDamage(float value) {
    ModifierStatsBuilder builder = ModifierStatsBuilder.builder();
    ToolStats.ATTACK_DAMAGE.update(builder, value);
    return builder.build();
  }

  @Test
  void test_valueWithinRange_matches() {
    StatInRangePredicate predicate = new StatInRangePredicate(ToolStats.ATTACK_DAMAGE, 5f, 10f);
    assertThat(predicate.test(statsWithAttackDamage(7f))).isTrue();
  }

  @Test
  void test_valueBelowMin_doesNotMatch() {
    StatInRangePredicate predicate = new StatInRangePredicate(ToolStats.ATTACK_DAMAGE, 5f, 10f);
    assertThat(predicate.test(statsWithAttackDamage(4.99f))).isFalse();
  }

  @Test
  void test_valueAboveMax_doesNotMatch() {
    StatInRangePredicate predicate = new StatInRangePredicate(ToolStats.ATTACK_DAMAGE, 5f, 10f);
    assertThat(predicate.test(statsWithAttackDamage(10.01f))).isFalse();
  }

  @Test
  void test_boundariesAreInclusive() {
    StatInRangePredicate predicate = new StatInRangePredicate(ToolStats.ATTACK_DAMAGE, 5f, 10f);
    assertThat(predicate.test(statsWithAttackDamage(5f))).isTrue();
    assertThat(predicate.test(statsWithAttackDamage(10f))).isTrue();
  }

  @Test
  void match_createsExactValuePredicate() {
    StatInRangePredicate predicate = StatInRangePredicate.match(ToolStats.ATTACK_DAMAGE, 7f);
    assertThat(predicate.test(statsWithAttackDamage(7f))).isTrue();
    assertThat(predicate.test(statsWithAttackDamage(6.99f))).isFalse();
    assertThat(predicate.test(statsWithAttackDamage(7.01f))).isFalse();
  }

  @Test
  void min_createsOpenEndedUpperBound() {
    StatInRangePredicate predicate = StatInRangePredicate.min(ToolStats.ATTACK_DAMAGE, 5f);
    assertThat(predicate.test(statsWithAttackDamage(5f))).isTrue();
    assertThat(predicate.test(statsWithAttackDamage(2000f))).isTrue();
    assertThat(predicate.test(statsWithAttackDamage(4.99f))).isFalse();
  }

  @Test
  void max_createsOpenEndedLowerBound() {
    StatInRangePredicate predicate = StatInRangePredicate.max(ToolStats.ATTACK_DAMAGE, 10f);
    assertThat(predicate.test(statsWithAttackDamage(10f))).isTrue();
    assertThat(predicate.test(statsWithAttackDamage(0f))).isTrue();
    assertThat(predicate.test(statsWithAttackDamage(10.01f))).isFalse();
  }

  @Test
  void matches_delegatesToToolStats() {
    StatInRangePredicate predicate = new StatInRangePredicate(ToolStats.ATTACK_DAMAGE, 5f, 10f);
    IToolStackView tool = mock(IToolStackView.class);
    when(tool.getStats()).thenReturn(statsWithAttackDamage(7f));
    assertThat(predicate.matches(tool)).isTrue();

    when(tool.getStats()).thenReturn(statsWithAttackDamage(1f));
    assertThat(predicate.matches(tool)).isFalse();
  }

  @Test
  void loader_serializeThenDeserialize_roundTrips() {
    StatInRangePredicate original = new StatInRangePredicate(ToolStats.ATTACK_DAMAGE, 5f, 10f);
    JsonObject json = new JsonObject();
    StatInRangePredicate.LOADER.serialize(original, json);
    StatInRangePredicate roundTripped = StatInRangePredicate.LOADER.deserialize(json);
    assertThat(roundTripped.test(statsWithAttackDamage(7f))).isTrue();
    assertThat(roundTripped.test(statsWithAttackDamage(4f))).isFalse();
  }

  @Test
  void loader_networkRoundTrips() {
    StatInRangePredicate original = new StatInRangePredicate(ToolStats.ATTACK_DAMAGE, 5f, 10f);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    StatInRangePredicate.LOADER.encode(buffer, original);
    StatInRangePredicate decoded = StatInRangePredicate.LOADER.decode(buffer);
    assertThat(decoded.test(statsWithAttackDamage(7f))).isTrue();
    assertThat(decoded.test(statsWithAttackDamage(4f))).isFalse();
  }
}
