package slimeknights.tconstruct.library.modifiers.modules.build;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ToolMaterial;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.modules.util.ModifierCondition;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.stat.ModifierStatsBuilder;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Tests {@link SetStatModule}: unconditionally sets (rather than boosts) a stat's value when its condition matches. */
class SetStatModuleTest extends BaseMcTest {
  private static final ModifierEntry LEVEL_1 = new ModifierEntry(new ModifierId("test", "foo"), 1);

  @Test
  void addToolStats_conditionMatches_updatesStat() {
    SetStatModule<ToolMaterial> module = SetStatModule.set(ToolStats.HARVEST_TIER).value(ToolMaterial.DIAMOND);
    ModifierStatsBuilder builder = ModifierStatsBuilder.builder();
    module.addToolStats(mock(IToolContext.class), LEVEL_1, builder);
    assertThat(builder.getStat(ToolStats.HARVEST_TIER)).isEqualTo(ToolMaterial.DIAMOND);
  }

  @Test
  void addToolStats_conditionFails_leavesDefaultValue() {
    SetStatModule<ToolMaterial> module = new SetStatModule<>(ToolStats.HARVEST_TIER, ToolMaterial.DIAMOND, ModifierCondition.ANY_CONTEXT.minLevel(5));
    ModifierStatsBuilder builder = ModifierStatsBuilder.builder();
    module.addToolStats(mock(IToolContext.class), LEVEL_1, builder);
    assertThat(builder.getStat(ToolStats.HARVEST_TIER)).isEqualTo(ToolStats.HARVEST_TIER.getDefaultValue());
  }

  @Test
  void addToolStats_tierStat_stillHonorsLargestTierWinsSemantic() {
    // SetStatModule.addToolStats() just delegates to the stat's own update(); for a tier stat that keeps the
    // largest tier seen so far - confirms this module doesn't bypass that semantic.
    SetStatModule<ToolMaterial> module = SetStatModule.set(ToolStats.HARVEST_TIER).value(ToolMaterial.IRON);
    ModifierStatsBuilder builder = ModifierStatsBuilder.builder();
    ToolStats.HARVEST_TIER.update(builder, ToolMaterial.DIAMOND);
    module.addToolStats(mock(IToolContext.class), LEVEL_1, builder);
    assertThat(builder.getStat(ToolStats.HARVEST_TIER)).isEqualTo(ToolMaterial.DIAMOND);
  }

  @Test
  void addToolStats_numericStat_setsFlatValue() {
    SetStatModule<Float> module = SetStatModule.set(ToolStats.DURABILITY).value(50f);
    ModifierStatsBuilder builder = ModifierStatsBuilder.builder();
    module.addToolStats(mock(IToolContext.class), LEVEL_1, builder);
    assertThat(builder.getStat(ToolStats.DURABILITY)).isEqualTo(50f);
  }

  @Test
  void builder_set_defaultsToAnyContextCondition() {
    SetStatModule<ToolMaterial> module = SetStatModule.set(ToolStats.HARVEST_TIER).value(ToolMaterial.WOOD);
    assertThat(module.condition()).isEqualTo(ModifierCondition.ANY_CONTEXT);
    assertThat(module.stat()).isSameAs(ToolStats.HARVEST_TIER);
    assertThat(module.value()).isEqualTo(ToolMaterial.WOOD);
  }

  @Test
  void loader_jsonRoundTrips() {
    SetStatModule<ToolMaterial> original = SetStatModule.set(ToolStats.HARVEST_TIER).minLevel(2).value(ToolMaterial.NETHERITE);
    JsonObject json = new JsonObject();
    SetStatModule.LOADER.serialize(original, json);
    SetStatModule<?> parsed = SetStatModule.LOADER.deserialize(json);

    ModifierStatsBuilder passingBuilder = ModifierStatsBuilder.builder();
    parsed.addToolStats(mock(IToolContext.class), new ModifierEntry(new ModifierId("test", "foo"), 3), passingBuilder);
    assertThat(passingBuilder.getStat(ToolStats.HARVEST_TIER)).isEqualTo(ToolMaterial.NETHERITE);

    ModifierStatsBuilder failingBuilder = ModifierStatsBuilder.builder();
    parsed.addToolStats(mock(IToolContext.class), LEVEL_1, failingBuilder);
    assertThat(failingBuilder.getStat(ToolStats.HARVEST_TIER)).isEqualTo(ToolStats.HARVEST_TIER.getDefaultValue());
  }

  @Test
  void loader_networkRoundTrips() {
    SetStatModule<Float> original = SetStatModule.set(ToolStats.DURABILITY).value(75f);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    SetStatModule.LOADER.encode(buffer, original);
    SetStatModule<?> decoded = SetStatModule.LOADER.decode(buffer);

    ModifierStatsBuilder builder = ModifierStatsBuilder.builder();
    decoded.addToolStats(mock(IToolContext.class), LEVEL_1, builder);
    assertThat(builder.getStat(ToolStats.DURABILITY)).isEqualTo(75f);
  }

  @Test
  void getLoader_returnsStaticLoader() {
    SetStatModule<ToolMaterial> module = SetStatModule.set(ToolStats.HARVEST_TIER).value(ToolMaterial.WOOD);
    assertThat(module.getLoader()).isSameAs(SetStatModule.LOADER);
  }
}
