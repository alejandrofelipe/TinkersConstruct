package slimeknights.tconstruct.gametest;

import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.recipe.FluidValues;
import slimeknights.tconstruct.library.tools.capability.inventory.InventoryModule;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.library.utils.TagUtil;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.MelterBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.module.MeltingModuleInventory;
import slimeknights.tconstruct.smeltery.item.ScaledFluidTank;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tables.block.entity.chest.PartChestBlockEntity;
import slimeknights.tconstruct.tables.block.entity.inventory.IChestItemHandler;
import slimeknights.tconstruct.tables.block.entity.table.CraftingStationBlockEntity;
import slimeknights.tconstruct.tables.block.entity.table.PartBuilderBlockEntity;
import slimeknights.tconstruct.tables.block.entity.table.RetexturedTableBlockEntity;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;
import slimeknights.tconstruct.tools.TinkerToolParts;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.data.ModifierIds;
import slimeknights.tconstruct.tools.data.material.MaterialIds;
import slimeknights.tconstruct.tools.entity.ModifiableArrow;
import slimeknights.tconstruct.tools.entity.ThrownShuriken;
import slimeknights.tconstruct.tools.entity.ThrownTool;

import java.util.ArrayList;
import java.util.List;

/**
 * Saved-world compatibility gametests for the 1.21.8 {@code ValueInput}/{@code ValueOutput} migration (M4 D1/D2).
 * <p>
 * 1.21.8 moved block entity and entity serialization from raw {@link CompoundTag}s to value IO; the on-disk NBT must keep
 * the 1.21.5 keys and shapes so existing worlds load. Three kinds of checks:
 * <ul>
 *   <li><b>Round-trips:</b> populate a block entity/entity, save it to a {@link CompoundTag} the way chunk saving does, load
 *   the tag into a freshly created instance the way chunk loading does ({@code BlockEntity.loadStatic} / {@code EntityType.create}),
 *   and compare contents. Save and load must also report no serialization problems.</li>
 *   <li><b>On-disk keys:</b> where cheap, the saved tag's key names and tag types are asserted against the 1.21.5 code
 *   ({@code git show 1.21.5:<file>}).</li>
 *   <li><b>Legacy shapes:</b> hand-built tags in the shape 1.21.5 (and older) wrote, loaded into live block entities.</li>
 * </ul>
 */
public class SaveCompatGameTests {

  /* Block entity round-trips */

