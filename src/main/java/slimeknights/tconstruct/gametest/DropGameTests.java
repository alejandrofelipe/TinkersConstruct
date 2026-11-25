package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import slimeknights.tconstruct.tables.block.entity.inventory.ChestItemHandler;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tables.block.entity.chest.CastChestBlockEntity;
import slimeknights.tconstruct.world.TinkerHeadType;
import slimeknights.tconstruct.world.TinkerWorld;

import java.util.List;
import java.util.function.Consumer;

/** Gametests for block and mob drops: Tinkers heads from charged creepers and the cast chest keeping its inventory. */
public class DropGameTests {
  /* Heads */

  /** Spawns a charged creeper that does not move or explode */
  private static Creeper chargedCreeper(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    Creeper creeper = helper.spawnWithNoFreeWill(EntityType.CREEPER, new BlockPos(1, 1, 1));
    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
    if (bolt == null) {
      throw new IllegalStateException("could not create a lightning bolt");
    }
    creeper.thunderHit(level, bolt);
    creeper.clearFire();
    helper.assertTrue(creeper.isPowered(), Component.literal("creeper was not charged"));
    return creeper;
  }

  /** Kills the husk as if by the creeper's explosion */
  private static void killByCreeper(GameTestHelper helper, Husk husk, Creeper creeper) {
    ServerLevel level = helper.getLevel();
    husk.hurtServer(level, level.damageSources().mobAttack(creeper), Float.MAX_VALUE);
    helper.assertTrue(husk.isDeadOrDying(), Component.literal("husk survived"));
  }

  /** Counts the husk head items lying in the test area */
  private static int countHuskHeads(GameTestHelper helper) {
    Item head = TinkerWorld.heads.get(TinkerHeadType.HUSK).asItem();
    int count = 0;
    for (ItemEntity entity : helper.getEntities(EntityType.ITEM)) {
      if (entity.getItem().is(head)) {
        count += entity.getItem().getCount();
      }
    }
    return count;
  }

