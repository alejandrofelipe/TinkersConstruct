package slimeknights.tconstruct.library.modifiers.modules.capacity;

import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.capability.ToolEnergyCapability;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.StatsNBT;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link EnergyAsCapacityModule}: a capacity bar hook that delegates entirely to {@link ToolEnergyCapability}'s
 * tool-stat/persistent-data backed energy storage rather than owning its own bar state.
 */
class EnergyAsCapacityModuleTest extends BaseMcTest {
  private static IToolStackView mockTool(ModDataNBT persistentData, int maxEnergy) {
    IToolStackView tool = mock(IToolStackView.class);
    when(tool.getPersistentData()).thenReturn(persistentData);
    StatsNBT stats = mock(StatsNBT.class);
    when(stats.getInt(ToolEnergyCapability.MAX_STAT)).thenReturn(maxEnergy);
    when(tool.getStats()).thenReturn(stats);
    return tool;
  }

  @Test
  void getAmount_delegatesToToolEnergyCapability() {
    IToolStackView tool = mockTool(new ModDataNBT(), 1000);
    ToolEnergyCapability.setEnergy(tool, 42);
    assertThat(EnergyAsCapacityModule.INSTANCE.getAmount(tool)).isEqualTo(42);
  }

  @Test
  void getCapacity_readsMaxEnergyStat() {
    IToolStackView tool = mockTool(new ModDataNBT(), 500);
    assertThat(EnergyAsCapacityModule.INSTANCE.getCapacity(tool, ModifierEntry.EMPTY)).isEqualTo(500);
  }

  @Test
  void setAmount_clampsToMaxEnergy() {
    IToolStackView tool = mockTool(new ModDataNBT(), 100);
    EnergyAsCapacityModule.INSTANCE.setAmount(tool, ModifierEntry.EMPTY, 250);
    assertThat(ToolEnergyCapability.getEnergy(tool)).isEqualTo(100);
  }

  @Test
  void setAmount_clampsBelowZero() {
    IToolStackView tool = mockTool(new ModDataNBT(), 100);
    EnergyAsCapacityModule.INSTANCE.setAmount(tool, ModifierEntry.EMPTY, -10);
    assertThat(ToolEnergyCapability.getEnergy(tool)).isZero();
  }

  @Test
  void addAmount_addsToExistingEnergy() {
    IToolStackView tool = mockTool(new ModDataNBT(), 1000);
    ToolEnergyCapability.setEnergy(tool, 10);
    EnergyAsCapacityModule.INSTANCE.addAmount(tool, ModifierEntry.EMPTY, 15);
    assertThat(ToolEnergyCapability.getEnergy(tool)).isEqualTo(25);
  }

  @Test
  void removeAmount_subtractsFromExistingEnergy() {
    IToolStackView tool = mockTool(new ModDataNBT(), 1000);
    ToolEnergyCapability.setEnergy(tool, 30);
    EnergyAsCapacityModule.INSTANCE.removeAmount(tool, ModifierEntry.EMPTY, 12);
    assertThat(ToolEnergyCapability.getEnergy(tool)).isEqualTo(18);
  }

  @Test
  void getLoader_returnsSingletonLoaderForInstance() {
    assertThat(EnergyAsCapacityModule.INSTANCE.getLoader()).isSameAs(EnergyAsCapacityModule.LOADER);
  }

  @Test
  void getDefaultHooks_containsCapacityBar() {
    assertThat(EnergyAsCapacityModule.INSTANCE.getDefaultHooks()).hasSize(1);
  }
}
