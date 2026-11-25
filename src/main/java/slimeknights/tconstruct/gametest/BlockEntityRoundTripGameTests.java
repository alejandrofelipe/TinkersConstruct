package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import slimeknights.mantle.block.entity.IRetexturedBlockEntity;
import slimeknights.mantle.block.entity.NameableBlockEntity;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.recipe.FluidValues;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.part.block.MaterialBlockEntity;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.component.SearedBlock;
import slimeknights.tconstruct.smeltery.block.component.SearedTankBlock.TankType;
import slimeknights.tconstruct.smeltery.block.controller.ControllerBlock;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.FaucetBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.HeatingStructureBlockEntity;
import slimeknights.tconstruct.smeltery.item.CopperCanItem;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tools.TinkerToolParts;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.data.material.MaterialIds;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Safety net for every Tinkers block entity type: each block entity is saved like chunk saving does, loaded into a fresh instance
 * like chunk loading does and saved again (the two saves must be identical), then sent through the client update path: the update
 * tag is loaded into a fresh instance ({@code handleUpdateTag}, the chunk packet) and again through {@code onDataPacket} (the block
 * update packet), and the client copy must produce the same update tag. Any exception or serialization problem fails the test.
 * <p>
 * Covers both empty block entities (an empty tank once threw while loading and disconnected clients) and filled ones, filled
 * through the registered item/fluid capabilities plus custom names, textures and materials where the block entity has them. The
 * client copies have no level, so client-only side effects ({@code level.isClientSide()} branches) are not exercised.
 */
public class BlockEntityRoundTripGameTests {
  /** Name set on nameable block entities when filling */
  private static final Component NAME = Component.literal("Round Trip");
  /** Texture set on retextured block entities when filling */
  private static final String TEXTURE = "minecraft:stone_bricks";

