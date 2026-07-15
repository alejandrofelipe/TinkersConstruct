package slimeknights.tconstruct.library.modifiers.modules.capacity;

import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.StatsNBT;
import slimeknights.tconstruct.test.BaseMcTest;
import slimeknights.tconstruct.tools.TinkerModifiers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link OverslimeModule}: the singleton overslime shield bar, including its "overworked" restoration bonus
 * (+100% per level of the overworked modifier) which is unique to this bar implementation.
 */
class OverslimeModuleTest extends BaseMcTest {
  private static IToolStackView mockTool(ModDataNBT persistentData, int capacity, int overworkedLevel) {
    IToolStackView tool = mock(IToolStackView.class);
    when(tool.getPersistentData()).thenReturn(persistentData);
    StatsNBT stats = mock(StatsNBT.class);
    when(stats.getInt(OverslimeModule.OVERSLIME_STAT)).thenReturn(capacity);
    when(tool.getStats()).thenReturn(stats);
    when(tool.getModifierLevel(TinkerModifiers.overworked.getId())).thenReturn(overworkedLevel);
    return tool;
  }

  @Test
  void setAmountRaw_positiveAmount_isReadableViaGetAmount() {
    ModDataNBT data = new ModDataNBT();
    IToolStackView tool = mockTool(data, 100, 0);
    OverslimeModule.INSTANCE.setAmountRaw(data, 42);
    assertThat(OverslimeModule.INSTANCE.getAmount(tool)).isEqualTo(42);
  }

  @Test
  void setAmountRaw_zeroOrNegative_clearsAmount() {
    ModDataNBT data = new ModDataNBT();
    IToolStackView tool = mockTool(data, 100, 0);
    OverslimeModule.INSTANCE.setAmountRaw(data, 42);
    OverslimeModule.INSTANCE.setAmountRaw(data, 0);
    assertThat(OverslimeModule.INSTANCE.getAmount(tool)).isZero();

    OverslimeModule.INSTANCE.setAmountRaw(data, 42);
    OverslimeModule.INSTANCE.setAmountRaw(data, -5);
    assertThat(OverslimeModule.INSTANCE.getAmount(tool)).isZero();
  }

  @Test
  void getCapacity_static_readsOverslimeStat() {
    IToolStackView tool = mockTool(new ModDataNBT(), 250, 0);
    assertThat(OverslimeModule.getCapacity(tool)).isEqualTo(250);
  }

  @Test
  void getCapacity_instanceOverload_matchesStaticHelper() {
    IToolStackView tool = mockTool(new ModDataNBT(), 250, 0);
    assertThat(OverslimeModule.INSTANCE.getCapacity(tool, ModifierEntry.EMPTY)).isEqualTo(250);
  }

  @Test
  void getOverworkedBonus_noLevels_isOne() {
    IToolStackView tool = mockTool(new ModDataNBT(), 100, 0);
    assertThat(OverslimeModule.getOverworkedBonus(tool)).isEqualTo(1);
  }

  @Test
  void getOverworkedBonus_threeLevels_isFour() {
    IToolStackView tool = mockTool(new ModDataNBT(), 100, 3);
    assertThat(OverslimeModule.getOverworkedBonus(tool)).isEqualTo(4);
  }

  @Test
  void addAmount_withEntry_scalesByOverworkedBonus() {
    ModDataNBT data = new ModDataNBT();
    IToolStackView tool = mockTool(data, 1000, 1); // bonus = 2
    OverslimeModule.INSTANCE.addAmount(tool, ModifierEntry.EMPTY, 10);
    assertThat(OverslimeModule.INSTANCE.getAmount(tool)).isEqualTo(20);
  }

  @Test
  void addAmount_withEntry_clampsToCapacityAfterBonus() {
    ModDataNBT data = new ModDataNBT();
    IToolStackView tool = mockTool(data, 15, 1); // bonus = 2, 10*2=20 > capacity 15
    OverslimeModule.INSTANCE.addAmount(tool, ModifierEntry.EMPTY, 10);
    assertThat(OverslimeModule.INSTANCE.getAmount(tool)).isEqualTo(15);
  }

  @Test
  void addAmount_twoArgHelper_delegatesWithEmptyEntry() {
    ModDataNBT data = new ModDataNBT();
    IToolStackView tool = mockTool(data, 1000, 0); // bonus = 1
    OverslimeModule.INSTANCE.addAmount(tool, 10);
    assertThat(OverslimeModule.INSTANCE.getAmount(tool)).isEqualTo(10);
  }

  @Test
  void removeAmount_doesNotApplyOverworkedBonus() {
    ModDataNBT data = new ModDataNBT();
    IToolStackView tool = mockTool(data, 1000, 5); // bonus would be 6 if (wrongly) applied
    OverslimeModule.INSTANCE.setAmountRaw(data, 50);
    OverslimeModule.INSTANCE.removeAmount(tool, 20);
    assertThat(OverslimeModule.INSTANCE.getAmount(tool)).isEqualTo(30);
  }

  @Test
  void getDefaultHooks_containsCapacityBar() {
    assertThat(OverslimeModule.INSTANCE.getDefaultHooks()).hasSize(1);
  }
}
