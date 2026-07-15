package slimeknights.tconstruct.library.modifiers.modules.build;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.ItemTags;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierFixture;
import slimeknights.tconstruct.library.modifiers.hook.build.ModifierTraitHook.TraitBuilder;
import slimeknights.tconstruct.library.modifiers.modules.util.ModifierCondition;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Tests {@link ModifierTraitModule}: adds a nested modifier as a trait, with fixed-level vs. level-scaling semantics. */
class ModifierTraitModuleTest extends BaseMcTest {
  @BeforeAll
  static void setup() {
    ModifierFixture.init();
  }

  private static TraitBuilder newTraitBuilder() {
    return new TraitBuilder(mock(IToolContext.class), ModifierNBT.builder());
  }

  private static List<ModifierEntry> traitsOf(TraitBuilder builder) {
    return builder.build().getModifiers();
  }

  @Test
  void addTraits_conditionFails_addsNothing() {
    ModifierTraitModule module = new ModifierTraitModule(new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 1), false, ModifierCondition.ANY_CONTEXT.minLevel(5));
    TraitBuilder builder = newTraitBuilder();
    module.addTraits(mock(IToolContext.class), new ModifierEntry(ModifierFixture.TEST_MODIFIER_2, 1), builder, true);
    assertThat(traitsOf(builder)).isEmpty();
  }

  @Test
  void addTraits_fixedLevel_firstEncounter_addsTraitAsIs() {
    ModifierTraitModule module = new ModifierTraitModule(new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 3), true, ModifierCondition.ANY_CONTEXT);
    TraitBuilder builder = newTraitBuilder();
    module.addTraits(mock(IToolContext.class), new ModifierEntry(ModifierFixture.TEST_MODIFIER_2, 7), builder, true);
    assertThat(traitsOf(builder)).containsExactly(new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 3));
  }

  @Test
  void addTraits_fixedLevel_notFirstEncounter_addsNothing() {
    ModifierTraitModule module = new ModifierTraitModule(new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 3), true, ModifierCondition.ANY_CONTEXT);
    TraitBuilder builder = newTraitBuilder();
    module.addTraits(mock(IToolContext.class), new ModifierEntry(ModifierFixture.TEST_MODIFIER_2, 7), builder, false);
    assertThat(traitsOf(builder)).isEmpty();
  }

  @Test
  void addTraits_scalingLevel_selfLevelOne_addsTraitAsIs() {
    ModifierTraitModule module = new ModifierTraitModule(new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 3), false, ModifierCondition.ANY_CONTEXT);
    TraitBuilder builder = newTraitBuilder();
    module.addTraits(mock(IToolContext.class), new ModifierEntry(ModifierFixture.TEST_MODIFIER_2, 1), builder, true);
    assertThat(traitsOf(builder)).containsExactly(new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 3));
  }

  @Test
  void addTraits_scalingLevel_selfLevelAbove1_multipliesLevel() {
    ModifierTraitModule module = new ModifierTraitModule(new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 3), false, ModifierCondition.ANY_CONTEXT);
    TraitBuilder builder = newTraitBuilder();
    module.addTraits(mock(IToolContext.class), new ModifierEntry(ModifierFixture.TEST_MODIFIER_2, 4), builder, true);
    // trait level 3 * self level 4 = 12
    assertThat(traitsOf(builder)).containsExactly(new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 12));
  }

  @Test
  void deprecatedTwoArgConstructor_defaultsToAnyContextCondition() {
    ModifierTraitModule module = new ModifierTraitModule(new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 1), false);
    assertThat(module.condition()).isEqualTo(ModifierCondition.ANY_CONTEXT);
  }

  @Test
  void idLevelConstructor_buildsEquivalentModifierEntry() {
    ModifierTraitModule module = new ModifierTraitModule(ModifierFixture.TEST_1, 5, true);
    assertThat(module.modifier()).isEqualTo(new ModifierEntry(ModifierFixture.TEST_1, 5));
    assertThat(module.fixedLevel()).isTrue();
    assertThat(module.condition()).isEqualTo(ModifierCondition.ANY_CONTEXT);
  }

  @Test
  void idLevelConditionConstructor_buildsEquivalentModifierEntry() {
    ModifierCondition<IToolContext> condition = ModifierCondition.ANY_CONTEXT.minLevel(2);
    ModifierTraitModule module = new ModifierTraitModule(ModifierFixture.TEST_1, 5, true, condition);
    assertThat(module.modifier()).isEqualTo(new ModifierEntry(ModifierFixture.TEST_1, 5));
    assertThat(module.condition()).isEqualTo(condition);
  }

  @Test
  void tagCondition_createsNonFixedLevelOneModule() {
    ModifierTraitModule module = ModifierTraitModule.tagCondition(ModifierFixture.TEST_1, ItemTags.PICKAXES);
    assertThat(module.fixedLevel()).isFalse();
    assertThat(module.modifier()).isEqualTo(new ModifierEntry(ModifierFixture.TEST_1, 1));
  }

  @Test
  void loader_jsonRoundTrips() {
    ModifierTraitModule original = new ModifierTraitModule(new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 2), true, ModifierCondition.ANY_CONTEXT);
    JsonObject json = new JsonObject();
    ModifierTraitModule.LOADER.serialize(original, json);
    ModifierTraitModule parsed = ModifierTraitModule.LOADER.deserialize(json);

    TraitBuilder builder = newTraitBuilder();
    parsed.addTraits(mock(IToolContext.class), new ModifierEntry(ModifierFixture.TEST_MODIFIER_2, 9), builder, true);
    assertThat(traitsOf(builder)).containsExactly(new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 2));
  }

  @Test
  void loader_networkRoundTrips() {
    ModifierTraitModule original = new ModifierTraitModule(new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 2), false, ModifierCondition.ANY_CONTEXT);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    ModifierTraitModule.LOADER.encode(buffer, original);
    ModifierTraitModule decoded = ModifierTraitModule.LOADER.decode(buffer);

    TraitBuilder builder = newTraitBuilder();
    decoded.addTraits(mock(IToolContext.class), new ModifierEntry(ModifierFixture.TEST_MODIFIER_2, 1), builder, true);
    assertThat(traitsOf(builder)).containsExactly(new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 2));
  }

  @Test
  void getLoader_returnsStaticLoader() {
    ModifierTraitModule module = new ModifierTraitModule(new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 1), false, ModifierCondition.ANY_CONTEXT);
    assertThat(module.getLoader()).isSameAs(ModifierTraitModule.LOADER);
  }
}
