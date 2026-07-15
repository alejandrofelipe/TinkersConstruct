package slimeknights.tconstruct.library.modifiers.modules.capacity;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.data.predicate.damage.DamageSourcePredicate;
import slimeknights.tconstruct.library.json.LevelingValue;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierFixture;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.special.CapacityBarHook;
import slimeknights.tconstruct.library.modifiers.modules.util.ModifierCondition;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests {@link DamageToCapacityModule}: converts incoming damage into capacity bar charge, up to the bar's
 * remaining headroom, optionally reducing the damage the holder actually takes by the absorbed amount.
 */
class DamageToCapacityModuleTest extends BaseMcTest {
  @BeforeAll
  static void setup() {
    ModifierFixture.init();
  }

  private static final DamageSource SOURCE = mock(DamageSource.class);

  private static ModifierEntry entryWithBar(CapacityBarHook bar) {
    Modifier modifier = ModifierFixture.withHooks(ModuleHookMap.builder().addHook(bar, ModifierHooks.CAPACITY_BAR).build());
    return new ModifierEntry(modifier, 1);
  }

  @Test
  void modifyDamageTaken_conditionFails_leavesBarAndAmountUntouched() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    ModifierEntry entry = entryWithBar(bar);
    DamageToCapacityModule module = new DamageToCapacityModule(DamageSourcePredicate.ANY, LevelingValue.ONE, true, null, ModifierCondition.ANY_TOOL.minLevel(5));