  /**
   * Places every block of every {@code tconstruct} block entity type on its own and round-trips its block entity, empty and filled.
   * Fails if a type has no valid block or a block does not create the block entity of its type.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void block_entity_round_trip_every_type(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
    List<String> failures = new ArrayList<>();
    List<String> skipped = new ArrayList<>();
    int checked = 0;
    for (ResourceLocation id : new TreeSet<>(BuiltInRegistries.BLOCK_ENTITY_TYPE.keySet())) {
      if (!TConstruct.MOD_ID.equals(id.getNamespace())) {
        continue;
      }
      BlockEntityType<?> type = Objects.requireNonNull(BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(id));
      List<Block> blocks = type.getValidBlocks().stream().sorted(Comparator.comparing(block -> BuiltInRegistries.BLOCK.getKey(block).toString())).toList();
      if (blocks.stream().noneMatch(block -> block.defaultBlockState().hasBlockEntity()
                                             || block.defaultBlockState().hasProperty(SearedBlock.IN_STRUCTURE))) {
        failures.add(id + ": no valid block has a block entity");
        continue;
      }
      for (Block block : blocks) {
        String label = id + " / " + BuiltInRegistries.BLOCK.getKey(block);
        // smeltery components only have a block entity inside a structure
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(SearedBlock.IN_STRUCTURE)) {
          state = state.setValue(SearedBlock.IN_STRUCTURE, true);
        }
        // the smeltery component type lists whole building block sets, whose slabs, stairs, fences and walls never have one
        if (!state.hasBlockEntity()) {
          skipped.add(BuiltInRegistries.BLOCK.getKey(block).toString());
          continue;
        }
        level.setBlockAndUpdate(pos, state);
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || be.getType() != type) {
          failures.add(label + ": placing the block created " + (be == null ? "no block entity" : "a " + BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType())) + ", block is now " + level.getBlockState(pos));
        } else {
          check(failures, label + " (empty)", () -> roundTrip(helper, be));
          if (fill(be)) {
            check(failures, label + " (filled)", () -> roundTrip(helper, be));
          }
        }
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        checked++;
      }
    }
    // removed inventories drop their contents
    helper.killAllEntitiesOfClass(ItemEntity.class);
    helper.assertTrue(checked > 0, Component.literal("no tconstruct block entity types found"));
    TConstruct.LOG.info("Block entity round trip: {} blocks checked, {} valid blocks without a block entity skipped: {}", checked, skipped.size(), skipped);
    assertNoFailures(helper, failures);
    helper.succeed();
  }

  /**
   * Round-trips the block entities of working structures, which a single block cannot reach: a formed smeltery and foundry holding
   * molten metal (controller with its structure, servants with a master: bricks, drain, duct, chute, a lava fuel tank and an empty
   * tank), a fueled melter and alloyer (their fuel tanks are servants too) and a faucet pouring into a casting table.
   */
  @TinkerGameTest(template = "gametest/empty_9x9x9", timeoutTicks = 200)
  public static void block_entity_round_trip_structures(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos smeltery = buildHeatingStructure(level, helper.absolutePos(new BlockPos(1, 1, 1)), TinkerSmeltery.searedBricks.get().defaultBlockState(),
      TinkerSmeltery.searedTank.get(TankType.FUEL_TANK), TinkerSmeltery.searedDrain.get(), TinkerSmeltery.searedDuct.get(), TinkerSmeltery.searedChute.get(),
      TinkerSmeltery.smelteryController.get());
    BlockPos foundry = buildHeatingStructure(level, helper.absolutePos(new BlockPos(1, 1, 5)), TinkerSmeltery.scorchedBricks.get().defaultBlockState(),
      TinkerSmeltery.scorchedTank.get(TankType.FUEL_TANK), TinkerSmeltery.scorchedDrain.get(), TinkerSmeltery.scorchedDuct.get(), TinkerSmeltery.scorchedChute.get(),
      TinkerSmeltery.foundryController.get());
    // melter and alloyer: the fuel tank goes in after the controller, so its shape update forms the controller
    BlockPos melter = helper.absolutePos(new BlockPos(6, 2, 1));
    level.setBlockAndUpdate(melter, TinkerSmeltery.searedMelter.get().defaultBlockState());
    level.setBlockAndUpdate(melter.below(), TinkerSmeltery.searedTank.get(TankType.FUEL_TANK).defaultBlockState());
    SmelteryRigs.fillTank(level, melter.below(), new FluidStack(Fluids.LAVA, 2000));
    BlockPos alloyer = helper.absolutePos(new BlockPos(6, 2, 3));
    level.setBlockAndUpdate(alloyer, TinkerSmeltery.scorchedAlloyer.get().defaultBlockState());
    level.setBlockAndUpdate(alloyer.below(), TinkerSmeltery.scorchedTank.get(TankType.FUEL_TANK).defaultBlockState());
    SmelteryRigs.fillTank(level, alloyer.below(), new FluidStack(Fluids.LAVA, 2000));
    level.setBlockAndUpdate(alloyer.east(), TinkerSmeltery.scorchedTank.get(TankType.INGOT_TANK).defaultBlockState());
    SmelteryRigs.fillTank(level, alloyer.east(), new FluidStack(TinkerFluids.moltenCopper.get(), FluidValues.INGOT * 3));
    // faucet over a casting table
    SmelteryRigs.CastingRig rig = SmelteryRigs.buildCastingRig(level, helper.absolutePos(new BlockPos(5, 1, 6)));

    // let the structures form, then fill them and start pouring
    helper.runAfterDelay(60, () -> {
      for (BlockPos controller : List.of(smeltery, foundry)) {
        helper.assertTrue(level.getBlockEntity(controller) instanceof HeatingStructureBlockEntity structure && structure.getStructure() != null,
                          Component.literal("structure at " + helper.relativePos(controller) + " did not form"));
        ResourceHandler<ItemResource> items = level.getCapability(Capabilities.Item.BLOCK, controller, null);
        helper.assertTrue(items != null && items.size() > 0 && insertItem(items, 0, new ItemStack(Items.IRON_INGOT)),
                          Component.literal("structure at " + helper.relativePos(controller) + " did not accept an iron ingot"));
        insertFluid(level, controller, new FluidStack(TinkerFluids.moltenGold.get(), FluidValues.INGOT * 2));
      }
      insertFluid(level, melter, new FluidStack(TinkerFluids.moltenIron.get(), FluidValues.INGOT));
      insertFluid(level, alloyer, new FluidStack(TinkerFluids.moltenBronze.get(), FluidValues.INGOT));
      if (level.getBlockEntity(rig.table()) instanceof CastingBlockEntity table) {
        table.setItem(CastingBlockEntity.INPUT, new ItemStack(TinkerSmeltery.ingotCast.get()));
      }
      if (level.getBlockEntity(rig.faucet()) instanceof FaucetBlockEntity faucet) {
        faucet.activate();
      }
    });
    // mid pour: faucet holds fluid, the table has part of an ingot
    helper.runAfterDelay(64, () -> {
      helper.assertTrue(level.getBlockEntity(rig.faucet()) instanceof FaucetBlockEntity faucet && faucet.isPouring(), Component.literal("faucet is not pouring"));
      List<String> failures = new ArrayList<>();
      Set<BlockEntityType<?>> types = new HashSet<>();
      for (BlockPos relative : BlockPos.betweenClosed(0, 0, 0, 8, 8, 8)) {
        BlockEntity be = level.getBlockEntity(helper.absolutePos(relative));
        if (be != null && TConstruct.MOD_ID.equals(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).getNamespace())) {
          types.add(be.getType());
          check(failures, BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()) + " at " + relative.immutable(), () -> roundTrip(helper, be));
        }
      }
      for (BlockEntityType<?> expected : List.of(TinkerSmeltery.smeltery.get(), TinkerSmeltery.foundry.get(), TinkerSmeltery.smelteryComponent.get(), TinkerSmeltery.tank.get(),
                                                  TinkerSmeltery.drain.get(), TinkerSmeltery.duct.get(), TinkerSmeltery.chute.get(), TinkerSmeltery.melter.get(),
                                                  TinkerSmeltery.alloyer.get(), TinkerSmeltery.faucet.get(), TinkerSmeltery.table.get())) {
        if (!types.contains(expected)) {
          failures.add("no " + BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(expected) + " block entity in the structures");
        }
      }
      assertNoFailures(helper, failures);
      helper.succeed();
    });
  }


  /* Round trip */

  /**
   * Round-trips the block entity: disk save → fresh load → save must be identical, then the client update paths must reproduce the update tag.
   * @throws IllegalStateException describing the first mismatch or serialization problem
   */
  private static void roundTrip(GameTestHelper helper, BlockEntity be) {
    HolderLookup.Provider registries = helper.getLevel().registryAccess();
    // disk: save like chunk saving, load into a fresh instance like chunk loading (BlockEntity.loadStatic, without its exception swallowing)
    CompoundTag saved = save(be, registries, "saving");
    BlockEntity loaded = be.getType().create(be.getBlockPos(), be.getBlockState());
    if (loaded == null) {
      throw new IllegalStateException("could not create a fresh block entity");
    }
    ProblemReporter.Collector problems = new ProblemReporter.Collector();
    loaded.loadWithComponents(TagValueInput.create(problems, registries, saved));
    checkProblems(problems, "loading");
    compare(saved, save(loaded, registries, "saving the loaded copy"), "re-save after load");

    // client: the chunk packet carries the update tag, loaded through handleUpdateTag into a fresh block entity
    CompoundTag update = be.getUpdateTag(registries);
    BlockEntity client = be.getType().create(be.getBlockPos(), be.getBlockState());
    if (client == null) {
      throw new IllegalStateException("could not create a fresh client block entity");
    }
    problems = new ProblemReporter.Collector();
    client.handleUpdateTag(TagValueInput.create(problems, registries, update));
    checkProblems(problems, "handleUpdateTag");
    compare(update, client.getUpdateTag(registries), "update tag after handleUpdateTag");
    // block updates send the update packet, applied with onDataPacket on the existing client block entity
    if (be.getUpdatePacket() instanceof ClientboundBlockEntityDataPacket packet) {
      problems = new ProblemReporter.Collector();
      client.onDataPacket(null, TagValueInput.create(problems, registries, packet.getTag()));
      checkProblems(problems, "onDataPacket");
      compare(update, client.getUpdateTag(registries), "update tag after onDataPacket");
    }
  }

  /** Saves the block entity with full metadata like chunk saving does */
  private static CompoundTag save(BlockEntity be, HolderLookup.Provider registries, String action) {
    ProblemReporter.Collector problems = new ProblemReporter.Collector();
    TagValueOutput output = TagValueOutput.createWithContext(problems, registries);
    be.saveWithFullMetadata(output);
    checkProblems(problems, action);
    return output.buildResult();
  }

  private static void checkProblems(ProblemReporter.Collector problems, String action) {
    if (!problems.isEmpty()) {
      throw new IllegalStateException("serialization problems " + action + ":\n" + problems.getTreeReport());
    }
  }

  /** Throws if the two tags differ, naming the differing top level keys */
  private static void compare(CompoundTag expected, CompoundTag actual, String what) {
    if (!expected.equals(actual)) {
      Set<String> keys = new TreeSet<>(expected.keySet());
      keys.addAll(actual.keySet());
      StringBuilder message = new StringBuilder(what).append(" differs:");
      for (String key : keys) {
        if (!Objects.equals(expected.get(key), actual.get(key))) {
          message.append("\n  ").append(key).append(": ").append(expected.get(key)).append(" -> ").append(actual.get(key));
        }
      }
      throw new IllegalStateException(message.toString());
    }
  }

  /** Runs the check, recording any exception as a failure with the label */
  private static void check(List<String> failures, String label, Runnable check) {
    try {
      check.run();
    } catch (RuntimeException e) {
      failures.add(label + ": " + e);
      TConstruct.LOG.error("Block entity round trip failed for {}", label, e);
    }
  }

  private static void assertNoFailures(GameTestHelper helper, List<String> failures) {
    helper.assertTrue(failures.isEmpty(), Component.literal(failures.size() + " block entity round trip failure(s):\n" + String.join("\n", failures)));
  }


  /* Filling */

  /** Item candidates tried in each slot, in order, until one is accepted */
  private static List<Supplier<ItemStack>> itemCandidates() {
    return List.of(
      () -> new ItemStack(TinkerSmeltery.ingotCast.get()),
      () -> new ItemStack(Items.IRON_INGOT),
      () -> new ItemStack(Items.COAL),
      () -> new ItemStack(TinkerTables.pattern),
      () -> TinkerToolParts.pickHead.get().withMaterial(MaterialIds.iron),
      () -> ToolBuildHandler.createSingleMaterial(TinkerTools.pickaxe.get(), MaterialVariant.of((MaterialVariantId) MaterialIds.iron)),
      () -> new ItemStack(Items.LAVA_BUCKET),
      () -> CopperCanItem.setFluid(new ItemStack(TinkerSmeltery.copperCan), new FluidStack(Fluids.LAVA, FluidValues.BOTTLE)));
  }

  /** Fluid candidates tried in order until one is accepted */
  private static List<FluidStack> fluidCandidates() {
    return List.of(
      new FluidStack(TinkerFluids.moltenIron.get(), FluidValues.INGOT),
      new FluidStack(Fluids.LAVA, FluidValues.INGOT),
      new FluidStack(Fluids.WATER, FluidValues.INGOT));
  }

  /** Sides tried for capabilities: unsided first */
  private static final Direction[] SIDES = {null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

  /**
   * Fills the block entity with whatever it accepts: a custom name, a texture, a material, items in every slot and a fluid.
   * @return true if anything changed
   */
  private static boolean fill(BlockEntity be) {
    boolean changed = false;
    if (be instanceof NameableBlockEntity nameable) {
      nameable.setCustomName(NAME);
      changed = true;
    }
    if (be instanceof IRetexturedBlockEntity retextured) {
      retextured.updateTexture(TEXTURE);
      changed = true;
    }
    if (be instanceof MaterialBlockEntity material) {
      material.setMaterial(MaterialIds.iron);
      changed = true;
    }
    ServerLevel level = (ServerLevel) Objects.requireNonNull(be.getLevel());
    BlockPos pos = be.getBlockPos();
    // items first, so a casting table holds a cast before the fluid arrives
    for (Direction side : SIDES) {
      ResourceHandler<ItemResource> items = level.getCapability(Capabilities.Item.BLOCK, pos, side);
      if (items != null && items.size() > 0) {
        boolean inserted = false;
        for (int slot = 0; slot < items.size(); slot++) {
          for (Supplier<ItemStack> candidate : itemCandidates()) {
            if (insertItem(items, slot, candidate.get())) {
              inserted = true;
              break;
            }
          }
        }
        if (inserted) {
          changed = true;
          break;
        }
      }
    }
    for (Direction side : SIDES) {
      if (insertFluid(level, pos, side)) {
        changed = true;
        break;
      }
    }
    return changed;
  }

  /** Inserts one of the stack into the slot, returns true if accepted */
  private static boolean insertItem(ResourceHandler<ItemResource> handler, int slot, ItemStack stack) {
    try (Transaction tx = Transaction.openRoot()) {
      if (handler.insert(slot, ItemResource.of(stack), 1, tx) > 0) {
        tx.commit();
        return true;
      }
    }
    return false;
  }

  /** Inserts the first accepted fluid candidate through the fluid capability on the side, returns true if any was accepted */
  private static boolean insertFluid(ServerLevel level, BlockPos pos, Direction side) {
    ResourceHandler<FluidResource> fluids = level.getCapability(Capabilities.Fluid.BLOCK, pos, side);
    if (fluids != null) {
      for (FluidStack fluid : fluidCandidates()) {
        try (Transaction tx = Transaction.openRoot()) {
          if (fluids.insert(FluidResource.of(fluid), fluid.getAmount(), tx) > 0) {
            tx.commit();
            return true;
          }
        }
      }
    }
    return false;
  }

  /** Inserts the fluid through the unsided fluid capability, failing if none of it is accepted */
  private static void insertFluid(ServerLevel level, BlockPos pos, FluidStack fluid) {
    ResourceHandler<FluidResource> fluids = level.getCapability(Capabilities.Fluid.BLOCK, pos, null);
    int inserted = 0;
    if (fluids != null) {
      try (Transaction tx = Transaction.openRoot()) {
        inserted = fluids.insert(FluidResource.of(fluid), fluid.getAmount(), tx);
        tx.commit();
      }
    }
    if (inserted <= 0) {
      throw new IllegalStateException("no " + BuiltInRegistries.FLUID.getKey(fluid.getFluid()) + " accepted at " + pos);
    }
  }


  /* Structures */

  /**
   * Builds a 3×3×3 heating structure (open top): bricks everywhere but the centre column, the south wall centre is the controller,
   * the west wall holds a filled fuel tank and above it an empty tank, the north wall a drain with a chute above it, the east wall a duct.
   * @return controller position
   */
  private static BlockPos buildHeatingStructure(ServerLevel level, BlockPos origin, BlockState bricks, Block tank, Block drain, Block duct, Block chute, Block controller) {
    for (int x = 0; x < 3; x++) {
      for (int z = 0; z < 3; z++) {
        level.setBlockAndUpdate(origin.offset(x, 0, z), bricks);
        for (int y = 1; y <= 2; y++) {
          level.setBlockAndUpdate(origin.offset(x, y, z), x == 1 && z == 1 ? Blocks.AIR.defaultBlockState() : bricks);
        }
      }
    }
    level.setBlockAndUpdate(origin.offset(0, 1, 1), tank.defaultBlockState());
    SmelteryRigs.fillTank(level, origin.offset(0, 1, 1), new FluidStack(Fluids.LAVA, 4000));
    level.setBlockAndUpdate(origin.offset(0, 2, 1), tank.defaultBlockState());
    level.setBlockAndUpdate(origin.offset(1, 1, 0), drain.defaultBlockState());
    level.setBlockAndUpdate(origin.offset(1, 2, 0), chute.defaultBlockState());
    level.setBlockAndUpdate(origin.offset(2, 1, 1), duct.defaultBlockState());
    BlockPos controllerPos = origin.offset(1, 1, 2);
    level.setBlockAndUpdate(controllerPos, controller.defaultBlockState().setValue(ControllerBlock.FACING, Direction.SOUTH));
    return controllerPos;
  }
}
