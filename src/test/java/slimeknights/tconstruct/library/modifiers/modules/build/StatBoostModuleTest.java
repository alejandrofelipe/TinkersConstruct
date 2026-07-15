package slimeknights.tconstruct.library.modifiers.modules.build;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.json.LevelingValue;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.modules.build.StatBoostModule.StatOperation;
import slimeknights.tconstruct.library.modifiers.modules.util.ModifierCondition;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.stat.INumericToolStat;
import slimeknights.tconstruct.library.tools.stat.ModifierStatsBuilder;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests {@link StatBoostModule}: dispatches a leveled amount to one of a numeric stat's boost operations, gated by
 * its {@link ModifierCondition} and {@link INumericToolStat#supports}. The stat collaborator is mocked so these
 * tests isolate the module's own dispatch/level-math logic from {@code FloatToolStat}'s tag-gated {@code supports()}
 * (real {@code ToolStats} fields are tag-restricted, and tags are unbound in a bare unit test - see
 * {@code loader_*RoundTrips} below for how the real-stat identity round trip is still verified without that gate).
 */
@SuppressWarnings("unchecked")
class StatBoostModuleTest extends BaseMcTest {
  private static final ModifierEntry LEVEL_2 = new ModifierEntry(new ModifierId("test", "foo"), 2);

  private static IToolContext contextWithItem(Item item) {
    IToolContext context = mock(IToolContext.class);
    when(context.getItem()).thenReturn(item);
    return context;
  }

  @Test
  void addToolStats_conditionFails_neverTouchesStat() {
    INumericToolStat<Float> stat = mock(INumericToolStat.class);
    StatBoostModule module = new StatBoostModule(stat, StatOperation.ADD, LevelingValue.flat(5f), ModifierCondition.ANY_CONTEXT.minLevel(5));
    module.addToolStats(contextWithItem(Items.DIAMOND_PICKAXE), LEVEL_2, ModifierStatsBuilder.builder());
    // condition fails first (short-circuit), so supports() is never even reached
    verifyNoInteractions(stat);
  }

  @Test
  void addToolStats_unsupportedItem_neverAppliesOperation() {
    INumericToolStat<Float> stat = mock(INumericToolStat.class);
    when(stat.supports(Items.DIAMOND_PICKAXE)).thenReturn(false);
    StatBoostModule module = new StatBoostModule(stat, StatOperation.ADD, LevelingValue.flat(5f), ModifierCondition.ANY_CONTEXT);
    module.addToolStats(contextWithItem(Items.DIAMOND_PICKAXE), LEVEL_2, ModifierStatsBuilder.builder());
    verify(stat).supports(Items.DIAMOND_PICKAXE);
    verifyNoMoreInteractions(stat);
  }

  @Test
  void addToolStats_add_callsStatAddWithComputedAmount() {
    INumericToolStat<Float> stat = mock(INumericToolStat.class);
    when(stat.supports(Items.DIAMOND_PICKAXE)).thenReturn(true);
    // flat 1 + eachLevel 3, level 2 -> 1 + 3*2 = 7
    StatBoostModule module = new StatBoostModule(stat, StatOperation.ADD, new LevelingValue(1f, 3f), ModifierCondition.ANY_CONTEXT);
    ModifierStatsBuilder builder = ModifierStatsBuilder.builder();
    module.addToolStats(contextWithItem(Items.DIAMOND_PICKAXE), LEVEL_2, builder);
    verify(stat).add(builder, 7.0);
  }

  @Test
  void addToolStats_percent_callsStatPercentWithComputedAmount() {
    INumericToolStat<Float> stat = mock(INumericToolStat.class);
    when(stat.supports(Items.DIAMOND_PICKAXE)).thenReturn(true);
    StatBoostModule module = new StatBoostModule(stat, StatOperation.PERCENT, LevelingValue.flat(0.25f), ModifierCondition.ANY_CONTEXT);
    ModifierStatsBuilder builder = ModifierStatsBuilder.builder();
    module.addToolStats(contextWithItem(Items.DIAMOND_PICKAXE), LEVEL_2, builder);
    verify(stat).percent(builder, 0.25);
  }

  @Test
  void addToolStats_multiplyBase_callsStatMultiplyWithOnePlusAmount() {
    INumericToolStat<Float> stat = mock(INumericToolStat.class);
    when(stat.supports(Items.DIAMOND_PICKAXE)).thenReturn(true);
    StatBoostModule module = new StatBoostModule(stat, StatOperation.MULTIPLY_BASE, LevelingValue.flat(0.5f), ModifierCondition.ANY_CONTEXT);
    ModifierStatsBuilder builder = ModifierStatsBuilder.builder();
    module.addToolStats(contextWithItem(Items.DIAMOND_PICKAXE), LEVEL_2, builder);
    verify(stat).multiply(builder, 1.5);
  }

  @Test
  void addToolStats_multiplyConditional_setsBuilderMultiplierInsteadOfCallingStat() {
    INumericToolStat<Float> stat = mock(INumericToolStat.class);
    when(stat.supports(Items.DIAMOND_PICKAXE)).thenReturn(true);
    StatBoostModule module = new StatBoostModule(stat, StatOperation.MULTIPLY_CONDITIONAL, LevelingValue.flat(1f), ModifierCondition.ANY_CONTEXT);
    ModifierStatsBuilder builder = ModifierStatsBuilder.builder();
    module.addToolStats(contextWithItem(Items.DIAMOND_PICKAXE), LEVEL_2, builder);
    assertThat(builder.getMultiplier(stat)).isEqualTo(2f);
    verify(stat, never()).multiply(any(), anyDouble());
  }

  @Test
  void addToolStats_multiplyAll_callsStatMultiplyAllWithOnePlusAmount() {
    INumericToolStat<Float> stat = mock(INumericToolStat.class);
    when(stat.supports(Items.DIAMOND_PICKAXE)).thenReturn(true);
    StatBoostModule module = new StatBoostModule(stat, StatOperation.MULTIPLY_ALL, LevelingValue.flat(0.5f), ModifierCondition.ANY_CONTEXT);
    ModifierStatsBuilder builder = ModifierStatsBuilder.builder();
    module.addToolStats(contextWithItem(Items.DIAMOND_PICKAXE), LEVEL_2, builder);
    verify(stat).multiplyAll(builder, 1.5);
  }

  @Test
  void statOperation_getName_isLowercaseEnumName() {
    assertThat(StatOperation.MULTIPLY_BASE.getName()).isEqualTo("multiply_base");
  }


  /* Builder */

  @Test
  void builder_add_setsAddOperation() {
    StatBoostModule module = StatBoostModule.add(ToolStats.DURABILITY).flat(5f);
    assertThat(module.operation()).isEqualTo(StatOperation.ADD);
    assertThat(module.amount()).isEqualTo(new LevelingValue(5f, 0f));
    assertThat(module.condition()).isEqualTo(ModifierCondition.ANY_CONTEXT);
  }

  @Test
  void builder_multiplyBase_setsMultiplyBaseOperation() {
    StatBoostModule module = StatBoostModule.multiplyBase(ToolStats.DURABILITY).eachLevel(0.1f);
    assertThat(module.operation()).isEqualTo(StatOperation.MULTIPLY_BASE);
    assertThat(module.amount()).isEqualTo(new LevelingValue(0f, 0.1f));
  }

  @Test
  void builder_multiplyConditional_setsMultiplyConditionalOperation() {
    StatBoostModule module = StatBoostModule.multiplyConditional(ToolStats.DURABILITY).amount(1f, 2f);
    assertThat(module.operation()).isEqualTo(StatOperation.MULTIPLY_CONDITIONAL);
    assertThat(module.amount()).isEqualTo(new LevelingValue(1f, 2f));
  }

  @Test
  void builder_multiplyAll_setsMultiplyAllOperation() {
    StatBoostModule module = StatBoostModule.multiplyAll(ToolStats.DURABILITY).flat(0.2f);
    assertThat(module.operation()).isEqualTo(StatOperation.MULTIPLY_ALL);
  }

  @Test
  void builder_minLevel_setsConditionRange() {
    StatBoostModule module = StatBoostModule.add(ToolStats.DURABILITY).minLevel(3).flat(1f);
    IToolContext context = contextWithItem(Items.DIAMOND_PICKAXE);
    assertThat(module.condition().matches(context, new ModifierEntry(new ModifierId("test", "foo"), 2))).isFalse();
    assertThat(module.condition().matches(context, new ModifierEntry(new ModifierId("test", "foo"), 3))).isTrue();
  }


  /* Loader */

  @Test
  void loader_jsonRoundTrips() {
    StatBoostModule original = new StatBoostModule(ToolStats.DURABILITY, StatOperation.ADD, new LevelingValue(2f, 1f), ModifierCondition.ANY_CONTEXT);
    JsonObject json = new JsonObject();
    StatBoostModule.LOADER.serialize(original, json);
    StatBoostModule parsed = StatBoostModule.LOADER.deserialize(json);

    assertThat(parsed.stat()).isSameAs(ToolStats.DURABILITY);
    assertThat(parsed.operation()).isEqualTo(StatOperation.ADD);
    assertThat(parsed.amount()).isEqualTo(new LevelingValue(2f, 1f));
  }

  @Test
  void loader_networkRoundTrips() {
    StatBoostModule original = new StatBoostModule(ToolStats.ATTACK_DAMAGE, StatOperation.MULTIPLY_ALL, new LevelingValue(0.1f, 0.05f), ModifierCondition.ANY_CONTEXT);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    StatBoostModule.LOADER.encode(buffer, original);
    StatBoostModule decoded = StatBoostModule.LOADER.decode(buffer);

    assertThat(decoded.stat()).isSameAs(ToolStats.ATTACK_DAMAGE);
    assertThat(decoded.operation()).isEqualTo(StatOperation.MULTIPLY_ALL);
    assertThat(decoded.amount()).isEqualTo(new LevelingValue(0.1f, 0.05f));
  }

  @Test
  void getLoader_returnsStaticLoader() {
    StatBoostModule module = new StatBoostModule(ToolStats.DURABILITY, StatOperation.ADD, LevelingValue.ONE, ModifierCondition.ANY_CONTEXT);
    assertThat(module.getLoader()).isSameAs(StatBoostModule.LOADER);
  }
}
