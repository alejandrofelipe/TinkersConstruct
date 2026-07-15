package slimeknights.tconstruct.library.modifiers.modules.build;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.json.LevelingValue;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.modules.util.ModifierCondition;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolDataNBT;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;

/** Tests {@link VolatileFloatModule}: adds a leveled float to volatile data, accumulating across multiple applications. */
class VolatileFloatModuleTest extends BaseMcTest {
  private static final ResourceLocation FLAG = ResourceLocation.fromNamespaceAndPath("test", "float_flag");
  private static final ModifierId ID = new ModifierId("test", "foo");

  @Test
  void oneArgConstructor_usesAnyContextCondition() {
    VolatileFloatModule module = new VolatileFloatModule(FLAG, LevelingValue.ONE);
    assertThat(module.condition()).isEqualTo(ModifierCondition.ANY_CONTEXT);
  }

  @Test
  void addVolatileData_computesFlatPlusPerLevel() {
    VolatileFloatModule module = new VolatileFloatModule(FLAG, new LevelingValue(1.5f, 0.5f));
    ToolDataNBT data = new ToolDataNBT();
    module.addVolatileData(mock(IToolContext.class), new ModifierEntry(ID, 4), data);
    // 1.5 + 0.5*4 = 3.5
    assertThat(data.getFloat(FLAG)).isCloseTo(3.5f, within(0.0001f));
  }

  @Test
  void addVolatileData_accumulatesAcrossCalls() {
    VolatileFloatModule module = new VolatileFloatModule(FLAG, LevelingValue.flat(0.25f));
    ToolDataNBT data = new ToolDataNBT();
    module.addVolatileData(mock(IToolContext.class), new ModifierEntry(ID, 1), data);
    module.addVolatileData(mock(IToolContext.class), new ModifierEntry(ID, 1), data);
    assertThat(data.getFloat(FLAG)).isCloseTo(0.5f, within(0.0001f));
  }

  @Test
  void addVolatileData_conditionFails_leavesValueAtZero() {
    VolatileFloatModule module = new VolatileFloatModule(FLAG, LevelingValue.flat(5f), ModifierCondition.ANY_CONTEXT.minLevel(3));
    ToolDataNBT data = new ToolDataNBT();
    module.addVolatileData(mock(IToolContext.class), new ModifierEntry(ID, 1), data);
    assertThat(data.getFloat(FLAG)).isZero();
  }

  @Test
  void onProjectileLaunch_conditionMatches_addsComputedAmount() {
    VolatileFloatModule module = new VolatileFloatModule(FLAG, LevelingValue.eachLevel(0.5f), ModifierCondition.ANY_CONTEXT);
    ModDataNBT persistent = new ModDataNBT();
    module.onProjectileLaunch(mock(IToolStackView.class), new ModifierEntry(ID, 3), null, null, null, persistent, true);
    assertThat(persistent.getFloat(FLAG)).isCloseTo(1.5f, within(0.0001f));
  }

  @Test
  void loader_jsonRoundTrips() {
    VolatileFloatModule original = new VolatileFloatModule(FLAG, new LevelingValue(1f, 2f), ModifierCondition.ANY_CONTEXT);
    JsonObject json = new JsonObject();
    VolatileFloatModule.LOADER.serialize(original, json);
    VolatileFloatModule parsed = VolatileFloatModule.LOADER.deserialize(json);

    ToolDataNBT data = new ToolDataNBT();
    parsed.addVolatileData(mock(IToolContext.class), new ModifierEntry(ID, 2), data);
    assertThat(data.getFloat(FLAG)).isCloseTo(5f, within(0.0001f));
  }

  @Test
  void loader_networkRoundTrips() {
    VolatileFloatModule original = new VolatileFloatModule(FLAG, new LevelingValue(1f, 2f), ModifierCondition.ANY_CONTEXT);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    VolatileFloatModule.LOADER.encode(buffer, original);
    VolatileFloatModule decoded = VolatileFloatModule.LOADER.decode(buffer);

    ToolDataNBT data = new ToolDataNBT();
    decoded.addVolatileData(mock(IToolContext.class), new ModifierEntry(ID, 2), data);
    assertThat(data.getFloat(FLAG)).isCloseTo(5f, within(0.0001f));
  }

  @Test
  void getLoader_returnsStaticLoader() {
    VolatileFloatModule module = new VolatileFloatModule(FLAG, LevelingValue.ONE);
    assertThat(module.getLoader()).isSameAs(VolatileFloatModule.LOADER);
  }
}
