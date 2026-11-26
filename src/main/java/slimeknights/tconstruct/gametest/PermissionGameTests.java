package slimeknights.tconstruct.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import slimeknights.mantle.command.MantleCommand;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tools.TinkerModifiers;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.data.ModifierIds;
import slimeknights.tconstruct.tools.data.material.MaterialIds;
import slimeknights.tconstruct.tools.item.CreativeSlotItem;
import slimeknights.tconstruct.tools.item.ModifierCrystalItem;

import java.util.List;
import java.util.UUID;

/**
 * Permission gates of the Mantle and Tinkers commands and of the op-only item clicks. 1.21.11 replaced the integer op levels
 * ({@code CommandSourceStack#hasPermission(int)}, {@code Player#hasPermissions(int)}) with {@link PermissionSet}s; Mantle maps its stable int
 * constants onto the new permissions in {@link MantleCommand#hasPermission}. A wrong mapping compiles and only shows as a command that is
 * missing for operators or open to everyone, so each gate is checked from both sides: the level it needs passes, the level below is rejected.
 * <p>
 * Commands run through {@code dispatcher.execute} so a rejection surfaces as the dispatcher's own "unknown argument" (a {@code requires}
 * predicate that fails hides the node), and a pass is proven by its side effect. Players are mock players reporting the given permission set,
 * like {@link GameTestHelper#makeMockPlayer} plus {@link Player#permissions()}.
 */
public class PermissionGameTests {
  /** Op levels 0 to 4 as permission sets, index = level */
  private static final List<PermissionSet> LEVELS = List.of(PermissionSet.NO_PERMISSIONS, LevelBasedPermissionSet.MODERATOR, LevelBasedPermissionSet.GAMEMASTER, LevelBasedPermissionSet.ADMIN, LevelBasedPermissionSet.OWNER);

  /** Command source of the server console with the given op level */
  private static CommandSourceStack source(GameTestHelper helper, int level) {
    return helper.getLevel().getServer().createCommandSourceStack().withPermission(LEVELS.get(level));
  }

  /** Runs the command, returning its result; fails the test on any error */
  private static int run(GameTestHelper helper, CommandSourceStack source, String command) {
    MinecraftServer server = helper.getLevel().getServer();
    try {
      return server.getCommands().getDispatcher().execute(command, source);
    } catch (CommandSyntaxException e) {
      throw helper.assertionException(Component.literal("'" + command + "' failed: " + e.getMessage()));
    }
  }

  /** Asserts the dispatcher hides the command from the source, failing if it runs or fails for another reason */
  private static void assertRejected(GameTestHelper helper, CommandSourceStack source, String command, int level) {
    MinecraftServer server = helper.getLevel().getServer();
    try {
      server.getCommands().getDispatcher().execute(command, source);
    } catch (CommandSyntaxException e) {
      if (e.getType() == CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument() || e.getType() == CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand()) {
        return;
      }
      throw helper.assertionException(Component.literal("'" + command + "' at op level " + level + " failed with an unexpected error: " + e.getMessage()));
    }
    throw helper.assertionException(Component.literal("'" + command + "' ran at op level " + level));
  }

  /** Mock player in the given game mode reporting the given permissions */
  private static Player player(GameTestHelper helper, GameType gameType, PermissionSet permissions) {
    return new Player(helper.getLevel(), new GameProfile(UUID.randomUUID(), "test-mock-player")) {
      @Override
      public GameType gameMode() {
        return gameType;
      }

      @Override
      public boolean isClientAuthoritative() {
        return false;
      }

      @Override
      public PermissionSet permissions() {
        return permissions;
      }
    };
  }

  /** Builds a rock/wood/wood pickaxe with the given upgrade */
  private static ItemStack pickaxe(int reinforced) {
    ToolStack tool = ToolStack.createTool(TinkerTools.pickaxe.get(), TinkerTools.pickaxe.get().getToolDefinition(),
      MaterialNBT.of(MaterialVariant.of(MaterialIds.rock, ""), MaterialVariant.of(MaterialIds.wood, ""), MaterialVariant.of(MaterialIds.wood, "")));
    if (reinforced > 0) {
      tool.addModifier(ModifierIds.reinforced, reinforced);
    }
    return tool.createStack();
  }