    float result = module.modifyDamageTaken(mock(IToolStackView.class), entry, null, EquipmentSlot.MAINHAND, SOURCE, 30f, true);
    assertThat(result).isEqualTo(30f);
    verify(bar, never()).setAmount(any(IToolStackView.class), any(ModifierEntry.class), anyInt());
  }

  @Test
  void modifyDamageTaken_sourceDoesNotMatch_leavesBarAndAmountUntouched() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    ModifierEntry entry = entryWithBar(bar);
    DamageToCapacityModule module = new DamageToCapacityModule(DamageSourcePredicate.NONE, LevelingValue.ONE, true, null, ModifierCondition.ANY_TOOL);

    float result = module.modifyDamageTaken(mock(IToolStackView.class), entry, null, EquipmentSlot.MAINHAND, SOURCE, 30f, true);
    assertThat(result).isEqualTo(30f);
    verify(bar, never()).setAmount(any(IToolStackView.class), any(ModifierEntry.class), anyInt());
  }

  @Test
  void modifyDamageTaken_barAlreadyFull_noAbsorption() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    ModifierEntry entry = entryWithBar(bar);
    when(bar.getAmount(any())).thenReturn(100);
    when(bar.getCapacity(any(), eq(entry))).thenReturn(100);
    DamageToCapacityModule module = new DamageToCapacityModule(DamageSourcePredicate.ANY, LevelingValue.ONE, true, null, ModifierCondition.ANY_TOOL);

    float result = module.modifyDamageTaken(mock(IToolStackView.class), entry, null, EquipmentSlot.MAINHAND, SOURCE, 30f, true);
    assertThat(result).isEqualTo(30f);
    verify(bar, never()).setAmount(any(IToolStackView.class), any(ModifierEntry.class), anyInt());
  }

  @Test
  void modifyDamageTaken_fullyAbsorbed_reduceDamageTrue_zeroesOutDamage() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    IToolStackView tool = mock(IToolStackView.class);
    ModifierEntry entry = entryWithBar(bar);
    when(bar.getAmount(tool)).thenReturn(0);
    when(bar.getCapacity(tool, entry)).thenReturn(100);
    DamageToCapacityModule module = new DamageToCapacityModule(DamageSourcePredicate.ANY, LevelingValue.ONE, true, null, ModifierCondition.ANY_TOOL);

    float result = module.modifyDamageTaken(tool, entry, null, EquipmentSlot.MAINHAND, SOURCE, 30f, true);
    verify(bar).setAmount(tool, entry, 30);
    assertThat(result).isEqualTo(0f);
  }

  @Test
  void modifyDamageTaken_fullyAbsorbed_reduceDamageFalse_leavesDamageUnchanged() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    IToolStackView tool = mock(IToolStackView.class);
    ModifierEntry entry = entryWithBar(bar);
    when(bar.getAmount(tool)).thenReturn(0);
    when(bar.getCapacity(tool, entry)).thenReturn(100);
    DamageToCapacityModule module = new DamageToCapacityModule(DamageSourcePredicate.ANY, LevelingValue.ONE, false, null, ModifierCondition.ANY_TOOL);

    float result = module.modifyDamageTaken(tool, entry, null, EquipmentSlot.MAINHAND, SOURCE, 30f, true);
    verify(bar).setAmount(tool, entry, 30);
    assertThat(result).isEqualTo(30f);
  }

  @Test
  void modifyDamageTaken_absorptionCappedByRemainingHeadroom() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    IToolStackView tool = mock(IToolStackView.class);
    ModifierEntry entry = entryWithBar(bar);
    when(bar.getAmount(tool)).thenReturn(90);
    when(bar.getCapacity(tool, entry)).thenReturn(100);
    DamageToCapacityModule module = new DamageToCapacityModule(DamageSourcePredicate.ANY, LevelingValue.ONE, true, null, ModifierCondition.ANY_TOOL);

    // only 10 headroom left even though 50 damage comes in
    float result = module.modifyDamageTaken(tool, entry, null, EquipmentSlot.MAINHAND, SOURCE, 50f, true);
    verify(bar).setAmount(tool, entry, 100);
    assertThat(result).isEqualTo(40f);
  }

  @Test
  void modifyDamageTaken_multiplierScalesAndRoundsUp() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    IToolStackView tool = mock(IToolStackView.class);
    ModifierEntry entry = entryWithBar(bar);
    when(bar.getAmount(tool)).thenReturn(0);
    when(bar.getCapacity(tool, entry)).thenReturn(100);
    // 7 damage * 0.5 multiplier = 3.5, ceil'd up to 4
    DamageToCapacityModule module = new DamageToCapacityModule(DamageSourcePredicate.ANY, LevelingValue.flat(0.5f), true, null, ModifierCondition.ANY_TOOL);

    module.modifyDamageTaken(tool, entry, null, EquipmentSlot.MAINHAND, SOURCE, 7f, true);
    verify(bar).setAmount(tool, entry, 4);
  }

  @Test
  void builder_wiresSourceReduceDamageAndAmount() {
    ModifierId owner = new ModifierId("test", "owner");
    DamageToCapacityModule module = DamageToCapacityModule.source(DamageSourcePredicate.NONE)
      .reduceDamage()
      .owner(owner)
      .amount(2f, 3f);
    assertThat(module.source()).isSameAs(DamageSourcePredicate.NONE);
    assertThat(module.reduceDamage()).isTrue();
    assertThat(module.owner()).isEqualTo(owner);
    assertThat(module.multiplier()).isEqualTo(new LevelingValue(2f, 3f));
  }

  @Test
  void builder_withoutReduceDamage_defaultsFalse() {
    DamageToCapacityModule module = DamageToCapacityModule.source(DamageSourcePredicate.ANY).flat(1f);
    assertThat(module.reduceDamage()).isFalse();
  }

  @Test
  void getLoader_returnsStaticLoader() {
    DamageToCapacityModule module = new DamageToCapacityModule(DamageSourcePredicate.ANY, LevelingValue.ONE, false, null, ModifierCondition.ANY_TOOL);
    assertThat(module.getLoader()).isSameAs(DamageToCapacityModule.LOADER);
  }

  @Test
  void loader_jsonRoundTrips() {
    DamageToCapacityModule original = new DamageToCapacityModule(DamageSourcePredicate.ANY, new LevelingValue(1f, 0.5f), true, null, ModifierCondition.ANY_TOOL);
    JsonObject json = new JsonObject();
    DamageToCapacityModule.LOADER.serialize(original, json);
    DamageToCapacityModule parsed = DamageToCapacityModule.LOADER.deserialize(json);
    assertThat(parsed.reduceDamage()).isTrue();
    assertThat(parsed.multiplier()).isEqualTo(new LevelingValue(1f, 0.5f));
    assertThat(parsed.source().matches(SOURCE)).isTrue();
  }

  @Test
  void loader_networkRoundTrips() {
    DamageToCapacityModule original = new DamageToCapacityModule(DamageSourcePredicate.NONE, LevelingValue.flat(2f), false, null, ModifierCondition.ANY_TOOL);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    DamageToCapacityModule.LOADER.encode(buffer, original);
    DamageToCapacityModule decoded = DamageToCapacityModule.LOADER.decode(buffer);
    assertThat(decoded.reduceDamage()).isFalse();
    assertThat(decoded.source().matches(SOURCE)).isFalse();
  }
}
