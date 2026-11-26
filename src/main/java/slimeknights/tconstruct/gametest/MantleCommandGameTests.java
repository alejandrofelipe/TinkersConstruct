package slimeknights.tconstruct.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.List;

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
 * raw {@code Identifier}/{@code ResourceKey}/{@code Path} to {@code Component.translatable}, so building
 * their feedback message threw and aborted the command. Executing through the dispatcher lets that exception
 * propagate and fail the test; a fixed command completes and reaches {@link GameTestHelper#succeed()}.
 *
 * <p>Only read-only commands are covered here (dump_tag, tag_preference, sources), plus the suggestion providers
 * of {@code sources}, queried through {@code dispatcher.getCompletionSuggestions}. Commands that write a datapack
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
   * {@code /mantle sources data loot_table <id>} suggests ids from the reloadable loot table registry
   * ({@code MantleCommand.init}); 1.21 moved loot tables from {@code LootDataManager} to
   * {@code ReloadableServerRegistries}, so the provider must still list the vanilla tables.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void sources_loot_table_suggestions(GameTestHelper helper) {
    assertSuggests(helper, "mantle sources data loot_table minecraft:chests/", "minecraft:chests/simple_dungeon");
    helper.succeed();
  }

  /**
   * {@code /mantle sources data recipes <id>} suggests ids from the server's {@code RecipeManager}; 1.21.4 removed
   * {@code CommandSourceStack#getRecipeNames}, so the provider reads the recipe holders instead.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void sources_recipe_suggestions(GameTestHelper helper) {
    assertSuggests(helper, "mantle sources data recipes minecraft:oak_pl", "minecraft:oak_planks");
    helper.succeed();
  }

  /**
   * {@code /mantle sources data loot_table <id>} finds the vanilla loot table file in the {@code loot_table} folder
   * ({@code SourcesCommand}) and builds its feedback message; a missing file throws a syntax exception.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void sources_loot_table(GameTestHelper helper) {
    runCommand(helper, "mantle sources data loot_table minecraft:chests/simple_dungeon");
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

  /** Asserts the completion suggestions for the partial command contain the expected text, failing with the full list. */
  private static void assertSuggests(GameTestHelper helper, String input, String expected) {
    MinecraftServer server = helper.getLevel().getServer();
    CommandSourceStack source = server.createCommandSourceStack();
    CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
    ParseResults<CommandSourceStack> parse = dispatcher.parse(input, source);
    Suggestions suggestions = dispatcher.getCompletionSuggestions(parse).join();
    List<String> texts = suggestions.getList().stream().map(Suggestion::getText).toList();
    helper.assertTrue(texts.contains(expected), Component.literal("expected suggestion '" + expected + "' for '" + input + "', got " + texts));
  }
}
