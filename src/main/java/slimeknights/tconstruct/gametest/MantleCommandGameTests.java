package slimeknights.tconstruct.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * Gametests for Mantle's debug/admin commands. Mantle has no gametest infrastructure of its own, and its
 * commands only exist at runtime on a running server (registered via {@code RegisterCommandsEvent}), so they
 * are exercised here from Tinkers, which consumes Mantle via the composite build.
 *
 * <p>Each test runs a command through {@code dispatcher.execute(String, CommandSourceStack)} rather than
 * {@code Commands.performPrefixedCommand}: the latter swallows non-syntax exceptions (it logs them and
 * returns 0), which would hide the very failure under test. In 1.21 {@code TranslatableContents}'s
 * constructor throws {@link IllegalArgumentException} in a dev environment ({@code !FMLEnvironment.production})
 * when handed an argument that is not a Component, Number, Boolean, or String. Several Mantle commands passed a
 * raw {@code ResourceLocation}/{@code ResourceKey}/{@code Path} to {@code Component.translatable}, so building
 * their feedback message threw and aborted the command. Executing through the dispatcher lets that exception
 * propagate and fail the test; a fixed command completes and reaches {@link GameTestHelper#succeed()}.
 *
 * <p>Only read-only commands are covered here (dump_tag, tag_preference). Commands that write a datapack
 * ({@code RemoveDataCommand} — its condition-remove would unbind a registry entry and corrupt the persistent
 * gametest world) or are client-only ({@code BookCommand}) share the identical {@code .toString()} fix and are
 * verified by inspection.
 */
public class MantleCommandGameTests {

  /** Executes a command as the server console (permission level 4), letting runtime exceptions propagate. */
  private static void runCommand(GameTestHelper helper, String command) {
    MinecraftServer server = helper.getLevel().getServer();
    CommandSourceStack source = server.createCommandSourceStack();
    try {
      server.getCommands().getDispatcher().execute(command, source);
    } catch (CommandSyntaxException e) {
      // a syntax failure (bad ids, or a command's own "not found" error) fails the test through the helper; an
      // AssertionError is an Error, which the gametest runner does not catch, so it crashed the whole test server
      helper.fail(Component.literal("command syntax error running '" + command + "': " + e.getMessage()));
    }
  }

  /**
   * {@code /mantle tags entries log <registry> <tag>} builds its success message from the registry id and tag
   * id ({@code DumpTagCommand}), both raw ResourceLocations.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void dump_tag_entries(GameTestHelper helper) {
    runCommand(helper, "mantle tags entries log minecraft:item minecraft:planks");
    helper.succeed();
  }

  /**
   * {@code /mantle tags preference <registry> <tag>} builds its message from the registry id and tag id
   * ({@code TagPreferenceCommand}), both raw ResourceLocations.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tag_preference(GameTestHelper helper) {
    runCommand(helper, "mantle tags preference minecraft:item minecraft:planks");
    helper.succeed();
  }

  /**
   * {@code /mantle sources data recipes <id>} finds the vanilla recipe file. 1.21 renamed the datapack folder from
   * {@code recipes} to {@code recipe}, so the subcommand (still literally {@code recipes}) must look in {@code recipe/}.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void sources_recipe(GameTestHelper helper) {
    runCommand(helper, "mantle sources data recipes minecraft:oak_planks");
    helper.succeed();
  }
}