  /**
   * A melter holding items in its melting inventory, molten iron in its tank and a custom name survives save → load.
   * Keys per 1.21.5 {@code MelterBlockEntity}/{@code MeltingModuleInventory}/{@code FluidTank}/{@code NameableBlockEntity}:
   * {@code tank.Fluid}, {@code inventory.items[].slot} (int), {@code inventory.size} (int), {@code CustomName} (JSON string).
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void save_compat_melter_round_trip(GameTestHelper helper) {
    MelterBlockEntity melter = place(helper, new BlockPos(2, 1, 2), TinkerSmeltery.searedMelter.get(), MelterBlockEntity.class);
    MeltingModuleInventory inventory = melter.getItemHandler();
    inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
    inventory.setStackInSlot(2, new ItemStack(Items.GOLD_INGOT));
    FluidStack fluid = new FluidStack(TinkerFluids.moltenIron.get(), FluidValues.INGOT * 3);
    check(helper, melter.getTank().fill(fluid.copy(), FluidAction.EXECUTE) == fluid.getAmount(), "melter tank did not accept " + fluid.getAmount() + "mb");
    melter.setCustomName(Component.literal("Save Compat Melter"));

    CompoundTag tag = save(helper, melter);
    // on-disk shape
    CompoundTag tank = compound(helper, tag, "tank");
    CompoundTag savedFluid = compound(helper, tank, "Fluid");
    check(helper, savedFluid.get("id") instanceof StringTag && savedFluid.get("amount") instanceof IntTag, "tank.Fluid must hold id/amount, got " + savedFluid);
    CompoundTag savedInventory = compound(helper, tag, "inventory");
    check(helper, savedInventory.get("size") instanceof IntTag size && size.intValue() == 3, "inventory.size must be int 3, got " + savedInventory.get("size"));
    ListTag items = list(helper, savedInventory, "items");
    check(helper, items.size() == 2, "expected 2 saved melting slots, got " + items);
    for (Tag item : items) {
      check(helper, item instanceof CompoundTag entry && entry.get("slot") instanceof IntTag && entry.get("id") instanceof StringTag, "melting entry must hold int slot + item id, got " + item);
    }
    check(helper, tag.get("CustomName") instanceof StringTag, "CustomName must stay a JSON string, got " + tag.get("CustomName"));

    MelterBlockEntity loaded = loadFresh(helper, melter, tag, MelterBlockEntity.class);
    for (int i = 0; i < inventory.getSlots(); i++) {
      checkStack(helper, inventory.getStackInSlot(i), loaded.getItemHandler().getStackInSlot(i), "melter slot " + i);
    }
    checkFluid(helper, fluid, loaded.getTank().getFluid(), "melter tank");
    check(helper, Component.literal("Save Compat Melter").equals(loaded.getCustomName()), "melter custom name lost, got " + loaded.getCustomName());
    helper.succeed();
  }

  /**
   * A casting table holding a cast and a partial pour, and a casting basin holding a partial pour, survive save → load.
   * Keys per 1.21.5 {@code CastingBlockEntity}/{@code CastingFluidHandler}/Mantle {@code InventoryBlockEntity}:
   * {@code Items[].Slot} (byte), {@code tank.fluid}, {@code tank.capacity} (int).
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void save_compat_casting_round_trip(GameTestHelper helper) {
    // table: cast in the input, half an ingot poured
    CastingBlockEntity table = place(helper, new BlockPos(1, 1, 2), TinkerSmeltery.searedTable.get(), CastingBlockEntity.class);
    table.setItem(CastingBlockEntity.INPUT, new ItemStack(TinkerSmeltery.ingotCast.get()));
    FluidStack tableFluid = new FluidStack(TinkerFluids.moltenIron.get(), FluidValues.INGOT / 2);
    check(helper, table.getTank().fill(tableFluid.copy(), FluidAction.EXECUTE) == tableFluid.getAmount(), "casting table rejected molten iron over an ingot cast");
    int tableCapacity = table.getTank().getCapacity();

    CompoundTag tag = save(helper, table);
    ListTag items = list(helper, tag, "Items");
    check(helper, items.size() == 1 && items.getCompoundOrEmpty(0).get("Slot") instanceof ByteTag, "Items must hold one entry with a byte Slot, got " + items);
    CompoundTag tank = compound(helper, tag, "tank");
    check(helper, tank.get("fluid") instanceof CompoundTag && tank.get("capacity") instanceof IntTag, "tank must hold fluid + int capacity, got " + tank);

    CastingBlockEntity loaded = loadFresh(helper, table, tag, CastingBlockEntity.class);
    checkStack(helper, table.getItem(CastingBlockEntity.INPUT), loaded.getItem(CastingBlockEntity.INPUT), "casting table input");
    checkStack(helper, ItemStack.EMPTY, loaded.getItem(CastingBlockEntity.OUTPUT), "casting table output");
    checkFluid(helper, tableFluid, loaded.getTank().getFluid(), "casting table tank");
    check(helper, loaded.getTank().getCapacity() == tableCapacity, "casting table capacity " + tableCapacity + " became " + loaded.getTank().getCapacity());

    // basin: no cast, a partial iron block poured through the real fill path (the recipe lookup must accept a castless input)
    CastingBlockEntity basin = place(helper, new BlockPos(3, 1, 2), TinkerSmeltery.searedBasin.get(), CastingBlockEntity.class);
    FluidStack basinFluid = new FluidStack(TinkerFluids.moltenIron.get(), FluidValues.INGOT * 2);
    int basinFilled = basin.getTank().fill(basinFluid.copy(), FluidAction.EXECUTE);
    check(helper, basinFilled == basinFluid.getAmount(), "empty seared basin accepted " + basinFilled + "mb of " + basinFluid.getAmount() + "mb molten iron; no castless iron block recipe found");
    int basinCapacity = basin.getTank().getCapacity();
    check(helper, basinCapacity == FluidValues.METAL_BLOCK, "empty basin + molten iron should size the tank to the iron block recipe (" + FluidValues.METAL_BLOCK + "), got " + basinCapacity);
    CastingBlockEntity loadedBasin = loadFresh(helper, basin, save(helper, basin), CastingBlockEntity.class);
    checkFluid(helper, basinFluid, loadedBasin.getTank().getFluid(), "casting basin tank");
    check(helper, loadedBasin.getTank().getCapacity() == basinCapacity, "casting basin capacity " + basinCapacity + " became " + loadedBasin.getTank().getCapacity());

    // table without a cast: molten iron casts iron bars (smeltery/casting/metal/iron/bars.json, 30mb)
    CastingBlockEntity castless = place(helper, new BlockPos(2, 1, 4), TinkerSmeltery.searedTable.get(), CastingBlockEntity.class);
    int castlessFilled = castless.getTank().fill(new FluidStack(TinkerFluids.moltenIron.get(), 20), FluidAction.EXECUTE);
    check(helper, castlessFilled == 20, "empty casting table accepted " + castlessFilled + "mb of 20mb molten iron; no castless iron bars recipe found");
    check(helper, castless.getTank().getCapacity() == 30, "empty table + molten iron should size the tank to the iron bars recipe (30), got " + castless.getTank().getCapacity());
    helper.succeed();
  }

  /**
   * Crafting station, part builder and tinker station keep their items, texture and custom name through save → load.
   * Keys per 1.21.5 Mantle {@code InventoryBlockEntity}/{@code NameableBlockEntity} and {@code RetexturedTableBlockEntity}:
   * {@code Items[].Slot} (byte), {@code CustomName} (JSON string), {@code texture} (string).
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void save_compat_tables_round_trip(GameTestHelper helper) {
    CraftingStationBlockEntity crafting = place(helper, new BlockPos(1, 1, 1), TinkerTables.craftingStation.get(), CraftingStationBlockEntity.class);
    crafting.setItem(0, new ItemStack(Items.OAK_PLANKS, 3));
    crafting.setItem(4, new ItemStack(Items.STICK, 2));
    ItemStack named = new ItemStack(Items.IRON_INGOT);
    named.set(DataComponents.CUSTOM_NAME, Component.literal("Named Ingot"));
    crafting.setItem(8, named);
    crafting.setCustomName(Component.literal("Save Compat Station"));
    crafting.updateTexture("minecraft:oak_planks");

    CompoundTag tag = save(helper, crafting);
    ListTag items = list(helper, tag, "Items");
    check(helper, items.size() == 3, "expected 3 saved crafting station slots, got " + items);
    for (Tag item : items) {
      check(helper, item instanceof CompoundTag entry && entry.get("Slot") instanceof ByteTag, "Items entry must hold a byte Slot, got " + item);
    }
    check(helper, tag.get("CustomName") instanceof StringTag, "CustomName must stay a JSON string, got " + tag.get("CustomName"));
    check(helper, tag.get("texture") instanceof StringTag, "texture must be a string, got " + tag.get("texture"));
    CraftingStationBlockEntity loadedCrafting = loadFresh(helper, crafting, tag, CraftingStationBlockEntity.class);
    checkInventory(helper, crafting, loadedCrafting, "crafting station");
    check(helper, Component.literal("Save Compat Station").equals(loadedCrafting.getCustomName()), "crafting station custom name lost, got " + loadedCrafting.getCustomName());
    check(helper, crafting.getTextureName().equals(loadedCrafting.getTextureName()), "crafting station texture " + crafting.getTextureName() + " became " + loadedCrafting.getTextureName());

    PartBuilderBlockEntity partBuilder = place(helper, new BlockPos(3, 1, 1), TinkerTables.partBuilder.get(), PartBuilderBlockEntity.class);
    partBuilder.setItem(PartBuilderBlockEntity.MATERIAL_SLOT, new ItemStack(Items.OAK_PLANKS, 7));
    partBuilder.setItem(PartBuilderBlockEntity.PATTERN_SLOT, new ItemStack(TinkerTables.pattern.get(), 4));
    checkInventory(helper, partBuilder, loadFresh(helper, partBuilder, save(helper, partBuilder), PartBuilderBlockEntity.class), "part builder");

    TinkerStationBlockEntity station = place(helper, new BlockPos(1, 1, 3), TinkerTables.tinkerStation.get(), TinkerStationBlockEntity.class);
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT, TinkerToolParts.pickHead.get().withMaterial(MaterialIds.rock));
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 1, TinkerToolParts.toolHandle.get().withMaterial(MaterialIds.wood));
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 2, TinkerToolParts.toolBinding.get().withMaterial(MaterialIds.iron));
    checkInventory(helper, station, loadFresh(helper, station, save(helper, station), TinkerStationBlockEntity.class), "tinker station");
    helper.succeed();
  }

  /**
   * A part chest ({@code ScalingChestItemHandler}) keeps its items and its slot count through save → load. Guards two
   * 1.21.8 regressions: NeoForge's {@code ItemStackHandler#deserialize} no longer calls {@code onLoad}, which left the visual
   * size at 1 after a reload; and the chest saves only {@code Items} (no {@code Size}) under the same key as 1.21.5, so the
   * handler must keep its full slot count. {@code Slot} is now an unsigned byte (was int in 1.21.5; old ints still read,
   * see {@link #save_compat_legacy_chest_items}).
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void save_compat_part_chest_round_trip(GameTestHelper helper) {
    PartChestBlockEntity chest = place(helper, new BlockPos(2, 1, 2), TinkerTables.partChest.get(), PartChestBlockEntity.class);
    IChestItemHandler handler = chest.getItemHandler();
    handler.setStackInSlot(0, TinkerToolParts.pickHead.get().withMaterial(MaterialIds.rock));
    handler.setStackInSlot(1, TinkerToolParts.toolHandle.get().withMaterial(MaterialIds.wood).copyWithCount(2));
    handler.setStackInSlot(2, TinkerToolParts.toolBinding.get().withMaterial(MaterialIds.iron));
    handler.setStackInSlot(200, TinkerToolParts.pickHead.get().withMaterial(MaterialIds.iron));
    int visualSize = handler.getVisualSize();
    check(helper, visualSize == 202, "part chest visual size should be 202 before saving, got " + visualSize);

    CompoundTag tag = save(helper, chest);
    check(helper, !tag.contains("Size"), "chest must not save a Size key (1.21.5 did not), got " + tag.get("Size"));
    ListTag items = list(helper, tag, "Items");
    check(helper, items.size() == 4, "expected 4 saved chest slots, got " + items);
    for (Tag item : items) {
      check(helper, item instanceof CompoundTag entry && entry.get("Slot") instanceof ByteTag && entry.get("id") instanceof StringTag, "chest entry must hold a byte Slot + item id, got " + item);
    }

    PartChestBlockEntity loaded = loadFresh(helper, chest, tag, PartChestBlockEntity.class);
    IChestItemHandler loadedHandler = loaded.getItemHandler();
    check(helper, loadedHandler.getSlots() == handler.getSlots(), "part chest slot count " + handler.getSlots() + " became " + loadedHandler.getSlots());
    check(helper, loadedHandler.getVisualSize() == visualSize, "part chest visual size " + visualSize + " became " + loadedHandler.getVisualSize());
    for (int i = 0; i < handler.getSlots(); i++) {
      checkStack(helper, handler.getStackInSlot(i), loadedHandler.getStackInSlot(i), "part chest slot " + i);
    }
    helper.succeed();
  }


  /* Legacy shapes */

