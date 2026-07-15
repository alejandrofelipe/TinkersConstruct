package slimeknights.tconstruct.library.modifiers.modules.build;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierFixture;
import slimeknights.tconstruct.library.modifiers.hook.build.ModifierTraitHook.TraitBuilder;
import slimeknights.tconstruct.library.module.ModuleHook;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.definition.module.build.ToolTraitHook;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests {@link SwappableToolTraitsModule}: redirects to a given {@link ToolTraitHook} (looked up off the tool's own
 * {@link ToolDefinition}) only when a persisted "swapped to" name matches the configured variant.
 */
class SwappableToolTraitsModuleTest extends BaseMcTest {
  @BeforeAll
  static void setup() {
    ModifierFixture.init();
  }

  private static final ResourceLocation CUSTOM_KEY = ResourceLocation.fromNamespaceAndPath("test", "traits_key");

  @SuppressWarnings("unchecked")
  private static ModuleHook<ToolTraitHook> mockHookKey() {
    return mock(ModuleHook.class);
  }

  private static TraitBuilder newTraitBuilder() {
    return new TraitBuilder(mock(IToolContext.class), ModifierNBT.builder());
  }

  @Test
  void addTraits_matchEmpty_alwaysDelegatesToUnderlyingHook() {
    ModuleHook<ToolTraitHook> hookKey = mockHookKey();
    SwappableToolTraitsModule module = new SwappableToolTraitsModule(CUSTOM_KEY, "", hookKey);

    ToolTraitHook underlyingHook = mock(ToolTraitHook.class);
    ToolDefinition definition = mock(ToolDefinition.class);
    when(definition.getHook(hookKey)).thenReturn(underlyingHook);
    MaterialNBT materials = MaterialNBT.EMPTY;

    IToolContext context = mock(IToolContext.class);
    when(context.getDefinition()).thenReturn(definition);
    when(context.getMaterials()).thenReturn(materials);
    when(context.getPersistentData()).thenReturn(new ModDataNBT());

    TraitBuilder builder = newTraitBuilder();
    module.addTraits(context, new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 1), builder, true);
    verify(underlyingHook).addTraits(definition, materials, builder);
  }

  @Test
  void addTraits_matchNonEmptyAndPersistentDataMatches_delegatesToUnderlyingHook() {
    ModuleHook<ToolTraitHook> hookKey = mockHookKey();
    SwappableToolTraitsModule module = new SwappableToolTraitsModule(CUSTOM_KEY, "abilities", hookKey);

    ToolTraitHook underlyingHook = mock(ToolTraitHook.class);
    ToolDefinition definition = mock(ToolDefinition.class);
    when(definition.getHook(hookKey)).thenReturn(underlyingHook);
    MaterialNBT materials = MaterialNBT.EMPTY;

    ModDataNBT persistentData = new ModDataNBT();
    persistentData.putString(CUSTOM_KEY, "abilities");
    IToolContext context = mock(IToolContext.class);
    when(context.getDefinition()).thenReturn(definition);
    when(context.getMaterials()).thenReturn(materials);
    when(context.getPersistentData()).thenReturn(persistentData);

    TraitBuilder builder = newTraitBuilder();
    module.addTraits(context, new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 1), builder, true);
    verify(underlyingHook).addTraits(definition, materials, builder);
  }

  @Test
  void addTraits_matchNonEmptyButPersistentDataDiffers_doesNotDelegate() {
    ModuleHook<ToolTraitHook> hookKey = mockHookKey();
    SwappableToolTraitsModule module = new SwappableToolTraitsModule(CUSTOM_KEY, "abilities", hookKey);

    ToolTraitHook underlyingHook = mock(ToolTraitHook.class);
    ToolDefinition definition = mock(ToolDefinition.class);
    when(definition.getHook(hookKey)).thenReturn(underlyingHook);

    ModDataNBT persistentData = new ModDataNBT();
    persistentData.putString(CUSTOM_KEY, "defense");
    IToolContext context = mock(IToolContext.class);
    when(context.getDefinition()).thenReturn(definition);
    when(context.getPersistentData()).thenReturn(persistentData);

    TraitBuilder builder = newTraitBuilder();
    module.addTraits(context, new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 1), builder, true);
    verify(underlyingHook, never()).addTraits(any(), any(), any());
  }

  @Test
  void getDisplayName_matchEmpty_returnsNameUnchanged() {
    SwappableToolTraitsModule module = new SwappableToolTraitsModule(CUSTOM_KEY, "", mockHookKey());
    IToolStackView tool = mock(IToolStackView.class);
    ModifierEntry entry = new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 1);
    Component name = Component.literal("Base");
    assertThat(module.getDisplayName(tool, entry, name, null)).isSameAs(name);
  }

  @Test
  void getDisplayName_matchNonEmptyAndPersistentDataMatches_wrapsName() {
    SwappableToolTraitsModule module = new SwappableToolTraitsModule(CUSTOM_KEY, "abilities", mockHookKey());
    IToolStackView tool = mock(IToolStackView.class);
    ModDataNBT persistentData = new ModDataNBT();
    persistentData.putString(CUSTOM_KEY, "abilities");
    when(tool.getPersistentData()).thenReturn(persistentData);

    ModifierEntry entry = new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 1);
    Component name = Component.literal("Base");
    Component result = module.getDisplayName(tool, entry, name, null);
    assertThat(result).isNotSameAs(name);
    assertThat(((TranslatableContents) result.getContents()).getKey()).isEqualTo(SwappableSlotModule.FORMAT);
  }

  @Test
  void getDisplayName_matchNonEmptyButPersistentDataDiffers_returnsNameUnchanged() {
    SwappableToolTraitsModule module = new SwappableToolTraitsModule(CUSTOM_KEY, "abilities", mockHookKey());
    IToolStackView tool = mock(IToolStackView.class);
    ModDataNBT persistentData = new ModDataNBT();
    persistentData.putString(CUSTOM_KEY, "defense");
    when(tool.getPersistentData()).thenReturn(persistentData);

    ModifierEntry entry = new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 1);
    Component name = Component.literal("Base");
    assertThat(module.getDisplayName(tool, entry, name, null)).isSameAs(name);
  }

  @Test
  void onRemoved_matchEmpty_neverTouchesPersistentData() {
    SwappableToolTraitsModule module = new SwappableToolTraitsModule(CUSTOM_KEY, "", mockHookKey());
    IToolStackView tool = mock(IToolStackView.class);
    Component result = module.onRemoved(tool, ModifierFixture.TEST_MODIFIER_1);
    assertThat(result).isNull();
    verifyNoInteractions(tool);
  }

  @Test
  void onRemoved_matchNonEmpty_removesPersistentDataAtKey() {
    SwappableToolTraitsModule module = new SwappableToolTraitsModule(CUSTOM_KEY, "abilities", mockHookKey());
    IToolStackView tool = mock(IToolStackView.class);
    ModDataNBT persistentData = new ModDataNBT();
    persistentData.putString(CUSTOM_KEY, "abilities");
    when(tool.getPersistentData()).thenReturn(persistentData);

    Component result = module.onRemoved(tool, ModifierFixture.TEST_MODIFIER_1);
    assertThat(result).isNull();
    assertThat(persistentData.getString(CUSTOM_KEY)).isEmpty();
  }

  @Test
  void key_returnsConfiguredKey() {
    SwappableToolTraitsModule module = new SwappableToolTraitsModule(CUSTOM_KEY, "abilities", mockHookKey());
    assertThat(module.key()).isEqualTo(CUSTOM_KEY);
  }

  @Test
  void getDefaultHooks_hasTraitsDisplayNameAndRemoveHooks() {
    SwappableToolTraitsModule module = new SwappableToolTraitsModule(CUSTOM_KEY, "abilities", mockHookKey());
    assertThat(module.getDefaultHooks()).hasSize(3);
  }

  @Test
  void getLoader_returnsStaticLoader() {
    SwappableToolTraitsModule module = new SwappableToolTraitsModule(CUSTOM_KEY, "abilities", mockHookKey());
    assertThat(module.getLoader()).isSameAs(SwappableToolTraitsModule.LOADER);
  }
}
