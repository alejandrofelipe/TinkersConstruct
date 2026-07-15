package slimeknights.tconstruct.library.modifiers.modules.build;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.mantle.data.predicate.block.BlockPredicate;
import slimeknights.mantle.data.predicate.entity.LivingEntityPredicate;
import slimeknights.tconstruct.library.json.LevelingInt;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.modules.util.ModifierCondition;
import slimeknights.tconstruct.library.tools.context.ToolHarvestContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests {@link EnchantmentModule} and its nested {@link EnchantmentModule.Constant}, {@link EnchantmentModule.Protection},
 * {@link EnchantmentModule.MainHandHarvest}, {@link EnchantmentModule.ArmorHarvest}, and {@link EnchantmentModule.Builder}.
 */
class EnchantmentModuleTest extends BaseMcTest {
  private static final ResourceKey<Enchantment> SHARPNESS = ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.withDefaultNamespace("sharpness"));
  private static final ResourceKey<Enchantment> UNBREAKING = ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.withDefaultNamespace("unbreaking"));
  private static final ModifierId ID = new ModifierId("test", "foo");
  private static final ResourceLocation FLAG = ResourceLocation.fromNamespaceAndPath("test", "enchant_flag");

  @SuppressWarnings("unchecked")
  private static IJsonPredicate<BlockState> blockPredicate(boolean result) {
    IJsonPredicate<BlockState> predicate = mock(IJsonPredicate.class);
    when(predicate.matches(any())).thenReturn(result);
    return predicate;
  }

  @SuppressWarnings("unchecked")
  private static IJsonPredicate<LivingEntity> holderPredicate(boolean result) {
    IJsonPredicate<LivingEntity> predicate = mock(IJsonPredicate.class);
    when(predicate.matches(any())).thenReturn(result);
    return predicate;
  }

  private static ToolHarvestContext harvestContext() {
    ToolHarvestContext context = mock(ToolHarvestContext.class);
    when(context.getState()).thenReturn(Blocks.DIRT.defaultBlockState());
    when(context.getLiving()).thenReturn(mock(LivingEntity.class));
    return context;
  }


  /* Constant */

  @Test
  void constant_updateEnchantmentLevel_matchingEnchantmentAndConditionTrue_addsComputedLevel() {
    EnchantmentModule.Constant module = new EnchantmentModule.Constant(SHARPNESS, new LevelingInt(1, 2), ModifierCondition.ANY_TOOL);
    IToolStackView tool = mock(IToolStackView.class);
    // flat 1 + 2*level(3) = 7, added onto the incoming 5
    int result = module.updateEnchantmentLevel(tool, new ModifierEntry(ID, 3), SHARPNESS, 5);
    assertThat(result).isEqualTo(12);
  }

  @Test
  void constant_updateEnchantmentLevel_differentEnchantment_returnsLevelUnchanged() {
    EnchantmentModule.Constant module = new EnchantmentModule.Constant(SHARPNESS, LevelingInt.flat(9), ModifierCondition.ANY_TOOL);
    IToolStackView tool = mock(IToolStackView.class);
    int result = module.updateEnchantmentLevel(tool, new ModifierEntry(ID, 1), UNBREAKING, 5);
    assertThat(result).isEqualTo(5);
  }

  @Test
  void constant_updateEnchantmentLevel_conditionFails_returnsLevelUnchanged() {
    EnchantmentModule.Constant module = new EnchantmentModule.Constant(SHARPNESS, LevelingInt.flat(9), ModifierCondition.ANY_TOOL.minLevel(5));
    IToolStackView tool = mock(IToolStackView.class);
    int result = module.updateEnchantmentLevel(tool, new ModifierEntry(ID, 1), SHARPNESS, 5);
    assertThat(result).isEqualTo(5);
  }

  @Test
  void constant_updateEnchantments_conditionTrue_addsToMap() {
    EnchantmentModule.Constant module = new EnchantmentModule.Constant(SHARPNESS, LevelingInt.flat(3), ModifierCondition.ANY_TOOL);
    IToolStackView tool = mock(IToolStackView.class);
    Map<ResourceKey<Enchantment>,Integer> map = new HashMap<>();
    module.updateEnchantments(tool, new ModifierEntry(ID, 1), map);
    assertThat(map).containsEntry(SHARPNESS, 3);
  }

  @Test
  void constant_updateEnchantments_conditionFalse_leavesMapEmpty() {
    EnchantmentModule.Constant module = new EnchantmentModule.Constant(SHARPNESS, LevelingInt.flat(3), ModifierCondition.ANY_TOOL.minLevel(5));
    IToolStackView tool = mock(IToolStackView.class);
    Map<ResourceKey<Enchantment>,Integer> map = new HashMap<>();
    module.updateEnchantments(tool, new ModifierEntry(ID, 1), map);
    assertThat(map).isEmpty();
  }

  @Test
  void constant_block_defaultsToAnyBlockPredicate() {
    EnchantmentModule.Constant module = new EnchantmentModule.Constant(SHARPNESS, LevelingInt.ONE, ModifierCondition.ANY_TOOL);
    assertThat(module.block()).isSameAs(BlockPredicate.ANY);
  }

  @Test
  void constant_holder_defaultsToAnyLivingEntityPredicate() {
    EnchantmentModule.Constant module = new EnchantmentModule.Constant(SHARPNESS, LevelingInt.ONE, ModifierCondition.ANY_TOOL);
    assertThat(module.holder()).isSameAs(LivingEntityPredicate.ANY);
  }

  @Test
  void constant_getDefaultHooks_hasEnchantmentsHook() {
    EnchantmentModule.Constant module = new EnchantmentModule.Constant(SHARPNESS, LevelingInt.ONE, ModifierCondition.ANY_TOOL);
    assertThat(module.getDefaultHooks()).hasSize(1);
  }

  @Test
  void constant_getLoader_returnsStaticLoader() {
    EnchantmentModule.Constant module = new EnchantmentModule.Constant(SHARPNESS, LevelingInt.ONE, ModifierCondition.ANY_TOOL);
    assertThat(module.getLoader()).isSameAs(EnchantmentModule.Constant.LOADER);
  }

  @Test
  void constant_deprecatedIntLevelConstructor_convertsToEachLevel() {
    EnchantmentModule.Constant module = new EnchantmentModule.Constant(SHARPNESS, 4, ModifierCondition.ANY_TOOL);
    IToolStackView tool = mock(IToolStackView.class);
    int result = module.updateEnchantmentLevel(tool, new ModifierEntry(ID, 2), SHARPNESS, 0);
    assertThat(result).isEqualTo(8); // eachLevel(4) at level 2 = 8
  }

  @Test
  void constant_deprecatedTwoArgConstructor_defaultsAnyToolCondition() {
    EnchantmentModule.Constant module = new EnchantmentModule.Constant(SHARPNESS, 4);
    assertThat(module.condition()).isEqualTo(ModifierCondition.ANY_TOOL);
  }

  @Test
  void constant_loader_jsonRoundTrips() {
    EnchantmentModule.Constant original = new EnchantmentModule.Constant(SHARPNESS, new LevelingInt(1, 2), ModifierCondition.ANY_TOOL);
    JsonObject json = new JsonObject();
    EnchantmentModule.Constant.LOADER.serialize(original, json);
    EnchantmentModule.Constant parsed = EnchantmentModule.Constant.LOADER.deserialize(json);
    IToolStackView tool = mock(IToolStackView.class);
    assertThat(parsed.updateEnchantmentLevel(tool, new ModifierEntry(ID, 2), SHARPNESS, 0)).isEqualTo(5); // 1+2*2
  }

  @Test
  void constant_loader_networkRoundTrips() {
    EnchantmentModule.Constant original = new EnchantmentModule.Constant(SHARPNESS, new LevelingInt(1, 2), ModifierCondition.ANY_TOOL);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    EnchantmentModule.Constant.LOADER.encode(buffer, original);
    EnchantmentModule.Constant decoded = EnchantmentModule.Constant.LOADER.decode(buffer);
    IToolStackView tool = mock(IToolStackView.class);
    assertThat(decoded.updateEnchantmentLevel(tool, new ModifierEntry(ID, 2), SHARPNESS, 0)).isEqualTo(5);
  }


  /* Protection */

  @Test
  void protection_conditionFails_returnsModifierValueUnchanged() {
    EnchantmentModule.Protection module = new EnchantmentModule.Protection(SHARPNESS, LevelingInt.flat(5), ModifierCondition.ANY_TOOL.minLevel(5));
    IToolStackView tool = mock(IToolStackView.class);
    DamageSource source = mock(DamageSource.class);
    float result = module.getProtectionModifier(tool, new ModifierEntry(ID, 1), null, EquipmentSlot.MAINHAND, source, 12f);
    assertThat(result).isEqualTo(12f);
    verifyNoInteractions(source);
  }

  @Test
  void protection_subtractLevelZero_returnsModifierValueUnchanged() {
    EnchantmentModule.Protection module = new EnchantmentModule.Protection(SHARPNESS, new LevelingInt(0, 0), ModifierCondition.ANY_TOOL);
    IToolStackView tool = mock(IToolStackView.class);
    DamageSource source = mock(DamageSource.class);
    float result = module.getProtectionModifier(tool, new ModifierEntry(ID, 3), null, EquipmentSlot.MAINHAND, source, 12f);
    assertThat(result).isEqualTo(12f);
    verifyNoInteractions(source);
  }

  @Test
  void protection_subtractLevelPositive_stillReturnsModifierValueUnchanged_bothBypassPolarities() {
    // PORT M3 (see EnchantmentModule.Protection): protection cancellation is a documented no-op pending a
    // registry-access port, so the modifier value is unchanged either way - both polarities of the bypass
    // check are exercised here to prove the guard is reached without asserting a value change that doesn't exist.
    EnchantmentModule.Protection module = new EnchantmentModule.Protection(SHARPNESS, LevelingInt.flat(2), ModifierCondition.ANY_TOOL);
    IToolStackView tool = mock(IToolStackView.class);
    ModifierEntry entry = new ModifierEntry(ID, 1);

    DamageSource bypassing = mock(DamageSource.class);
    when(bypassing.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)).thenReturn(true);
    assertThat(module.getProtectionModifier(tool, entry, null, EquipmentSlot.MAINHAND, bypassing, 12f)).isEqualTo(12f);

    DamageSource nonBypassing = mock(DamageSource.class);
    when(nonBypassing.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)).thenReturn(false);
    assertThat(module.getProtectionModifier(tool, entry, null, EquipmentSlot.MAINHAND, nonBypassing, 12f)).isEqualTo(12f);
    verify(nonBypassing).is(DamageTypeTags.BYPASSES_ENCHANTMENTS);
  }

  @Test
  void protection_getDefaultHooks_hasEnchantmentsAndProtectionHooks() {
    EnchantmentModule.Protection module = new EnchantmentModule.Protection(SHARPNESS, LevelingInt.ONE, ModifierCondition.ANY_TOOL);
    assertThat(module.getDefaultHooks()).hasSize(2);
  }

  @Test
  void protection_getLoader_returnsOwnStaticLoader() {
    EnchantmentModule.Protection module = new EnchantmentModule.Protection(SHARPNESS, LevelingInt.ONE, ModifierCondition.ANY_TOOL);
    assertThat(module.getLoader()).isSameAs(EnchantmentModule.Protection.LOADER);
  }

  @Test
  void protection_deprecatedConstructor_delegatesToCanonical() {
    EnchantmentModule.Protection module = new EnchantmentModule.Protection(SHARPNESS, 3, ModifierCondition.ANY_TOOL);
    IToolStackView tool = mock(IToolStackView.class);
    assertThat(module.updateEnchantmentLevel(tool, new ModifierEntry(ID, 2), SHARPNESS, 0)).isEqualTo(6); // eachLevel(3) at level 2
  }


  /* MainHandHarvest */

  @Test
  void mainHandHarvest_startHarvest_allMatch_setsPersistentFlag() {
    EnchantmentModule.MainHandHarvest module = new EnchantmentModule.MainHandHarvest(SHARPNESS, LevelingInt.ONE, ModifierCondition.ANY_TOOL, FLAG, blockPredicate(true), holderPredicate(true));
    IToolStackView tool = mock(IToolStackView.class);
    ModDataNBT persistentData = new ModDataNBT();
    when(tool.getPersistentData()).thenReturn(persistentData);

    module.startHarvest(tool, new ModifierEntry(ID, 1), harvestContext());
    assertThat(persistentData.getBoolean(FLAG)).isTrue();
  }

  @Test
  void mainHandHarvest_startHarvest_conditionFails_doesNotSetFlag() {
    EnchantmentModule.MainHandHarvest module = new EnchantmentModule.MainHandHarvest(SHARPNESS, LevelingInt.ONE, ModifierCondition.ANY_TOOL.minLevel(99), FLAG, blockPredicate(true), holderPredicate(true));
    IToolStackView tool = mock(IToolStackView.class);
    ModDataNBT persistentData = new ModDataNBT();
    when(tool.getPersistentData()).thenReturn(persistentData);

    module.startHarvest(tool, new ModifierEntry(ID, 1), harvestContext());
    assertThat(persistentData.getBoolean(FLAG)).isFalse();
  }

  @Test
  void mainHandHarvest_startHarvest_blockPredicateFails_doesNotSetFlag() {
    EnchantmentModule.MainHandHarvest module = new EnchantmentModule.MainHandHarvest(SHARPNESS, LevelingInt.ONE, ModifierCondition.ANY_TOOL, FLAG, blockPredicate(false), holderPredicate(true));
    IToolStackView tool = mock(IToolStackView.class);
    ModDataNBT persistentData = new ModDataNBT();
    when(tool.getPersistentData()).thenReturn(persistentData);

    module.startHarvest(tool, new ModifierEntry(ID, 1), harvestContext());
    assertThat(persistentData.getBoolean(FLAG)).isFalse();
  }

  @Test
  void mainHandHarvest_startHarvest_holderPredicateFails_doesNotSetFlag() {
    EnchantmentModule.MainHandHarvest module = new EnchantmentModule.MainHandHarvest(SHARPNESS, LevelingInt.ONE, ModifierCondition.ANY_TOOL, FLAG, blockPredicate(true), holderPredicate(false));
    IToolStackView tool = mock(IToolStackView.class);
    ModDataNBT persistentData = new ModDataNBT();
    when(tool.getPersistentData()).thenReturn(persistentData);

    module.startHarvest(tool, new ModifierEntry(ID, 1), harvestContext());
    assertThat(persistentData.getBoolean(FLAG)).isFalse();
  }

  @Test
  void mainHandHarvest_finishHarvest_removesPersistentFlag() {
    EnchantmentModule.MainHandHarvest module = new EnchantmentModule.MainHandHarvest(SHARPNESS, LevelingInt.ONE, ModifierCondition.ANY_TOOL, FLAG, blockPredicate(true), holderPredicate(true));
    IToolStackView tool = mock(IToolStackView.class);
    ModDataNBT persistentData = new ModDataNBT();
    persistentData.putBoolean(FLAG, true);
    when(tool.getPersistentData()).thenReturn(persistentData);

    module.finishHarvest(tool, new ModifierEntry(ID, 1), null, 3);
    assertThat(persistentData.getBoolean(FLAG)).isFalse();
  }

  @Test
  void mainHandHarvest_updateEnchantmentLevel_matchingEnchantmentAndFlagSet_addsLevel() {
    EnchantmentModule.MainHandHarvest module = new EnchantmentModule.MainHandHarvest(SHARPNESS, LevelingInt.flat(4), ModifierCondition.ANY_TOOL, FLAG, blockPredicate(true), holderPredicate(true));
    IToolStackView tool = mock(IToolStackView.class);
    ModDataNBT persistentData = new ModDataNBT();
    persistentData.putBoolean(FLAG, true);
    when(tool.getPersistentData()).thenReturn(persistentData);

    int result = module.updateEnchantmentLevel(tool, new ModifierEntry(ID, 1), SHARPNESS, 5);
    assertThat(result).isEqualTo(9);
  }

  @Test
  void mainHandHarvest_updateEnchantmentLevel_flagNotSet_returnsUnchanged() {
    EnchantmentModule.MainHandHarvest module = new EnchantmentModule.MainHandHarvest(SHARPNESS, LevelingInt.flat(4), ModifierCondition.ANY_TOOL, FLAG, blockPredicate(true), holderPredicate(true));
    IToolStackView tool = mock(IToolStackView.class);
    when(tool.getPersistentData()).thenReturn(new ModDataNBT());

    int result = module.updateEnchantmentLevel(tool, new ModifierEntry(ID, 1), SHARPNESS, 5);
    assertThat(result).isEqualTo(5);
  }

  @Test
  void mainHandHarvest_updateEnchantmentLevel_differentEnchantment_returnsUnchanged() {
    EnchantmentModule.MainHandHarvest module = new EnchantmentModule.MainHandHarvest(SHARPNESS, LevelingInt.flat(4), ModifierCondition.ANY_TOOL, FLAG, blockPredicate(true), holderPredicate(true));
    IToolStackView tool = mock(IToolStackView.class);
    ModDataNBT persistentData = new ModDataNBT();
    persistentData.putBoolean(FLAG, true);
    when(tool.getPersistentData()).thenReturn(persistentData);

    int result = module.updateEnchantmentLevel(tool, new ModifierEntry(ID, 1), UNBREAKING, 5);
    assertThat(result).isEqualTo(5);
  }

  @Test
  void mainHandHarvest_updateEnchantments_flagSet_addsToMap() {
    EnchantmentModule.MainHandHarvest module = new EnchantmentModule.MainHandHarvest(SHARPNESS, LevelingInt.flat(2), ModifierCondition.ANY_TOOL, FLAG, blockPredicate(true), holderPredicate(true));
    IToolStackView tool = mock(IToolStackView.class);
    ModDataNBT persistentData = new ModDataNBT();
    persistentData.putBoolean(FLAG, true);
    when(tool.getPersistentData()).thenReturn(persistentData);

    Map<ResourceKey<Enchantment>,Integer> map = new HashMap<>();
    module.updateEnchantments(tool, new ModifierEntry(ID, 1), map);
    assertThat(map).containsEntry(SHARPNESS, 2);
  }

  @Test
  void mainHandHarvest_updateEnchantments_flagNotSet_leavesMapEmpty() {
    EnchantmentModule.MainHandHarvest module = new EnchantmentModule.MainHandHarvest(SHARPNESS, LevelingInt.flat(2), ModifierCondition.ANY_TOOL, FLAG, blockPredicate(true), holderPredicate(true));
    IToolStackView tool = mock(IToolStackView.class);
    when(tool.getPersistentData()).thenReturn(new ModDataNBT());

    Map<ResourceKey<Enchantment>,Integer> map = new HashMap<>();
    module.updateEnchantments(tool, new ModifierEntry(ID, 1), map);
    assertThat(map).isEmpty();
  }

  @Test
  void mainHandHarvest_getDefaultHooks_hasEnchantmentsAndBlockHarvestHooks() {
    EnchantmentModule.MainHandHarvest module = new EnchantmentModule.MainHandHarvest(SHARPNESS, LevelingInt.ONE, ModifierCondition.ANY_TOOL, FLAG, blockPredicate(true), holderPredicate(true));
    assertThat(module.getDefaultHooks()).hasSize(2);
  }

  @Test
  void mainHandHarvest_getLoader_returnsStaticLoader() {
    EnchantmentModule.MainHandHarvest module = new EnchantmentModule.MainHandHarvest(SHARPNESS, LevelingInt.ONE, ModifierCondition.ANY_TOOL, FLAG, blockPredicate(true), holderPredicate(true));
    assertThat(module.getLoader()).isSameAs(EnchantmentModule.MainHandHarvest.LOADER);
  }

  @Test
  void mainHandHarvest_deprecatedIntLevelConstructor_convertsToEachLevel() {
    EnchantmentModule.MainHandHarvest module = new EnchantmentModule.MainHandHarvest(SHARPNESS, 3, ModifierCondition.ANY_TOOL, FLAG, blockPredicate(true), holderPredicate(true));
    IToolStackView tool = mock(IToolStackView.class);
    ModDataNBT persistentData = new ModDataNBT();
    persistentData.putBoolean(FLAG, true);
    when(tool.getPersistentData()).thenReturn(persistentData);

    int result = module.updateEnchantmentLevel(tool, new ModifierEntry(ID, 2), SHARPNESS, 0);
    assertThat(result).isEqualTo(6); // eachLevel(3) at level 2
  }

  @Test
  void mainHandHarvest_loader_jsonRoundTrips() {
    EnchantmentModule.MainHandHarvest original = new EnchantmentModule.MainHandHarvest(SHARPNESS, new LevelingInt(1, 1), ModifierCondition.ANY_TOOL, FLAG, BlockPredicate.ANY, LivingEntityPredicate.ANY);
    JsonObject json = new JsonObject();
    EnchantmentModule.MainHandHarvest.LOADER.serialize(original, json);
    EnchantmentModule.MainHandHarvest parsed = EnchantmentModule.MainHandHarvest.LOADER.deserialize(json);
    assertThat(parsed.conditionFlag()).isEqualTo(FLAG);
    assertThat(parsed.enchantment()).isEqualTo(SHARPNESS);
  }

  @Test
  void mainHandHarvest_loader_networkRoundTrips() {
    EnchantmentModule.MainHandHarvest original = new EnchantmentModule.MainHandHarvest(SHARPNESS, new LevelingInt(1, 1), ModifierCondition.ANY_TOOL, FLAG, BlockPredicate.ANY, LivingEntityPredicate.ANY);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    EnchantmentModule.MainHandHarvest.LOADER.encode(buffer, original);
    EnchantmentModule.MainHandHarvest decoded = EnchantmentModule.MainHandHarvest.LOADER.decode(buffer);
    assertThat(decoded.conditionFlag()).isEqualTo(FLAG);
    assertThat(decoded.enchantment()).isEqualTo(SHARPNESS);
  }


  /* ArmorHarvest */

  @Test
  void armorHarvest_slotMatchesAndAllPredicatesTrue_addsToMap() {
    EnchantmentModule.ArmorHarvest module = new EnchantmentModule.ArmorHarvest(SHARPNESS, LevelingInt.flat(3), ModifierCondition.ANY_TOOL, Set.of(EquipmentSlot.HEAD), blockPredicate(true), holderPredicate(true));
    IToolStackView tool = mock(IToolStackView.class);
    Map<ResourceKey<Enchantment>,Integer> map = new HashMap<>();

    module.updateHarvestEnchantments(tool, new ModifierEntry(ID, 1), harvestContext(), null, EquipmentSlot.HEAD, map);
    assertThat(map).containsEntry(SHARPNESS, 3);
  }

  @Test
  void armorHarvest_slotNotInSet_doesNotAdd() {
    EnchantmentModule.ArmorHarvest module = new EnchantmentModule.ArmorHarvest(SHARPNESS, LevelingInt.flat(3), ModifierCondition.ANY_TOOL, Set.of(EquipmentSlot.HEAD), blockPredicate(true), holderPredicate(true));
    IToolStackView tool = mock(IToolStackView.class);
    Map<ResourceKey<Enchantment>,Integer> map = new HashMap<>();

    module.updateHarvestEnchantments(tool, new ModifierEntry(ID, 1), harvestContext(), null, EquipmentSlot.CHEST, map);
    assertThat(map).isEmpty();
  }

  @Test
  void armorHarvest_conditionFails_doesNotAdd() {
    EnchantmentModule.ArmorHarvest module = new EnchantmentModule.ArmorHarvest(SHARPNESS, LevelingInt.flat(3), ModifierCondition.ANY_TOOL.minLevel(99), Set.of(EquipmentSlot.HEAD), blockPredicate(true), holderPredicate(true));
    IToolStackView tool = mock(IToolStackView.class);
    Map<ResourceKey<Enchantment>,Integer> map = new HashMap<>();

    module.updateHarvestEnchantments(tool, new ModifierEntry(ID, 1), harvestContext(), null, EquipmentSlot.HEAD, map);
    assertThat(map).isEmpty();
  }

  @Test
  void armorHarvest_blockPredicateFails_doesNotAdd() {
    EnchantmentModule.ArmorHarvest module = new EnchantmentModule.ArmorHarvest(SHARPNESS, LevelingInt.flat(3), ModifierCondition.ANY_TOOL, Set.of(EquipmentSlot.HEAD), blockPredicate(false), holderPredicate(true));
    IToolStackView tool = mock(IToolStackView.class);
    Map<ResourceKey<Enchantment>,Integer> map = new HashMap<>();

    module.updateHarvestEnchantments(tool, new ModifierEntry(ID, 1), harvestContext(), null, EquipmentSlot.HEAD, map);
    assertThat(map).isEmpty();
  }

  @Test
  void armorHarvest_holderPredicateFails_doesNotAdd() {
    EnchantmentModule.ArmorHarvest module = new EnchantmentModule.ArmorHarvest(SHARPNESS, LevelingInt.flat(3), ModifierCondition.ANY_TOOL, Set.of(EquipmentSlot.HEAD), blockPredicate(true), holderPredicate(false));
    IToolStackView tool = mock(IToolStackView.class);
    Map<ResourceKey<Enchantment>,Integer> map = new HashMap<>();

    module.updateHarvestEnchantments(tool, new ModifierEntry(ID, 1), harvestContext(), null, EquipmentSlot.HEAD, map);
    assertThat(map).isEmpty();
  }

  @Test
  void armorHarvest_getDefaultHooks_hasHarvestEnchantmentsHook() {
    EnchantmentModule.ArmorHarvest module = new EnchantmentModule.ArmorHarvest(SHARPNESS, LevelingInt.ONE, ModifierCondition.ANY_TOOL, Set.of(EquipmentSlot.HEAD), blockPredicate(true), holderPredicate(true));
    assertThat(module.getDefaultHooks()).hasSize(1);
  }

  @Test
  void armorHarvest_getLoader_returnsStaticLoader() {
    EnchantmentModule.ArmorHarvest module = new EnchantmentModule.ArmorHarvest(SHARPNESS, LevelingInt.ONE, ModifierCondition.ANY_TOOL, Set.of(EquipmentSlot.HEAD), blockPredicate(true), holderPredicate(true));
    assertThat(module.getLoader()).isSameAs(EnchantmentModule.ArmorHarvest.LOADER);
  }

  @Test
  void armorHarvest_deprecatedIntLevelConstructor_convertsToEachLevel() {
    EnchantmentModule.ArmorHarvest module = new EnchantmentModule.ArmorHarvest(SHARPNESS, 3, ModifierCondition.ANY_TOOL, Set.of(EquipmentSlot.HEAD), blockPredicate(true), holderPredicate(true));
    IToolStackView tool = mock(IToolStackView.class);
    Map<ResourceKey<Enchantment>,Integer> map = new HashMap<>();

    module.updateHarvestEnchantments(tool, new ModifierEntry(ID, 2), harvestContext(), null, EquipmentSlot.HEAD, map);
    assertThat(map).containsEntry(SHARPNESS, 6); // eachLevel(3) at level 2
  }

  @Test
  void armorHarvest_loader_jsonRoundTrips() {
    EnchantmentModule.ArmorHarvest original = new EnchantmentModule.ArmorHarvest(SHARPNESS, new LevelingInt(1, 1), ModifierCondition.ANY_TOOL, Set.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST), BlockPredicate.ANY, LivingEntityPredicate.ANY);
    JsonObject json = new JsonObject();
    EnchantmentModule.ArmorHarvest.LOADER.serialize(original, json);
    EnchantmentModule.ArmorHarvest parsed = EnchantmentModule.ArmorHarvest.LOADER.deserialize(json);
    assertThat(parsed.slots()).isEqualTo(Set.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST));
    assertThat(parsed.enchantment()).isEqualTo(SHARPNESS);
  }

  @Test
  void armorHarvest_loader_networkRoundTrips() {
    EnchantmentModule.ArmorHarvest original = new EnchantmentModule.ArmorHarvest(SHARPNESS, new LevelingInt(1, 1), ModifierCondition.ANY_TOOL, Set.of(EquipmentSlot.HEAD), BlockPredicate.ANY, LivingEntityPredicate.ANY);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    EnchantmentModule.ArmorHarvest.LOADER.encode(buffer, original);
    EnchantmentModule.ArmorHarvest decoded = EnchantmentModule.ArmorHarvest.LOADER.decode(buffer);
    assertThat(decoded.slots()).isEqualTo(Set.of(EquipmentSlot.HEAD));
    assertThat(decoded.enchantment()).isEqualTo(SHARPNESS);
  }


  /* Builder */

  @Test
  void builder_constant_withBlockCondition_throws() {
    assertThatThrownBy(() -> EnchantmentModule.builder(SHARPNESS).block(blockPredicate(false)).constant())
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void builder_constant_withHolderCondition_throws() {
    assertThatThrownBy(() -> EnchantmentModule.builder(SHARPNESS).holder(holderPredicate(false)).constant())
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void builder_constant_buildsConstantWithConfiguredLevel() {
    EnchantmentModule.Constant module = EnchantmentModule.builder(SHARPNESS).lootingLevel(LevelingInt.flat(7)).constant();
    IToolStackView tool = mock(IToolStackView.class);
    assertThat(module.updateEnchantmentLevel(tool, new ModifierEntry(ID, 1), SHARPNESS, 0)).isEqualTo(7);
  }

  @Test
  void builder_protection_withBlockCondition_throws() {
    assertThatThrownBy(() -> EnchantmentModule.builder(SHARPNESS).block(blockPredicate(false)).protection())
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void builder_protection_withHolderCondition_throws() {
    assertThatThrownBy(() -> EnchantmentModule.builder(SHARPNESS).holder(holderPredicate(false)).protection())
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void builder_protection_buildsProtectionInstance() {
    EnchantmentModule.Protection module = EnchantmentModule.builder(SHARPNESS).protection();
    assertThat(module).isInstanceOf(EnchantmentModule.Protection.class);
  }

  @Test
  void builder_mainHandHarvest_buildsWithGivenKeyBlockHolder() {
    IJsonPredicate<BlockState> block = blockPredicate(true);
    IJsonPredicate<LivingEntity> holder = holderPredicate(true);
    EnchantmentModule.MainHandHarvest module = EnchantmentModule.builder(SHARPNESS).block(block).holder(holder).mainHandHarvest(FLAG);
    assertThat(module.conditionFlag()).isEqualTo(FLAG);
    assertThat(module.block()).isSameAs(block);
    assertThat(module.holder()).isSameAs(holder);
  }

  @Test
  void builder_armorHarvest_emptySlots_throws() {
    assertThatThrownBy(() -> EnchantmentModule.builder(SHARPNESS).armorHarvest(new EquipmentSlot[0]))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void builder_armorHarvest_containsMainHand_throws() {
    assertThatThrownBy(() -> EnchantmentModule.builder(SHARPNESS).armorHarvest(EquipmentSlot.MAINHAND, EquipmentSlot.HEAD))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void builder_armorHarvest_explicitSlots_usesGivenSet() {
    EnchantmentModule.ArmorHarvest module = EnchantmentModule.builder(SHARPNESS).armorHarvest(EquipmentSlot.HEAD, EquipmentSlot.FEET);
    assertThat(module.slots()).containsExactlyInAnyOrder(EquipmentSlot.HEAD, EquipmentSlot.FEET);
  }

  @Test
  void builder_armorHarvest_defaultSlots_usesApplicableSlots() {
    EnchantmentModule.ArmorHarvest module = EnchantmentModule.builder(SHARPNESS).armorHarvest();
    assertThat(module.slots()).containsExactlyInAnyOrder(EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
  }

  @Test
  void builder_deprecatedLevelMethod_setsEachLevelLootingLevel() {
    EnchantmentModule.Constant module = EnchantmentModule.builder(SHARPNESS).level(5).constant();
    IToolStackView tool = mock(IToolStackView.class);
    assertThat(module.updateEnchantmentLevel(tool, new ModifierEntry(ID, 2), SHARPNESS, 0)).isEqualTo(10); // eachLevel(5) at level 2
  }
}