  /**
   * Melting inventories saved by older versions wrote {@code slot}/{@code size} as (unsigned) bytes; 1.21.5 already
   * read both. A variable size inventory (the smeltery's) must read byte size 200 and byte slot 150 as unsigned, and
   * the melter must read the legacy JSON string {@code CustomName}, an empty {@code {}} fluid and invalid item entries
   * ({@code {}}, id-less) without crashing.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void save_compat_legacy_melting_inventory(GameTestHelper helper) {
    MelterBlockEntity melter = place(helper, new BlockPos(2, 1, 2), TinkerSmeltery.searedMelter.get(), MelterBlockEntity.class);
    HolderLookup.Provider registries = helper.getLevel().registryAccess();

    // variable size inventory, as used by the smeltery
    MeltingModuleInventory variable = new MeltingModuleInventory(melter, melter.getTank(), Config.COMMON.melterOreRate);
    CompoundTag variableTag = new CompoundTag();
    variableTag.putByte("size", (byte) 200);
    ListTag variableItems = new ListTag();
    variableItems.add(meltingEntry(ByteTag.valueOf((byte) 150), "minecraft:iron_ingot"));
    variableTag.put("items", variableItems);
    variable.readFromTag(TagValueInput.create(ProblemReporter.DISCARDING, registries, variableTag));
    check(helper, variable.getSlots() == 200, "byte size 200 must read unsigned, got " + variable.getSlots());
    check(helper, variable.getStackInSlot(150).is(Items.IRON_INGOT), "byte slot 150 must read unsigned, got " + variable.getStackInSlot(150));

    // melter, byte slots, plus entries that must be skipped
    CompoundTag tag = new CompoundTag();
    CompoundTag inventory = new CompoundTag();
    inventory.putByte("size", (byte) 3);
    ListTag items = new ListTag();
    items.add(meltingEntry(ByteTag.valueOf((byte) 1), "minecraft:gold_ingot"));
    CompoundTag idless = new CompoundTag();
    idless.putByte("slot", (byte) 2);
    items.add(idless);
    items.add(new CompoundTag());
    inventory.put("items", items);
    tag.put("inventory", inventory);
    CompoundTag tank = new CompoundTag();
    tank.put("Fluid", new CompoundTag());
    tag.put("tank", tank);
    tag.putString("CustomName", "\"Legacy Melter\"");
    melter.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

    MeltingModuleInventory melterInventory = melter.getItemHandler();
    check(helper, melterInventory.getStackInSlot(0).isEmpty(), "melter slot 0 should be empty, got " + melterInventory.getStackInSlot(0));
    check(helper, melterInventory.getStackInSlot(1).is(Items.GOLD_INGOT), "melter slot 1 should hold the gold ingot, got " + melterInventory.getStackInSlot(1));
    check(helper, melterInventory.getStackInSlot(2).isEmpty(), "id-less melting entry should load as empty, got " + melterInventory.getStackInSlot(2));
    check(helper, melter.getTank().getFluid().isEmpty(), "{} fluid should load as empty, got " + melter.getTank().getFluid());
    check(helper, Component.literal("Legacy Melter").equals(melter.getCustomName()), "legacy JSON string CustomName not read, got " + melter.getCustomName());
    helper.succeed();
  }

  /**
   * Chest data saved by 1.21.5 (NeoForge {@code ItemStackHandler} entries) has an int {@code Slot} and no {@code Size}.
   * Int slots must read (including 200, above the signed byte range), invalid entries must be skipped without clobbering
   * slot 0 (the codec defaults a missing {@code Slot} to 0), and the slot count must stay at the chest's maximum.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void save_compat_legacy_chest_items(GameTestHelper helper) {
    PartChestBlockEntity chest = place(helper, new BlockPos(2, 1, 2), TinkerTables.partChest.get(), PartChestBlockEntity.class);
    HolderLookup.Provider registries = helper.getLevel().registryAccess();
    ItemStack head = TinkerToolParts.pickHead.get().withMaterial(MaterialIds.rock);
    ItemStack handle = TinkerToolParts.toolHandle.get().withMaterial(MaterialIds.wood).copyWithCount(2);

    CompoundTag tag = new CompoundTag();
    ListTag items = new ListTag();
    items.add(legacyChestEntry(registries, 0, head));
    items.add(legacyChestEntry(registries, 200, handle));
    CompoundTag idless = new CompoundTag();
    idless.putInt("Slot", 3);
    items.add(idless);
    items.add(new CompoundTag());
    tag.put("Items", items);
    int maxSlots = chest.getItemHandler().getSlots();
    chest.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

    IChestItemHandler handler = chest.getItemHandler();
    check(helper, handler.getSlots() == maxSlots, "legacy chest without Size must keep " + maxSlots + " slots, got " + handler.getSlots());
    checkStack(helper, head, handler.getStackInSlot(0), "legacy chest slot 0");
    checkStack(helper, handle, handler.getStackInSlot(200), "legacy chest int slot 200");
    check(helper, handler.getStackInSlot(3).isEmpty(), "id-less chest entry should load as empty, got " + handler.getStackInSlot(3));
    check(helper, handler.getVisualSize() == 202, "legacy chest visual size should be 202, got " + handler.getVisualSize());
    helper.succeed();
  }

  /**
   * A casting table saved by 1.21.5: byte {@code Slot}, a {@code {}} fluid (written by the old {@code FluidStack.saveOptional}
   * for an empty tank), and a basin with a real fluid entry in the 1.21.5 {@code FluidStack} shape ({@code id}/{@code amount}).
   * Also covers the item-side {@code {}} helpers and {@link ScaledFluidTank} not mutating the shared {@link FluidStack#EMPTY}.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void save_compat_legacy_casting_and_empty_entries(GameTestHelper helper) {
    HolderLookup.Provider registries = helper.getLevel().registryAccess();
    CastingBlockEntity table = place(helper, new BlockPos(1, 1, 2), TinkerSmeltery.searedTable.get(), CastingBlockEntity.class);
    CompoundTag tag = new CompoundTag();
    ListTag items = new ListTag();
    CompoundTag cast = new CompoundTag();
    cast.putByte("Slot", (byte) CastingBlockEntity.INPUT);
    cast.putString("id", "tconstruct:ingot_cast");
    cast.putInt("count", 1);
    items.add(cast);
    tag.put("Items", items);
    CompoundTag tank = new CompoundTag();
    tank.putInt("capacity", 0);
    tank.put("fluid", new CompoundTag());
    tag.put("tank", tank);
    table.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
    check(helper, table.getItem(CastingBlockEntity.INPUT).is(TinkerSmeltery.ingotCast.get()), "legacy casting table lost its cast, got " + table.getItem(CastingBlockEntity.INPUT));
    check(helper, table.getTank().getFluid().isEmpty(), "{} casting fluid should load as empty, got " + table.getTank().getFluid());

    CastingBlockEntity basin = place(helper, new BlockPos(3, 1, 2), TinkerSmeltery.searedBasin.get(), CastingBlockEntity.class);
    CompoundTag basinTag = new CompoundTag();
    CompoundTag basinTank = new CompoundTag();
    basinTank.putInt("capacity", FluidValues.METAL_BLOCK);
    CompoundTag fluid = new CompoundTag();
    fluid.putString("id", "tconstruct:molten_iron");
    fluid.putInt("amount", 180);
    basinTank.put("fluid", fluid);
    basinTank.putString("filter", "tconstruct:molten_iron");
    basinTag.put("tank", basinTank);
    basin.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, basinTag));
    checkFluid(helper, new FluidStack(TinkerFluids.moltenIron.get(), 180), basin.getTank().getFluid(), "legacy casting basin");
    check(helper, basin.getTank().getCapacity() == FluidValues.METAL_BLOCK, "legacy basin capacity lost, got " + basin.getTank().getCapacity());

    // {} item helpers
    check(helper, TagUtil.parseOptionalItem(registries, new CompoundTag()).isEmpty(), "{} item tag should parse as empty");
    CompoundTag slotOnly = new CompoundTag();
    slotOnly.putInt("Slot", 0);
    check(helper, TagUtil.parseOptionalItem(registries, slotOnly).isEmpty(), "{Slot}-only item tag should parse as empty");

    // {} into a scaled (item) tank must not scale the shared empty stack
    FluidTank scaled = ScaledFluidTank.create(FluidValues.INGOT, 4);
    scaled.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, registries, new CompoundTag()));
    check(helper, scaled.getFluid().isEmpty(), "{} scaled tank should load as empty, got " + scaled.getFluid());
    check(helper, FluidStack.EMPTY.isEmpty() && FluidStack.EMPTY.getAmount() == 0, "FluidStack.EMPTY was mutated, amount " + FluidStack.EMPTY.getAmount());
    helper.succeed();
  }

  /**
   * Tool modifier inventories saved by 1.21.1–1.21.5 may contain {@code {Slot}}-only entries (the old {@code writeStack}
   * dropped the stack). They must read as empty slots, without breaking the other entries in the list.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void save_compat_legacy_modifier_inventory(GameTestHelper helper) {
    InventoryModule module = InventoryModule.builder().flatSlots(3);
    ToolStack tool = ToolStack.from(pickaxe());
    ModifierEntry entry = new ModifierEntry(ModifierIds.toolBelt, 1);
    ListTag list = new ListTag();
    CompoundTag slotOnly = new CompoundTag();
    slotOnly.putInt(InventoryModule.TAG_SLOT, 0);
    list.add(slotOnly);
    CompoundTag stone = new CompoundTag();
    stone.putInt(InventoryModule.TAG_SLOT, 1);
    stone.putString("id", "minecraft:stone");
    stone.putInt("count", 3);
    list.add(stone);
    tool.getPersistentData().put(ModifierIds.toolBelt.getLocation(), list);

    check(helper, module.getStack(tool, entry, 0).isEmpty(), "{Slot}-only modifier inventory entry should read as empty, got " + module.getStack(tool, entry, 0));
    checkStack(helper, new ItemStack(Items.STONE, 3), module.getStack(tool, entry, 1), "modifier inventory slot 1");
    List<ItemStack> all = module.getAllStacks(tool, entry, new ArrayList<>());
    check(helper, all.size() == 1, "expected only the stone stack from the modifier inventory, got " + all);
    helper.succeed();
  }


  /* Modifier inventory writes */

