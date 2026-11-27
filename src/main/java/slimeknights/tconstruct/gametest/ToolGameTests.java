package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.recipe.partbuilder.Pattern;
import slimeknights.tconstruct.library.tools.capability.fluid.ToolTankHelper;
import slimeknights.tconstruct.library.tools.capability.inventory.ToolInventoryCapability;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.item.armor.ModifiableArmorItem;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolDataVerifier;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import slimeknights.tconstruct.library.tools.part.IToolPart;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.library.utils.TagUtil;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tables.block.entity.table.PartBuilderBlockEntity;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;
import slimeknights.tconstruct.tools.TinkerToolParts;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.data.ModifierIds;
import slimeknights.tconstruct.tools.data.material.MaterialIds;
import slimeknights.tconstruct.tools.logic.InteractionHandler;
import slimeknights.tconstruct.tools.menu.ToolContainerMenu;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Gametests for the tool crafting subsystem: assembling parts into a working tool at a tinker station. */
public class ToolGameTests {
  /** Fails unless the armor item's equippable uses the given equipment asset, keeping iron's equip sound */
  static void assertEquipmentAsset(GameTestHelper helper, Item item, ResourceKey<EquipmentAsset> expected) {
    Equippable equippable = new ItemStack(item).get(DataComponents.EQUIPPABLE);
    helper.assertTrue(equippable != null && equippable.assetId().equals(Optional.of(expected)),
      Component.literal(BuiltInRegistries.ITEM.getKey(item) + " has equipment asset " + (equippable == null ? null : equippable.assetId()) + ", expected " + expected.identifier()));
    helper.assertTrue(equippable.equipSound().equals(ArmorMaterials.IRON.equipSound()), Component.literal(BuiltInRegistries.ITEM.getKey(item) + " lost iron's equip sound: " + equippable.equipSound()));
  }

