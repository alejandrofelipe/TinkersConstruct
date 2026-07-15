package slimeknights.tconstruct.library.modifiers.modules.build;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.json.LevelingInt;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.modules.util.ModifierCondition;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.ToolDataNBT;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Tests {@link ModifierSlotModule}: grants a leveled count of extra modifier slots of a given type. */
class ModifierSlotModuleTest extends BaseMcTest {
  private static final SlotType TYPE = SlotType.getOrCreate("p2t5_modifier_slot");
  private static final ModifierId ID = new ModifierId("test", "foo");

  @Test
  void addVolatileData_conditionMatches_addsComputedSlotCount() {
    // flat 1, +2 per level, at level 3 -> 1 + 2*3 = 7
    ModifierSlotModule module = new ModifierSlotModule(TYPE, new LevelingInt(1, 2), ModifierCondition.ANY_CONTEXT);
    ToolDataNBT data = new ToolDataNBT();
    module.addVolatileData(mock(IToolContext.class), new ModifierEntry(ID, 3), data);
    assertThat(data.getSlots(TYPE)).isEqualTo(7);
  }

  @Test
  void addVolatileData_conditionFails_addsNothing() {
    ModifierSlotModule module = new ModifierSlotModule(TYPE, LevelingInt.flat(5), ModifierCondition.ANY_CONTEXT.minLevel(3));
    ToolDataNBT data = new ToolDataNBT();
    module.addVolatileData(mock(IToolContext.class), new ModifierEntry(ID, 1), data);
    assertThat(data.getSlots(TYPE)).isZero();
  }

  @Test
  void getPriority_isFifty() {
    ModifierSlotModule module = new ModifierSlotModule(TYPE, LevelingInt.ONE, ModifierCondition.ANY_CONTEXT);
    assertThat(module.getPriority()).isEqualTo(50);
  }

  @Test
  void getDefaultHooks_hasVolatileDataHook() {
    ModifierSlotModule module = new ModifierSlotModule(TYPE, LevelingInt.ONE, ModifierCondition.ANY_CONTEXT);
    assertThat(module.getDefaultHooks()).hasSize(1);
  }

  @Test
  void threeArgDeprecatedConstructor_convertsFlatCountToEachLevel() {
    ModifierSlotModule module = new ModifierSlotModule(TYPE, 4, ModifierCondition.ANY_CONTEXT);
    ToolDataNBT data = new ToolDataNBT();
    module.addVolatileData(mock(IToolContext.class), new ModifierEntry(ID, 2), data);
    assertThat(data.getSlots(TYPE)).isEqualTo(8); // eachLevel(4) at level 2 -> 4*2=8
  }

  @Test
  void twoArgDeprecatedConstructor_defaultsToAnyContextCondition() {
    ModifierSlotModule module = new ModifierSlotModule(TYPE, 4);
    assertThat(module.condition()).isEqualTo(ModifierCondition.ANY_CONTEXT);
  }

  @Test
  void oneArgDeprecatedConstructor_defaultsCountToOneEachLevel() {
    ModifierSlotModule module = new ModifierSlotModule(TYPE);
    ToolDataNBT data = new ToolDataNBT();
    module.addVolatileData(mock(IToolContext.class), new ModifierEntry(ID, 5), data);
    assertThat(data.getSlots(TYPE)).isEqualTo(5); // eachLevel(1) at level 5 -> 5
  }

  @Test
  void slotBuilder_amount_buildsModuleWithLevelingInt() {
    ModifierSlotModule module = ModifierSlotModule.slot(TYPE).amount(2, 3);
    ToolDataNBT data = new ToolDataNBT();
    module.addVolatileData(mock(IToolContext.class), new ModifierEntry(ID, 2), data);
    assertThat(data.getSlots(TYPE)).isEqualTo(8); // 2 + 3*2 = 8
  }

  @Test
  void getLoader_returnsStaticLoader() {
    ModifierSlotModule module = new ModifierSlotModule(TYPE, LevelingInt.ONE, ModifierCondition.ANY_CONTEXT);
    assertThat(module.getLoader()).isSameAs(ModifierSlotModule.LOADER);
  }

  @Test
  void loader_jsonRoundTrips() {
    ModifierSlotModule original = new ModifierSlotModule(TYPE, new LevelingInt(1, 2), ModifierCondition.ANY_CONTEXT);
    JsonObject json = new JsonObject();
    ModifierSlotModule.LOADER.serialize(original, json);
    ModifierSlotModule parsed = ModifierSlotModule.LOADER.deserialize(json);

    ToolDataNBT data = new ToolDataNBT();
    parsed.addVolatileData(mock(IToolContext.class), new ModifierEntry(ID, 2), data);
    assertThat(data.getSlots(TYPE)).isEqualTo(5); // 1 + 2*2
  }

  @Test
  void loader_networkRoundTrips() {
    ModifierSlotModule original = new ModifierSlotModule(TYPE, new LevelingInt(1, 2), ModifierCondition.ANY_CONTEXT);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    ModifierSlotModule.LOADER.encode(buffer, original);
    ModifierSlotModule decoded = ModifierSlotModule.LOADER.decode(buffer);

    ToolDataNBT data = new ToolDataNBT();
    decoded.addVolatileData(mock(IToolContext.class), new ModifierEntry(ID, 2), data);
    assertThat(data.getSlots(TYPE)).isEqualTo(5);
  }
}
