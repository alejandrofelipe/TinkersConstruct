package slimeknights.tconstruct.library.modifiers.modules.capacity;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.json.LevelingValue;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierFixture;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.special.CapacityBarHook;
import slimeknights.tconstruct.library.modifiers.modules.util.ModifierCondition;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests {@link TimeToCapacityModule}: a thin {@code OvergrowthModule} specialization that redirects the inherited
 * per-second restore chance onto the modifier's own {@link ModifierHooks#CAPACITY_BAR} hook instead of the
 * overslime singleton. {@code onInventoryTick} itself lives on {@code OvergrowthModule} (a different package,
 * outside this task's {@code library/} scope) and needs a live {@code Level}, so it's not exercised here -
 * only the members {@code TimeToCapacityModule} itself declares/overrides.
 */
class TimeToCapacityModuleTest extends BaseMcTest {
  @BeforeAll
  static void setup() {
    ModifierFixture.init();
  }

  @Test
  void constructor_delegatesChanceAndConditionToSuper() {
    ModifierCondition<IToolStackView> condition = ModifierCondition.ANY_TOOL.minLevel(2);
    TimeToCapacityModule module = new TimeToCapacityModule(LevelingValue.flat(0.25f), condition);
    assertThat(module.chance()).isEqualTo(LevelingValue.flat(0.25f));
    assertThat(module.condition()).isEqualTo(condition);
  }

  @Test
  void getBar_resolvesModifierOwnCapacityBarHook() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    Modifier modifier = ModifierFixture.withHooks(ModuleHookMap.builder().addHook(bar, ModifierHooks.CAPACITY_BAR).build());
    ModifierEntry entry = new ModifierEntry(modifier, 1);

    TimeToCapacityModule module = new TimeToCapacityModule(LevelingValue.ONE, ModifierCondition.ANY_TOOL);
    assertThat(module.getBar(entry)).isSameAs(bar);
  }

  @Test
  void getLoader_returnsStaticLoader() {
    TimeToCapacityModule module = new TimeToCapacityModule(LevelingValue.ONE, ModifierCondition.ANY_TOOL);
    assertThat(module.getLoader()).isSameAs(TimeToCapacityModule.LOADER);
  }

  @Test
  void loader_jsonRoundTrips() {
    TimeToCapacityModule original = new TimeToCapacityModule(new LevelingValue(0.1f, 0.05f), ModifierCondition.ANY_TOOL.minLevel(2));
    JsonObject json = new JsonObject();
    TimeToCapacityModule.LOADER.serialize(original, json);
    TimeToCapacityModule parsed = TimeToCapacityModule.LOADER.deserialize(json);
    assertThat(parsed.chance()).isEqualTo(new LevelingValue(0.1f, 0.05f));
    assertThat(parsed.condition()).isEqualTo(ModifierCondition.ANY_TOOL.minLevel(2));
  }

  @Test
  void loader_networkRoundTrips() {
    TimeToCapacityModule original = new TimeToCapacityModule(LevelingValue.flat(0.5f), ModifierCondition.ANY_TOOL);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    TimeToCapacityModule.LOADER.encode(buffer, original);
    TimeToCapacityModule decoded = TimeToCapacityModule.LOADER.decode(buffer);
    assertThat(decoded.chance()).isEqualTo(LevelingValue.flat(0.5f));
  }
}
