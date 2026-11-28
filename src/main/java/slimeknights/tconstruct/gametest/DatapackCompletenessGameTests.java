package slimeknights.tconstruct.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.criterion.InventoryChangeTrigger;
import net.minecraft.advancements.criterion.ItemPredicate;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifierManager;
import net.neoforged.neoforge.resource.NeoForgeReloadListeners;
import slimeknights.mantle.Mantle;
import org.jspecify.annotations.Nullable;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.library.materials.IMaterialRegistry;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.definition.MaterialManager;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.ModifierManager;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.definition.ToolDefinitionLoader;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.component.SearedTankBlock.TankType;
import slimeknights.tconstruct.smeltery.item.TankItem;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Datapack completeness guards (M7 plan Phase 0.5c, roadmap R1): every recipe, loot table, global loot modifier and advancement JSON
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
    // 26.1: NeoForge loads every JSON under loot_modifiers/ (ordered by priority); the global_loot_modifiers.json index is gone
    LootModifierManager manager = server.getServerResources().managers().getListener(NeoForgeReloadListeners.LOOT_MODIFIERS_KEY);
    Set<Identifier> loaded = new TreeSet<>();
    for (IGlobalLootModifier modifier : manager.getSortedModifiers()) {
      Identifier id = manager.getId(modifier);
      if (id != null && NAMESPACES.contains(id.getNamespace())) {
        loaded.add(id);
      }
    }
    compare(helper, "loot modifier", expectedIds(helper, server, FileToIdConverter.json("loot_modifiers")), loaded);
  }

  /** Order of the Tinkers loot modifiers up to 1.21.11, from the removed {@code global_loot_modifiers.json} index */
  private static final List<String> LOOT_MODIFIER_ORDER = List.of(
    "wither_bone", "modifier_hook", "tasty_bacon", "chrysophilite_modifier",
    "lustrous/iron", "lustrous/gold", "lustrous/copper", "lustrous/cobalt", "lustrous/netherite_scrap",
    "lustrous/tin", "lustrous/aluminum", "lustrous/lead", "lustrous/silver", "lustrous/nickel", "lustrous/zinc", "lustrous/platinum",
    "lustrous/tungsten", "lustrous/osmium", "lustrous/uranium", "lustrous/chromium", "lustrous/cadmium");

  /**
   * 26.1: NeoForge applies the loot modifiers sorted by priority only (highest first, ties in hash order), the index that ordered them is gone. Each
   * Tinkers modifier has its own priority so they keep the 1.21.11 order (e.g. the modifier hook melts the drops before lustrous adds nuggets).
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void loot_modifiers_keep_the_old_order(GameTestHelper helper) {
    LootModifierManager manager = helper.getLevel().getServer().getServerResources().managers().getListener(NeoForgeReloadListeners.LOOT_MODIFIERS_KEY);
    List<String> applied = new ArrayList<>();
    List<Integer> priorities = new ArrayList<>();
    for (IGlobalLootModifier modifier : manager.getSortedModifiers()) {
      Identifier id = manager.getId(modifier);
      if (id != null && TConstruct.MOD_ID.equals(id.getNamespace())) {
        applied.add(id.getPath());
        priorities.add(modifier.priority());
      }
    }
    helper.assertTrue(applied.equals(LOOT_MODIFIER_ORDER), Component.literal("loot modifiers apply in the order " + applied + ", expected " + LOOT_MODIFIER_ORDER));
    // distinct priorities, else the order of a tie depends on the identifier hashes
    helper.assertTrue(Set.copyOf(priorities).size() == priorities.size(), Component.literal("loot modifiers share a priority: " + applied + " -> " + priorities));
    helper.succeed();
  }

  /**
   * M7 Phase 4: the blaze subtree (blaze, manyullyn lanterns and plate armor as their child) was dropped on the first load because its exact
   * tank fluid component predicates and filled tank icons decoded fluid stacks before components were bound.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 200)
  public static void datapack_advancements_complete(GameTestHelper helper) {
    MinecraftServer server = helper.getLevel().getServer();
    Set<Identifier> loaded = server.getAdvancements().getAllAdvancements().stream()
      .map(AdvancementHolder::id)
      .filter(id -> NAMESPACES.contains(id.getNamespace()))
      .collect(Collectors.toCollection(TreeSet::new));
    compare(helper, "advancement", expectedIds(helper, server, FileToIdConverter.registry(Registries.ADVANCEMENT)), loaded);
  }

  /** The loaded blaze advancement's {@code tconstruct:tank_fluid} criterion matches a full blazing blood tank only (exact fluid and amount) */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tank_fluid_advancement_criterion_matches(GameTestHelper helper) {
    AdvancementHolder blaze = helper.getLevel().getServer().getAdvancements().get(TConstruct.getResource("foundry/blaze"));
    helper.assertTrue(blaze != null, Component.literal("tconstruct:foundry/blaze is not loaded"));
    Criterion<?> criterion = blaze.value().criteria().get("seared_fuel_tank");
    helper.assertTrue(criterion != null && criterion.triggerInstance() instanceof InventoryChangeTrigger.TriggerInstance, Component.literal("expected an inventory criterion seared_fuel_tank, found " + criterion));
    List<ItemPredicate> items = ((InventoryChangeTrigger.TriggerInstance) criterion.triggerInstance()).items();
    helper.assertTrue(items.size() == 1, Component.literal("expected one item predicate, found " + items));
    ItemPredicate predicate = items.getFirst();
    int capacity = TankType.FUEL_TANK.getCapacity();
    Block tank = TinkerSmeltery.searedTank.get(TankType.FUEL_TANK);
    Map<String, Boolean> cases = Map.of(
      "full blazing blood", predicate.test(TankItem.setTank(new ItemStack(tank), new FluidStack(TinkerFluids.blazingBlood.get(), capacity))),
      "blazing blood one mB short", predicate.test(TankItem.setTank(new ItemStack(tank), new FluidStack(TinkerFluids.blazingBlood.get(), capacity - 1))),
      "full lava", predicate.test(TankItem.setTank(new ItemStack(tank), new FluidStack(Fluids.LAVA, capacity))),
      "empty tank", predicate.test(new ItemStack(tank)),
      "full blazing blood in a gauge", predicate.test(TankItem.setTank(new ItemStack(TinkerSmeltery.searedTank.get(TankType.FUEL_GAUGE)), new FluidStack(TinkerFluids.blazingBlood.get(), capacity))));
    helper.assertTrue(cases.get("full blazing blood"), Component.literal("a full blazing blood tank does not match: " + cases));
    helper.assertTrue(cases.entrySet().stream().filter(Map.Entry::getValue).count() == 1, Component.literal("only the full blazing blood tank may match: " + cases));
    helper.succeed();
  }

  /* Tinkers' own JSON registries (M8 plan R1 guard S3): they embed vanilla and Mantle predicates and codecs, and skip a file that fails to decode */

  /** Every modifier JSON (redirects included) whose {@code condition} passes is in the modifier manager */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 200)
  public static void datapack_modifiers_complete(GameTestHelper helper) {
    MinecraftServer server = helper.getLevel().getServer();
    Set<Identifier> expected = expectedTinkerIds(helper, server, FileToIdConverter.json(ModifierManager.FOLDER));
    if (expected == null) {
      return;
    }
    Set<Identifier> loaded = expected.stream().filter(id -> ModifierManager.INSTANCE.contains(new ModifierId(id))).collect(Collectors.toCollection(TreeSet::new));
    ModifierManager.INSTANCE.getAllLocations().filter(id -> NAMESPACES.contains(id.getNamespace()) && !ModifierManager.INSTANCE.containsStatic(new ModifierId(id))).forEach(loaded::add);
    compare(helper, "modifier", expected, loaded);
  }

  /** Every material definition JSON (redirects included) whose {@code condition} passes resolves to a material */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 200)
  public static void datapack_materials_complete(GameTestHelper helper) {
    MinecraftServer server = helper.getLevel().getServer();
    Set<Identifier> expected = expectedTinkerIds(helper, server, FileToIdConverter.json(MaterialManager.FOLDER));
    if (expected == null) {
      return;
    }
    IMaterialRegistry registry = MaterialRegistry.getInstance();
    Set<Identifier> loaded = expected.stream().filter(id -> registry.getMaterial(new MaterialId(id)) != IMaterial.UNKNOWN || !registry.resolve(new MaterialId(id)).equals(new MaterialId(id))).collect(Collectors.toCollection(TreeSet::new));
    registry.getAllMaterials().stream().map(material -> material.getIdentifier().getLocation()).filter(id -> NAMESPACES.contains(id.getNamespace())).forEach(loaded::add);
    compare(helper, "material", expected, loaded);
  }

  /** Every tool definition JSON whose {@code condition} passes belongs to a registered tool whose data loaded */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 200)
  public static void datapack_tool_definitions_complete(GameTestHelper helper) {
    MinecraftServer server = helper.getLevel().getServer();
    Set<Identifier> expected = expectedTinkerIds(helper, server, FileToIdConverter.json(ToolDefinitionLoader.FOLDER));
    if (expected == null) {
      return;
    }
    Set<Identifier> loaded = ToolDefinitionLoader.getInstance().getRegisteredToolDefinitions().stream()
      .filter(definition -> definition.isDataLoaded() && NAMESPACES.contains(definition.getId().getNamespace()) && expected.contains(definition.getId()))
      .map(ToolDefinition::getId)
      .collect(Collectors.toCollection(TreeSet::new));
    compare(helper, "tool definition", expected, loaded);
  }

  /** Like {@link #expectedIds}, for Tinkers' loaders: their files gate on a top level {@code condition} (a NeoForge condition), not {@code neoforge:conditions} */
  @Nullable
  private static Set<Identifier> expectedTinkerIds(GameTestHelper helper, MinecraftServer server, FileToIdConverter converter) {
    RegistryAccess access = server.registryAccess();
    ICondition.IContext context = conditionContext(server);
    Set<Identifier> expected = new TreeSet<>();
    for (Map.Entry<Identifier, Resource> entry : converter.listMatchingResources(server.getResourceManager()).entrySet()) {
      Identifier file = entry.getKey();
      if (!NAMESPACES.contains(file.getNamespace())) {
        continue;
      }
      JsonElement json = read(helper, file, entry.getValue());
      if (json == null) {
        return null;
      }
      JsonElement condition = json.isJsonObject() ? json.getAsJsonObject().get("condition") : null;
      if (condition == null || ICondition.CODEC.parse(access.createSerializationContext(JsonOps.INSTANCE), condition).getOrThrow().test(context)) {
        expected.add(converter.fileToId(file));
      }
    }
    return expected;
  }

  /** Condition context over the loaded registries and tags */
  private static ICondition.IContext conditionContext(MinecraftServer server) {
    RegistryAccess access = server.registryAccess();
    return new ICondition.IContext() {
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
