package slimeknights.tconstruct.gametest;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import slimeknights.mantle.MantleEvents;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.data.ModifierIds;
import slimeknights.tconstruct.tools.data.material.MaterialIds;

import java.util.List;

/**
 * Player death with the {@code keep_inventory} game rule, which 1.21.11 moved to the new {@code GameRules} API
 * ({@code net.minecraft.world.level.gamerules}, typed {@code get}/{@code set}). Soulbound runs in three steps, all gated on the rule: Tinkers'
 * {@code ModifierEvents#onLivingDeath} marks soulbound tools with their slot, Mantle's {@code MantleEvents#onPlayerDropItems} takes the marked
 * stacks back out of the drops, and {@code MantleEvents#onPlayerClone} moves them into the respawned player. A rule read that is inverted or
 * reads another rule drops the soulbound tool (or marks tools while the vanilla rule keeps everything).
 * <p>
 * The dying player is a mock player in the test structure: {@code Player#die} runs the real death path (death event, inventory drop, drops
 * event, item entities); the respawn is the clone event NeoForge fires from {@code PlayerList#respawn}, posted with a fresh mock player.
 */
public class DeathGameTests {
  private static final int TOOL_SLOT = 0;
  private static final int PLAIN_SLOT = 10;

  /** Rock/wood/wood pickaxe with soulbound */
  private static ItemStack soulboundPickaxe() {
    ToolStack tool = ToolStack.createTool(TinkerTools.pickaxe.get(), TinkerTools.pickaxe.get().getToolDefinition(),
      MaterialNBT.of(MaterialVariant.of(MaterialIds.rock, ""), MaterialVariant.of(MaterialIds.wood, ""), MaterialVariant.of(MaterialIds.wood, "")));
    tool.addModifier(ModifierIds.soulbound, 1);
    return tool.createStack();
  }

  /** Survival mock player standing in the test structure, holding a soulbound pickaxe and some dirt */
  private static Player dyingPlayer(GameTestHelper helper) {
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    player.setPos(helper.absoluteVec(new Vec3(2.5, 1, 2.5)));
    player.getInventory().setItem(TOOL_SLOT, soulboundPickaxe());
    player.getInventory().setItem(PLAIN_SLOT, new ItemStack(Items.DIRT, 5));
    return player;
  }

  /** Item entities in the test structure */
  private static List<ItemEntity> drops(GameTestHelper helper) {
    return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2));
  }

  /** Checks the stack still carries Mantle's soulbound slot marker */
  private static boolean hasSlotMarker(ItemStack stack) {
    CustomData data = stack.get(DataComponents.CUSTOM_DATA);
    return data != null && data.contains(MantleEvents.SOULBOUND_SLOT);
  }

  /** Runs the test with keep inventory set as given, restoring the previous value after */
  private static void withKeepInventory(GameTestHelper helper, boolean keepInventory, Runnable test) {
    ServerLevel level = helper.getLevel();
    GameRules rules = level.getGameRules();
    boolean original = rules.get(GameRules.KEEP_INVENTORY);
    try {
      rules.set(GameRules.KEEP_INVENTORY, keepInventory, level.getServer());
      test.run();
    } finally {
      rules.set(GameRules.KEEP_INVENTORY, original, level.getServer());
      drops(helper).forEach(ItemEntity::discard);
    }
  }

  /** Keep inventory off: the soulbound tool survives death and respawn in its slot, the plain item drops. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void soulbound_kept_without_keep_inventory(GameTestHelper helper) {
    withKeepInventory(helper, false, () -> {
      Player player = dyingPlayer(helper);
      player.die(helper.getLevel().damageSources().generic());

      // drops: dirt on the ground, the soulbound tool taken back into the dead player's inventory, still marked for the clone
      List<ItemEntity> drops = drops(helper);
      helper.assertTrue(drops.stream().anyMatch(drop -> drop.getItem().is(Items.DIRT)), Component.literal("plain item was not dropped, drops " + drops));
      helper.assertFalse(drops.stream().anyMatch(drop -> drop.getItem().is(TinkerTools.pickaxe.get())), Component.literal("soulbound pickaxe was dropped"));
      ItemStack kept = player.getInventory().getItem(TOOL_SLOT);
      helper.assertTrue(kept.is(TinkerTools.pickaxe.get()) && hasSlotMarker(kept), Component.literal("soulbound pickaxe not back in its slot with the slot marker, found " + kept));

      // respawn: the clone receives the tool in the same slot, the marker is removed
      Player respawned = helper.makeMockPlayer(GameType.SURVIVAL);
      NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(respawned, player, true));
      ItemStack moved = respawned.getInventory().getItem(TOOL_SLOT);
      helper.assertTrue(moved.is(TinkerTools.pickaxe.get()), Component.literal("respawned player did not get the soulbound pickaxe, found " + moved));
      helper.assertFalse(hasSlotMarker(moved), Component.literal("soulbound slot marker left on the respawned player's pickaxe"));
      helper.assertTrue(ToolStack.from(moved).getModifierLevel(ModifierIds.soulbound) == 1, Component.literal("respawned pickaxe lost its soulbound modifier"));
      helper.assertTrue(respawned.getInventory().getItem(PLAIN_SLOT).isEmpty(), Component.literal("respawned player got the plain item"));
    });
    helper.succeed();
  }

  /** Keep inventory on: vanilla keeps both stacks and nothing drops; Tinkers and Mantle leave the stacks unmarked. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void keep_inventory_keeps_everything(GameTestHelper helper) {
    withKeepInventory(helper, true, () -> {
      Player player = dyingPlayer(helper);
      player.die(helper.getLevel().damageSources().generic());

      List<ItemEntity> drops = drops(helper);
      helper.assertTrue(drops.isEmpty(), Component.literal("keep inventory dropped items " + drops));
      ItemStack tool = player.getInventory().getItem(TOOL_SLOT);
      helper.assertTrue(tool.is(TinkerTools.pickaxe.get()) && !hasSlotMarker(tool), Component.literal("soulbound pickaxe not kept unmarked, found " + tool));
      helper.assertTrue(player.getInventory().getItem(PLAIN_SLOT).is(Items.DIRT), Component.literal("plain item not kept, found " + player.getInventory().getItem(PLAIN_SLOT)));
    });
    helper.succeed();
  }
}
