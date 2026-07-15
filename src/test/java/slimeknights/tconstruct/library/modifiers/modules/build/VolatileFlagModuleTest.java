package slimeknights.tconstruct.library.modifiers.modules.build;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
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

/** Tests {@link VolatileFlagModule}: sets a boolean volatile-data flag when its {@link ModifierCondition} matches. */
class VolatileFlagModuleTest extends BaseMcTest {
  private static final ResourceLocation FLAG = ResourceLocation.fromNamespaceAndPath("test", "flag");
  private static final ModifierEntry LOW = new ModifierEntry(new ModifierId("test", "foo"), 1);
  private static final ModifierEntry HIGH = new ModifierEntry(new ModifierId("test", "foo"), 5);

  @Test
  void oneArgConstructor_usesAnyContextCondition() {
    VolatileFlagModule module = new VolatileFlagModule(FLAG);
    assertThat(module.condition()).isEqualTo(ModifierCondition.ANY_CONTEXT);
  }

  @Test
  void addVolatileData_conditionMatches_setsFlagTrue() {
    VolatileFlagModule module = new VolatileFlagModule(FLAG, ModifierCondition.ANY_CONTEXT.minLevel(3));
    ToolDataNBT data = new ToolDataNBT();
    module.addVolatileData(mock(IToolContext.class), HIGH, data);
    assertThat(data.getBoolean(FLAG)).isTrue();
  }

  @Test
  void addVolatileData_conditionFails_leavesFlagUnset() {
    VolatileFlagModule module = new VolatileFlagModule(FLAG, ModifierCondition.ANY_CONTEXT.minLevel(3));
    ToolDataNBT data = new ToolDataNBT();
    module.addVolatileData(mock(IToolContext.class), LOW, data);
    assertThat(data.getBoolean(FLAG)).isFalse();
  }

  @Test
  void onProjectileLaunch_conditionMatches_setsFlagTrue() {
    // condition is declared ModifierCondition<IToolContext> even though this hook receives an IToolStackView
    // (IToolStackView extends IToolContext), so ANY_CONTEXT is the correct fixture here, not ANY_TOOL.
    VolatileFlagModule module = new VolatileFlagModule(FLAG, ModifierCondition.ANY_CONTEXT.minLevel(3));
    ModDataNBT persistent = new ModDataNBT();
    module.onProjectileLaunch(mock(IToolStackView.class), HIGH, null, null, null, persistent, true);
    assertThat(persistent.getBoolean(FLAG)).isTrue();
  }

  @Test
  void onProjectileLaunch_conditionFails_leavesFlagUnset() {
    VolatileFlagModule module = new VolatileFlagModule(FLAG, ModifierCondition.ANY_CONTEXT.minLevel(3));
    ModDataNBT persistent = new ModDataNBT();
    module.onProjectileLaunch(mock(IToolStackView.class), LOW, null, null, null, persistent, true);
    assertThat(persistent.getBoolean(FLAG)).isFalse();
  }

  @Test
  void loader_jsonRoundTrips() {
    VolatileFlagModule original = new VolatileFlagModule(FLAG, ModifierCondition.ANY_CONTEXT.minLevel(3));
    JsonObject json = new JsonObject();
    VolatileFlagModule.LOADER.serialize(original, json);
    VolatileFlagModule parsed = VolatileFlagModule.LOADER.deserialize(json);

    ToolDataNBT passing = new ToolDataNBT();
    parsed.addVolatileData(mock(IToolContext.class), HIGH, passing);
    assertThat(passing.getBoolean(FLAG)).isTrue();

    ToolDataNBT failing = new ToolDataNBT();
    parsed.addVolatileData(mock(IToolContext.class), LOW, failing);
    assertThat(failing.getBoolean(FLAG)).isFalse();
  }

  @Test
  void loader_networkRoundTrips() {
    VolatileFlagModule original = new VolatileFlagModule(FLAG, ModifierCondition.ANY_CONTEXT.minLevel(3));
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    VolatileFlagModule.LOADER.encode(buffer, original);
    VolatileFlagModule decoded = VolatileFlagModule.LOADER.decode(buffer);

    ToolDataNBT passing = new ToolDataNBT();
    decoded.addVolatileData(mock(IToolContext.class), HIGH, passing);
    assertThat(passing.getBoolean(FLAG)).isTrue();
  }

  @Test
  void getLoader_returnsStaticLoader() {
    VolatileFlagModule module = new VolatileFlagModule(FLAG);
    assertThat(module.getLoader()).isSameAs(VolatileFlagModule.LOADER);
  }
}
