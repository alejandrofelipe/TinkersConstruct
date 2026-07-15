package slimeknights.tconstruct.library.modifiers.modules.build;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.json.LevelingInt;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.modules.util.ModifierCondition;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolDataNBT;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Tests {@link VolatileIntModule}: adds a leveled integer to volatile data, accumulating across multiple applications. */
class VolatileIntModuleTest extends BaseMcTest {
  private static final ResourceLocation FLAG = ResourceLocation.fromNamespaceAndPath("test", "int_flag");
  private static final ModifierId ID = new ModifierId("test", "foo");

  @Test
  void oneArgConstructor_usesAnyContextCondition() {
    VolatileIntModule module = new VolatileIntModule(FLAG, LevelingInt.ONE);
    assertThat(module.condition()).isEqualTo(ModifierCondition.ANY_CONTEXT);
  }

  @Test
  void addVolatileData_computesFlatPlusPerLevel() {
    // flat 2, +3 per level, at level 4 -> 2 + 3*4 = 14
    VolatileIntModule module = new VolatileIntModule(FLAG, new LevelingInt(2, 3));
    ToolDataNBT data = new ToolDataNBT();
    module.addVolatileData(mock(IToolContext.class), new ModifierEntry(ID, 4), data);
    assertThat(data.getInt(FLAG)).isEqualTo(14);
  }

  @Test
  void addVolatileData_accumulatesAcrossCalls() {
    VolatileIntModule module = new VolatileIntModule(FLAG, LevelingInt.flat(5));
    ToolDataNBT data = new ToolDataNBT();
    module.addVolatileData(mock(IToolContext.class), new ModifierEntry(ID, 1), data);
    module.addVolatileData(mock(IToolContext.class), new ModifierEntry(ID, 1), data);
    assertThat(data.getInt(FLAG)).isEqualTo(10);
  }

  @Test
  void addVolatileData_conditionFails_leavesValueAtZero() {
    VolatileIntModule module = new VolatileIntModule(FLAG, LevelingInt.flat(5), ModifierCondition.ANY_CONTEXT.minLevel(3));
    ToolDataNBT data = new ToolDataNBT();
    module.addVolatileData(mock(IToolContext.class), new ModifierEntry(ID, 1), data);
    assertThat(data.getInt(FLAG)).isZero();
  }

  @Test
  void onProjectileLaunch_conditionMatches_addsComputedAmount() {
    VolatileIntModule module = new VolatileIntModule(FLAG, LevelingInt.eachLevel(2), ModifierCondition.ANY_CONTEXT);
    ModDataNBT persistent = new ModDataNBT();
    module.onProjectileLaunch(mock(IToolStackView.class), new ModifierEntry(ID, 3), null, null, null, persistent, true);
    assertThat(persistent.getInt(FLAG)).isEqualTo(6);
  }

  @Test
  void loader_jsonRoundTrips() {
    VolatileIntModule original = new VolatileIntModule(FLAG, new LevelingInt(1, 2), ModifierCondition.ANY_CONTEXT);
    JsonObject json = new JsonObject();
    VolatileIntModule.LOADER.serialize(original, json);
    VolatileIntModule parsed = VolatileIntModule.LOADER.deserialize(json);

    ToolDataNBT data = new ToolDataNBT();
    parsed.addVolatileData(mock(IToolContext.class), new ModifierEntry(ID, 2), data);
    assertThat(data.getInt(FLAG)).isEqualTo(5); // 1 + 2*2
  }

  @Test
  void loader_networkRoundTrips() {
    VolatileIntModule original = new VolatileIntModule(FLAG, new LevelingInt(1, 2), ModifierCondition.ANY_CONTEXT);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    VolatileIntModule.LOADER.encode(buffer, original);
    VolatileIntModule decoded = VolatileIntModule.LOADER.decode(buffer);

    ToolDataNBT data = new ToolDataNBT();
    decoded.addVolatileData(mock(IToolContext.class), new ModifierEntry(ID, 2), data);
    assertThat(data.getInt(FLAG)).isEqualTo(5);
  }

  @Test
  void getLoader_returnsStaticLoader() {
    VolatileIntModule module = new VolatileIntModule(FLAG, LevelingInt.ONE);
    assertThat(module.getLoader()).isSameAs(VolatileIntModule.LOADER);
  }
}
