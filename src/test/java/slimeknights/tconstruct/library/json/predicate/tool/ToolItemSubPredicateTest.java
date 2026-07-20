package slimeknights.tconstruct.library.json.predicate.tool;

import com.mojang.serialization.JsonOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tiers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.fixture.RegistrationFixture;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers the {@link ToolItemSubPredicate} advancement bridge: codec round-trip and the non-tool guard. */
class ToolItemSubPredicateTest extends BaseMcTest {
  /** Predicate loaders self-register at mod init via {@code TinkerTools}, which does not run in a bare unit test. */
  @BeforeAll
  static void registerPredicates() {
    RegistrationFixture.register(ToolStackPredicate.LOADER, "stat_in_set", StatInSetPredicate.LOADER);
  }

  @Test
  void codec_roundTrips() {
    ToolItemSubPredicate original = ToolItemSubPredicate.ofTool(
      new StatInSetPredicate<>(ToolStats.HARVEST_TIER, Tiers.NETHERITE));
    var encoded = ToolItemSubPredicate.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
    ToolItemSubPredicate decoded = ToolItemSubPredicate.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
    var reEncoded = ToolItemSubPredicate.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow();
    assertThat(reEncoded).isEqualTo(encoded);
  }

  @Test
  void matches_nonTool_isFalse() {
    ToolItemSubPredicate predicate = ToolItemSubPredicate.ofTool(ToolStackPredicate.ANY);
    assertThat(predicate.matches(new ItemStack(Items.STICK))).isFalse();
  }
}