  /**
   * {@link InventoryModule#writeStack} replacing an existing entry must not leak the old stack's count or components (in
   * 1.21.1–1.21.5 the encoded stack was discarded, losing it; the 1.21.8 merge must strip the stale keys first since the
   * codec omits the default count). Unrelated keys in the entry are kept. Also checked through {@code setStack}.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void save_compat_inventory_module_write_stack(GameTestHelper helper) {
    HolderLookup.Provider registries = helper.getLevel().registryAccess();
    ItemStack fiveNamed = new ItemStack(Items.STONE, 5);
    fiveNamed.set(DataComponents.CUSTOM_NAME, Component.literal("Five Stones"));
    CompoundTag compound = InventoryModule.writeStack(fiveNamed, 2, new CompoundTag());
    compound.putInt("time", 7);
    checkStack(helper, fiveNamed, TagUtil.parseOptionalItem(registries, compound), "first written stack");

    // count 5 named → count 1 plain
    InventoryModule.writeStack(new ItemStack(Items.STONE), 2, compound);
    ItemStack read = TagUtil.parseOptionalItem(registries, compound);
    checkStack(helper, new ItemStack(Items.STONE), read, "stack overwritten by a single plain stone");
    check(helper, !compound.contains("components"), "stale components survived the overwrite: " + compound);
    check(helper, compound.getIntOr("count", 1) == 1, "stale count survived the overwrite: " + compound);
    check(helper, compound.getIntOr(InventoryModule.TAG_SLOT, -1) == 2, "slot lost on overwrite: " + compound);
    check(helper, compound.getIntOr("time", 0) == 7, "unrelated key dropped on overwrite: " + compound);

    // plain → single named stack of a different item
    ItemStack namedDirt = new ItemStack(Items.DIRT);
    namedDirt.set(DataComponents.CUSTOM_NAME, Component.literal("One Dirt"));
    InventoryModule.writeStack(namedDirt, 2, compound);
    checkStack(helper, namedDirt, TagUtil.parseOptionalItem(registries, compound), "stack overwritten by a named dirt");

    // same through the module's setStack on a tool
    InventoryModule module = InventoryModule.builder().flatSlots(3);
    ToolStack tool = ToolStack.from(pickaxe());
    ModifierEntry entry = new ModifierEntry(ModifierIds.toolBelt, 1);
    module.setStack(tool, entry, 1, fiveNamed.copy());
    module.setStack(tool, entry, 1, new ItemStack(Items.STONE));
    checkStack(helper, new ItemStack(Items.STONE), module.getStack(tool, entry, 1), "setStack overwrite");
    helper.succeed();
  }


  /* Tool stacks */

