package slimeknights.tconstruct.library.modifiers.modules.build;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierFixture;
import slimeknights.tconstruct.library.modifiers.modules.build.SwappableSlotModule.BonusSlot;
import slimeknights.tconstruct.library.modifiers.modules.util.ModifierCondition;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolDataNBT;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests {@link SwappableSlotModule} and its nested {@link BonusSlot}: slots that key off a persisted "swapped to" slot type name. */
class SwappableSlotModuleTest extends BaseMcTest {
  @BeforeAll
  static void setup() {
    ModifierFixture.init();
  }

  private static final SlotType MATCH_TYPE = SlotType.getOrCreate("p2t5_swap_match");
  private static final SlotType BONUS_TYPE = SlotType.getOrCreate("p2t5_swap_bonus");
  private static final ResourceLocation CUSTOM_KEY = ResourceLocation.fromNamespaceAndPath("test", "swap_key");

  /* SwappableSlotModule */

  @Test
  void addVolatileData_slotNameMatchesRegisteredType_addsSlots() {
    SwappableSlotModule module = new SwappableSlotModule(CUSTOM_KEY, 3, ModifierCondition.ANY_CONTEXT);
    IToolContext context = mock(IToolContext.class);
    ModDataNBT persistentData = new ModDataNBT();
    persistentData.putString(CUSTOM_KEY, MATCH_TYPE.getName());
    when(context.getPersistentData()).thenReturn(persistentData);

    ToolDataNBT data = new ToolDataNBT();
    module.addVolatileData(context, new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 1), data);
    assertThat(data.getSlots(MATCH_TYPE)).isEqualTo(3);
  }

  @Test
  void addVolatileData_noSlotNameStored_addsNothing() {
    SwappableSlotModule module = new SwappableSlotModule(CUSTOM_KEY, 3, ModifierCondition.ANY_CONTEXT);
    IToolContext context = mock(IToolContext.class);
    when(context.getPersistentData()).thenReturn(new ModDataNBT());

    ToolDataNBT data = new ToolDataNBT();
    module.addVolatileData(context, new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 1), data);
    assertThat(data.getSlots(MATCH_TYPE)).isZero();
  }

  @Test
  void addVolatileData_conditionFails_addsNothingEvenIfSlotNameStored() {
    SwappableSlotModule module = new SwappableSlotModule(CUSTOM_KEY, 3, ModifierCondition.ANY_CONTEXT.minLevel(5));
    IToolContext context = mock(IToolContext.class);
    ModDataNBT persistentData = new ModDataNBT();
    persistentData.putString(CUSTOM_KEY, MATCH_TYPE.getName());
    when(context.getPersistentData()).thenReturn(persistentData);

    ToolDataNBT data = new ToolDataNBT();
    module.addVolatileData(context, new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 1), data);
    assertThat(data.getSlots(MATCH_TYPE)).isZero();
  }

  @Test
  void addVolatileData_unknownSlotTypeName_addsNothing() {
    SwappableSlotModule module = new SwappableSlotModule(CUSTOM_KEY, 3, ModifierCondition.ANY_CONTEXT);
    IToolContext context = mock(IToolContext.class);
    ModDataNBT persistentData = new ModDataNBT();
    persistentData.putString(CUSTOM_KEY, "not_a_registered_slot_type_xyz");
    when(context.getPersistentData()).thenReturn(persistentData);

    ToolDataNBT data = new ToolDataNBT();
    module.addVolatileData(context, new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 1), data);
    assertThat(data.getSlots(MATCH_TYPE)).isZero();
  }

  @Test
  void getDisplayName_noKeyOverride_fallsBackToModifierIdLocation_andWrapsNameWhenMatched() {
    SwappableSlotModule module = new SwappableSlotModule(null, 3);
    IToolStackView tool = mock(IToolStackView.class);
    ModDataNBT persistentData = new ModDataNBT();
    // no explicit key -> falls back to the modifier's own id location
    persistentData.putString(ModifierFixture.TEST_1.getLocation(), MATCH_TYPE.getName());
    when(tool.getPersistentData()).thenReturn(persistentData);

    ModifierEntry entry = new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 1);
    Component name = Component.literal("Base Name");
    Component result = module.getDisplayName(tool, entry, name, null);
    assertThat(result).isNotSameAs(name);
    assertThat(((TranslatableContents) result.getContents()).getKey()).isEqualTo(SwappableSlotModule.FORMAT);
  }

  @Test
  void getDisplayName_slotNameEmpty_returnsNameUnchanged() {
    SwappableSlotModule module = new SwappableSlotModule(CUSTOM_KEY, 3);
    IToolStackView tool = mock(IToolStackView.class);
    when(tool.getPersistentData()).thenReturn(new ModDataNBT());

    ModifierEntry entry = new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 1);
    Component name = Component.literal("Base Name");
    assertThat(module.getDisplayName(tool, entry, name, null)).isSameAs(name);
  }

  @Test
  void getDisplayName_unknownSlotTypeName_returnsNameUnchanged() {
    SwappableSlotModule module = new SwappableSlotModule(CUSTOM_KEY, 3);
    IToolStackView tool = mock(IToolStackView.class);
    ModDataNBT persistentData = new ModDataNBT();
    persistentData.putString(CUSTOM_KEY, "not_a_registered_slot_type_xyz");
    when(tool.getPersistentData()).thenReturn(persistentData);

    ModifierEntry entry = new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 1);
    Component name = Component.literal("Base Name");
    assertThat(module.getDisplayName(tool, entry, name, null)).isSameAs(name);
  }

  @Test
  void getPriority_isFifty() {
    SwappableSlotModule module = new SwappableSlotModule(CUSTOM_KEY, 1);
    assertThat(module.getPriority()).isEqualTo(50);
  }

  @Test
  void onRemoved_removesPersistentDataAtKey() {
    SwappableSlotModule module = new SwappableSlotModule(CUSTOM_KEY, 1);
    IToolStackView tool = mock(IToolStackView.class);
    ModDataNBT persistentData = new ModDataNBT();
    persistentData.putString(CUSTOM_KEY, MATCH_TYPE.getName());
    when(tool.getPersistentData()).thenReturn(persistentData);

    Component result = module.onRemoved(tool, ModifierFixture.TEST_MODIFIER_1);
    assertThat(result).isNull();
    assertThat(persistentData.getString(CUSTOM_KEY)).isEmpty();
  }

  @Test
  void oneArgConstructor_defaultsKeyNullAndAnyContextCondition() {
    SwappableSlotModule module = new SwappableSlotModule(5);
    assertThat(module.key()).isNull();
    assertThat(module.condition()).isEqualTo(ModifierCondition.ANY_CONTEXT);
  }

  @Test
  void getDefaultHooks_hasVolatileDataDisplayNameAndRemoveHooks() {
    SwappableSlotModule module = new SwappableSlotModule(CUSTOM_KEY, 1);
    assertThat(module.getDefaultHooks()).hasSize(3);
  }

  @Test
  void getLoader_returnsStaticLoader() {
    SwappableSlotModule module = new SwappableSlotModule(CUSTOM_KEY, 1);
    assertThat(module.getLoader()).isSameAs(SwappableSlotModule.LOADER);
  }

  @Test
  void loader_jsonRoundTrips() {
    SwappableSlotModule original = new SwappableSlotModule(CUSTOM_KEY, 4, ModifierCondition.ANY_CONTEXT);
    JsonObject json = new JsonObject();
    SwappableSlotModule.LOADER.serialize(original, json);
    SwappableSlotModule parsed = SwappableSlotModule.LOADER.deserialize(json);
    assertThat(parsed.key()).isEqualTo(CUSTOM_KEY);
    assertThat(parsed.slotCount()).isEqualTo(4);
  }

  @Test
  void loader_networkRoundTrips() {
    SwappableSlotModule original = new SwappableSlotModule(CUSTOM_KEY, 4, ModifierCondition.ANY_CONTEXT);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    SwappableSlotModule.LOADER.encode(buffer, original);
    SwappableSlotModule decoded = SwappableSlotModule.LOADER.decode(buffer);
    assertThat(decoded.key()).isEqualTo(CUSTOM_KEY);
    assertThat(decoded.slotCount()).isEqualTo(4);
  }


  /* BonusSlot */

  @Test
  void bonusSlot_matchingStoredSlotName_addsBonusSlots() {
    BonusSlot module = new BonusSlot(CUSTOM_KEY, MATCH_TYPE, BONUS_TYPE, 2, ModifierCondition.ANY_CONTEXT);
    IToolContext context = mock(IToolContext.class);
    ModDataNBT persistentData = new ModDataNBT();
    persistentData.putString(CUSTOM_KEY, MATCH_TYPE.getName());
    when(context.getPersistentData()).thenReturn(persistentData);

    ToolDataNBT data = new ToolDataNBT();
    module.addVolatileData(context, new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 1), data);
    assertThat(data.getSlots(BONUS_TYPE)).isEqualTo(2);
  }

  @Test
  void bonusSlot_noSlotNameStored_addsNothing() {
    BonusSlot module = new BonusSlot(CUSTOM_KEY, MATCH_TYPE, BONUS_TYPE, 2, ModifierCondition.ANY_CONTEXT);
    IToolContext context = mock(IToolContext.class);
    when(context.getPersistentData()).thenReturn(new ModDataNBT());

    ToolDataNBT data = new ToolDataNBT();
    module.addVolatileData(context, new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 1), data);
    assertThat(data.getSlots(BONUS_TYPE)).isZero();
  }

  @Test
  void bonusSlot_storedSlotNameDoesNotMatch_addsNothing() {
    BonusSlot module = new BonusSlot(CUSTOM_KEY, MATCH_TYPE, BONUS_TYPE, 2, ModifierCondition.ANY_CONTEXT);
    IToolContext context = mock(IToolContext.class);
    ModDataNBT persistentData = new ModDataNBT();
    persistentData.putString(CUSTOM_KEY, BONUS_TYPE.getName()); // stored type is the bonus type, not the required match type
    when(context.getPersistentData()).thenReturn(persistentData);

    ToolDataNBT data = new ToolDataNBT();
    module.addVolatileData(context, new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 1), data);
    assertThat(data.getSlots(BONUS_TYPE)).isZero();
  }

  @Test
  void bonusSlot_conditionFails_addsNothing() {
    BonusSlot module = new BonusSlot(CUSTOM_KEY, MATCH_TYPE, BONUS_TYPE, 2, ModifierCondition.ANY_CONTEXT.minLevel(5));
    IToolContext context = mock(IToolContext.class);
    ModDataNBT persistentData = new ModDataNBT();
    persistentData.putString(CUSTOM_KEY, MATCH_TYPE.getName());
    when(context.getPersistentData()).thenReturn(persistentData);

    ToolDataNBT data = new ToolDataNBT();
    module.addVolatileData(context, new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 1), data);
    assertThat(data.getSlots(BONUS_TYPE)).isZero();
  }

  @Test
  void bonusSlot_threeArgConstructor_defaultsKeyNullAndAnyContextCondition() {
    BonusSlot module = new BonusSlot(MATCH_TYPE, BONUS_TYPE, 2);
    assertThat(module.key()).isNull();
    assertThat(module.condition()).isEqualTo(ModifierCondition.ANY_CONTEXT);
  }

  @Test
  void bonusSlot_getDefaultHooks_hasVolatileDataHook() {
    BonusSlot module = new BonusSlot(CUSTOM_KEY, MATCH_TYPE, BONUS_TYPE, 1, ModifierCondition.ANY_CONTEXT);
    assertThat(module.getDefaultHooks()).hasSize(1);
  }

  @Test
  void bonusSlot_getLoader_returnsStaticLoader() {
    BonusSlot module = new BonusSlot(CUSTOM_KEY, MATCH_TYPE, BONUS_TYPE, 1, ModifierCondition.ANY_CONTEXT);
    assertThat(module.getLoader()).isSameAs(BonusSlot.LOADER);
  }

  @Test
  void bonusSlot_loader_jsonRoundTrips() {
    BonusSlot original = new BonusSlot(CUSTOM_KEY, MATCH_TYPE, BONUS_TYPE, 6, ModifierCondition.ANY_CONTEXT);
    JsonObject json = new JsonObject();
    BonusSlot.LOADER.serialize(original, json);
    BonusSlot parsed = BonusSlot.LOADER.deserialize(json);
    assertThat(parsed.match()).isEqualTo(MATCH_TYPE);
    assertThat(parsed.bonus()).isEqualTo(BONUS_TYPE);
    assertThat(parsed.slotCount()).isEqualTo(6);
  }

  @Test
  void bonusSlot_loader_networkRoundTrips() {
    BonusSlot original = new BonusSlot(CUSTOM_KEY, MATCH_TYPE, BONUS_TYPE, 6, ModifierCondition.ANY_CONTEXT);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    BonusSlot.LOADER.encode(buffer, original);
    BonusSlot decoded = BonusSlot.LOADER.decode(buffer);
    assertThat(decoded.match()).isEqualTo(MATCH_TYPE);
    assertThat(decoded.bonus()).isEqualTo(BONUS_TYPE);
    assertThat(decoded.slotCount()).isEqualTo(6);
  }
}
