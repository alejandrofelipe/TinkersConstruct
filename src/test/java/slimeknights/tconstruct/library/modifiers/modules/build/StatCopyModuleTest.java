package slimeknights.tconstruct.library.modifiers.modules.build;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.json.LevelingValue;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.modules.util.ModifierCondition;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.stat.ModifierStatsBuilder;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;

/** Tests {@link StatCopyModule}: copies a percentage of one numeric stat's built value into another, dividing out any multiplier already applied to the source. */
class StatCopyModuleTest extends BaseMcTest {
  private static final ModifierEntry LEVEL_1 = new ModifierEntry(new ModifierId("test", "foo"), 1);

  @Test
  void addToolStats_copiesPercentageOfSourceIntoTarget() {
    StatCopyModule module = StatCopyModule.builder(ToolStats.DURABILITY, ToolStats.ATTACK_DAMAGE).flat(0.5f);
    ModifierStatsBuilder builder = ModifierStatsBuilder.builder();
    ToolStats.ATTACK_DAMAGE.update(builder, 10f);
    module.addToolStats(mock(IToolContext.class), LEVEL_1, builder);
    // source built value 10, 50% of it (5) added on top of durability's default (1) -> 6
    assertThat(builder.getStat(ToolStats.DURABILITY)).isCloseTo(6f, within(0.0001f));
  }

  @Test
  void addToolStats_dividesOutSourceMultiplier() {
    StatCopyModule module = StatCopyModule.builder(ToolStats.DURABILITY, ToolStats.ATTACK_DAMAGE).flat(0.5f);
    ModifierStatsBuilder builder = ModifierStatsBuilder.builder();
    ToolStats.ATTACK_DAMAGE.update(builder, 10f);
    builder.multiplier(ToolStats.ATTACK_DAMAGE, 2.0);
    module.addToolStats(mock(IToolContext.class), LEVEL_1, builder);
    // 10 * 0.5 / 2.0 = 2.5, on top of durability's default (1) -> 3.5
    assertThat(builder.getStat(ToolStats.DURABILITY)).isCloseTo(3.5f, within(0.0001f));
  }

  @Test
  void addToolStats_scalesWithLevel() {
    StatCopyModule module = StatCopyModule.builder(ToolStats.DURABILITY, ToolStats.ATTACK_DAMAGE).eachLevel(0.1f);
    ModifierStatsBuilder builder = ModifierStatsBuilder.builder();
    ToolStats.ATTACK_DAMAGE.update(builder, 10f);
    module.addToolStats(mock(IToolContext.class), new ModifierEntry(new ModifierId("test", "foo"), 3), builder);
    // 10 * (0.1*3) = 3, on top of default (1) -> 4
    assertThat(builder.getStat(ToolStats.DURABILITY)).isCloseTo(4f, within(0.0001f));
  }

  @Test
  void addToolStats_conditionFails_leavesTargetDefault() {
    StatCopyModule module = new StatCopyModule(ToolStats.DURABILITY, ToolStats.ATTACK_DAMAGE, LevelingValue.flat(0.5f), ModifierCondition.ANY_CONTEXT.minLevel(5));
    ModifierStatsBuilder builder = ModifierStatsBuilder.builder();
    ToolStats.ATTACK_DAMAGE.update(builder, 10f);
    module.addToolStats(mock(IToolContext.class), LEVEL_1, builder);
    assertThat(builder.getStat(ToolStats.DURABILITY)).isEqualTo(ToolStats.DURABILITY.getDefaultValue());
  }

  @Test
  void getPriority_runsLateAt50() {
    StatCopyModule module = StatCopyModule.builder(ToolStats.DURABILITY, ToolStats.ATTACK_DAMAGE).flat(1f);
    assertThat(module.getPriority()).isEqualTo(50);
  }

  @Test
  void loader_jsonRoundTrips() {
    StatCopyModule original = StatCopyModule.builder(ToolStats.DURABILITY, ToolStats.ATTACK_DAMAGE).flat(0.25f);
    JsonObject json = new JsonObject();
    StatCopyModule.LOADER.serialize(original, json);
    StatCopyModule parsed = StatCopyModule.LOADER.deserialize(json);

    ModifierStatsBuilder builder = ModifierStatsBuilder.builder();
    ToolStats.ATTACK_DAMAGE.update(builder, 8f);
    parsed.addToolStats(mock(IToolContext.class), LEVEL_1, builder);
    assertThat(builder.getStat(ToolStats.DURABILITY)).isCloseTo(3f, within(0.0001f)); // 1 + 8*0.25
  }

  @Test
  void loader_networkRoundTrips() {
    StatCopyModule original = StatCopyModule.builder(ToolStats.DURABILITY, ToolStats.ATTACK_DAMAGE).flat(0.25f);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    StatCopyModule.LOADER.encode(buffer, original);
    StatCopyModule decoded = StatCopyModule.LOADER.decode(buffer);

    ModifierStatsBuilder builder = ModifierStatsBuilder.builder();
    ToolStats.ATTACK_DAMAGE.update(builder, 8f);
    decoded.addToolStats(mock(IToolContext.class), LEVEL_1, builder);
    assertThat(builder.getStat(ToolStats.DURABILITY)).isCloseTo(3f, within(0.0001f));
  }

  @Test
  void getLoader_returnsStaticLoader() {
    StatCopyModule module = StatCopyModule.builder(ToolStats.DURABILITY, ToolStats.ATTACK_DAMAGE).flat(1f);
    assertThat(module.getLoader()).isSameAs(StatCopyModule.LOADER);
  }
}