  /**
   * A tool built at a tinker station (and one built through {@code ToolStack#createStack} directly) must survive
   * {@link ItemStack#CODEC}, the codec every inventory, container and entity save goes through. {@code createStack} marks
   * tools damageable with a {@code minecraft:max_damage} marker; that component is {@code POSITIVE_INT}, so the old
   * {@code -1} marker failed the encode and every built tool vanished from saves (1.21.4–1.21.8). The marker must keep the
   * stack damageable, with the real max damage still coming from the durability stat.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void save_compat_tool_stack_codec_round_trip(GameTestHelper helper) {
    TinkerStationBlockEntity station = place(helper, new BlockPos(2, 1, 2), TinkerTables.tinkerStation.get(), TinkerStationBlockEntity.class);
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT, TinkerToolParts.pickHead.get().withMaterial(MaterialIds.rock));
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 1, TinkerToolParts.toolHandle.get().withMaterial(MaterialIds.wood));
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 2, TinkerToolParts.toolBinding.get().withMaterial(MaterialIds.wood));
    ItemStack crafted = station.calcResult(null);
    check(helper, crafted.is(TinkerTools.pickaxe.get()), "tinker station built no pickaxe, got " + describe(crafted) + "; error=" + station.getCurrentError());
    checkToolCodec(helper, crafted, "crafted pickaxe");
    checkToolCodec(helper, damagedTool(pickaxe()), "damaged iron pickaxe");
    helper.succeed();
  }

  /**
   * The dagger is the one tool stackable to 2; its built stack must round-trip {@link ItemStack#CODEC} like any other tool
   * (no {@code max_damage} the codec rejects), and keep its durability.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void save_compat_dagger_codec_round_trip(GameTestHelper helper) {
    ItemStack dagger = ToolBuildHandler.createSingleMaterial(TinkerTools.dagger.get(), MaterialVariant.of((MaterialVariantId) MaterialIds.iron));
    check(helper, !dagger.isEmpty(), "could not build an iron dagger");
    checkToolCodec(helper, dagger, "iron dagger");
    checkToolCodec(helper, dagger.copyWithCount(2), "stack of 2 iron daggers");
    helper.succeed();
  }

  /**
   * A thrown tool keeps its tool stack (item, damage, persistent data) and its synced display stack through save → load.
   * The stack is vanilla's arrow pickup item, stored under {@code item} in both 1.21.5 and 1.21.8 ({@code AbstractArrow}).
   * The display stack (plus loyalty, shiny, indestructible and magnet) is refreshed on load only if {@code ThrownTool} sees
   * that key; it checked the pre-1.20.5 {@code Trident} key, so a reloaded thrown tool was invisible and lost loyalty.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void save_compat_thrown_tool_round_trip(GameTestHelper helper) {
    ItemStack stack = damagedTool(pickaxe());
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    ThrownTool thrown = new ThrownTool(helper.getLevel(), player, stack, 1f, 1.5f, 0.6f);
    CompoundTag tag = saveEntity(helper, thrown);
    CompoundTag item = compound(helper, tag, "item");
    check(helper, "tconstruct:pickaxe".equals(item.getStringOr("id", "")), "thrown tool stack must be saved under item, got " + item);
    check(helper, tag.get("multiplier") instanceof net.minecraft.nbt.FloatTag && tag.get("water_inertia") instanceof net.minecraft.nbt.FloatTag, "thrown tool floats missing: " + tag);

    ThrownTool loaded = loadEntity(helper, tag, ThrownTool.class);
    checkStack(helper, stack, loaded.getPickupItemStackOrigin(), "thrown tool stack");
    checkStack(helper, stack, loaded.getDisplayTool(), "thrown tool display stack");
    helper.succeed();
  }

  /**
   * A Tinkers arrow and a thrown shuriken keep their tool stack through save → load, stored under {@code stack} as in 1.21.5.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void save_compat_tool_projectiles_round_trip(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
    ItemStack arrowStack = damagedTool(ToolBuildHandler.buildItemRandomMaterials(TinkerTools.arrow.get(), RandomSource.create(42)));
    ModifiableArrow arrow = new ModifiableArrow(helper.getLevel(), pos.getX(), pos.getY(), pos.getZ());
    arrow.onCreate(arrowStack, null);
    CompoundTag arrowTag = saveEntity(helper, arrow);
    check(helper, "tconstruct:arrow".equals(compound(helper, arrowTag, "stack").getStringOr("id", "")), "arrow stack must be saved under stack, got " + arrowTag.get("stack"));
    ModifiableArrow loadedArrow = loadEntity(helper, arrowTag, ModifiableArrow.class);
    checkStack(helper, arrowStack.copyWithCount(1), loadedArrow.getPickupItem(), "arrow stack");
    checkStack(helper, arrowStack.copyWithCount(1), loadedArrow.getDisplayTool(), "arrow display stack");

    ItemStack shurikenStack = damagedTool(ToolBuildHandler.buildItemRandomMaterials(TinkerTools.shuriken.get(), RandomSource.create(42)));
    ThrownShuriken shuriken = new ThrownShuriken(helper.getLevel(), pos.getX(), pos.getY(), pos.getZ());
    shuriken.onCreate(shurikenStack, null);
    CompoundTag shurikenTag = saveEntity(helper, shuriken);
    check(helper, "tconstruct:shuriken".equals(compound(helper, shurikenTag, "stack").getStringOr("id", "")), "shuriken stack must be saved under stack, got " + shurikenTag.get("stack"));
    ThrownShuriken loadedShuriken = loadEntity(helper, shurikenTag, ThrownShuriken.class);
    checkStack(helper, shurikenStack.copyWithCount(1), loadedShuriken.getDisplayTool(), "shuriken stack");
    helper.succeed();
  }


  /* Helpers */

