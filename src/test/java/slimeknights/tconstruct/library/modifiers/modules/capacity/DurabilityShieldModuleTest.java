package slimeknights.tconstruct.library.modifiers.modules.capacity;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.json.LevelingInt;
import slimeknights.tconstruct.library.json.predicate.modifier.ModifierPredicate;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierFixture;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.special.CapacityBarHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests {@link DurabilityShieldModule}: a capacity bar that intercepts tool damage before it's applied, "spending"
 * shield charge (optionally at a non-1:1 cost per durability point, with the cost remainder handled as a
 * chance-based reduction) instead of letting the tool take it, plus the matching durability-bar display hooks.
 */
class DurabilityShieldModuleTest extends BaseMcTest {
  @BeforeAll
  static void setup() {
    ModifierFixture.init();
  }

  private static final ModifierId CAUSE = new ModifierId("test", "cause");

  private static ModifierEntry entryWithBar(CapacityBarHook bar) {
    Modifier modifier = ModifierFixture.withHooks(ModuleHookMap.builder().addHook(bar, ModifierHooks.CAPACITY_BAR).build());
    return new ModifierEntry(modifier, 2);
  }


  /* static onDamageTool(bar, tool, modifier, amount, cost) - the core algorithm */

  @Test
  void onDamageTool_static_noShield_returnsAmountUnchanged() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    IToolStackView tool = mock(IToolStackView.class);
    when(bar.getAmount(tool)).thenReturn(0);
    int result = DurabilityShieldModule.onDamageTool(bar, tool, ModifierEntry.EMPTY, 10, 1);
    assertThat(result).isEqualTo(10);
    verify(bar, never()).setAmount(any(), any(), anyInt());
  }

  @Test
  void onDamageTool_static_zeroCost_returnsAmountUnchanged() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    IToolStackView tool = mock(IToolStackView.class);
    when(bar.getAmount(tool)).thenReturn(50);
    int result = DurabilityShieldModule.onDamageTool(bar, tool, ModifierEntry.EMPTY, 10, 0);
    assertThat(result).isEqualTo(10);
    verify(bar, never()).setAmount(any(), any(), anyInt());
  }

  @Test
  void onDamageTool_static_shieldFullyCoversAmount_absorbsEverything() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    IToolStackView tool = mock(IToolStackView.class);
    ModifierEntry modifier = ModifierEntry.EMPTY;
    when(bar.getAmount(tool)).thenReturn(100);
    // canReduce = 100/2 = 50 >= amount (10)
    int result = DurabilityShieldModule.onDamageTool(bar, tool, modifier, 10, 2);
    assertThat(result).isZero();
    verify(bar).setAmount(tool, modifier, 100 - 10 * 2);
  }

  @Test
  void onDamageTool_static_partialCoverage_costOne_skipsChanceBranchEntirely() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    IToolStackView tool = mock(IToolStackView.class);
    ModifierEntry modifier = ModifierEntry.EMPTY;
    when(bar.getAmount(tool)).thenReturn(5);
    // canReduce = 5/1 = 5 < amount (10); cost == 1 so the RNG-gated remainder branch never runs
    int result = DurabilityShieldModule.onDamageTool(bar, tool, modifier, 10, 1);
    assertThat(result).isEqualTo(5);
    verify(bar).setAmount(tool, modifier, 0);
  }

  @Test
  void onDamageTool_static_partialCoverage_exactMultipleOfCost_skipsChanceBranch() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    IToolStackView tool = mock(IToolStackView.class);
    ModifierEntry modifier = ModifierEntry.EMPTY;
    when(bar.getAmount(tool)).thenReturn(10);
    // canReduce = 10/5 = 2 < amount (10); remainder chance = 10 % 5 = 0, so the "chance > 0" guard skips the RNG
    int result = DurabilityShieldModule.onDamageTool(bar, tool, modifier, 10, 5);
    assertThat(result).isEqualTo(8); // 10 - canReduce(2)
    verify(bar).setAmount(tool, modifier, 0);
  }

  @Test
  void onDamageTool_static_partialCoverage_chanceBranch_boundedAndBothOutcomesReachable() {
    // shield=7, cost=5: canReduce=1, so amount reduces to 9 before the chance branch; chance = 7 % 5 = 2, gated by
    // "2 > TConstruct.RANDOM.nextInt(5)" (2/5 odds). bar.setAmount(modifier, 0) fires unconditionally in this
    // branch regardless of the coin flip (asserted every iteration); the numeric result is bounds-checked across
    // repeated trials instead of pinned to a single value since TConstruct.RANDOM is shared, unseeded state.
    Set<Integer> seen = new HashSet<>();
    for (int i = 0; i < 200; i++) {
      CapacityBarHook bar = mock(CapacityBarHook.class);
      IToolStackView tool = mock(IToolStackView.class);
      ModifierEntry modifier = ModifierEntry.EMPTY;
      when(bar.getAmount(tool)).thenReturn(7);
      int result = DurabilityShieldModule.onDamageTool(bar, tool, modifier, 10, 5);
      assertThat(result).isIn(8, 9);
      verify(bar).setAmount(tool, modifier, 0);
      seen.add(result);
    }
    assertThat(seen).containsExactlyInAnyOrder(8, 9);
  }

  @Test
  void onDamageTool_static_fourArgOverload_defaultsCostToOne() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    IToolStackView tool = mock(IToolStackView.class);
    ModifierEntry modifier = ModifierEntry.EMPTY;
    when(bar.getAmount(tool)).thenReturn(5);
    int result = DurabilityShieldModule.onDamageTool(bar, tool, modifier, 3);
    assertThat(result).isZero();
    verify(bar).setAmount(tool, modifier, 5 - 3 * 1);
  }


  /* instance onDamageTool(tool, modifier, amount, holder) - resolves cost from the module's LevelingInt and the bar from the hook */

  @Test
  void onDamageTool_instance_fourArg_computesCostFromLevelingIntAndModifierLevel() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    ModifierEntry modifier = entryWithBar(bar); // level 2
    IToolStackView tool = mock(IToolStackView.class);
    when(bar.getAmount(tool)).thenReturn(100);
    // cost = eachLevel(3) at level 2 -> 6; canReduce = 100/6 = 16 >= amount (5)
    DurabilityShieldModule module = new DurabilityShieldModule(LevelingInt.eachLevel(3), 0xFFFFFF, ModifierPredicate.ANY);

    int result = module.onDamageTool(tool, modifier, 5, null);
    assertThat(result).isZero();
    verify(bar).setAmount(tool, modifier, 100 - 5 * 6);
  }


  /* instance onDamageTool(tool, modifier, amount, holder, stack, cause) - additionally gated by the cause predicate */

  @Test
  void onDamageTool_instance_sixArg_causeMatches_delegatesToBar() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    ModifierEntry modifier = entryWithBar(bar);
    IToolStackView tool = mock(IToolStackView.class);
    when(bar.getAmount(tool)).thenReturn(100);
    DurabilityShieldModule module = new DurabilityShieldModule(LevelingInt.ONE, 0xFFFFFF, ModifierPredicate.ANY);

    int result = module.onDamageTool(tool, modifier, 5, null, null, CAUSE);
    assertThat(result).isZero();
    verify(bar).setAmount(tool, modifier, 95);
  }

  @Test
  void onDamageTool_instance_sixArg_causeDoesNotMatch_leavesAmountAndBarUntouched() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    ModifierEntry modifier = entryWithBar(bar);
    IToolStackView tool = mock(IToolStackView.class);
    DurabilityShieldModule module = new DurabilityShieldModule(LevelingInt.ONE, 0xFFFFFF, ModifierPredicate.NONE);

    int result = module.onDamageTool(tool, modifier, 5, null, null, CAUSE);
    assertThat(result).isEqualTo(5);
    verify(bar, never()).setAmount(any(), any(), anyInt());
  }


  /* display hooks */

  @Test
  void getDurabilityWidth_noShield_returnsZero() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    ModifierEntry modifier = entryWithBar(bar);
    IToolStackView tool = mock(IToolStackView.class);
    when(bar.getAmount(tool)).thenReturn(0);
    DurabilityShieldModule module = new DurabilityShieldModule(0xFFFFFF);

    assertThat(module.getDurabilityWidth(tool, modifier)).isZero();
  }

  @Test
  void getDurabilityWidth_withShield_scalesBetweenOneAndThirteen() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    ModifierEntry modifier = entryWithBar(bar);
    IToolStackView tool = mock(IToolStackView.class);
    when(bar.getAmount(tool)).thenReturn(50);
    when(bar.getCapacity(tool, modifier)).thenReturn(100);
    DurabilityShieldModule module = new DurabilityShieldModule(0xFFFFFF);

    // matches DurabilityDisplayModifierHook#getWidthFor: 1 + (13 * (50-1) / 100) = 1 + 6 = 7
    assertThat(module.getDurabilityWidth(tool, modifier)).isEqualTo(7);
  }

  @Test
  void showDurabilityBar_withShield_isTrue() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    ModifierEntry modifier = entryWithBar(bar);
    IToolStackView tool = mock(IToolStackView.class);
    when(bar.getAmount(tool)).thenReturn(1);
    DurabilityShieldModule module = new DurabilityShieldModule(0xFFFFFF);

    assertThat(module.showDurabilityBar(tool, modifier)).isTrue();
  }

  @Test
  void showDurabilityBar_noShield_isNull() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    ModifierEntry modifier = entryWithBar(bar);
    IToolStackView tool = mock(IToolStackView.class);
    when(bar.getAmount(tool)).thenReturn(0);
    DurabilityShieldModule module = new DurabilityShieldModule(0xFFFFFF);

    assertThat(module.showDurabilityBar(tool, modifier)).isNull();
  }

  @Test
  void getDurabilityRGB_withShield_returnsConfiguredColor() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    ModifierEntry modifier = entryWithBar(bar);
    IToolStackView tool = mock(IToolStackView.class);
    when(bar.getAmount(tool)).thenReturn(1);
    DurabilityShieldModule module = new DurabilityShieldModule(0x00AAFF);

    assertThat(module.getDurabilityRGB(tool, modifier)).isEqualTo(0x00AAFF);
  }

  @Test
  void getDurabilityRGB_noShield_returnsNegativeOne() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    ModifierEntry modifier = entryWithBar(bar);
    IToolStackView tool = mock(IToolStackView.class);
    when(bar.getAmount(tool)).thenReturn(0);
    DurabilityShieldModule module = new DurabilityShieldModule(0x00AAFF);

    assertThat(module.getDurabilityRGB(tool, modifier)).isEqualTo(-1);
  }


  /* constructors */

  @Test
  void threeArgConstructor_setsAllFieldsDirectly() {
    DurabilityShieldModule module = new DurabilityShieldModule(LevelingInt.eachLevel(2), 0x123456, ModifierPredicate.ANY);
    assertThat(module.cost()).isEqualTo(LevelingInt.eachLevel(2));
    assertThat(module.color()).isEqualTo(0x123456);
    assertThat(module.cause()).isSameAs(ModifierPredicate.ANY);
  }

  @Test
  void twoArgConstructor_defaultsCostToOne() {
    DurabilityShieldModule module = new DurabilityShieldModule(0x123456, ModifierPredicate.NONE);
    assertThat(module.cost()).isEqualTo(LevelingInt.ONE);
    assertThat(module.cause()).isSameAs(ModifierPredicate.NONE);
  }

  @Test
  void oneArgConstructor_doesNotThrow_andDefaultsCostToOne() {
    DurabilityShieldModule module = new DurabilityShieldModule(0x123456);
    assertThat(module.cost()).isEqualTo(LevelingInt.ONE);
    assertThat(module.color()).isEqualTo(0x123456);
    assertThat(module.cause()).isNotNull();
  }


  /* loader */

  @Test
  void getLoader_returnsStaticLoader() {
    DurabilityShieldModule module = new DurabilityShieldModule(0xFFFFFF);
    assertThat(module.getLoader()).isSameAs(DurabilityShieldModule.LOADER);
  }

  @Test
  void loader_jsonRoundTrips_withNonDefaultCause() {
    // ColorLoadable.NO_ALPHA writes only 6 hex digits (RGB) but always forces full alpha back in on read, so the
    // input color must already be fully opaque (0xFF alpha) for the round trip to be an identity
    DurabilityShieldModule original = new DurabilityShieldModule(LevelingInt.eachLevel(4), 0xFFABCDEF, ModifierPredicate.NONE);
    JsonObject json = new JsonObject();
    DurabilityShieldModule.LOADER.serialize(original, json);
    DurabilityShieldModule parsed = DurabilityShieldModule.LOADER.deserialize(json);
    assertThat(parsed.cost()).isEqualTo(LevelingInt.eachLevel(4));
    assertThat(parsed.color()).isEqualTo(0xFFABCDEF);
    assertThat(parsed.cause().matches(CAUSE)).isFalse();
  }

  @Test
  void loader_networkRoundTrips_withNonDefaultCause() {
    DurabilityShieldModule original = new DurabilityShieldModule(LevelingInt.flat(2), 0x00FF00, ModifierPredicate.ANY);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    DurabilityShieldModule.LOADER.encode(buffer, original);
    DurabilityShieldModule decoded = DurabilityShieldModule.LOADER.decode(buffer);
    assertThat(decoded.cost()).isEqualTo(LevelingInt.flat(2));
    assertThat(decoded.color()).isEqualTo(0x00FF00);
    assertThat(decoded.cause().matches(CAUSE)).isTrue();
  }

  @Test
  void getDefaultHooks_hasToolDamageAndDurabilityDisplayHooks() {
    DurabilityShieldModule module = new DurabilityShieldModule(0xFFFFFF);
    assertThat(module.getDefaultHooks()).hasSize(2);
  }
}
