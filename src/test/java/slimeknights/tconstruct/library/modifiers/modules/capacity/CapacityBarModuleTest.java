package slimeknights.tconstruct.library.modifiers.modules.capacity;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.json.LevelingInt;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.ModifierManager;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.stat.INumericToolStat;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link CapacityBarModule}: the standard capacity bar whose capacity is a {@link LevelingInt} optionally
 * scaled by a numeric tool stat multiplier (e.g. the durability global multiplier).
 */
class CapacityBarModuleTest extends BaseMcTest {
  private static IToolStackView mockToolWithData(ModDataNBT data) {
    IToolStackView tool = mock(IToolStackView.class);
    when(tool.getPersistentData()).thenReturn(data);
    return tool;
  }

  @Test
  void getCapacity_noMultiplier_computesLeveledCapacity() {
    CapacityBarModule module = new CapacityBarModule(new LevelingInt(10, 5), null);
    // flat 10 + eachLevel 5 * level 3 = 25
    assertThat(module.getCapacity(mock(IToolStackView.class), new ModifierEntry(new ModifierId("test", "foo"), 3))).isEqualTo(25);
  }

  @Test
  void getCapacity_withMultiplier_scalesByToolMultiplier() {
    @SuppressWarnings("unchecked")
    INumericToolStat<Float> multiplier = mock(INumericToolStat.class);
    CapacityBarModule module = new CapacityBarModule(LevelingInt.flat(10), multiplier);
    IToolStackView tool = mock(IToolStackView.class);
    when(tool.getMultiplier(multiplier)).thenReturn(2.5f);
    assertThat(module.getCapacity(tool, new ModifierEntry(new ModifierId("test", "foo"), 1))).isEqualTo(25);
  }

  @Test
  void datagenConstructor_usesEmptyModifierLocationAsKey() {
    CapacityBarModule module = new CapacityBarModule(LevelingInt.flat(50), null);
    ModDataNBT data = new ModDataNBT();
    IToolStackView tool = mockToolWithData(data);
    module.setAmount(tool, ModifierEntry.EMPTY, 30);
    assertThat(data.getInt(ModifierManager.EMPTY.getLocation())).isEqualTo(30);
  }

  @Test
  void setAmount_withinCapacity_isStoredExactly() {
    CapacityBarModule module = new CapacityBarModule(ModifierManager.EMPTY.getLocation(), LevelingInt.flat(100), null);
    ModDataNBT data = new ModDataNBT();
    IToolStackView tool = mockToolWithData(data);
    module.setAmount(tool, ModifierEntry.EMPTY, 50);
    assertThat(module.getAmount(tool)).isEqualTo(50);
  }

  @Test
  void setAmount_negativeOrZero_clearsAmount() {
    CapacityBarModule module = new CapacityBarModule(ModifierManager.EMPTY.getLocation(), LevelingInt.flat(100), null);
    ModDataNBT data = new ModDataNBT();
    IToolStackView tool = mockToolWithData(data);
    module.setAmount(tool, ModifierEntry.EMPTY, 50);
    module.setAmount(tool, ModifierEntry.EMPTY, 0);
    assertThat(module.getAmount(tool)).isZero();
  }

  @Test
  void setAmount_aboveCapacity_clampsToCapacity() {
    CapacityBarModule module = new CapacityBarModule(ModifierManager.EMPTY.getLocation(), LevelingInt.flat(100), null);
    ModDataNBT data = new ModDataNBT();
    IToolStackView tool = mockToolWithData(data);
    module.setAmount(tool, ModifierEntry.EMPTY, 500);
    assertThat(module.getAmount(tool)).isEqualTo(100);
  }

  @Test
  void getLoader_returnsStaticLoader() {
    CapacityBarModule module = new CapacityBarModule(LevelingInt.flat(1), null);
    assertThat(module.getLoader()).isSameAs(CapacityBarModule.LOADER);
  }

  @Test
  void loader_jsonRoundTrips_capacityAndMultiplier() {
    CapacityBarModule original = new CapacityBarModule(ModifierManager.EMPTY.getLocation(), new LevelingInt(3, 2), null);
    JsonObject json = new JsonObject();
    CapacityBarModule.LOADER.serialize(original, json);
    CapacityBarModule parsed = CapacityBarModule.LOADER.deserialize(json);
    // flat 3 + eachLevel 2 * level 4 = 11, verified indirectly through getCapacity since fields aren't exposed
    assertThat(parsed.getCapacity(mock(IToolStackView.class), new ModifierEntry(new ModifierId("test", "foo"), 4))).isEqualTo(11);
  }
}
