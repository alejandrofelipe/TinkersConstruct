package slimeknights.tconstruct.library.modifiers.modules.build;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
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
import slimeknights.tconstruct.library.tools.definition.module.ToolHooks;
import slimeknights.tconstruct.library.tools.definition.module.build.ToolTraitHook;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

  /**
   * {@link ToolHooks#DISPLAY_NAME} is a real, registered {@code ModuleHook<ToolNameHook>} - registered (so the LOADER
   * can serialize it by id) but NOT valid for {@link ToolTraitHook}, which is exactly what the LOADER's validation
   * lambda rejects. The unchecked cast lets us build a module holding it; serialization does not validate (only the
   * deserialize/decode direction runs the guard), so we can produce the "bad hook" JSON/buffer from a real module.
   */
  @SuppressWarnings("unchecked")
  private static ModuleHook<ToolTraitHook> displayNameHookMislabeled() {
    return (ModuleHook<ToolTraitHook>) (ModuleHook<?>) ToolHooks.DISPLAY_NAME;
  }

  /** Verifies a round-tripped module still delegates to exactly {@code expectedHook} (confirms the hook field survived). */
  private static void assertDelegatesToHook(SwappableToolTraitsModule module, ModuleHook<ToolTraitHook> expectedHook) {
    ToolTraitHook underlyingHook = mock(ToolTraitHook.class);
    ToolDefinition definition = mock(ToolDefinition.class);
    when(definition.getHook(expectedHook)).thenReturn(underlyingHook);
    MaterialNBT materials = MaterialNBT.EMPTY;
    IToolContext context = mock(IToolContext.class);
    when(context.getDefinition()).thenReturn(definition);
    when(context.getMaterials()).thenReturn(materials);

    TraitBuilder builder = newTraitBuilder();
    // match "" -> always delegates, so getHook(module.hook) must resolve to the stubbed expectedHook
    module.addTraits(context, new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 1), builder, true);
    verify(underlyingHook).addTraits(definition, materials, builder);
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


  /* LOADER hook-support validation (uses real registered ToolHooks.* constants, mirroring HasToolHookPredicateTest) */

  @Test
  void loader_jsonRoundTrips_withValidToolTraitsHook() {
    SwappableToolTraitsModule original = new SwappableToolTraitsModule(CUSTOM_KEY, "", ToolHooks.TOOL_TRAITS);
    JsonObject json = new JsonObject();
    SwappableToolTraitsModule.LOADER.serialize(original, json);
    SwappableToolTraitsModule decoded = SwappableToolTraitsModule.LOADER.deserialize(json);
    assertThat(decoded.key()).isEqualTo(CUSTOM_KEY);
    // TOOL_TRAITS supports ToolTraitHook, so the validation lambda's "ok" branch returns it unchanged
    assertDelegatesToHook(decoded, ToolHooks.TOOL_TRAITS);
  }

  @Test
  void loader_networkRoundTrips_withValidToolTraitsHook() {
    SwappableToolTraitsModule original = new SwappableToolTraitsModule(CUSTOM_KEY, "", ToolHooks.TOOL_TRAITS);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    SwappableToolTraitsModule.LOADER.encode(buffer, original);
    SwappableToolTraitsModule decoded = SwappableToolTraitsModule.LOADER.decode(buffer);
    assertThat(decoded.key()).isEqualTo(CUSTOM_KEY);
    assertDelegatesToHook(decoded, ToolHooks.TOOL_TRAITS);
  }

  @Test
  void loader_deserialize_rejectsHookNotSupportingToolTraitHook() {
    // serialize does not validate, so we build the invalid JSON from a real module holding DISPLAY_NAME
    SwappableToolTraitsModule badModule = new SwappableToolTraitsModule(CUSTOM_KEY, "", displayNameHookMislabeled());
    JsonObject json = new JsonObject();
    SwappableToolTraitsModule.LOADER.serialize(badModule, json);
    assertThatThrownBy(() -> SwappableToolTraitsModule.LOADER.deserialize(json))
      .isInstanceOf(JsonSyntaxException.class)
      .hasMessageContaining("is not valid for ToolTraitHook");
  }

  @Test
  void loader_decode_rejectsHookNotSupportingToolTraitHook() {
    SwappableToolTraitsModule badModule = new SwappableToolTraitsModule(CUSTOM_KEY, "", displayNameHookMislabeled());
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    SwappableToolTraitsModule.LOADER.encode(buffer, badModule);
    assertThatThrownBy(() -> SwappableToolTraitsModule.LOADER.decode(buffer))
      .isInstanceOf(DecoderException.class)
      .hasMessageContaining("is not valid for ToolTraitHook");
  }
}