  /** Armor drawn from armor models uses the Tinkers equipment asset (vanilla draws nothing), the slime wings the one with a wings layer (hides the cape) */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 20)
  public static void armor_equipment_assets(GameTestHelper helper) {
    Consumer<ModifiableArmorItem> modelArmor = item -> assertEquipmentAsset(helper, item, ModifiableArmorItem.EQUIPMENT_ASSET);
    TinkerTools.plateArmor.forEach(modelArmor);
    TinkerTools.travelersGear.forEach(modelArmor);
    TinkerTools.slimesuit.forEach(modelArmor);
    assertEquipmentAsset(helper, TinkerTools.slimeWings.get(), ModifiableArmorItem.WINGS_EQUIPMENT_ASSET);
    helper.succeed();
  }


  /**
   * A stone pick head, wood tool handle, and wood tool binding are placed in a tinker station's input
   * slots and assembled into a pickaxe through the same seam the real menu uses:
   * {@link TinkerStationBlockEntity#calcResult} looks up the tool building recipe from the level's
   * {@code RecipeManager} (matching on part items, see {@code ToolBuildingRecipe.matches}) and validates
   * the built result (see {@code ToolBuildingRecipe.getValidatedResult}). Asserts the crafted stack is a
   * real pickaxe whose {@link ToolStack} has positive durability and the 3 expected part materials.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tool_crafting(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
    helper.getLevel().setBlockAndUpdate(pos, TinkerTables.tinkerStation.get().defaultBlockState());
    if (!(helper.getLevel().getBlockEntity(pos) instanceof TinkerStationBlockEntity station)) {
      helper.fail(Component.literal("no tinker station BE"), helper.relativePos(pos));
      return;
    }

    ItemStack head = partStack(TinkerToolParts.pickHead.get(), MaterialIds.rock);
    ItemStack handle = partStack(TinkerToolParts.toolHandle.get(), MaterialIds.wood);
    ItemStack binding = partStack(TinkerToolParts.toolBinding.get(), MaterialIds.wood);

    // input slots follow the pickaxe's part order - head, handle, binding, per PartStatsModule.parts()
    // in ToolDefinitionDataProvider (also the ToolPartsHook consulted by ToolBuildingRecipe.matches).
    // The tool slot (index 0) stays empty since we are building a new tool, not modifying one.
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT, head);
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 1, handle);
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 2, binding);

    // no world ticking is needed for a tinker station recipe (unlike smeltery melting/casting), so the
    // result is computed and asserted directly instead of polling via succeedWhen
    ItemStack result = station.calcResult(null);
    if (result.isEmpty()) {
      throw new IllegalStateException("Tinker station found no recipe for parts [" + head + ", " + handle + ", " + binding
                                      + "]; station error=" + station.getCurrentError());
    }
    helper.assertTrue(result.is(TinkerTools.pickaxe.get()), Component.literal("expected a pickaxe, got " + result));

    ToolStack tool = ToolStack.from(result);
    int durability = tool.getStats().getInt(ToolStats.DURABILITY);
    helper.assertTrue(durability > 0, Component.literal("expected positive durability, got " + durability));
    helper.assertTrue(tool.getMaterials().size() == 3, Component.literal("expected 3 materials, got " + tool.getMaterials().size()));
    helper.assertTrue(tool.getMaterials().get(0).getVariant().equals(MaterialIds.rock), Component.literal("expected rock head material, got " + tool.getMaterials().get(0).getVariant()));
    helper.assertTrue(tool.getMaterials().get(1).getVariant().equals(MaterialIds.wood), Component.literal("expected wood handle material, got " + tool.getMaterials().get(1).getVariant()));
    helper.assertTrue(tool.getMaterials().get(2).getVariant().equals(MaterialIds.wood), Component.literal("expected wood binding material, got " + tool.getMaterials().get(2).getVariant()));

    helper.succeed();
  }

  /**
   * {@link ModifierUtil#getModifierLevel(ItemStack, ModifierId)} reads the level straight from the stack NBT (used by
   * skull traits, loot conditions and armor textures). 1.21.5 made {@code CompoundTag#getString} return an
   * {@code Optional}, and comparing it with {@code String#equals} still compiles but never matches, so the level
   * silently read as 0. Asserts it agrees with the {@link ToolStack} for every modifier of a crafted pickaxe.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void modifier_level_from_stack(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
    helper.getLevel().setBlockAndUpdate(pos, TinkerTables.tinkerStation.get().defaultBlockState());
    if (!(helper.getLevel().getBlockEntity(pos) instanceof TinkerStationBlockEntity station)) {
      helper.fail(Component.literal("no tinker station BE"), helper.relativePos(pos));
      return;
    }
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT, partStack(TinkerToolParts.pickHead.get(), MaterialIds.rock));
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 1, partStack(TinkerToolParts.toolHandle.get(), MaterialIds.wood));
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 2, partStack(TinkerToolParts.toolBinding.get(), MaterialIds.wood));
    ItemStack result = station.calcResult(null);
    helper.assertFalse(result.isEmpty(), Component.literal("tinker station built nothing; error=" + station.getCurrentError()));

    ToolStack tool = ToolStack.from(result);
    helper.assertFalse(tool.getModifierList().isEmpty(), Component.literal("crafted pickaxe has no modifiers to check"));
    for (ModifierEntry entry : tool.getModifierList()) {
      int expected = tool.getModifierLevel(entry.getId());
      int actual = ModifierUtil.getModifierLevel(result, entry.getId());
      helper.assertTrue(actual == expected, Component.literal("modifier " + entry.getId() + ": stack read " + actual + ", tool has " + expected));
    }
    helper.succeed();
  }

  /**
   * Mirrors {@link #tool_crafting} for {@link TinkerTools#handAxe}: an iron small axe head, wood tool handle,
   * and wood tool binding are assembled at a tinker station into a hand axe. Parts and order (head, handle,
   * binding) come from {@code ToolDefinitions.HAND_AXE} in {@code ToolDefinitionDataProvider} - the same shape
   * as the pickaxe, but a different tool/part family, and an iron (tier 2) head instead of rock to confirm a
   * higher-tier base material threads through as cleanly as tier 1.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void hand_axe_crafting(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
    helper.getLevel().setBlockAndUpdate(pos, TinkerTables.tinkerStation.get().defaultBlockState());
    if (!(helper.getLevel().getBlockEntity(pos) instanceof TinkerStationBlockEntity station)) {
      helper.fail(Component.literal("no tinker station BE"), helper.relativePos(pos));
      return;
    }

    ItemStack head = partStack(TinkerToolParts.smallAxeHead.get(), MaterialIds.iron);
    ItemStack handle = partStack(TinkerToolParts.toolHandle.get(), MaterialIds.wood);
    ItemStack binding = partStack(TinkerToolParts.toolBinding.get(), MaterialIds.wood);

    station.setItem(TinkerStationBlockEntity.INPUT_SLOT, head);
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 1, handle);
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 2, binding);

    ItemStack result = station.calcResult(null);
    if (result.isEmpty()) {
      throw new IllegalStateException("Tinker station found no recipe for parts [" + head + ", " + handle + ", " + binding
                                      + "]; station error=" + station.getCurrentError());
    }
    helper.assertTrue(result.is(TinkerTools.handAxe.get()), Component.literal("expected a hand axe, got " + result));

    ToolStack tool = ToolStack.from(result);
    int durability = tool.getStats().getInt(ToolStats.DURABILITY);
    helper.assertTrue(durability > 0, Component.literal("expected positive durability, got " + durability));
    helper.assertTrue(tool.getMaterials().size() == 3, Component.literal("expected 3 materials, got " + tool.getMaterials().size()));
    helper.assertTrue(tool.getMaterials().get(0).getVariant().equals(MaterialIds.iron), Component.literal("expected iron head material, got " + tool.getMaterials().get(0).getVariant()));
    helper.assertTrue(tool.getMaterials().get(1).getVariant().equals(MaterialIds.wood), Component.literal("expected wood handle material, got " + tool.getMaterials().get(1).getVariant()));
    helper.assertTrue(tool.getMaterials().get(2).getVariant().equals(MaterialIds.wood), Component.literal("expected wood binding material, got " + tool.getMaterials().get(2).getVariant()));

    helper.succeed();
  }

  /**
   * {@link TinkerTools#sword} takes 3 parts, but two of them are the very same part item: {@code smallBlade}
   * plus {@code toolHandle} twice, per {@code ToolDefinitions.SWORD} in {@code ToolDefinitionDataProvider} -
   * unlike the pickaxe's 3 distinct part types. The second handle slot gets a different material (iron instead
   * of wood) to confirm the tinker station keeps per-slot materials independent even when two slots hold the
   * same part item.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void sword_crafting(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
    helper.getLevel().setBlockAndUpdate(pos, TinkerTables.tinkerStation.get().defaultBlockState());
    if (!(helper.getLevel().getBlockEntity(pos) instanceof TinkerStationBlockEntity station)) {
      helper.fail(Component.literal("no tinker station BE"), helper.relativePos(pos));
      return;
    }

    ItemStack blade = partStack(TinkerToolParts.smallBlade.get(), MaterialIds.rock);
    ItemStack handle1 = partStack(TinkerToolParts.toolHandle.get(), MaterialIds.wood);
    ItemStack handle2 = partStack(TinkerToolParts.toolHandle.get(), MaterialIds.iron);

    station.setItem(TinkerStationBlockEntity.INPUT_SLOT, blade);
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 1, handle1);
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 2, handle2);

    ItemStack result = station.calcResult(null);
    if (result.isEmpty()) {
      throw new IllegalStateException("Tinker station found no recipe for parts [" + blade + ", " + handle1 + ", " + handle2
                                      + "]; station error=" + station.getCurrentError());
    }
    helper.assertTrue(result.is(TinkerTools.sword.get()), Component.literal("expected a sword, got " + result));

    ToolStack tool = ToolStack.from(result);
    int durability = tool.getStats().getInt(ToolStats.DURABILITY);
    helper.assertTrue(durability > 0, Component.literal("expected positive durability, got " + durability));
    helper.assertTrue(tool.getMaterials().size() == 3, Component.literal("expected 3 materials, got " + tool.getMaterials().size()));
    helper.assertTrue(tool.getMaterials().get(0).getVariant().equals(MaterialIds.rock), Component.literal("expected rock blade material, got " + tool.getMaterials().get(0).getVariant()));
    helper.assertTrue(tool.getMaterials().get(1).getVariant().equals(MaterialIds.wood), Component.literal("expected wood handle material, got " + tool.getMaterials().get(1).getVariant()));
    helper.assertTrue(tool.getMaterials().get(2).getVariant().equals(MaterialIds.iron), Component.literal("expected iron handle material, got " + tool.getMaterials().get(2).getVariant()));

    helper.succeed();
  }

  /**
   * A 4-part large tool: hammer head, tough handle, and two large plates assembled into a sledge hammer, parts
   * per {@code ToolDefinitions.SLEDGE_HAMMER} (hammerHead, toughHandle, largePlate, largePlate) in
   * {@code ToolDefinitionDataProvider}. Unlike the 3-part tools above, {@code ToolBuildingRecipe.matches}
   * rejects a 4-part recipe at the base {@link TinkerTables#tinkerStation} (only 3 input slots) -
   * {@code ToolBuildingRecipe.requiresAnvil} returns true once a tool has 4 or more parts, so this test places
   * a {@link TinkerTables#tinkersAnvil} (6 slots) instead.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void sledge_hammer_crafting(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
    helper.getLevel().setBlockAndUpdate(pos, TinkerTables.tinkersAnvil.get().defaultBlockState());
    if (!(helper.getLevel().getBlockEntity(pos) instanceof TinkerStationBlockEntity station)) {
      helper.fail(Component.literal("no tinker's anvil BE"), helper.relativePos(pos));
      return;
    }

    ItemStack head = partStack(TinkerToolParts.hammerHead.get(), MaterialIds.rock);
    ItemStack handle = partStack(TinkerToolParts.toughHandle.get(), MaterialIds.wood);
    ItemStack plate1 = partStack(TinkerToolParts.largePlate.get(), MaterialIds.iron);
    ItemStack plate2 = partStack(TinkerToolParts.largePlate.get(), MaterialIds.iron);

    station.setItem(TinkerStationBlockEntity.INPUT_SLOT, head);
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 1, handle);
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 2, plate1);
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 3, plate2);

    ItemStack result = station.calcResult(null);
    if (result.isEmpty()) {
      throw new IllegalStateException("Tinker's anvil found no recipe for parts [" + head + ", " + handle + ", " + plate1 + ", " + plate2
                                      + "]; station error=" + station.getCurrentError());
    }
    helper.assertTrue(result.is(TinkerTools.sledgeHammer.get()), Component.literal("expected a sledge hammer, got " + result));

    ToolStack tool = ToolStack.from(result);
    int durability = tool.getStats().getInt(ToolStats.DURABILITY);
    helper.assertTrue(durability > 0, Component.literal("expected positive durability, got " + durability));
    helper.assertTrue(tool.getMaterials().size() == 4, Component.literal("expected 4 materials, got " + tool.getMaterials().size()));
    helper.assertTrue(tool.getMaterials().get(0).getVariant().equals(MaterialIds.rock), Component.literal("expected rock head material, got " + tool.getMaterials().get(0).getVariant()));
    helper.assertTrue(tool.getMaterials().get(1).getVariant().equals(MaterialIds.wood), Component.literal("expected wood handle material, got " + tool.getMaterials().get(1).getVariant()));
    helper.assertTrue(tool.getMaterials().get(2).getVariant().equals(MaterialIds.iron), Component.literal("expected iron plate material, got " + tool.getMaterials().get(2).getVariant()));
    helper.assertTrue(tool.getMaterials().get(3).getVariant().equals(MaterialIds.iron), Component.literal("expected iron plate material, got " + tool.getMaterials().get(3).getVariant()));

    helper.succeed();
  }

  /**
   * A wood tool handle is built at a part builder from an oak plank and a pattern, through the block entity's
   * own result seam: {@link PartBuilderBlockEntity#calcResult} looks up the currently selected recipe and, if
   * it matches the inventory, assembles it (see {@code PartRecipe.matches}/{@code PartRecipe.assemble}).
   * Unlike the tinker station, the part builder needs an explicit recipe selection - {@code selectRecipe},
   * mirroring a player clicking a pattern button - because several recipes accept the same generic pattern
   * item and a plank (pick_head, hammer_head, tool_handle, ... are all "available" per
   * {@code ToolsRecipeProvider.partRecipes}); the tool_handle {@link Pattern} (its item's resource id) is what
   * disambiguates which one actually gets crafted. No world ticking is needed, so the result is asserted
   * directly, same as {@link #tool_crafting}. Asserts the crafted stack is a tool handle whose material is wood.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void part_builder_crafting(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
    helper.getLevel().setBlockAndUpdate(pos, TinkerTables.partBuilder.get().defaultBlockState());
    if (!(helper.getLevel().getBlockEntity(pos) instanceof PartBuilderBlockEntity builder)) {
      helper.fail(Component.literal("no part builder BE"), helper.relativePos(pos));
      return;
    }

    builder.setItem(PartBuilderBlockEntity.PATTERN_SLOT, new ItemStack(TinkerTables.pattern.get()));
    builder.setItem(PartBuilderBlockEntity.MATERIAL_SLOT, new ItemStack(Items.OAK_PLANKS));
    builder.selectRecipe(new Pattern(BuiltInRegistries.ITEM.getKey(TinkerToolParts.toolHandle.get().asItem())));

    ItemStack result = builder.calcResult(null);
    if (result.isEmpty()) {
      throw new IllegalStateException("Part builder found no tool_handle result for an oak plank + pattern");
    }
    helper.assertTrue(result.is(TinkerToolParts.toolHandle.get()), Component.literal("expected a tool handle, got " + result));
    helper.assertTrue(MaterialIds.wood.equals(IMaterialItem.getMaterialFromStack(result)), Component.literal("expected wood material, got " + IMaterialItem.getMaterialFromStack(result)));

    helper.succeed();
  }

  /* Lazy verification (1.21.9 removed Item#verifyComponentsAfterLoad, see ToolDataVerifier) */

  /** Material redirected to rose gold by the Tinkers data ({@code tinkering/materials/definition/chain.json}) */
  private static final MaterialId REDIRECTED_CHAIN = new MaterialId(TConstruct.MOD_ID, "chain");
  /** Stats key in the tool data, see {@code ToolStack.TAG_STATS} */
  private static final String TAG_STATS = "tic_stats";

  /** Builds a rock/wood/wood pickaxe through the normal tool building path */
  private static ItemStack buildPickaxe() {
    return ToolBuildHandler.buildItemFromMaterials(TinkerTools.pickaxe.get(), MaterialNBT.of(MaterialVariant.of(MaterialIds.rock, ""), MaterialVariant.of(MaterialIds.wood, ""), MaterialVariant.of(MaterialIds.wood, "")));
  }

  /** Simulates a pickaxe saved before a data change: a fresh custom data instance (as decoding gives) with a redirected head material and no stats */
  private static ItemStack staleLoadedPickaxe() {
    ItemStack built = buildPickaxe();
    CompoundTag tag = Objects.requireNonNull(built.get(DataComponents.CUSTOM_DATA)).copyTag();
    ListTag materials = TagUtil.getList(tag, ToolStack.TAG_MATERIALS, Tag.TAG_STRING);
    materials.set(0, StringTag.valueOf(REDIRECTED_CHAIN.toString()));
    tag.remove(TAG_STATS);
    ItemStack loaded = built.copy();
    loaded.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    return loaded;
  }

  /** Live tool tag of the stack */
  private static CompoundTag liveTag(ItemStack stack) {
    return Objects.requireNonNull(stack.get(DataComponents.CUSTOM_DATA)).tag;
  }

  /**
   * A tool loaded with stale data is verified on its first server read: the redirected material is resolved and the stats are rebuilt, like the
   * removed {@code verifyComponentsAfterLoad} did on decode. The second read does not verify again (stats removed in place stay removed), until
   * the next data load starts a new generation.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tool_verified_once_on_server_read(GameTestHelper helper) {
    ItemStack loaded = staleLoadedPickaxe();
    helper.assertFalse(liveTag(loaded).contains(TAG_STATS), Component.literal("stale pickaxe should start without stats"));

    // first read: verified
    ToolStack tool = ToolStack.from(loaded);
    helper.assertTrue(MaterialIds.roseGold.equals(tool.getMaterials().get(0).getId()), Component.literal("expected the chain head redirected to rose gold, got " + tool.getMaterials().get(0).getVariant()));
    helper.assertTrue(liveTag(loaded).contains(TAG_STATS), Component.literal("expected the stats rebuilt on the first server read"));
    helper.assertTrue(tool.isSameStack(loaded), Component.literal("tool from the verified stack should be bound to its live tag"));
    helper.assertTrue(tool.getStats().getInt(ToolStats.DURABILITY) > 0, Component.literal("expected positive durability after the rebuild"));

    // second read: not verified again
    CustomData verified = loaded.get(DataComponents.CUSTOM_DATA);
    liveTag(loaded).remove(TAG_STATS);
    ToolStack.from(loaded);
    helper.assertTrue(loaded.get(DataComponents.CUSTOM_DATA) == verified, Component.literal("second read replaced the custom data"));
    helper.assertFalse(liveTag(loaded).contains(TAG_STATS), Component.literal("second read verified the stack again"));
    // copies share the verification
    ItemStack copy = loaded.copy();
    ToolStack.from(copy);
    helper.assertFalse(liveTag(copy).contains(TAG_STATS), Component.literal("a copy of a verified stack was verified again"));

    // next data load: verified again
    ToolDataVerifier.startGeneration();
    ToolStack.from(loaded);
    helper.assertTrue(liveTag(loaded).contains(TAG_STATS), Component.literal("expected the stats rebuilt after a new data generation"));
    helper.succeed();
  }

  /** A freshly built tool is already verified: its first read does not rebuild it. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void fresh_tool_not_verified_again(GameTestHelper helper) {
    ItemStack built = buildPickaxe();
    CustomData data = built.get(DataComponents.CUSTOM_DATA);
    helper.assertTrue(data != null && data.tag.contains(TAG_STATS), Component.literal("built pickaxe has no stats"));
    liveTag(built).remove(TAG_STATS);
    ToolStack.from(built);
    helper.assertTrue(built.get(DataComponents.CUSTOM_DATA) == data, Component.literal("reading a fresh tool replaced its custom data"));
    helper.assertFalse(liveTag(built).contains(TAG_STATS), Component.literal("reading a fresh tool rebuilt it"));
    helper.succeed();
  }

  /**
   * Reads off the logical server thread (client, render or worker threads) never verify the stack: the tool data keeps its stale content (a bound
   * tool may give the stack its own copy of the data, see ToolDataOwnership), but redirected materials read resolved, in memory only.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tool_not_verified_off_server_thread(GameTestHelper helper) throws InterruptedException {
    ItemStack loaded = staleLoadedPickaxe();
    ItemStack part = TinkerToolParts.pickHead.get().getDefaultInstance();
    CompoundTag partTag = new CompoundTag();
    partTag.putString(IMaterialItem.MATERIAL_TAG, REDIRECTED_CHAIN.toString());
    part.set(DataComponents.CUSTOM_DATA, CustomData.of(partTag));
    CustomData partData = part.get(DataComponents.CUSTOM_DATA);

    AtomicReference<Object> toolMaterial = new AtomicReference<>();
    AtomicReference<Object> partMaterial = new AtomicReference<>();
    AtomicReference<Throwable> error = new AtomicReference<>();
    Thread thread = new Thread(() -> {
      try {
        toolMaterial.set(ToolStack.from(loaded).getMaterials().get(0).getVariant());
        ToolStack.copyFrom(loaded);
        partMaterial.set(IMaterialItem.getMaterialFromStack(part));
      } catch (Throwable e) {
        error.set(e);
      }
    }, "tconstruct-gametest-off-thread-read");
    thread.start();
    thread.join(5000);
    helper.assertFalse(thread.isAlive(), Component.literal("off thread read did not finish within 5 seconds"));
    if (error.get() != null) {
      throw new IllegalStateException("off thread read failed", error.get());
    }
    String storedHead = TagUtil.getList(liveTag(loaded), ToolStack.TAG_MATERIALS, Tag.TAG_STRING).getString(0).orElse("");
    helper.assertTrue(!liveTag(loaded).contains(TAG_STATS) && REDIRECTED_CHAIN.toString().equals(storedHead), Component.literal("off thread tool read verified the stack"));
    helper.assertTrue(MaterialIds.roseGold.equals(toolMaterial.get()), Component.literal("off thread tool read should resolve the redirect in memory, got " + toolMaterial.get()));
    helper.assertTrue(MaterialIds.roseGold.equals(partMaterial.get()), Component.literal("off thread part read should resolve the redirect in memory, got " + partMaterial.get()));
    helper.assertTrue(part.get(DataComponents.CUSTOM_DATA) == partData, Component.literal("off thread part read changed the stack"));

    // the server read still verifies afterwards
    ToolStack.from(loaded);
    helper.assertTrue(liveTag(loaded).contains(TAG_STATS), Component.literal("server read after the off thread read did not verify"));
    helper.succeed();
  }

  /**
   * A copy made before the verification shares the old data; on its own read it takes the verified result of the first stack instead of verifying
   * again, as a copy of its own, so both end up with equal data and keep stacking, and later changes to the first stack do not leak into it.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void copy_takes_verified_result(GameTestHelper helper) {
    ItemStack loaded = staleLoadedPickaxe();
    ItemStack early = loaded.copy();
    ToolStack.from(loaded);
    CustomData verified = loaded.get(DataComponents.CUSTOM_DATA);
    helper.assertTrue(verified != null && verified.tag.contains(TAG_STATS), Component.literal("first stack was not verified"));
    // marker: a recompute would rebuild the stats, taking the result copies the data as is
    verified.tag.remove(TAG_STATS);
    ToolStack.copyFrom(early);
    CustomData taken = early.get(DataComponents.CUSTOM_DATA);
    helper.assertTrue(taken != null && taken != verified, Component.literal("early copy shares the verified instance owned by the first stack"));
    helper.assertFalse(taken.tag.contains(TAG_STATS), Component.literal("early copy verified again instead of taking the result"));
    helper.assertTrue(ItemStack.isSameItemSameComponents(loaded, early), Component.literal("early copy does not match the verified stack"));
    // changing the first stack in place leaves the early copy alone
    CompoundTag before = taken.tag.copy();
    ToolStack.from(loaded).setDamage(5);
    helper.assertTrue(liveTag(early).equals(before), Component.literal("changing the verified stack changed the early copy"));
    helper.assertFalse(ItemStack.matches(loaded, early), Component.literal("changed stack still matches the early copy"));
    helper.succeed();
  }

  /**
   * The tool container menu keeps a tool bound to the stack for its lifetime. Opening it copies the stack for the remote slots, and the inventory
   * tick binds the stack again: tank clicks must still reach the real stack (copies used to take the data ownership away, so the menu wrote to a
   * detached instance, voiding or duping fluid).
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tool_container_tank_keeps_writes(GameTestHelper helper) {
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    ToolStack built = ToolStack.createTool(TinkerTools.pickaxe.get(), TinkerTools.pickaxe.get().getToolDefinition(),
      MaterialNBT.of(MaterialVariant.of(MaterialIds.rock, ""), MaterialVariant.of(MaterialIds.wood, ""), MaterialVariant.of(MaterialIds.wood, "")));
    built.addModifier(ModifierIds.tank, 1);
    ItemStack stack = built.createStack();
    player.setItemSlot(EquipmentSlot.MAINHAND, stack);
    int slot = player.getInventory().getSelectedSlot();
    ToolContainerMenu menu = new ToolContainerMenu(0, player.getInventory(), stack, ToolInventoryCapability.getMenuHandler(stack), slot);
    // what opening the menu does to the stack: remote slot copies, then the held inventory tick binds it again
    ItemStack remote = stack.copy();
    stack.inventoryTick(helper.getLevel(), player, EquipmentSlot.MAINHAND);
    ToolStack.from(stack);

    menu.setCarried(new ItemStack(Items.WATER_BUCKET));
    helper.assertTrue(menu.clickMenuButton(player, 1), Component.literal("tank click was not handled"));
    helper.assertTrue(menu.getCarried().is(Items.BUCKET), Component.literal("water bucket was not emptied into the tool, carried " + menu.getCarried()));
    FluidStack inTool = ToolTankHelper.TANK_HELPER.getFluid(ToolStack.copyFrom(stack));
    helper.assertTrue(inTool.is(Fluids.WATER) && inTool.getAmount() == FluidType.BUCKET_VOLUME, Component.literal("the real stack does not hold the water, found " + inTool));
    helper.assertTrue(ToolTankHelper.TANK_HELPER.getFluid(ToolStack.copyFrom(remote)).isEmpty(), Component.literal("the remote copy was filled too"));
    helper.succeed();
  }

  /**
   * 1.21.9 copies share the custom data instance: changing a tool in place must not change its copies (1.21.8 gave each copy its own tag through
   * the removed verifyComponentsAfterLoad), see ToolDataOwnership. Covers a copy taken after the original was bound and owns its data.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tool_changes_do_not_leak_into_copies(GameTestHelper helper) {
    ItemStack original = buildPickaxe();
    ToolStack.from(original).getStats();
    ItemStack copy = original.copy();
    ToolStack tool = ToolStack.from(original);
    tool.addModifier(ModifierIds.reinforced, 1);
    tool.setDamage(tool.getStats().getInt(ToolStats.DURABILITY));
    helper.assertTrue(ToolDamageUtil.isBroken(original), Component.literal("original did not break"));
    helper.assertFalse(ToolDamageUtil.isBroken(copy), Component.literal("breaking the original broke its copy"));
    helper.assertTrue(ToolStack.copyFrom(copy).getModifierLevel(ModifierIds.reinforced) == 0, Component.literal("modifying the original modified its copy"));
    helper.assertFalse(ItemStack.matches(original, copy), Component.literal("changed original still matches its copy"));
    helper.succeed();
  }

  /** The equipment snapshot of an entity is a copy: breaking the held tool in place must count as an equipment change (event, attributes). */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void breaking_held_tool_is_an_equipment_change(GameTestHelper helper) throws ReflectiveOperationException {
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    ItemStack held = buildPickaxe();
    player.setItemSlot(EquipmentSlot.MAINHAND, held);
    Method detect = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates");
    Method collect = LivingEntity.class.getDeclaredMethod("collectEquipmentChanges");
    detect.setAccessible(true);
    collect.setAccessible(true);
    detect.invoke(player);
    helper.assertTrue(collect.invoke(player) == null, Component.literal("equipment changed without a change"));
    ToolStack tool = ToolStack.from(held);
    tool.setDamage(tool.getStats().getInt(ToolStats.DURABILITY));
    helper.assertTrue(ToolDamageUtil.isBroken(held), Component.literal("held tool did not break"));
    Object changes = collect.invoke(player);
    helper.assertTrue(changes instanceof Map<?,?> map && map.containsKey(EquipmentSlot.MAINHAND), Component.literal("breaking the held tool in place was not seen as an equipment change"));
    helper.succeed();
  }

  /** Parts and material blocks resolve a redirected material on a server read and store the resolved one. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void part_material_redirected_on_server_read(GameTestHelper helper) {
    ItemStack part = TinkerToolParts.pickHead.get().getDefaultInstance();
    CompoundTag tag = new CompoundTag();
    tag.putString(IMaterialItem.MATERIAL_TAG, REDIRECTED_CHAIN.toString());
    part.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

    MaterialVariantId material = IMaterialItem.getMaterialFromStack(part);
    helper.assertTrue(MaterialIds.roseGold.equals(material), Component.literal("expected chain redirected to rose gold, got " + material));
    String stored = liveTag(part).getStringOr(IMaterialItem.MATERIAL_TAG, "");
    helper.assertTrue(MaterialIds.roseGold.toString().equals(stored), Component.literal("expected rose gold stored on the part, got " + stored));
    // an unredirected part is left alone
    ItemStack iron = TinkerToolParts.pickHead.get().withMaterial(MaterialIds.iron);
    CustomData ironData = iron.get(DataComponents.CUSTOM_DATA);
    IMaterialItem.getMaterialFromStack(iron);
    helper.assertTrue(iron.get(DataComponents.CUSTOM_DATA) == ironData, Component.literal("reading an unredirected part replaced its data"));
    helper.succeed();
  }

  /**
   * A chestplate interaction with an empty hand takes over the entity interaction ({@code InteractionHandler#afterEntityInteract})
   * and calls {@code Entity#interact(player, hand, location)} itself. 26.1 dropped the hit location from that path, so the handler
   * takes it from the {@code EntityInteractSpecific} event NeoForge fires just before for the same click. Clicks an armor stand at
   * head height through the same hooks as the server packet handler while the player looks away from it (a location recomputed
   * from the player's view would miss the stand and swap nothing): the helmet must move to the player's hand, the boots stay.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 20)
  public static void chestplate_entity_interaction_uses_click_location(GameTestHelper helper) {
    ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(2, 1, 2));
    stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
    stand.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(TinkerTools.travelersGear.get(ArmorType.CHESTPLATE)));
    Vec3 standPos = stand.position();
    player.setPos(standPos.x - 2, standPos.y, standPos.z);
    player.setYRot(90); // facing -x, away from the stand
    player.setXRot(0);

    Vec3 location = new Vec3(0, 1.8, 0); // relative to the stand: head height
    InteractionResult result = CommonHooks.onInteractEntityAt(player, stand, location, InteractionHand.MAIN_HAND);
    if (result == null) {
      result = player.interactOn(stand, InteractionHand.MAIN_HAND, location);
    }
    helper.assertTrue(result.consumesAction(), Component.literal("interaction did not consume the action: " + result));
    helper.assertTrue(player.getMainHandItem().is(Items.IRON_HELMET), Component.literal("expected the helmet in hand, got " + player.getMainHandItem()));
    helper.assertTrue(stand.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), Component.literal("helmet still on the stand"));
    helper.assertTrue(stand.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS), Component.literal("boots were taken instead"));
    helper.assertFalse(InteractionHandler.hasCapturedClickLocation(), Component.literal("click location left captured after the interaction"));

    // a canceled entity interaction clears the captured location too (no stale player/target kept until the next click)
    CommonHooks.onInteractEntityAt(player, stand, location, InteractionHand.MAIN_HAND);
    helper.assertTrue(InteractionHandler.hasCapturedClickLocation(), Component.literal("specific interaction did not capture the click location"));
    PlayerInteractEvent.EntityInteract canceled = new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, stand);
    canceled.setCanceled(true);
    NeoForge.EVENT_BUS.post(canceled);
    helper.assertFalse(InteractionHandler.hasCapturedClickLocation(), Component.literal("click location left captured after a canceled interaction"));

    // paths where the plain event never follows keep nothing: a canceled specific event, a spectator (interactOn returns before the plain event)
    PlayerInteractEvent.EntityInteractSpecific canceledSpecific = new PlayerInteractEvent.EntityInteractSpecific(player, InteractionHand.MAIN_HAND, stand, location);
    canceledSpecific.setCanceled(true);
    NeoForge.EVENT_BUS.post(canceledSpecific);
    helper.assertFalse(InteractionHandler.hasCapturedClickLocation(), Component.literal("a canceled specific interaction captured the click location"));
    Player spectator = helper.makeMockPlayer(GameType.SPECTATOR);
    CommonHooks.onInteractEntityAt(spectator, stand, location, InteractionHand.MAIN_HAND);
    helper.assertFalse(InteractionHandler.hasCapturedClickLocation(), Component.literal("a spectator click captured the click location"));
    helper.succeed();
  }

  /**
   * Every Tinkers creative tab builds: NeoForge 26.1 fails a tab whose contents repeat a stack, which drops the whole tab and its items from JEI.
   * M7 Phase 4: the Tools tab (tool variants of materials only usable by fallback parts, e.g. string on a crossbow) and the Smeltery tab (the
   * gold arrow cast added with each cast type) failed.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 20)
  public static void creative_tabs_build(GameTestHelper helper) {
    MinecraftServer server = helper.getLevel().getServer();
    CreativeModeTab.ItemDisplayParameters parameters = new CreativeModeTab.ItemDisplayParameters(server.getWorldData().enabledFeatures(), true, server.registryAccess());
    List<String> failures = new java.util.ArrayList<>();
    int tabs = 0;
    for (var entry : BuiltInRegistries.CREATIVE_MODE_TAB.entrySet()) {
      if (!TConstruct.MOD_ID.equals(entry.getKey().identifier().getNamespace())) {
        continue;
      }
      tabs++;
      try {
        entry.getValue().buildContents(parameters);
        if (entry.getValue().getDisplayItems().isEmpty()) {
          failures.add(entry.getKey().identifier() + " is empty");
        }
      } catch (RuntimeException e) {
        failures.add(entry.getKey().identifier() + ": " + e.getMessage());
      }
    }
    helper.assertTrue(tabs > 0, Component.literal("no tconstruct creative tabs"));
    helper.assertTrue(failures.isEmpty(), Component.literal(failures.size() + " creative tab(s) fail to build: " + String.join("; ", failures)));
    helper.succeed();
  }
  /**
   * Builds a tool part item stack of the given material.
   * @throws IllegalStateException if the part rejects the material, which would otherwise silently
   *                               produce an unmaterialed part and risk a vacuous pass or a confusing
   *                               failure further down in the recipe lookup
   */
  private static ItemStack partStack(IToolPart part, MaterialId material) {
    ItemStack stack = part.withMaterial(material);
    if (!material.equals(IMaterialItem.getMaterialFromStack(stack))) {
      throw new IllegalStateException("Part " + BuiltInRegistries.ITEM.getKey(part.asItem()) + " rejected material " + material);
    }
    return stack;
  }
}