  /** {@link MantleCommand#hasPermission} keeps the old int semantics for every level pair, for command sources and players. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void mantle_permission_levels_map(GameTestHelper helper) {
    for (int has = 0; has < LEVELS.size(); has++) {
      CommandSourceStack source = source(helper, has);
      Player player = player(helper, GameType.SURVIVAL, LEVELS.get(has));
      for (int needs = 0; needs < LEVELS.size(); needs++) {
        boolean expected = has >= needs;
        helper.assertTrue(MantleCommand.hasPermission(source, needs) == expected, Component.literal("source at op " + has + " checking op " + needs + ": expected " + expected));
        helper.assertTrue(MantleCommand.hasPermission(player, needs) == expected, Component.literal("player at op " + has + " checking op " + needs + ": expected " + expected));
      }
    }
    helper.succeed();
  }

  /** {@code /tconstruct durability} is gated on {@link MantleCommand#PERMISSION_GAME_COMMANDS}: it changes a held tool at op 2, and is hidden at op 1 and 0. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tconstruct_game_command_needs_op_2(GameTestHelper helper) {
    Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
    zombie.setItemInHand(InteractionHand.MAIN_HAND, pickaxe(0));
    String command = "tconstruct durability " + zombie.getStringUUID() + " set 5";
    try {
      assertRejected(helper, source(helper, 0), command, 0);
      assertRejected(helper, source(helper, 1), command, 1);
      int unchanged = ToolStack.from(zombie.getMainHandItem()).getCurrentDurability();
      helper.assertTrue(unchanged != 5, Component.literal("rejected command changed the durability"));
      helper.assertTrue(run(helper, source(helper, 2), command) == 1, Component.literal("durability command at op 2 did not report the zombie"));
      int durability = ToolStack.from(zombie.getMainHandItem()).getCurrentDurability();
      helper.assertTrue(durability == 5, Component.literal("durability command at op 2 left " + durability + " durability, expected 5"));
    } finally {
      zombie.discard();
    }
    helper.succeed();
  }

  /** {@code /tconstruct report modifier_priority} is gated on {@link MantleCommand#PERMISSION_EDIT_SPAWN}: runs at op 1, hidden at op 0. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tconstruct_edit_spawn_command_needs_op_1(GameTestHelper helper) {
    // filtered to one hook so the logged table stays short
    String command = "tconstruct report modifier_priority " + ModifierHooks.PROJECTILE_LAUNCH.getId();
    assertRejected(helper, source(helper, 0), command, 0);
    int listed = run(helper, source(helper, 1), command);
    helper.assertTrue(listed > 0, Component.literal("modifier priority report at op 1 listed no modifiers"));
    helper.succeed();
  }

  /** A Mantle command through {@link MantleCommand#hasPermission}: {@code /mantle tags preference} runs at op 1 and is hidden at op 0. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void mantle_edit_spawn_command_needs_op_1(GameTestHelper helper) {
    String command = "mantle tags preference minecraft:item minecraft:planks";
    assertRejected(helper, source(helper, 0), command, 0);
    run(helper, source(helper, 1), command);
    helper.succeed();
  }

  /**
   * {@link CreativeSlotItem#canApply} (creative slots, and modifier crystals stacked onto a tool) allows creative players, and survival operators
   * at op 2 only when {@code quickApplyToolModifiersSurvival} is on; op 1 and 0 are denied.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void quick_apply_needs_op_2(GameTestHelper helper) {
    boolean config = Config.COMMON.quickApplyToolModifiersSurvival.get();
    try {
      Config.COMMON.quickApplyToolModifiersSurvival.set(true);
      helper.assertTrue(CreativeSlotItem.canApply(player(helper, GameType.SURVIVAL, LEVELS.get(2))), Component.literal("survival op 2 cannot quick apply"));
      helper.assertFalse(CreativeSlotItem.canApply(player(helper, GameType.SURVIVAL, LEVELS.get(1))), Component.literal("survival op 1 can quick apply"));
      helper.assertFalse(CreativeSlotItem.canApply(player(helper, GameType.SURVIVAL, LEVELS.get(0))), Component.literal("survival non-op can quick apply"));
      helper.assertTrue(CreativeSlotItem.canApply(player(helper, GameType.CREATIVE, LEVELS.get(0))), Component.literal("creative non-op cannot quick apply"));

      // the crystal's quick apply path: stacking it onto a tool slot adds the modifier for an operator only
      Player op = player(helper, GameType.SURVIVAL, LEVELS.get(2));
      Slot opSlot = new Slot(new SimpleContainer(pickaxe(0)), 0, 0, 0);
      helper.assertTrue(crystalItem().overrideStackedOnOther(ModifierCrystalItem.withModifier(ModifierIds.reinforced), opSlot, ClickAction.SECONDARY, op),
        Component.literal("crystal quick apply was not handled for survival op 2"));
      helper.assertTrue(ToolStack.from(opSlot.getItem()).getModifierLevel(ModifierIds.reinforced) == 1, Component.literal("crystal quick apply did not add the modifier for survival op 2"));
      Player nonOp = player(helper, GameType.SURVIVAL, LEVELS.get(0));
      Slot nonOpSlot = new Slot(new SimpleContainer(pickaxe(0)), 0, 0, 0);
      helper.assertFalse(crystalItem().overrideStackedOnOther(ModifierCrystalItem.withModifier(ModifierIds.reinforced), nonOpSlot, ClickAction.SECONDARY, nonOp),
        Component.literal("crystal quick apply was handled for a survival non-op"));
      helper.assertTrue(ToolStack.from(nonOpSlot.getItem()).getModifierLevel(ModifierIds.reinforced) == 0, Component.literal("crystal quick apply added the modifier for a survival non-op"));

      // config off: survival operators are denied too
      Config.COMMON.quickApplyToolModifiersSurvival.set(false);
      helper.assertFalse(CreativeSlotItem.canApply(player(helper, GameType.SURVIVAL, LEVELS.get(2))), Component.literal("survival op 2 can quick apply with the config off"));
    } finally {
      Config.COMMON.quickApplyToolModifiersSurvival.set(config);
    }
    helper.succeed();
  }

  /** Stacking a tool onto a modifier crystal removes the modifier for creative players and survival operators at op 2 (no config), never for op 0. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void crystal_removal_needs_op_2(GameTestHelper helper) {
    Slot crystalSlot = new Slot(new SimpleContainer(ModifierCrystalItem.withModifier(ModifierIds.reinforced)), 0, 0, 0);
    ItemStack crystal = crystalSlot.getItem();

    ItemStack nonOpTool = pickaxe(1);
    Player nonOp = player(helper, GameType.SURVIVAL, LEVELS.get(0));
    helper.assertFalse(crystal.getItem().overrideOtherStackedOnMe(crystal, nonOpTool, crystalSlot, ClickAction.SECONDARY, nonOp, carried(nonOpTool)),
      Component.literal("crystal removal was handled for a survival non-op"));
    helper.assertTrue(ToolStack.from(nonOpTool).getModifierLevel(ModifierIds.reinforced) == 1, Component.literal("crystal removal removed the modifier for a survival non-op"));

    ItemStack opTool = pickaxe(1);
    Player op = player(helper, GameType.SURVIVAL, LEVELS.get(2));
    helper.assertTrue(crystal.getItem().overrideOtherStackedOnMe(crystal, opTool, crystalSlot, ClickAction.SECONDARY, op, carried(opTool)),
      Component.literal("crystal removal was not handled for survival op 2"));
    helper.assertTrue(ToolStack.from(opTool).getModifierLevel(ModifierIds.reinforced) == 0, Component.literal("crystal removal did not remove the modifier for survival op 2"));
    helper.succeed();
  }

  /** The modifier crystal item */
  private static ModifierCrystalItem crystalItem() {
    return (ModifierCrystalItem) TinkerModifiers.modifierCrystal.get();
  }

  /** Carried stack access for a click */
  private static SlotAccess carried(ItemStack stack) {
    return SlotAccess.of(() -> stack, s -> {});
  }
}
