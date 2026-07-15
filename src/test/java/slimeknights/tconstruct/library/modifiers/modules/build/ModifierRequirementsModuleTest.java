package slimeknights.tconstruct.library.modifiers.modules.build;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.tconstruct.library.json.predicate.tool.ToolContextPredicate;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierFixture;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.util.LazyModifier;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.library.utils.Util;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests {@link ModifierRequirementsModule}: gates a modifier's addition on a tool-context predicate within a level range. */
class ModifierRequirementsModuleTest extends BaseMcTest {
  @BeforeAll
  static void setup() {
    ModifierFixture.init();
  }

  private static final ModifierId MODIFIER_ID = new ModifierId("test", "requires_stuff");
  private static final String EXPECTED_KEY = Util.makeTranslationKey("modifier", MODIFIER_ID.getLocation()) + ".requirements";

  @SuppressWarnings("unchecked")
  private static IJsonPredicate<IToolContext> mockPredicate() {
    return mock(IJsonPredicate.class);
  }

  private static String keyOf(Component component) {
    return ((TranslatableContents) component.getContents()).getKey();
  }

  @Test
  void validate_levelInRangeAndRequirementFails_returnsErrorMessage() {
    IJsonPredicate<IToolContext> requirement = mockPredicate();
    ModifierRequirementsModule module = ModifierRequirementsModule.builder().modifierKey(MODIFIER_ID).requirement(requirement).build();
    IToolStackView tool = mock(IToolStackView.class);
    when(requirement.matches(tool)).thenReturn(false);

    Component result = module.validate(tool, new ModifierEntry(MODIFIER_ID, 1));
    assertThat(result).isNotNull();
    assertThat(keyOf(result)).isEqualTo(EXPECTED_KEY);
  }

  @Test
  void validate_levelInRangeAndRequirementPasses_returnsNull() {
    IJsonPredicate<IToolContext> requirement = mockPredicate();
    ModifierRequirementsModule module = ModifierRequirementsModule.builder().modifierKey(MODIFIER_ID).requirement(requirement).build();
    IToolStackView tool = mock(IToolStackView.class);
    when(requirement.matches(tool)).thenReturn(true);

    assertThat(module.validate(tool, new ModifierEntry(MODIFIER_ID, 1))).isNull();
  }

  @Test
  void validate_levelOutOfRange_returnsNullRegardlessOfRequirement() {
    IJsonPredicate<IToolContext> requirement = mockPredicate();
    ModifierRequirementsModule module = ModifierRequirementsModule.builder().modifierKey(MODIFIER_ID).requirement(requirement).minLevel(1).maxLevel(2).build();
    IToolStackView tool = mock(IToolStackView.class);
    when(requirement.matches(tool)).thenReturn(false); // would fail if checked, but level 3 is outside [1,2]

    assertThat(module.validate(tool, new ModifierEntry(MODIFIER_ID, 3))).isNull();
  }

  @Test
  void requirementsError_levelInRange_returnsErrorMessage() {
    ModifierRequirementsModule module = ModifierRequirementsModule.builder().modifierKey(MODIFIER_ID).requirement(mockPredicate()).build();
    Component result = module.requirementsError(new ModifierEntry(MODIFIER_ID, 1));
    assertThat(result).isNotNull();
    assertThat(keyOf(result)).isEqualTo(EXPECTED_KEY);
  }

  @Test
  void requirementsError_levelOutOfRange_returnsNull() {
    ModifierRequirementsModule module = ModifierRequirementsModule.builder().modifierKey(MODIFIER_ID).requirement(mockPredicate()).minLevel(1).maxLevel(2).build();
    assertThat(module.requirementsError(new ModifierEntry(MODIFIER_ID, 5))).isNull();
  }

  @Test
  void displayModifiers_levelInRange_returnsConfiguredList() {
    ModifierId displayId = new ModifierId("test", "displayed");
    ModifierRequirementsModule module = ModifierRequirementsModule.builder().modifierKey(MODIFIER_ID)
      .requirement(mockPredicate()).displayModifier(displayId, 2).build();
    assertThat(module.displayModifiers(new ModifierEntry(MODIFIER_ID, 1))).containsExactly(new ModifierEntry(displayId, 2));
  }

  @Test
  void displayModifiers_levelOutOfRange_returnsEmptyList() {
    ModifierId displayId = new ModifierId("test", "displayed");
    ModifierRequirementsModule module = ModifierRequirementsModule.builder().modifierKey(MODIFIER_ID)
      .requirement(mockPredicate()).displayModifier(displayId, 2).minLevel(1).maxLevel(2).build();
    assertThat(module.displayModifiers(new ModifierEntry(MODIFIER_ID, 9))).isEmpty();
  }

  @Test
  void modifierKey_fromLazyModifier_producesSameKeyAsFromModifierId() {
    ModifierRequirementsModule viaId = ModifierRequirementsModule.builder().modifierKey(MODIFIER_ID).requirement(mockPredicate()).build();
    ModifierRequirementsModule viaLazyModifier = ModifierRequirementsModule.builder().modifierKey(new LazyModifier(MODIFIER_ID)).requirement(mockPredicate()).build();
    assertThat(keyOf(viaLazyModifier.requirementsError(new ModifierEntry(MODIFIER_ID, 1))))
      .isEqualTo(keyOf(viaId.requirementsError(new ModifierEntry(MODIFIER_ID, 1))));
  }

