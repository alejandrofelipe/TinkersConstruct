package slimeknights.tconstruct.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.loot.LootModifierManager;
import slimeknights.mantle.Mantle;
import org.jspecify.annotations.Nullable;
import slimeknights.tconstruct.TConstruct;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Datapack completeness guards (M7 plan Phase 0.5c, roadmap R1): every recipe, loot table and global loot modifier JSON
 * that Tinkers and Mantle ship must be loaded by the game. A decoder that throws makes the loader log
 * {@code Couldn't parse data file} (or {@code Couldn't parse element}) and skip the file, so the entry silently
 * disappears; no other test fails on a missing recipe outside the few lookups it checks. On 26.1 this is the main hazard
 * of the port: {@code ItemStack}/{@code FluidStack} codecs refuse items whose default data components are not bound yet,
 * and those are bound only after every reload listener has run.
 *
 * <p>Each test lists the JSON files of the {@code tconstruct} and {@code mantle} namespaces through the server's resource
 * manager (the same view the loaders read), drops the files whose {@code neoforge:conditions} fail against the loaded
 * registries and tags (compat and config gated data), and compares the remaining ids with what the game loaded, both
 * ways.
 */
public class DatapackCompletenessGameTests {
  /** Namespaces whose data must load completely */
  private static final Set<String> NAMESPACES = Set.of(TConstruct.MOD_ID, Mantle.modId);
  /** How many missing or unexpected ids to name in a failure message */
  private static final int SHOWN = 20;

  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 200)
  public static void datapack_recipes_complete(GameTestHelper helper) {
    MinecraftServer server = helper.getLevel().getServer();
    Set<Identifier> loaded = server.getRecipeManager().getRecipes().stream()
      .map(holder -> holder.id().identifier())
      .filter(id -> NAMESPACES.contains(id.getNamespace()))
      .collect(Collectors.toCollection(TreeSet::new));
    compare(helper, "recipe", expectedIds(helper, server, FileToIdConverter.registry(Registries.RECIPE)), loaded);
  }

  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 200)
  public static void datapack_loot_tables_complete(GameTestHelper helper) {
    MinecraftServer server = helper.getLevel().getServer();
    Set<Identifier> loaded = server.reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).listElementIds()
      .map(ResourceKey::identifier)
      .filter(id -> NAMESPACES.contains(id.getNamespace()))
      .collect(Collectors.toCollection(TreeSet::new));
    compare(helper, "loot table", expectedIds(helper, server, FileToIdConverter.registry(Registries.LOOT_TABLE)), loaded);
  }

  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 200)
  public static void datapack_loot_modifiers_complete(GameTestHelper helper) {
    MinecraftServer server = helper.getLevel().getServer();
    // NeoForge only loads the modifiers named in a neoforge:loot_modifiers/global_loot_modifiers.json; one that is not
    // listed is dead data, so every shipped modifier must be listed
    Set<Identifier> listed = new TreeSet<>();
    Identifier listFile = Identifier.fromNamespaceAndPath("neoforge", "loot_modifiers/global_loot_modifiers.json");
    for (Resource resource : server.getResourceManager().getResourceStack(listFile)) {
      JsonElement json = read(helper, listFile, resource);
      if (json == null) {
        return;
      }
      json.getAsJsonObject().getAsJsonArray("entries").forEach(entry -> listed.add(Identifier.parse(entry.getAsString())));
    }
    Set<Identifier> expected = expectedIds(helper, server, FileToIdConverter.json("loot_modifiers"));
    if (expected == null) {
      return;
    }
    Set<Identifier> unlisted = new TreeSet<>(expected);
    unlisted.removeAll(listed);
    if (!unlisted.isEmpty()) {
      helper.fail(Component.literal(unlisted.size() + " loot modifier(s) missing from global_loot_modifiers.json: " + shown(unlisted)));
      return;
    }
    // NeoForge exposes no public accessor for the loaded modifiers by id (see DatapackTagGapGameTests#ore_loot_modifiers_load)
    Map<Identifier, ?> modifiers;
    try {
      Method getter = Class.forName("net.neoforged.neoforge.common.NeoForgeEventHandler").getDeclaredMethod("getLootModifierManager");
      getter.setAccessible(true);
      Field field = LootModifierManager.class.getDeclaredField("registeredLootModifiers");
      field.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<Identifier, ?> map = (Map<Identifier, ?>) field.get(getter.invoke(null));
      modifiers = map;
    } catch (ReflectiveOperationException | RuntimeException e) {
      helper.fail(Component.literal("could not read NeoForge's loaded loot modifiers: " + e));
      return;
    }
    Set<Identifier> loaded = modifiers.keySet().stream()
      .filter(id -> NAMESPACES.contains(id.getNamespace()))
      .collect(Collectors.toCollection(TreeSet::new));
    compare(helper, "loot modifier", expected, loaded);
  }

  /**
   * Lists the ids of every JSON file of our namespaces under the converter's folder whose {@code neoforge:conditions}
   * (if any) pass now. Returns null after failing the test on an unreadable file.
   */
  @Nullable
  private static Set<Identifier> expectedIds(GameTestHelper helper, MinecraftServer server, FileToIdConverter converter) {
    RegistryAccess access = server.registryAccess();
    // after the reload every tag is bound, so "loaded" means the registry knows the tag (what NeoForge's reload context asks)
    ICondition.IContext context = new ICondition.IContext() {
      @Override
      public <T> boolean isTagLoaded(TagKey<T> key) {
        return access.lookup(key.registry()).flatMap(registry -> registry.get(key)).isPresent();
      }

      @Override
      public RegistryAccess registryAccess() {
        return access;
      }

      @Override
      public FeatureFlagSet enabledFeatures() {
        return server.getWorldData().enabledFeatures();
      }
    };
    ConditionalOps<JsonElement> ops = new ConditionalOps<>(access.createSerializationContext(JsonOps.INSTANCE), context);
    Set<Identifier> expected = new TreeSet<>();
    int skipped = 0;
    for (Map.Entry<Identifier, Resource> entry : converter.listMatchingResources(server.getResourceManager()).entrySet()) {
      Identifier file = entry.getKey();
      if (!NAMESPACES.contains(file.getNamespace())) {
        continue;
      }
      JsonElement json = read(helper, file, entry.getValue());
      if (json == null) {
        return null;
      }
      if (ICondition.conditionsMatched(ops, json)) {
        expected.add(converter.fileToId(file));
      } else {
        skipped++;
      }
    }
    TConstruct.LOG.info("Datapack completeness: {} files skipped by their neoforge:conditions", skipped);
    return expected;
  }

  /** Reads a JSON resource, failing the test (and returning null) when it can't be parsed at all */
  @Nullable
  private static JsonElement read(GameTestHelper helper, Identifier file, Resource resource) {
    try (Reader reader = resource.openAsReader()) {
      return JsonParser.parseReader(reader);
    } catch (IOException | RuntimeException e) {
      helper.fail(Component.literal("could not read " + file + ": " + e));
      return null;
    }
  }

  /** Fails when the loaded ids differ from the expected ones, naming both differences */
  private static void compare(GameTestHelper helper, String kind, @Nullable Set<Identifier> expected, Set<Identifier> loaded) {
    if (expected == null) {
      return;
    }
    Set<Identifier> missing = new TreeSet<>(expected);
    missing.removeAll(loaded);
    Set<Identifier> unexpected = new TreeSet<>(loaded);
    unexpected.removeAll(expected);
    if (expected.isEmpty()) {
      helper.fail(Component.literal("no " + kind + " files found for " + NAMESPACES + ", the resource listing is broken"));
    } else if (!missing.isEmpty() || !unexpected.isEmpty()) {
      helper.fail(Component.literal(String.format("%s files %d, loaded %d: %d failed to load %s; %d loaded without a file %s",
        kind, expected.size(), loaded.size(), missing.size(), shown(missing), unexpected.size(), shown(unexpected))));
    } else {
      TConstruct.LOG.info("Datapack completeness: all {} {} files of {} loaded", expected.size(), kind, NAMESPACES);
      helper.succeed();
    }
  }

  /** First few ids of the set, for a readable failure */
  private static String shown(Set<Identifier> ids) {
    List<String> names = ids.stream().limit(SHOWN).map(Identifier::toString).toList();
    return ids.size() > SHOWN ? names + "..." : names.toString();
  }
}