  /** Places a block at the relative position and returns its block entity */
  private static <T extends BlockEntity> T place(GameTestHelper helper, BlockPos relative, Block block, Class<T> clazz) {
    BlockPos pos = helper.absolutePos(relative);
    helper.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
    BlockEntity be = helper.getLevel().getBlockEntity(pos);
    if (!clazz.isInstance(be)) {
      throw new IllegalStateException("Expected a " + clazz.getSimpleName() + " at " + relative + ", got " + be);
    }
    return clazz.cast(be);
  }

  /** Saves the block entity like chunk saving does ({@code saveWithFullMetadata}), failing on any serialization problem */
  private static CompoundTag save(GameTestHelper helper, BlockEntity be) {
    ProblemReporter.Collector problems = new ProblemReporter.Collector();
    TagValueOutput output = TagValueOutput.createWithContext(problems, helper.getLevel().registryAccess());
    be.saveWithFullMetadata(output);
    checkNoProblems(helper, problems, "saving " + be.getType());
    return output.buildResult();
  }

  /**
   * Loads the tag into a freshly created block entity of the same type, like chunk loading does ({@code BlockEntity.loadStatic}:
   * create without a level, then {@code loadWithComponents}), failing on any serialization problem.
   */
  private static <T extends BlockEntity> T loadFresh(GameTestHelper helper, BlockEntity original, CompoundTag tag, Class<T> clazz) {
    BlockEntity fresh = original.getType().create(original.getBlockPos(), original.getBlockState());
    if (!clazz.isInstance(fresh)) {
      throw new IllegalStateException("Expected a fresh " + clazz.getSimpleName() + ", got " + fresh);
    }
    ProblemReporter.Collector problems = new ProblemReporter.Collector();
    fresh.loadWithComponents(TagValueInput.create(problems, helper.getLevel().registryAccess(), tag));
    checkNoProblems(helper, problems, "loading " + original.getType());
    return clazz.cast(fresh);
  }

