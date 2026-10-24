package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.common.registration.GeodeItemObject;
import slimeknights.tconstruct.world.TinkerStructures;
import slimeknights.tconstruct.world.TinkerWorld;
import slimeknights.tconstruct.world.block.FoliageType;
import slimeknights.tconstruct.world.worldgen.islands.IslandStructure;
import slimeknights.tconstruct.world.worldgen.trees.config.SlimeFungusConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Worldgen regression guard: every structure, structure set, feature and biome modifier resolves, every island template
 * loads with the right palette, and each island/geode/ore/tree actually places when forced at a fixed position (the
 * "{@code /place}" path, bypassing biome and spacing checks, which the flat gametest world cannot exercise).
 * Placements happen far from the gametest grid; the gametest world is wiped before every run ({@code cleanGameTestWorld}),
 * so nothing leaks between runs. Feature placement uses fixed seeds so the results are deterministic.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(TConstruct.MOD_ID)
public class WorldgenGameTests {
  private static final String[] TEMPLATE_SIZES = {"0x1x0", "2x2x4", "4x1x6", "8x1x11", "11x1x11"};
  /** Blocks that mean an island template was not repaletted */
  private static final Set<Block> UNPALETTED = Set.of(Blocks.GRASS_BLOCK, Blocks.CLAY, Blocks.SAND);
  /** Far away from the gametest grid, so placements never touch another test */
  private static final int ORIGIN = 32_000;

  /** Expected grass blocks per island template folder */
  private static Map<String, Set<Block>> templateGrass() {
    return Map.of(
      "earth", Set.of(TinkerWorld.earthSlimeGrass.get(FoliageType.EARTH), TinkerWorld.skySlimeGrass.get(FoliageType.EARTH)),
      "sky", Set.of(TinkerWorld.earthSlimeGrass.get(FoliageType.SKY), TinkerWorld.skySlimeGrass.get(FoliageType.SKY)),
      "blood", Set.of(TinkerWorld.ichorSlimeGrass.get(FoliageType.BLOOD)),
      "ender", Set.of(TinkerWorld.ichorSlimeGrass.get(FoliageType.ENDER), TinkerWorld.enderSlimeGrass.get(FoliageType.ENDER)),
      "dirt", Set.of(Blocks.GRASS_BLOCK));
  }

  /** Island structure to the template folder it builds from */
  private static final Map<ResourceKey<Structure>, String> ISLANDS = Map.of(
    TinkerStructures.earthSlimeIsland, "earth",
    TinkerStructures.skySlimeIsland, "sky",
    TinkerStructures.oceanSkyslimeIsland, "sky",
    TinkerStructures.clayIsland, "dirt",
    TinkerStructures.bloodIsland, "blood",
    TinkerStructures.endSlimeIsland, "ender");

  private static final List<ResourceKey<StructureSet>> STRUCTURE_SETS = List.of(
    TinkerStructures.overworldOceanIsland, TinkerStructures.overworldSkyIsland, TinkerStructures.netherOceanIsland, TinkerStructures.endSkyIsland);

  private static final List<ResourceKey<ConfiguredFeature<?,?>>> TREES = List.of(
    TinkerStructures.earthSlimeTree, TinkerStructures.earthSlimeIslandTree, TinkerStructures.skySlimeTree, TinkerStructures.skySlimeIslandTree,
    TinkerStructures.enderSlimeTree, TinkerStructures.enderSlimeTreeTall,
    TinkerStructures.bloodSlimeFungus, TinkerStructures.bloodSlimeIslandFungus, TinkerStructures.ichorSlimeFungus);

  /** Geode feature to the crystal blocks it must produce */
  private static Map<ResourceKey<ConfiguredFeature<?,?>>, GeodeItemObject> geodes() {
    return Map.of(
      TinkerWorld.configuredEarthGeode, TinkerWorld.earthGeode,
      TinkerWorld.configuredSkyGeode, TinkerWorld.skyGeode,
      TinkerWorld.configuredIchorGeode, TinkerWorld.ichorGeode,
      TinkerWorld.configuredEnderGeode, TinkerWorld.enderGeode);
  }

