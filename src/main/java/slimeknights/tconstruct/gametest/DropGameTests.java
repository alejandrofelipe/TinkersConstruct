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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
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
    IItemHandlerModifiable inventory = chest.getItemHandler();
    for (int i = 0; i < inventory.getSlots(); i++) {
      ItemStack actual = inventory.getStackInSlot(i);
      ItemStack wanted = i < expected.length ? expected[i] : ItemStack.EMPTY;
      helper.assertTrue(ItemStack.matches(actual, wanted), Component.literal("slot " + i + ": expected " + wanted + ", got " + actual));
    }
  }

  /** Breaking a filled cast chest drops it with its inventory, and placing the dropped item restores it */
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

    // loot table drop
    List<ItemStack> drops = Block.getDrops(state, level, pos, chest);
    helper.assertTrue(drops.size() == 1 && drops.getFirst().is(TinkerTables.castChest.asItem()), Component.literal("expected a single cast chest drop, got " + drops));
    ItemStack drop = drops.getFirst();
    helper.assertTrue(drop.has(DataComponents.BLOCK_ENTITY_DATA), Component.literal("dropped cast chest has no block entity data: " + drop.getComponentsPatch()));
    level.removeBlock(pos, false);

    // place it again
    assertContents(helper, placeChest(helper, drop, new BlockPos(3, 1, 3)), ingots, nuggets);

    // an empty chest drops a plain item, which stacks with crafted ones
    BlockPos emptyPos = helper.absolutePos(new BlockPos(1, 1, 3));
    level.setBlockAndUpdate(emptyPos, state);
    List<ItemStack> emptyDrops = Block.getDrops(state, level, emptyPos, level.getBlockEntity(emptyPos));
    helper.assertTrue(emptyDrops.size() == 1 && ItemStack.isSameItemSameComponents(emptyDrops.getFirst(), new ItemStack(TinkerTables.castChest)),
                      Component.literal("expected a plain cast chest from an empty chest, got " + emptyDrops + " " + emptyDrops.stream().map(ItemStack::getComponentsPatch).toList()));
    helper.succeed();
  }

  /** Cast chests dropped before 1.21.10 hold the inventory in custom data ({@code TinkerData.Items}); placing one still restores it */
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

    assertContents(helper, placeChest(helper, legacy, new BlockPos(2, 1, 2)), ingots);
    helper.succeed();
  }
}
