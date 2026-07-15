package slimeknights.tconstruct.library.json.predicate.tool;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Tiers;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.StatsNBT;
import slimeknights.tconstruct.library.tools.stat.ModifierStatsBuilder;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests {@link StatInSetPredicate}: set membership check on a tool stat, plus its hand rolled {@code LOADER}. */
class StatInSetPredicateTest extends BaseMcTest {
  private static StatsNBT statsWithHarvestTier(net.minecraft.world.item.Tier tier) {
    ModifierStatsBuilder builder = ModifierStatsBuilder.builder();
    ToolStats.HARVEST_TIER.update(builder, tier);
    return builder.build();
  }

  @Test
  void matches_valueInSet_returnsTrue() {
    StatInSetPredicate<net.minecraft.world.item.Tier> predicate = new StatInSetPredicate<>(ToolStats.HARVEST_TIER, Set.of(Tiers.IRON, Tiers.DIAMOND));
    IToolStackView tool = mock(IToolStackView.class);
    when(tool.getStats()).thenReturn(statsWithHarvestTier(Tiers.IRON));
    assertThat(predicate.matches(tool)).isTrue();
  }

  @Test
  void matches_valueNotInSet_returnsFalse() {
    StatInSetPredicate<net.minecraft.world.item.Tier> predicate = new StatInSetPredicate<>(ToolStats.HARVEST_TIER, Set.of(Tiers.IRON, Tiers.DIAMOND));
    IToolStackView tool = mock(IToolStackView.class);
    when(tool.getStats()).thenReturn(statsWithHarvestTier(Tiers.STONE));
    assertThat(predicate.matches(tool)).isFalse();
  }

  @Test
  void singleValueConstructor_matchesOnlyThatValue() {
    StatInSetPredicate<net.minecraft.world.item.Tier> predicate = new StatInSetPredicate<>(ToolStats.HARVEST_TIER, Tiers.DIAMOND);
    IToolStackView tool = mock(IToolStackView.class);
    when(tool.getStats()).thenReturn(statsWithHarvestTier(Tiers.DIAMOND));
    assertThat(predicate.matches(tool)).isTrue();

    when(tool.getStats()).thenReturn(statsWithHarvestTier(Tiers.IRON));
    assertThat(predicate.matches(tool)).isFalse();
  }

  @Test
  void loader_serializeThenDeserialize_roundTrips() {
    StatInSetPredicate<net.minecraft.world.item.Tier> original = new StatInSetPredicate<>(ToolStats.HARVEST_TIER, Set.of(Tiers.IRON, Tiers.DIAMOND));
    JsonObject json = new JsonObject();
    StatInSetPredicate.LOADER.serialize(original, json);
    StatInSetPredicate<?> roundTripped = StatInSetPredicate.LOADER.deserialize(json);

    IToolStackView tool = mock(IToolStackView.class);
    when(tool.getStats()).thenReturn(statsWithHarvestTier(Tiers.IRON));
    assertThat(roundTripped.matches(tool)).isTrue();
    when(tool.getStats()).thenReturn(statsWithHarvestTier(Tiers.STONE));
    assertThat(roundTripped.matches(tool)).isFalse();
  }

  @Test
  void loader_networkRoundTrips() {
    StatInSetPredicate<net.minecraft.world.item.Tier> original = new StatInSetPredicate<>(ToolStats.HARVEST_TIER, Set.of(Tiers.IRON, Tiers.DIAMOND));
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    StatInSetPredicate.LOADER.encode(buffer, original);
    StatInSetPredicate<?> decoded = StatInSetPredicate.LOADER.decode(buffer);

    IToolStackView tool = mock(IToolStackView.class);
    when(tool.getStats()).thenReturn(statsWithHarvestTier(Tiers.DIAMOND));
    assertThat(decoded.matches(tool)).isTrue();
    when(tool.getStats()).thenReturn(statsWithHarvestTier(Tiers.STONE));
    assertThat(decoded.matches(tool)).isFalse();
  }
}
