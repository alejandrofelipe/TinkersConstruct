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

/** Gametests for block and mob drops: Tinkers heads from charged creepers. */
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
}
