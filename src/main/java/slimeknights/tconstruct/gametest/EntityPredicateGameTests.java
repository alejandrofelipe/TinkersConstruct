package slimeknights.tconstruct.gametest;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.criterion.PlayerInteractTrigger;
import net.minecraft.advancements.criterion.PlayerTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.gadgets.TinkerGadgets;
import slimeknights.tconstruct.world.TinkerStructures;
import slimeknights.tconstruct.world.TinkerWorld;
import slimeknights.tconstruct.world.entity.ArmoredSlimeEntity;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Entity predicates in shipped data (M8 plan R1 guard S3): 26.2 turns the {@code EntityPredicate} JSON into a map keyed by sub-predicate id, so data
 * with the old keys stops decoding, and a regenerated file can decode yet never match. The completeness tests catch files that fail to load; these
 * evaluate the loaded predicates: the six {@code world/*} advancements whose criteria carry an entity predicate, and the three entity loot tables
 * whose drops depend on the slime size, the metal core and a frog kill.
 */
public class EntityPredicateGameTests {
  /** Island advancements and the structure each one's location criterion names */
  private static final Map<String, ResourceKey<Structure>> ISLANDS = Map.of(
    "world/earth_island", TinkerStructures.earthSlimeIsland,
    "world/sky_island", TinkerStructures.skySlimeIsland,
    "world/clay_island", TinkerStructures.clayIsland,
    "world/blood_island", TinkerStructures.bloodIsland,
    "world/ender_island", TinkerStructures.endSlimeIsland);
  /** Chunk x of the first generated island start, far from the test arenas */
  private static final int ISLAND_CHUNK_X = 6000;

  /** The single criterion of the loaded advancement, failing the test if it is missing */
  private static Criterion<?> criterion(GameTestHelper helper, String advancement, String name) {
    AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(TConstruct.getResource(advancement));
    helper.assertTrue(holder != null, Component.literal("tconstruct:" + advancement + " is not loaded"));
    Criterion<?> criterion = holder.value().criteria().get(name);
    helper.assertTrue(criterion != null, Component.literal("tconstruct:" + advancement + " has no criterion " + name));
    return criterion;
  }

  /** Loot context of an advancement entity predicate, as {@code EntityPredicate#createContext} builds it */
  private static LootContext advancementContext(ServerLevel level, Entity entity) {
    LootParams params = new LootParams.Builder(level)
      .withParameter(LootContextParams.THIS_ENTITY, entity)
      .withParameter(LootContextParams.ORIGIN, entity.position())
      .create(LootContextParamSets.ADVANCEMENT_ENTITY);
    return new LootContext.Builder(params).create(Optional.empty());
  }

  /**
   * The island advancements' location criterion matches a player inside the island and nobody outside it. Each island start is generated far away and
   * registered in its chunk the way worldgen does (start + reference), so {@code LocationPredicate} finds a piece at the player's position.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 200)
  public static void island_advancements_match_inside_the_island(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    ChunkGenerator generator = level.getChunkSource().getGenerator();
    Player outside = helper.makeMockPlayer(GameType.SURVIVAL);
    outside.setPos(helper.absoluteVec(new Vec3(2.5, 1, 2.5)));
    List<String> problems = new ArrayList<>();
    int index = 0;
    for (Map.Entry<String, ResourceKey<Structure>> island : new TreeMap<>(ISLANDS).entrySet()) {
      Criterion<?> criterion = criterion(helper, island.getKey(), "found_island");
      if (!(criterion.triggerInstance() instanceof PlayerTrigger.TriggerInstance location) || location.player().isEmpty()) {
        problems.add(island.getKey() + " has no player location criterion: " + criterion.triggerInstance());
        continue;
      }
      Holder.Reference<Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(island.getValue());
      ChunkPos chunk = new ChunkPos(ISLAND_CHUNK_X + 8 * index++, 0);
      StructureStart start = structure.value().generate(
        structure, level.dimension(), level.registryAccess(), generator, generator.getBiomeSource(), level.getChunkSource().randomState(),
        level.getStructureManager(), level.getSeed(), chunk, 0, level, biome -> true);
      if (!start.isValid()) {
        problems.add(island.getValue().identifier() + " generated no pieces");
        continue;
      }
      BlockPos inside = start.getPieces().getFirst().getBoundingBox().getCenter();
      level.getChunk(chunk.x(), chunk.z()).setStartForStructure(structure.value(), start);
      level.getChunk(SectionPos.blockToSectionCoord(inside.getX()), SectionPos.blockToSectionCoord(inside.getZ())).addReferenceForStructure(structure.value(), chunk.pack());
      Player player = helper.makeMockPlayer(GameType.SURVIVAL);
      player.setPos(Vec3.atCenterOf(inside));
      if (!location.player().get().matches(advancementContext(level, player))) {
        problems.add(island.getKey() + " does not match a player inside " + island.getValue().identifier() + " at " + inside);
      }
      if (location.player().get().matches(advancementContext(level, outside))) {
        problems.add(island.getKey() + " matches a player outside the island");
      }
    }
    helper.assertTrue(problems.isEmpty(), Component.literal(String.join("; ", problems)));
    helper.succeed();
  }

  /** The piggyback pack advancement's interaction criterion matches the pack used on a pig only */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 20)
  public static void piggybackpack_advancement_matches_a_pig(GameTestHelper helper) {
    Criterion<?> criterion = criterion(helper, "world/piggybackpack", "used_pack");
    helper.assertTrue(criterion.triggerInstance() instanceof PlayerInteractTrigger.TriggerInstance, Component.literal("expected an entity interaction criterion, found " + criterion.triggerInstance()));
    PlayerInteractTrigger.TriggerInstance interact = (PlayerInteractTrigger.TriggerInstance) criterion.triggerInstance();
    ServerLevel level = helper.getLevel();
    Entity pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 1, 1));
    Entity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(3, 1, 3));
    ItemStack pack = new ItemStack(TinkerGadgets.piggyBackpack.get());
    Map<String, Boolean> cases = new TreeMap<>(Map.of(
      "pack on a pig", interact.matches(pack, advancementContext(level, pig)),
      "pack on a cow", interact.matches(pack, advancementContext(level, cow)),
      "saddle on a pig", interact.matches(new ItemStack(Items.SADDLE), advancementContext(level, pig))));
    helper.assertTrue(cases.get("pack on a pig"), Component.literal("the pack on a pig does not match: " + cases));
    helper.assertTrue(cases.values().stream().filter(Boolean::booleanValue).count() == 1, Component.literal("only the pack on a pig may match: " + cases));
    helper.succeed();
  }


  /* Entity loot tables */

  /** A slime of the type and size, with the metal core if set, for loot rolls */
  private static ArmoredSlimeEntity slime(GameTestHelper helper, EntityType<? extends ArmoredSlimeEntity> type, int size, boolean metal) {
    ArmoredSlimeEntity slime = helper.spawnWithNoFreeWill(type, new BlockPos(2, 1, 2));
    slime.setSize(size, true);
    if (metal) {
      try {
        Method setMetal = ArmoredSlimeEntity.class.getDeclaredMethod("setMetal", boolean.class);
        setMetal.setAccessible(true);
        setMetal.invoke(slime, true);
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("cannot set the metal core", e);
      }
    }
    return slime;
  }

  /** Rolls the slime's loot table the given number of times for the damage source, summing the drops by item id */
  private static Map<String, Integer> roll(GameTestHelper helper, ArmoredSlimeEntity slime, DamageSource source, int rolls) {
    ServerLevel level = helper.getLevel();
    LootTable table = level.getServer().reloadableRegistries().getLootTable(slime.getLootTable().orElseThrow());
    Map<String, Integer> drops = new TreeMap<>();
    for (int i = 0; i < rolls; i++) {
      LootParams params = new LootParams.Builder(level)
        .withParameter(LootContextParams.THIS_ENTITY, slime)
        .withParameter(LootContextParams.ORIGIN, slime.position())
        .withParameter(LootContextParams.DAMAGE_SOURCE, source)
        .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity())
        .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity())
        .create(LootContextParamSets.ENTITY);
      for (ItemStack stack : table.getRandomItems(params)) {
        if (stack.isEmpty()) {
          continue; // a count roll of 0
        }
        drops.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
      }
    }
    return drops;
  }

  /** Damage dealt by a frog, the loot condition for the frog pools */
  private static DamageSource frogKill(GameTestHelper helper) {
    Entity frog = EntityType.FROG.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
    helper.assertTrue(frog != null, Component.literal("could not create a frog"));
    return helper.getLevel().damageSources().mobAttack((net.minecraft.world.entity.LivingEntity) frog);
  }

  /** Fails unless the drops of the case equal the expected ones */
  private static void expectDrops(List<String> problems, String name, Map<String, Integer> drops, Map<String, Integer> expected) {
    if (!drops.equals(new TreeMap<>(expected))) {
      problems.add(name + " dropped " + drops + ", expected " + expected);
    }
  }

  /** Fails unless the drops of the case only hold the item, at least once */
  private static void expectOnly(List<String> problems, String name, Map<String, Integer> drops, String item) {
    if (drops.isEmpty() || !drops.keySet().equals(java.util.Set.of(item))) {
      problems.add(name + " dropped " + drops + ", expected some " + item + " only");
    }
  }

  /**
   * Sky and ender slimes drop their slime ball, or their metal shard with a metal core, only at size 1 ({@code type_specific} slime size): exactly one
   * when a frog kills them, 0 to 2 otherwise.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 20)
  public static void slime_loot_tables_match_size_metal_and_frog(GameTestHelper helper) {
    DamageSource frog = frogKill(helper);
    DamageSource generic = helper.getLevel().damageSources().generic();
    List<String> problems = new ArrayList<>();
    for (Map.Entry<EntityType<? extends ArmoredSlimeEntity>, String[]> entry : Map.<EntityType<? extends ArmoredSlimeEntity>, String[]>of(
        TinkerWorld.skySlimeEntity.get(), new String[] {"tconstruct:sky_slime_ball", "tconstruct:steel_shard"},
        TinkerWorld.enderSlimeEntity.get(), new String[] {"tconstruct:ender_slime_ball", "tconstruct:knightmetal_shard"}).entrySet()) {
      String type = BuiltInRegistries.ENTITY_TYPE.getKey(entry.getKey()).getPath();
      String ball = entry.getValue()[0], shard = entry.getValue()[1];
      ArmoredSlimeEntity small = slime(helper, entry.getKey(), 1, false);
      ArmoredSlimeEntity metal = slime(helper, entry.getKey(), 1, true);
      ArmoredSlimeEntity big = slime(helper, entry.getKey(), 2, false);
      expectDrops(problems, type + " size 1 killed by a frog", roll(helper, small, frog, 1), Map.of(ball, 1));
      expectDrops(problems, type + " size 1 with a metal core killed by a frog", roll(helper, metal, frog, 1), Map.of(shard, 1));
      expectDrops(problems, type + " size 2 killed by a frog", roll(helper, big, frog, 20), Map.of());
      expectOnly(problems, type + " size 1 killed 40 times", roll(helper, small, generic, 40), ball);
      expectOnly(problems, type + " size 1 with a metal core killed 40 times", roll(helper, metal, generic, 40), shard);
      expectDrops(problems, type + " size 2 killed 20 times", roll(helper, big, generic, 20), Map.of());
    }
    helper.assertTrue(problems.isEmpty(), Component.literal(String.join("; ", problems)));
    helper.succeed();
  }

  /** Terracubes drop one seared lamp when a frog kills them (any size), clay balls from size 2 up otherwise, nothing at size 1 */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 20)
  public static void terracube_loot_table_matches_size_and_frog(GameTestHelper helper) {
    DamageSource frog = frogKill(helper);
    DamageSource generic = helper.getLevel().damageSources().generic();
    List<String> problems = new ArrayList<>();
    ArmoredSlimeEntity small = slime(helper, TinkerWorld.terracubeEntity.get(), 1, false);
    ArmoredSlimeEntity big = slime(helper, TinkerWorld.terracubeEntity.get(), 4, false);
    expectDrops(problems, "terracube size 1 killed by a frog", roll(helper, small, frog, 1), Map.of("tconstruct:seared_lamp", 1));
    expectDrops(problems, "terracube size 4 killed by a frog", roll(helper, big, frog, 1), Map.of("tconstruct:seared_lamp", 1));
    expectDrops(problems, "terracube size 1 killed 20 times", roll(helper, small, generic, 20), Map.of());
    expectOnly(problems, "terracube size 4 killed 40 times", roll(helper, big, generic, 40), "minecraft:clay_ball");
    helper.assertTrue(problems.isEmpty(), Component.literal(String.join("; ", problems)));
    helper.succeed();
  }
}