  /** Saves the entity with its type ID like chunk saving does, failing on any serialization problem */
  private static CompoundTag saveEntity(GameTestHelper helper, Entity entity) {
    ProblemReporter.Collector problems = new ProblemReporter.Collector();
    TagValueOutput output = TagValueOutput.createWithContext(problems, helper.getLevel().registryAccess());
    check(helper, entity.save(output), "entity " + entity + " refused to save");
    checkNoProblems(helper, problems, "saving " + entity.getType());
    return output.buildResult();
  }

  /** Loads an entity from the tag like chunk loading does ({@link EntityType#create}), failing on any serialization problem */
  private static <T extends Entity> T loadEntity(GameTestHelper helper, CompoundTag tag, Class<T> clazz) {
    ProblemReporter.Collector problems = new ProblemReporter.Collector();
    Entity entity = EntityType.create(TagValueInput.create(problems, helper.getLevel().registryAccess(), tag), helper.getLevel(), EntitySpawnReason.LOAD).orElse(null);
    checkNoProblems(helper, problems, "loading entity " + tag.getStringOr("id", "?"));
    if (!clazz.isInstance(entity)) {
      throw new IllegalStateException("Expected a loaded " + clazz.getSimpleName() + ", got " + entity);
    }
    return clazz.cast(entity);
  }