  private static final List<ResourceKey<PlacedFeature>> PLACED_FEATURES = List.of(
    TinkerWorld.placedEarthGeode, TinkerWorld.placedSkyGeode, TinkerWorld.placedIchorGeode, TinkerWorld.placedEnderGeode,
    TinkerWorld.placedSmallCobaltOre, TinkerWorld.placedLargeCobaltOre);

  private static final List<String> BIOME_MODIFIERS = List.of(
    "cobalt_ore", "earth_geode", "sky_geode", "ichor_geode", "ender_geode", "spawn_overworld_slime", "spawn_end_slime", "spawn_terracube");


  /* Data validation */

  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void worldgen_registries_resolve(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    List<String> failures = new ArrayList<>();
    Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
    for (ResourceKey<Structure> key : ISLANDS.keySet()) {
      Structure structure = structures.getValue(key);
      if (!(structure instanceof IslandStructure)) {
        failures.add("structure " + key.location() + " is " + structure);
      } else if (structure.biomes().size() == 0) {
        failures.add("structure " + key.location() + " has an empty biome set");
      }
    }
    Registry<StructureSet> sets = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
    for (ResourceKey<StructureSet> key : STRUCTURE_SETS) {
      StructureSet set = sets.getValue(key);
      if (set == null) {
        failures.add("structure set " + key.location() + " missing");
      } else if (set.structures().isEmpty()) {
        failures.add("structure set " + key.location() + " is empty");
      } else {
        for (StructureSet.StructureSelectionEntry entry : set.structures()) {
          if (!(entry.structure().value() instanceof IslandStructure)) {
            failures.add("structure set " + key.location() + " references " + entry.structure());
          }
        }
      }
    }
    Registry<ConfiguredFeature<?,?>> configured = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE);
    List<ResourceKey<ConfiguredFeature<?,?>>> configuredKeys = new ArrayList<>(TREES);
    configuredKeys.addAll(geodes().keySet());
    configuredKeys.add(TinkerWorld.configuredSmallCobaltOre);
    configuredKeys.add(TinkerWorld.configuredLargeCobaltOre);
    for (ResourceKey<ConfiguredFeature<?,?>> key : configuredKeys) {
      if (configured.getValue(key) == null) {
        failures.add("configured feature " + key.location() + " missing");
      }
    }
    Registry<PlacedFeature> placed = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
    for (ResourceKey<PlacedFeature> key : PLACED_FEATURES) {
      PlacedFeature feature = placed.getValue(key);
      if (feature == null) {
        failures.add("placed feature " + key.location() + " missing");
      } else if (!feature.feature().isBound()) {
        failures.add("placed feature " + key.location() + " has an unbound feature");
      }
    }
    var modifiers = level.registryAccess().lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS);
    for (String id : BIOME_MODIFIERS) {
      if (!modifiers.containsKey(TConstruct.getResource(id))) {
        failures.add("biome modifier " + TConstruct.getResource(id) + " missing");
      }
    }
    Registry<Biome> biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
    for (String island : List.of("earthslime", "skyslime", "clay", "blood", "enderslime")) {
      TagKey<Biome> tag = TagKey.create(Registries.BIOME, TConstruct.getResource("islands/" + island));
      if (biomes.get(tag).map(HolderSet::size).orElse(0) == 0) {
        failures.add("biome tag " + tag.location() + " is empty");
      }
    }
    finish(helper, failures);
  }

  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void island_templates_load(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    List<String> failures = new ArrayList<>();
    templateGrass().forEach((folder, grass) -> {
      for (String size : TEMPLATE_SIZES) {
        ResourceLocation id = TConstruct.getResource("islands/" + folder + "/" + size);
        Optional<StructureTemplate> template = level.getStructureManager().get(id);
        if (template.isEmpty()) {
          failures.add("template " + id + " missing");
          continue;
        }
        StructureTemplate value = template.get();
        if (value.getSize().getX() <= 0 || value.getSize().getY() <= 0 || value.getSize().getZ() <= 0) {
          failures.add("template " + id + " is empty " + value.getSize());
          continue;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings();
        if (grass.stream().allMatch(block -> value.filterBlocks(BlockPos.ZERO, settings, block).isEmpty())) {
          failures.add("template " + id + " has none of " + grass);
        }
        if (!folder.equals("dirt")) {
          for (Block block : UNPALETTED) {
            if (!value.filterBlocks(BlockPos.ZERO, settings, block).isEmpty()) {
              failures.add("template " + id + " still has " + block);
            }
          }
        }
      }
    });
    finish(helper, failures);
  }


  /* Forced placement */

  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 200)
  public static void islands_place(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
    ChunkGenerator generator = level.getChunkSource().getGenerator();
    Map<String, Set<Block>> grass = templateGrass();
    List<String> failures = new ArrayList<>();
    int index = 0;
    for (Map.Entry<ResourceKey<Structure>, String> island : ISLANDS.entrySet()) {
      ResourceKey<Structure> key = island.getKey();
      ChunkPos chunk = new ChunkPos(SectionPos.blockToSectionCoord(ORIGIN) + 8 * index++, SectionPos.blockToSectionCoord(ORIGIN));
      Optional<Holder.Reference<Structure>> holder = structures.get(key);
      if (holder.isEmpty()) {
        failures.add(key.location() + " missing");
        continue;
      }
      // same as the /place structure command
      StructureStart start = holder.get().value().generate(
        holder.get(), level.dimension(), level.registryAccess(), generator, generator.getBiomeSource(), level.getChunkSource().randomState(),
        level.getStructureManager(), level.getSeed(), chunk, 0, level, biome -> true);
      if (!start.isValid()) {
        failures.add(key.location() + " generated no pieces");
        continue;
      }
      BoundingBox box = start.getBoundingBox();
      RandomSource random = RandomSource.create(index);
      ChunkPos.rangeClosed(new ChunkPos(SectionPos.blockToSectionCoord(box.minX()), SectionPos.blockToSectionCoord(box.minZ())),
                           new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()), SectionPos.blockToSectionCoord(box.maxZ())))
              .forEach(pos -> start.placeInChunk(level, level.structureManager(), generator, random,
                new BoundingBox(pos.getMinBlockX(), level.getMinY(), pos.getMinBlockZ(), pos.getMaxBlockX(), level.getMaxY() + 1, pos.getMaxBlockZ()), pos));

      // the island must contain its own grass, and slime islands no leftover vanilla palette
      Set<Block> expected = grass.get(island.getValue());
      boolean foundGrass = false;
      Set<Block> leftovers = new HashSet<>();
      for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
        Block block = level.getBlockState(pos).getBlock();
        if (expected.contains(block)) {
          foundGrass = true;
        } else if (!island.getValue().equals("dirt") && UNPALETTED.contains(block)) {
          leftovers.add(block);
        }
      }
      if (!foundGrass) {
        failures.add(key.location() + " placed at " + box + " without any of " + expected);
      }
      if (!leftovers.isEmpty()) {
        failures.add(key.location() + " placed vanilla blocks " + leftovers);
      }
    }
    finish(helper, failures);
  }

  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 200)
  public static void geodes_place(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    Registry<ConfiguredFeature<?,?>> configured = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE);
    List<String> failures = new ArrayList<>();
    int index = 0;
    for (Map.Entry<ResourceKey<ConfiguredFeature<?,?>>, GeodeItemObject> geode : geodes().entrySet()) {
      BlockPos origin = new BlockPos(ORIGIN + 64 * index++, 40, ORIGIN + 1000);
      // geodes refuse to place when their shell touches air, so bury them in stone
      fill(level, origin.offset(-16, -16, -16), origin.offset(16, 16, 16), Blocks.STONE.defaultBlockState());
      ConfiguredFeature<?,?> feature = configured.getValue(geode.getKey());
      if (feature == null || !feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(index), origin)) {
        failures.add(geode.getKey().location() + " did not place");
        continue;
      }
      GeodeItemObject crystal = geode.getValue();
      if (count(level, origin.offset(-16, -16, -16), origin.offset(16, 16, 16), Set.of(crystal.getBlock(), crystal.getBudding())) == 0) {
        failures.add(geode.getKey().location() + " placed no " + crystal.getBlock());
      }
    }
    finish(helper, failures);
  }

  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 200)
  public static void cobalt_ore_places(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    Registry<ConfiguredFeature<?,?>> configured = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE);
    List<String> failures = new ArrayList<>();
    int index = 0;
    for (ResourceKey<ConfiguredFeature<?,?>> key : List.of(TinkerWorld.configuredSmallCobaltOre, TinkerWorld.configuredLargeCobaltOre)) {
      BlockPos origin = new BlockPos(ORIGIN + 64 * index++, 40, ORIGIN + 2000);
      // ore only replaces netherrack
      fill(level, origin.offset(-8, -8, -8), origin.offset(8, 8, 8), Blocks.NETHERRACK.defaultBlockState());
      ConfiguredFeature<?,?> feature = configured.getValue(key);
      if (feature == null || !feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(index), origin)) {
        failures.add(key.location() + " did not place");
      } else if (count(level, origin.offset(-8, -8, -8), origin.offset(8, 8, 8), Set.of(TinkerWorld.cobaltOre.get())) == 0) {
        failures.add(key.location() + " placed no cobalt ore");
      }
    }
    finish(helper, failures);
  }

  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 200)
  public static void slime_trees_place(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    Registry<ConfiguredFeature<?,?>> configured = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE);
    Registry<Block> blocks = level.registryAccess().lookupOrThrow(Registries.BLOCK);
    List<String> failures = new ArrayList<>();
    int index = 0;
    for (ResourceKey<ConfiguredFeature<?,?>> key : TREES) {
      BlockPos origin = new BlockPos(ORIGIN + 32 * index++, 41, ORIGIN + 3000);
      ConfiguredFeature<?,?> feature = configured.getValue(key);
      if (feature == null) {
        failures.add(key.location() + " missing");
        continue;
      }
      // slime trees grow on slimy soil, fungi on their configured ground
      TagKey<Block> ground = feature.config() instanceof SlimeFungusConfig fungus ? fungus.getGroundTag() : TinkerTags.Blocks.SLIMY_SOIL;
      Optional<Block> soil = blocks.get(ground).flatMap(set -> set.stream().findFirst()).map(Holder::value);
      if (soil.isEmpty()) {
        failures.add(key.location() + " ground tag " + ground.location() + " is empty");
        continue;
      }
      // a platform, not a single block: enderbark's mangrove roots grow down until they hit ground, failing past max_root_length
      fill(level, origin.offset(-8, -1, -8), origin.offset(8, -1, 8), soil.get().defaultBlockState());
      if (!feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(index), origin)) {
        failures.add(key.location() + " did not place on " + soil.get());
      } else if (BlockPos.betweenClosedStream(origin.offset(-8, 0, -8), origin.offset(8, 30, 8)).allMatch(pos -> level.getBlockState(pos).isAir())) {
        // mangrove-style enderbark raises its trunk above the origin, so look at the whole volume over the platform
        failures.add(key.location() + " placed nothing above its platform");
      }
    }
    finish(helper, failures);
  }


  /* Helpers */

  /** Fills the box with the given state */
  private static void fill(ServerLevel level, BlockPos from, BlockPos to, BlockState state) {
    for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
      level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
  }

  /** Counts blocks in the box matching any of the given blocks */
  private static int count(ServerLevel level, BlockPos from, BlockPos to, Set<Block> targets) {
    int count = 0;
    for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
      if (targets.contains(level.getBlockState(pos).getBlock())) {
        count++;
      }
    }
    return count;
  }

  private static void finish(GameTestHelper helper, List<String> failures) {
    if (failures.isEmpty()) {
      helper.succeed();
    } else {
      helper.fail(String.join("; ", failures));
    }
  }
}