  /** A husk killed by a charged creeper drops exactly one Tinkers head */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void charged_creeper_drops_one_head(GameTestHelper helper) {
    Creeper creeper = chargedCreeper(helper);
    Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(3, 1, 3));
    killByCreeper(helper, husk, creeper);
    int heads = countHuskHeads(helper);
    helper.assertTrue(heads == 1, Component.literal("expected 1 husk head, got " + heads));
    helper.succeed();
  }

  /**
   * The head is only added to the event's drops: when another mod cancels {@link LivingDropsEvent} (after Tinkers added the head), no head appears.
   * Before the fix the head was spawned into the level right away by {@code spawnAtLocation} (and added again by vanilla, a duplicate UUID warning).
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void charged_creeper_head_respects_cancelled_drops(GameTestHelper helper) {
    Creeper creeper = chargedCreeper(helper);
    Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(3, 1, 3));
    // lowest priority so Tinkers' listener has added the head before the cancel; other tests run in parallel, so only cancel this husk's drops
    Consumer<LivingDropsEvent> cancel = event -> {
      if (event.getEntity() == husk) {
        helper.assertTrue(event.getDrops().stream().anyMatch(drop -> drop.getItem().is(TinkerWorld.heads.get(TinkerHeadType.HUSK).asItem())),
                          Component.literal("Tinkers did not add the head to the drops"));
        event.setCanceled(true);
      }
    };
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDropsEvent.class, cancel);
    try {
      killByCreeper(helper, husk, creeper);
    } finally {
      NeoForge.EVENT_BUS.unregister(cancel);
    }
    int heads = countHuskHeads(helper);
    helper.assertTrue(heads == 0, Component.literal("expected no husk head after cancelling the drops, got " + heads));
    helper.succeed();
  }


  /* Cast chest */

  /** Places the given cast chest item on top of the floor at the relative position, the way a player would */
  private static CastChestBlockEntity placeChest(GameTestHelper helper, ItemStack stack, BlockPos relative) {
    ServerLevel level = helper.getLevel();
    BlockPos floor = helper.absolutePos(relative.below());
    BlockPlaceContext context = new BlockPlaceContext(level, null, InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false));
    BlockPos placed = context.getClickedPos();
    InteractionResult result = ((BlockItem) stack.getItem()).place(context);
    helper.assertTrue(result.consumesAction(), Component.literal("cast chest was not placed: " + result));
    if (!(level.getBlockEntity(placed) instanceof CastChestBlockEntity chest)) {
      throw new IllegalStateException("no cast chest block entity at " + placed);
    }
    return chest;
  }

  /** Asserts the chest holds exactly the given stacks in its first slots, and nothing else */
  private static void assertContents(GameTestHelper helper, CastChestBlockEntity chest, ItemStack... expected) {
    ChestItemHandler inventory = chest.getItemHandler();
    for (int i = 0; i < inventory.size(); i++) {
      ItemStack actual = inventory.getStackInSlot(i);
      ItemStack wanted = i < expected.length ? expected[i] : ItemStack.EMPTY;
      helper.assertTrue(ItemStack.matches(actual, wanted), Component.literal("slot " + i + ": expected " + wanted + ", got " + actual));
    }
  }

  /** Breaks the cast chest at the position through its loot table, asserting it drops just the chest item, and removes the block */
  private static ItemStack breakChest(GameTestHelper helper, BlockPos pos) {
    ServerLevel level = helper.getLevel();
    BlockState state = level.getBlockState(pos);
    List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos));
    helper.assertTrue(drops.size() == 1 && drops.getFirst().is(TinkerTables.castChest.asItem()), Component.literal("expected a single cast chest drop, got " + drops));
    level.removeBlock(pos, false);
    return drops.getFirst();
  }

  /** Asserts the stack holds no block entity data and exactly the given stacks (in the first slots) in its container component */
  private static void assertDroppedContents(GameTestHelper helper, ItemStack drop, ItemStack... expected) {
    helper.assertFalse(drop.has(DataComponents.BLOCK_ENTITY_DATA), Component.literal("cast chest item has block entity data: " + drop.getComponentsPatch()));
    ItemContainerContents contents = drop.get(DataComponents.CONTAINER);
    helper.assertTrue(contents != null, Component.literal("cast chest item has no container contents: " + drop.getComponentsPatch()));
    List<ItemStack> stacks = contents.stream().toList();
    helper.assertTrue(stacks.size() == expected.length, Component.literal("expected " + expected.length + " container slots, got " + stacks));
    for (int i = 0; i < expected.length; i++) {
      helper.assertTrue(ItemStack.matches(stacks.get(i), expected[i]), Component.literal("container slot " + i + ": expected " + expected[i] + ", got " + stacks.get(i)));
    }
  }

  /** Asserts the stack is a plain cast chest, which stacks with a crafted one */
  private static void assertPlainChest(GameTestHelper helper, ItemStack drop, String what) {
    helper.assertTrue(ItemStack.isSameItemSameComponents(drop, new ItemStack(TinkerTables.castChest)),
                      Component.literal("expected a plain cast chest from " + what + ", got " + drop.getComponentsPatch()));
  }

  /**
   * Breaking a filled cast chest drops it with its inventory in {@code minecraft:container} (tooltip hidden), and placing the dropped item
   * restores it; the creative pick-block copy (block entity data without the items plus the components) restores it too. Empty and
   * emptied chests drop a plain item, which stacks with crafted ones.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void cast_chest_keeps_inventory_when_broken(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
    BlockState state = TinkerTables.castChest.get().defaultBlockState();
    level.setBlockAndUpdate(pos, state);
    if (!(level.getBlockEntity(pos) instanceof CastChestBlockEntity chest)) {
      throw new IllegalStateException("no cast chest block entity");
    }
    ItemStack ingots = new ItemStack(TinkerSmeltery.ingotCast.get(), 3);
    ItemStack nuggets = new ItemStack(TinkerSmeltery.nuggetCast.get(), 1);
    chest.getItemHandler().setStackInSlot(0, ingots.copy());
    chest.getItemHandler().setStackInSlot(1, nuggets.copy());

    // creative pick-block: like ServerGamePacketListenerImpl#addBlockDataToItem
    ItemStack picked = new ItemStack(TinkerTables.castChest);
    TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
    chest.saveCustomOnly(output);
    chest.removeComponentsFromTag(output);
    BlockItem.setBlockEntityData(picked, chest.getType(), output);
    picked.applyComponents(chest.collectComponents());
    TypedEntityData<?> pickedData = picked.get(DataComponents.BLOCK_ENTITY_DATA);
    helper.assertTrue(pickedData == null || !pickedData.copyTagWithoutId().contains("Items"), Component.literal("picked cast chest still holds the items in block entity data: " + pickedData));
    helper.assertTrue(picked.has(DataComponents.CONTAINER), Component.literal("picked cast chest has no container contents: " + picked.getComponentsPatch()));

    // loot table drop
    ItemStack drop = breakChest(helper, pos);
    assertDroppedContents(helper, drop, ingots, nuggets);
    TooltipDisplay tooltip = drop.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
    helper.assertFalse(tooltip.shows(DataComponents.CONTAINER), Component.literal("cast chest shows the container tooltip: " + tooltip));

    // place both again; the block entity keeps no copy of the applied component
    CastChestBlockEntity placed = placeChest(helper, drop, new BlockPos(3, 1, 3));
    assertContents(helper, placed, ingots, nuggets);
    helper.assertFalse(placed.components().has(DataComponents.CONTAINER), Component.literal("placed cast chest kept the container component: " + placed.components()));
    assertContents(helper, placeChest(helper, picked, new BlockPos(3, 1, 1)), ingots, nuggets);

    // emptied chest: plain drop
    for (int i = 0; i < placed.getItemHandler().size(); i++) {
      placed.getItemHandler().setStackInSlot(i, ItemStack.EMPTY);
    }
    assertPlainChest(helper, breakChest(helper, placed.getBlockPos()), "an emptied chest");

    // never filled chest: plain drop
    BlockPos emptyPos = helper.absolutePos(new BlockPos(1, 1, 3));
    level.setBlockAndUpdate(emptyPos, state);
    assertPlainChest(helper, breakChest(helper, emptyPos), "an empty chest");
    helper.succeed();
  }

  /**
   * Breaks the block like a player in the given game mode, following {@code ServerPlayerGameMode#destroyBlock}: {@code playerWillDestroy}, the
   * block is removed, then the loot drops through {@code playerDestroy} unless the player prevents block drops (creative)
   */
  private static void breakAsPlayer(GameTestHelper helper, BlockPos pos, GameType mode) {
    ServerLevel level = helper.getLevel();
    Player player = helper.makeMockPlayer(mode);
    mode.updatePlayerAbilities(player.getAbilities());
    BlockState state = level.getBlockState(pos);
    BlockEntity blockEntity = level.getBlockEntity(pos);
    Block block = state.getBlock();
    BlockState destroyed = block.playerWillDestroy(level, pos, state, player);
    level.removeBlock(pos, false);
    block.destroy(level, pos, destroyed);
    if (!player.preventsBlockDrops()) {
      block.playerDestroy(level, player, pos, destroyed, blockEntity, player.getMainHandItem().copy());
    }
  }

  /** Item entities lying in the block space at the position */
  private static List<ItemEntity> itemsAt(GameTestHelper helper, BlockPos pos) {
    return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos));
  }

  /**
   * Breaking a filled cast chest in creative drops no loot, so like a shulker box it drops the chest with its inventory instead
   * (nothing is lost); an empty chest drops nothing.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void cast_chest_keeps_inventory_when_broken_in_creative(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockState state = TinkerTables.castChest.get().defaultBlockState();
    BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
    level.setBlockAndUpdate(pos, state);
    if (!(level.getBlockEntity(pos) instanceof CastChestBlockEntity chest)) {
      throw new IllegalStateException("no cast chest block entity");
    }
    ItemStack ingots = new ItemStack(TinkerSmeltery.ingotCast.get(), 3);
    chest.getItemHandler().setStackInSlot(0, ingots.copy());
    breakAsPlayer(helper, pos, GameType.CREATIVE);
    helper.assertTrue(level.getBlockState(pos).isAir(), Component.literal("cast chest was not removed"));
    List<ItemEntity> drops = itemsAt(helper, pos);
    helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(TinkerTables.castChest.asItem()), Component.literal("expected a single cast chest item, got " + drops));
    assertDroppedContents(helper, drops.getFirst().getItem(), ingots);

    // empty chest: nothing to keep, so nothing drops
    BlockPos emptyPos = helper.absolutePos(new BlockPos(3, 1, 3));
    level.setBlockAndUpdate(emptyPos, state);
    breakAsPlayer(helper, emptyPos, GameType.CREATIVE);
    helper.assertTrue(itemsAt(helper, emptyPos).isEmpty(), Component.literal("empty cast chest dropped " + itemsAt(helper, emptyPos)));
    helper.succeed();
  }

  /**
   * Breaking a filled cast chest in survival drops exactly one chest item holding the inventory, from the loot table: the creative drop in
   * {@code playerWillDestroy} must not add a second one.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void cast_chest_drops_once_when_broken_in_survival(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
    level.setBlockAndUpdate(pos, TinkerTables.castChest.get().defaultBlockState());
    if (!(level.getBlockEntity(pos) instanceof CastChestBlockEntity chest)) {
      throw new IllegalStateException("no cast chest block entity");
    }
    ItemStack ingots = new ItemStack(TinkerSmeltery.ingotCast.get(), 3);
    chest.getItemHandler().setStackInSlot(0, ingots.copy());
    breakAsPlayer(helper, pos, GameType.SURVIVAL);
    helper.assertTrue(level.getBlockState(pos).isAir(), Component.literal("cast chest was not removed"));
    List<ItemEntity> drops = itemsAt(helper, pos);
    int total = drops.stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    helper.assertTrue(drops.size() == 1 && total == 1 && drops.getFirst().getItem().is(TinkerTables.castChest.asItem()),
                      Component.literal("expected exactly one cast chest item, got " + drops));
    assertDroppedContents(helper, drops.getFirst().getItem(), ingots);
    helper.succeed();
  }

  /**
   * Cast chests dropped before 1.21.10 hold the inventory in custom data ({@code TinkerData.Items}); placing one still restores it,
   * and breaking it again drops the inventory in the container component.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void cast_chest_reads_legacy_item_inventory(GameTestHelper helper) {
    ItemStack ingots = new ItemStack(TinkerSmeltery.ingotCast.get(), 2);
    ListTag items = new ListTag();
    items.add(ItemStackWithSlot.CODEC.encodeStart(helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE), new ItemStackWithSlot(0, ingots)).getOrThrow());
    CompoundTag tinkerData = new CompoundTag();
    tinkerData.put("Items", items);
    CompoundTag custom = new CompoundTag();
    custom.put("TinkerData", tinkerData);
    ItemStack legacy = new ItemStack(TinkerTables.castChest);
    legacy.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));

    CastChestBlockEntity chest = placeChest(helper, legacy, new BlockPos(2, 1, 2));
    assertContents(helper, chest, ingots);
    assertDroppedContents(helper, breakChest(helper, chest.getBlockPos()), ingots);
    helper.succeed();
  }
}