  /** Builds an iron pickaxe */
  private static ItemStack pickaxe() {
    ItemStack stack = ToolBuildHandler.createSingleMaterial(TinkerTools.pickaxe.get(), MaterialVariant.of((MaterialVariantId) MaterialIds.iron));
    if (stack.isEmpty()) {
      throw new IllegalStateException("Could not build an iron pickaxe");
    }
    return stack;
  }

  /** Damages the tool and adds persistent data, so the round-trip compares more than the default components */
  private static ItemStack damagedTool(ItemStack stack) {
    ToolStack tool = ToolStack.from(stack);
    tool.setDamage(3);
    tool.getPersistentData().putInt(TConstruct.getResource("save_compat"), 42);
    return tool.createStack(stack.getCount());
  }

  /** Builds a melting module entry in the 1.21.5 shape */
  private static CompoundTag meltingEntry(Tag slot, String item) {
    CompoundTag entry = new CompoundTag();
    entry.put("slot", slot);
    entry.putString("id", item);
    entry.putInt("count", 1);
    entry.putInt("time", 5);
    entry.putInt("required", 100);
    entry.putInt("temp", 800);
    return entry;
  }

  /** Builds a chest entry in the 1.21.5 NeoForge {@code ItemStackHandler} shape: the encoded stack plus an int {@code Slot} */
  private static CompoundTag legacyChestEntry(HolderLookup.Provider registries, int slot, ItemStack stack) {
    CompoundTag entry = (CompoundTag) TagUtil.saveItem(registries, stack, new CompoundTag());
    entry.putInt("Slot", slot);
    return entry;
  }

  /** Gets a compound child, failing if missing or of another type */
  private static CompoundTag compound(GameTestHelper helper, CompoundTag parent, String key) {
    check(helper, parent.get(key) instanceof CompoundTag, "expected a compound under '" + key + "', got " + parent.get(key) + " in " + parent);
    return parent.getCompoundOrEmpty(key);
  }

  /** Gets a list child, failing if missing or of another type */
  private static ListTag list(GameTestHelper helper, CompoundTag parent, String key) {
    check(helper, parent.get(key) instanceof ListTag, "expected a list under '" + key + "', got " + parent.get(key) + " in " + parent);
    return parent.getListOrEmpty(key);
  }

  private static void checkInventory(GameTestHelper helper, RetexturedTableBlockEntity expected, RetexturedTableBlockEntity actual, String name) {
    check(helper, expected.getContainerSize() == actual.getContainerSize(), name + " size " + expected.getContainerSize() + " became " + actual.getContainerSize());
    for (int i = 0; i < expected.getContainerSize(); i++) {
      checkStack(helper, expected.getItem(i), actual.getItem(i), name + " slot " + i);
    }
  }

  /**
   * Asserts the tool stack encodes and decodes through {@link ItemStack#CODEC} with equal components, any {@code max_damage}
   * in its patch is positive, and it stays damageable with the durability stat as max damage.
   */
  private static void checkToolCodec(GameTestHelper helper, ItemStack stack, String name) {
    RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
    DataResult<Tag> encoded = ItemStack.CODEC.encodeStart(ops, stack);
    check(helper, encoded.isSuccess(), name + " " + describe(stack) + " failed to encode: " + encoded.error().map(DataResult.Error::message).orElse(""));
    DataResult<ItemStack> decoded = ItemStack.CODEC.parse(ops, encoded.getOrThrow());
    check(helper, decoded.isSuccess(), name + " failed to decode: " + decoded.error().map(DataResult.Error::message).orElse(""));
    ItemStack read = decoded.getOrThrow();
    check(helper, ItemStack.isSameItemSameComponents(stack, read) && read.getCount() == stack.getCount(), name + " changed through the codec: " + describe(stack) + " became " + describe(read));
    Integer marker = stack.get(DataComponents.MAX_DAMAGE);
    check(helper, marker == null || marker > 0, name + " has a non-positive max_damage " + marker);
    check(helper, stack.isDamageableItem() && read.isDamageableItem(), name + " is no longer damageable: " + describe(read));
    int durability = ToolStack.from(read).getStats().getInt(ToolStats.DURABILITY);
    check(helper, read.getMaxDamage() == durability, name + " max damage " + read.getMaxDamage() + " should be the durability stat " + durability);
  }

  private static void checkStack(GameTestHelper helper, ItemStack expected, ItemStack actual, String name) {
    check(helper, ItemStack.matches(expected, actual), name + ": expected " + describe(expected) + ", got " + describe(actual));
  }

  private static void checkFluid(GameTestHelper helper, FluidStack expected, FluidStack actual, String name) {
    check(helper, FluidStack.matches(expected, actual), name + ": expected " + expected.getAmount() + "mb " + expected.getFluid() + ", got " + actual.getAmount() + "mb " + actual.getFluid());
  }

  private static String describe(ItemStack stack) {
    return stack.isEmpty() ? "empty" : stack.getCount() + "x " + stack.getItem() + " " + stack.getComponentsPatch();
  }

  private static void checkNoProblems(GameTestHelper helper, ProblemReporter.Collector problems, String action) {
    check(helper, problems.isEmpty(), "serialization problems " + action + ":\n" + problems.getTreeReport());
  }

  private static void check(GameTestHelper helper, boolean condition, String message) {
    helper.assertTrue(condition, Component.literal(message));
  }
}