  @Test
  void getDefaultHooks_hasValidateUpgradeAndRequirementsHooks() {
    ModifierRequirementsModule module = ModifierRequirementsModule.builder().modifierKey(MODIFIER_ID).requirement(mockPredicate()).build();
    assertThat(module.getDefaultHooks()).hasSize(2);
  }

  @Test
  void getLoader_returnsStaticLoader() {
    ModifierRequirementsModule module = ModifierRequirementsModule.builder().modifierKey(MODIFIER_ID).requirement(mockPredicate()).build();
    assertThat(module.getLoader()).isSameAs(ModifierRequirementsModule.LOADER);
  }


  /* Builder */

  @Test
  void builder_withoutTranslationKey_throws() {
    assertThatThrownBy(() -> ModifierRequirementsModule.builder().requirement(mockPredicate()).build())
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void builder_withoutRequirements_throws() {
    assertThatThrownBy(() -> ModifierRequirementsModule.builder().modifierKey(MODIFIER_ID).build())
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void builder_singleRequirement_usedDirectlyWithoutWrapping() {
    IJsonPredicate<IToolContext> requirement = mockPredicate();
    ModifierRequirementsModule module = ModifierRequirementsModule.builder().modifierKey(MODIFIER_ID).requirement(requirement).build();
    IToolStackView tool = mock(IToolStackView.class);

    when(requirement.matches(tool)).thenReturn(true);
    assertThat(module.validate(tool, new ModifierEntry(MODIFIER_ID, 1))).isNull();
    when(requirement.matches(tool)).thenReturn(false);
    assertThat(module.validate(tool, new ModifierEntry(MODIFIER_ID, 1))).isNotNull();
  }

  @Test
  void builder_multipleRequirements_andsThemTogether() {
    IJsonPredicate<IToolContext> req1 = mockPredicate();
    IJsonPredicate<IToolContext> req2 = mockPredicate();
    ModifierRequirementsModule module = ModifierRequirementsModule.builder().modifierKey(MODIFIER_ID).requirement(req1).requirement(req2).build();
    IToolStackView tool = mock(IToolStackView.class);

    when(req1.matches(tool)).thenReturn(true);
    when(req2.matches(tool)).thenReturn(true);
    assertThat(module.validate(tool, new ModifierEntry(MODIFIER_ID, 1))).isNull(); // both pass -> requirement met -> no error

    when(req2.matches(tool)).thenReturn(false);
    assertThat(module.validate(tool, new ModifierEntry(MODIFIER_ID, 1))).isNotNull(); // one fails -> AND fails -> error shown
  }

  @Test
  void builder_requireModifier_addsDisplayModifierAndChecksAllModifiers() {
    ModifierRequirementsModule module = ModifierRequirementsModule.builder().modifierKey(MODIFIER_ID).requireModifier(ModifierFixture.TEST_1, 2).build();
    ModifierEntry selfEntry = new ModifierEntry(MODIFIER_ID, 1);

    assertThat(module.displayModifiers(selfEntry)).containsExactly(new ModifierEntry(ModifierFixture.TEST_1, 2));

    IToolStackView toolWithout = mock(IToolStackView.class);
    when(toolWithout.getModifiers()).thenReturn(ModifierNBT.EMPTY);
    assertThat(module.validate(toolWithout, selfEntry)).isNotNull(); // missing the required modifier -> error

    IToolStackView toolWith = mock(IToolStackView.class);
    when(toolWith.getModifiers()).thenReturn(ModifierNBT.builder().add(new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 2)).build());
    assertThat(module.validate(toolWith, selfEntry)).isNull(); // has it at the required level -> no error
  }

  @Test
  void builder_requireUpgrade_checksUpgradesNotAllModifiers() {
    ModifierRequirementsModule module = ModifierRequirementsModule.builder().modifierKey(MODIFIER_ID).requireUpgrade(ModifierFixture.TEST_1, 1).build();
    ModifierEntry selfEntry = new ModifierEntry(MODIFIER_ID, 1);
    ModifierNBT hasRequired = ModifierNBT.builder().add(new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 1)).build();

    IToolStackView tool = mock(IToolStackView.class);
    // present in getModifiers() (all traits+upgrades) but NOT in getUpgrades() -> requireUpgrade must still fail
    when(tool.getModifiers()).thenReturn(hasRequired);
    when(tool.getUpgrades()).thenReturn(ModifierNBT.EMPTY);
    assertThat(module.validate(tool, selfEntry)).isNotNull();

    when(tool.getUpgrades()).thenReturn(hasRequired);
    assertThat(module.validate(tool, selfEntry)).isNull();
  }

  @Test
  void loader_jsonAndNetworkRoundTrip_withRealAnyPredicate() {
    ModifierRequirementsModule original = ModifierRequirementsModule.builder().modifierKey(MODIFIER_ID).requirement(ToolContextPredicate.ANY).build();
    JsonObject json = new JsonObject();
    ModifierRequirementsModule.LOADER.serialize(original, json);
    ModifierRequirementsModule parsed = ModifierRequirementsModule.LOADER.deserialize(json);
    assertThat(parsed.validate(mock(IToolStackView.class), new ModifierEntry(MODIFIER_ID, 1))).isNull();

    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    ModifierRequirementsModule.LOADER.encode(buffer, original);
    ModifierRequirementsModule decoded = ModifierRequirementsModule.LOADER.decode(buffer);
    assertThat(decoded.validate(mock(IToolStackView.class), new ModifierEntry(MODIFIER_ID, 1))).isNull();
  }
}
