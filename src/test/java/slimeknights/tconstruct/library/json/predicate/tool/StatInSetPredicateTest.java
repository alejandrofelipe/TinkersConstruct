package slimeknights.tconstruct.library.json.predicate.tool;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ToolMaterial;
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
  private static StatsNBT statsWithHarvestTier(ToolMaterial tier) {
    ModifierStatsBuilder builder = ModifierStatsBuilder.builder();
    ToolStats.HARVEST_TIER.update(builder, tier);
    return builder.build();
  }

  @Test
  void matches_valueInSet_returnsTrue() {
    StatInSetPredicate<ToolMaterial> predicate = new StatInSetPredicate<>(ToolStats.HARVEST_TIER, Set.of(ToolMaterial.IRON, ToolMaterial.DIAMOND));
    IToolStackView tool = mock(IToolStackView.class);
    when(tool.getStats()).thenReturn(statsWithHarvestTier(ToolMaterial.IRON));
    assertThat(predicate.matches(tool)).isTrue();
  }

  @Test
  void matches_valueNotInSet_returnsFalse() {
    StatInSetPredicate<ToolMaterial> predicate = new StatInSetPredicate<>(ToolStats.HARVEST_TIER, Set.of(ToolMaterial.IRON, ToolMaterial.DIAMOND));
    IToolStackView tool = mock(IToolStackView.class);
    when(tool.getStats()).thenReturn(statsWithHarvestTier(ToolMaterial.STONE));
    assertThat(predicate.matches(tool)).isFalse();
  }

  @Test
  void singleValueConstructor_matchesOnlyThatValue() {
    StatInSetPredicate<ToolMaterial> predicate = new StatInSetPredicate<>(ToolStats.HARVEST_TIER, ToolMaterial.DIAMOND);
    IToolStackView tool = mock(IToolStackView.class);
    when(tool.getStats()).thenReturn(statsWithHarvestTier(ToolMaterial.DIAMOND));
    assertThat(predicate.matches(tool)).isTrue();

    when(tool.getStats()).thenReturn(statsWithHarvestTier(ToolMaterial.IRON));
    assertThat(predicate.matches(tool)).isFalse();
  }

  @Test
  void loader_serializeThenDeserialize_roundTrips() {
    StatInSetPredicate<ToolMaterial> original = new StatInSetPredicate<>(ToolStats.HARVEST_TIER, Set.of(ToolMaterial.IRON, ToolMaterial.DIAMOND));
    JsonObject json = new JsonObject();
    StatInSetPredicate.LOADER.serialize(original, json);
    StatInSetPredicate<?> roundTripped = StatInSetPredicate.LOADER.deserialize(json);

    IToolStackView tool = mock(IToolStackView.class);
    when(tool.getStats()).thenReturn(statsWithHarvestTier(ToolMaterial.IRON));
    assertThat(roundTripped.matches(tool)).isTrue();
    when(tool.getStats()).thenReturn(statsWithHarvestTier(ToolMaterial.STONE));
    assertThat(roundTripped.matches(tool)).isFalse();
  }

  @Test
  void loader_networkRoundTrips() {
    StatInSetPredicate<ToolMaterial> original = new StatInSetPredicate<>(ToolStats.HARVEST_TIER, Set.of(ToolMaterial.IRON, ToolMaterial.DIAMOND));
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    StatInSetPredicate.LOADER.encode(buffer, original);
    StatInSetPredicate<?> decoded = StatInSetPredicate.LOADER.decode(buffer);

    IToolStackView tool = mock(IToolStackView.class);
    when(tool.getStats()).thenReturn(statsWithHarvestTier(ToolMaterial.DIAMOND));
    assertThat(decoded.matches(tool)).isTrue();
    when(tool.getStats()).thenReturn(statsWithHarvestTier(ToolMaterial.STONE));
    assertThat(decoded.matches(tool)).isFalse();
  }
}
