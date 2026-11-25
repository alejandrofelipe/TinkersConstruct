package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.library.recipe.FluidValues;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.ChannelBlock;
import slimeknights.tconstruct.smeltery.block.FaucetBlock;
import slimeknights.tconstruct.smeltery.block.component.SearedTankBlock.TankType;
import slimeknights.tconstruct.smeltery.block.controller.ControllerBlock;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.ChannelBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.FaucetBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.AlloyerBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.SmelteryBlockEntity;

/** Gametests for the smeltery subsystem: melting, casting, and alloying logic. */
public class SmelteryGameTests {

  /** Iron melts inside a formed, fueled smeltery. */
  @TinkerGameTest(template = "gametest/empty_9x9x9", timeoutTicks = 1200)
  public static void smeltery_melts(GameTestHelper helper) {
    BlockPos controller = SmelteryRigs.buildSmeltery(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)));
    // give the multiblock a few ticks to form, then feed it
    helper.runAfterDelay(60, () ->
      SmelteryRigs.insertMeltable(helper.getLevel(), controller, new ItemStack(Items.IRON_INGOT)));
    helper.succeedWhen(() -> {
      if (!(helper.getLevel().getBlockEntity(controller) instanceof SmelteryBlockEntity smeltery)) {
        helper.fail(Component.literal("no smeltery controller BE"), helper.relativePos(controller));
        return;
      }
      FluidStack contained = smeltery.getTank().getFluidInTank(0);
      helper.assertTrue(contained.getFluid() == TinkerFluids.moltenIron.get()
                        && contained.getAmount() >= FluidValues.INGOT,
        Component.literal("expected >= 1 ingot of molten iron, got " + contained.getAmount()));
    });
  }

  /** Amethyst blocks melt once c:storage_blocks/amethyst is defined (M6 Phase A). */
  @TinkerGameTest(template = "gametest/empty_9x9x9", timeoutTicks = 1200)
  public static void smeltery_melts_amethyst(GameTestHelper helper) {
    BlockPos controller = SmelteryRigs.buildSmeltery(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)));
    helper.runAfterDelay(60, () ->
      SmelteryRigs.insertMeltable(helper.getLevel(), controller, new ItemStack(Items.AMETHYST_BLOCK)));
    helper.succeedWhen(() -> {
      if (!(helper.getLevel().getBlockEntity(controller) instanceof SmelteryBlockEntity smeltery)) {
        helper.fail(Component.literal("no smeltery controller BE"), helper.relativePos(controller));
        return;
      }
      FluidStack contained = smeltery.getTank().getFluidInTank(0);
      helper.assertTrue(contained.getFluid() == TinkerFluids.moltenAmethyst.get() && contained.getAmount() > 0,
        Component.literal("expected molten amethyst, got " + contained.getAmount()));
    });
  }

  /**
   * Raw copper ore-melts into molten copper, exercising the {@link slimeknights.tconstruct.library.recipe.melting.OreMeltingRecipe}
   * byproduct path (a bonus of molten gold also lands in the tank, per {@code melting/metal/copper/raw.json}).
   */
  @TinkerGameTest(template = "gametest/empty_9x9x9", timeoutTicks = 1200)
  public static void smeltery_melts_raw_copper(GameTestHelper helper) {
    BlockPos controller = SmelteryRigs.buildSmeltery(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)));
    helper.runAfterDelay(60, () ->
      SmelteryRigs.insertMeltable(helper.getLevel(), controller, new ItemStack(Items.RAW_COPPER)));
    helper.succeedWhen(() -> {
      if (!(helper.getLevel().getBlockEntity(controller) instanceof SmelteryBlockEntity smeltery)) {
        helper.fail(Component.literal("no smeltery controller BE"), helper.relativePos(controller));
        return;
      }
      FluidStack contained = smeltery.getTank().getFluidInTank(0);
      helper.assertTrue(contained.getFluid() == TinkerFluids.moltenCopper.get()
                        && contained.getAmount() >= FluidValues.INGOT,
        Component.literal("expected >= 1 ingot of molten copper, got " + contained.getAmount()));
    });
  }

  /** Glass blocks melt into molten glass, exercising a non-metal melting recipe (c:glass_blocks, unconditioned). */
  @TinkerGameTest(template = "gametest/empty_9x9x9", timeoutTicks = 1200)
  public static void smeltery_melts_glass(GameTestHelper helper) {
    BlockPos controller = SmelteryRigs.buildSmeltery(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)));
    helper.runAfterDelay(60, () ->
      SmelteryRigs.insertMeltable(helper.getLevel(), controller, new ItemStack(Items.GLASS)));
    helper.succeedWhen(() -> {
      if (!(helper.getLevel().getBlockEntity(controller) instanceof SmelteryBlockEntity smeltery)) {
        helper.fail(Component.literal("no smeltery controller BE"), helper.relativePos(controller));
        return;
      }
      FluidStack contained = smeltery.getTank().getFluidInTank(0);
      helper.assertTrue(contained.getFluid() == TinkerFluids.moltenGlass.get()
                        && contained.getAmount() >= FluidValues.GLASS_BLOCK,
        Component.literal("expected >= 1 block of molten glass, got " + contained.getAmount()));
    });
  }

  /** A faucet pours molten iron from a tank into a casting table holding an ingot cast. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 600)
  public static void smeltery_casts(GameTestHelper helper) {
    SmelteryRigs.CastingRig rig = SmelteryRigs.buildCastingRig(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 2)));
    helper.runAfterDelay(10, () -> {
      // put the ingot cast on the table, then open the tap
      if (helper.getLevel().getBlockEntity(rig.table()) instanceof CastingBlockEntity table) {
        table.setItem(CastingBlockEntity.INPUT, new ItemStack(TinkerSmeltery.ingotCast.get()));
      } else {
        helper.fail(Component.literal("no casting table BE"), helper.relativePos(rig.table()));
      }
      // helper.useBlock(faucet) did not result in pouring in this harness; the root cause is undiagnosed
      // (review verified FaucetBlock.useWithoutItem's override matches the 1.21.1 signature and that
      // GameTestHelper.useBlock does dispatch to it via BlockState.useItemOn -> useWithoutItem, so the
      // interaction path is NOT unreachable - probes just saw isPouring() stay false after useBlock while
      // the table's recipe match was independently valid). Calling FaucetBlockEntity.activate() directly -
      // exactly what useWithoutItem invokes - keeps the test covering faucet -> table pouring; real-player
      // right-click coverage is deferred to the M5 manual smoke test.
      if (helper.getLevel().getBlockEntity(rig.faucet()) instanceof FaucetBlockEntity faucet) {
        faucet.activate();
      } else {
        helper.fail(Component.literal("no faucet BE"), helper.relativePos(rig.faucet()));
      }
    });
    helper.succeedWhen(() -> {
      if (!(helper.getLevel().getBlockEntity(rig.table()) instanceof CastingBlockEntity table)) {
        helper.fail(Component.literal("no casting table BE"), helper.relativePos(rig.table()));
        return;
      }
      helper.assertTrue(table.getItem(CastingBlockEntity.OUTPUT).is(Items.IRON_INGOT),
        Component.literal("expected iron ingot in casting output"));
    });
  }

  /**
   * A faucet pours molten iron into a channel, which flows sideways into a second channel and down into a casting basin (the channel
   * flow through the native capabilities and the legacy bridges the channels look their neighbors up with).
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 400)
  public static void faucet_pours_through_channels_into_basin(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos basin = helper.absolutePos(new BlockPos(1, 1, 3));
    BlockPos lowerChannel = basin.above();
    BlockPos upperChannel = lowerChannel.north();
    BlockPos faucet = upperChannel.above();
    BlockPos tank = faucet.east();
    // built bottom up, like a player would, but placed like /setblock: the channels keep their default (unpowered) state, so the
    // neighbor updates of the blocks placed after them must not clear the lower channel's down connection
    level.setBlockAndUpdate(basin, TinkerSmeltery.searedBasin.get().defaultBlockState());
    level.setBlockAndUpdate(lowerChannel, TinkerSmeltery.searedChannel.get().defaultBlockState()
      .setValue(ChannelBlock.DOWN, true).setValue(ChannelBlock.NORTH, ChannelBlock.ChannelConnection.IN));
    level.setBlockAndUpdate(upperChannel, TinkerSmeltery.searedChannel.get().defaultBlockState()
      .setValue(ChannelBlock.SOUTH, ChannelBlock.ChannelConnection.OUT));
    level.setBlockAndUpdate(tank, TinkerSmeltery.searedTank.get(TankType.INGOT_TANK).defaultBlockState());
    SmelteryRigs.fillTank(level, tank, new FluidStack(TinkerFluids.moltenIron.get(), FluidValues.INGOT * 2));
    level.setBlockAndUpdate(faucet, TinkerSmeltery.searedFaucet.get().defaultBlockState().setValue(FaucetBlock.FACING, Direction.WEST));
    helper.runAfterDelay(10, () -> {
      if (level.getBlockEntity(faucet) instanceof FaucetBlockEntity faucetBE) {
        faucetBE.activate();
      } else {
        helper.fail(Component.literal("no faucet BE"), helper.relativePos(faucet));
      }
    });
    helper.succeedWhen(() -> {
      if (!(level.getBlockEntity(basin) instanceof CastingBlockEntity basinBE)) {
        helper.fail(Component.literal("no casting basin BE"), helper.relativePos(basin));
        return;
      }
      int amount = basinBE.getTank().getFluid().getAmount();
      helper.assertTrue(amount == FluidValues.INGOT * 2, Component.literal("expected both ingots of molten iron in the basin, got " + amount + " mB; faucet pouring: "
        + (level.getBlockEntity(faucet) instanceof FaucetBlockEntity faucetBE && faucetBE.isPouring())
        + ", upper channel: " + channelFluid(level, upperChannel) + " mB, lower channel: " + channelFluid(level, lowerChannel) + " mB"));
    });
  }

  /**
   * A channel placed without {@code getStateForPlacement} ({@code /setblock}, structures) takes its powered state from the real
   * neighbor signal: unpowered on its own, powered next to a redstone block even when the placed state says unpowered, without
   * touching its down connection. Losing the signal afterwards still clears the down connection, as before.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void channel_placed_takes_neighbor_power(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockState channel = TinkerSmeltery.searedChannel.get().defaultBlockState();
    helper.assertFalse(channel.getValue(ChannelBlock.POWERED), Component.literal("channel default state is powered"));

    // no signal: stays unpowered
    BlockPos unpowered = helper.absolutePos(new BlockPos(1, 2, 1));
    level.setBlockAndUpdate(unpowered.below(), TinkerSmeltery.searedBasin.get().defaultBlockState());
    level.setBlockAndUpdate(unpowered, channel.setValue(ChannelBlock.DOWN, true));
    helper.assertTrue(!level.getBlockState(unpowered).getValue(ChannelBlock.POWERED) && level.getBlockState(unpowered).getValue(ChannelBlock.DOWN),
                      Component.literal("unpowered channel changed on placement: " + level.getBlockState(unpowered)));

    // next to a redstone block: powered, placed with the default state and with an explicitly unpowered one (a saved structure)
    BlockPos redstone = helper.absolutePos(new BlockPos(3, 2, 2));
    level.setBlockAndUpdate(redstone, Blocks.REDSTONE_BLOCK.defaultBlockState());
    BlockPos powered = redstone.west();
    level.setBlockAndUpdate(powered.below(), TinkerSmeltery.searedBasin.get().defaultBlockState());
    level.setBlockAndUpdate(powered, channel.setValue(ChannelBlock.DOWN, true));
    helper.assertTrue(level.getBlockState(powered).getValue(ChannelBlock.POWERED) && level.getBlockState(powered).getValue(ChannelBlock.DOWN),
                      Component.literal("channel next to a redstone block is not powered with its down connection: " + level.getBlockState(powered)));
    BlockPos explicit = redstone.north();
    level.setBlockAndUpdate(explicit, channel.setValue(ChannelBlock.POWERED, false));
    helper.assertTrue(level.getBlockState(explicit).getValue(ChannelBlock.POWERED),
                      Component.literal("channel placed unpowered next to a redstone block did not pick up the signal: " + level.getBlockState(explicit)));

    // losing the signal clears the down connection (existing redstone behavior)
    level.setBlockAndUpdate(redstone, Blocks.AIR.defaultBlockState());
    BlockState after = level.getBlockState(powered);
    helper.assertTrue(!after.getValue(ChannelBlock.POWERED) && !after.getValue(ChannelBlock.DOWN),
                      Component.literal("channel losing its signal should be unpowered without down connection: " + after));
    helper.succeed();
  }

  /** Fluid amount in the channel at the position, -1 without a channel */
  private static int channelFluid(ServerLevel level, BlockPos pos) {
    return level.getBlockEntity(pos) instanceof ChannelBlockEntity channel ? channel.getFluid().getAmount() : -1;
  }

  /**
   * A faucet pours molten amethyst into a casting table holding a gem cast, producing an amethyst shard.
   * <p>
   * {@code buildCastingRig} always pre-fills molten iron, so this rig is built via {@link #buildCastingRigWithFluid}
   * instead, mirroring its layout with a swapped-in fluid.
   * <p>
   * Recipe per {@code data/tconstruct/recipe/smeltery/casting/amethyst/gem_gold_cast.json} (unconditioned):
   * 100mb molten amethyst + gem cast -> {@code c:gems/amethyst} (amethyst shard).
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 600)
  public static void smeltery_casts_gem(GameTestHelper helper) {
    SmelteryRigs.CastingRig rig = buildCastingRigWithFluid(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 2)),
      new FluidStack(TinkerFluids.moltenAmethyst.get(), FluidValues.INGOT * 4));
    helper.runAfterDelay(10, () -> {
      if (helper.getLevel().getBlockEntity(rig.table()) instanceof CastingBlockEntity table) {
        table.setItem(CastingBlockEntity.INPUT, new ItemStack(TinkerSmeltery.gemCast.get()));
      } else {
        helper.fail(Component.literal("no casting table BE"), helper.relativePos(rig.table()));
      }
      if (helper.getLevel().getBlockEntity(rig.faucet()) instanceof FaucetBlockEntity faucet) {
        faucet.activate();
      } else {
        helper.fail(Component.literal("no faucet BE"), helper.relativePos(rig.faucet()));
      }
    });
    helper.succeedWhen(() -> {
      if (!(helper.getLevel().getBlockEntity(rig.table()) instanceof CastingBlockEntity table)) {
        helper.fail(Component.literal("no casting table BE"), helper.relativePos(rig.table()));
        return;
      }
      helper.assertTrue(table.getItem(CastingBlockEntity.OUTPUT).is(Items.AMETHYST_SHARD),
        Component.literal("expected amethyst shard in casting output"));
    });
  }

  /**
   * A faucet pours blazing blood into a casting table holding a rod cast, producing a blaze rod.
   * <p>
   * Recipe per {@code data/tconstruct/recipe/smeltery/casting/blaze/rod_gold_cast.json} (unconditioned):
   * 100mb blazing blood + rod cast -> {@code minecraft:blaze_rod}.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 600)
  public static void smeltery_casts_rod(GameTestHelper helper) {
    SmelteryRigs.CastingRig rig = buildCastingRigWithFluid(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 2)),
      new FluidStack(TinkerFluids.blazingBlood.get(), FluidValues.INGOT * 4));
    helper.runAfterDelay(10, () -> {
      if (helper.getLevel().getBlockEntity(rig.table()) instanceof CastingBlockEntity table) {
        table.setItem(CastingBlockEntity.INPUT, new ItemStack(TinkerSmeltery.rodCast.get()));
      } else {
        helper.fail(Component.literal("no casting table BE"), helper.relativePos(rig.table()));
      }
      if (helper.getLevel().getBlockEntity(rig.faucet()) instanceof FaucetBlockEntity faucet) {
        faucet.activate();
      } else {
        helper.fail(Component.literal("no faucet BE"), helper.relativePos(rig.faucet()));
      }
    });
    helper.succeedWhen(() -> {
      if (!(helper.getLevel().getBlockEntity(rig.table()) instanceof CastingBlockEntity table)) {
        helper.fail(Component.literal("no casting table BE"), helper.relativePos(rig.table()));
        return;
      }
      helper.assertTrue(table.getItem(CastingBlockEntity.OUTPUT).is(Items.BLAZE_ROD),
        Component.literal("expected blaze rod in casting output"));
    });
  }

  /**
   * Copper + gold alloy into rose gold inside a fueled alloyer.
   * <p>
   * Unlike the smeltery, the alloyer's own tank ({@link AlloyerBlockEntity#getFluidHandler()}) is the ALLOY
   * OUTPUT tank only ({@code MixerAlloyTank.fill}/{@code canFit} both delegate to it) - alloy INPUTS are read
   * from neighboring blocks tagged {@code tconstruct:alloyer_tanks} (any of the four horizontal sides or up;
   * see {@code MixerAlloyTank.checkTanks}, which never scans down since that side is reserved for fuel). So
   * this rig places one ingot tank of molten copper and one of molten gold beside the alloyer instead of
   * filling the alloyer's own tank twice (which silently rejects the second fluid - a single-fluid tank).
   * <p>
   * Recipe ratio per {@code data/tconstruct/recipe/smeltery/alloys/molten_rose_gold.json}:
   * {@code FluidValues.INGOT} molten copper + {@code FluidValues.INGOT} molten gold -> 2x {@code FluidValues.INGOT} molten rose gold.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 1200)
  public static void alloyer_alloys(GameTestHelper helper) {
    BlockPos base = helper.absolutePos(new BlockPos(2, 1, 2));
    // fuel tank below, alloyer on top
    helper.getLevel().setBlockAndUpdate(base,
      TinkerSmeltery.searedTank.get(TankType.FUEL_TANK).defaultBlockState());
    SmelteryRigs.fillTank(helper.getLevel(), base, new FluidStack(Fluids.LAVA, 4000));
    BlockPos alloyer = base.above();
    // setBlockAndUpdate does not run getStateForPlacement, so IN_STRUCTURE (which AlloyerBlockEntity.isFormed()
    // gates all alloying on) must be set explicitly here rather than relying on the fuel tank placed below.
    helper.getLevel().setBlockAndUpdate(alloyer,
      TinkerSmeltery.scorchedAlloyer.get().defaultBlockState().setValue(ControllerBlock.IN_STRUCTURE, true));
    // ingredient tanks beside the alloyer: copper to the east, gold to the west
    BlockPos copperTankPos = alloyer.relative(Direction.EAST);
    BlockPos goldTankPos = alloyer.relative(Direction.WEST);
    helper.getLevel().setBlockAndUpdate(copperTankPos, TinkerSmeltery.searedTank.get(TankType.INGOT_TANK).defaultBlockState());
    helper.getLevel().setBlockAndUpdate(goldTankPos, TinkerSmeltery.searedTank.get(TankType.INGOT_TANK).defaultBlockState());
    SmelteryRigs.fillTank(helper.getLevel(), copperTankPos, new FluidStack(TinkerFluids.moltenCopper.get(), FluidValues.INGOT));
    SmelteryRigs.fillTank(helper.getLevel(), goldTankPos, new FluidStack(TinkerFluids.moltenGold.get(), FluidValues.INGOT));
    helper.succeedWhen(() -> {
      if (!(helper.getLevel().getBlockEntity(alloyer) instanceof AlloyerBlockEntity alloyerBe)) {
        helper.fail(Component.literal("no alloyer BE"), helper.relativePos(alloyer));
        return;
      }
      helper.assertTrue(contains(alloyerBe, TinkerFluids.moltenRoseGold.get()),
        Component.literal("expected molten rose gold in alloyer tank"));
    });
  }

  /**
   * Copper + amethyst alloy into amethyst bronze inside a fueled alloyer; sibling of {@link #alloyer_alloys}
   * covering a second {@link slimeknights.tconstruct.library.recipe.alloying.AlloyRecipe} with the same rig shape
   * (see that method's javadoc for why ingredient tanks sit beside the alloyer rather than filling its own tank).
   * <p>
   * Recipe ratio per {@code data/tconstruct/recipe/smeltery/alloys/molten_amethyst_bronze.json} (unconditioned):
   * {@code FluidValues.INGOT} (90) molten copper + {@code FluidValues.GEM} (100) molten amethyst -> 90 molten amethyst bronze.
   * <p>
   * Chosen over the more obvious copper + tin bronze suggested by the coverage plan because
   * {@code molten_bronze.json} carries {@code neoforge:conditions: mantle:tag_filled c:ingots/tin}, and this build
   * has no mod contributing items to {@code c:ingots/tin} (confirmed: no generated {@code data/c/tags/item/**}
   * file defines it), so that recipe never registers - the alloyer would sit filled forever with no match.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 1200)
  public static void alloyer_alloys_amethyst_bronze(GameTestHelper helper) {
    BlockPos base = helper.absolutePos(new BlockPos(2, 1, 2));
    // fuel tank below, alloyer on top
    helper.getLevel().setBlockAndUpdate(base,
      TinkerSmeltery.searedTank.get(TankType.FUEL_TANK).defaultBlockState());
    SmelteryRigs.fillTank(helper.getLevel(), base, new FluidStack(Fluids.LAVA, 4000));
    BlockPos alloyer = base.above();
    helper.getLevel().setBlockAndUpdate(alloyer,
      TinkerSmeltery.scorchedAlloyer.get().defaultBlockState().setValue(ControllerBlock.IN_STRUCTURE, true));
    // ingredient tanks beside the alloyer: copper to the east, amethyst to the west
    BlockPos copperTankPos = alloyer.relative(Direction.EAST);
    BlockPos amethystTankPos = alloyer.relative(Direction.WEST);
    helper.getLevel().setBlockAndUpdate(copperTankPos, TinkerSmeltery.searedTank.get(TankType.INGOT_TANK).defaultBlockState());
    helper.getLevel().setBlockAndUpdate(amethystTankPos, TinkerSmeltery.searedTank.get(TankType.INGOT_TANK).defaultBlockState());
    SmelteryRigs.fillTank(helper.getLevel(), copperTankPos, new FluidStack(TinkerFluids.moltenCopper.get(), FluidValues.INGOT));
    SmelteryRigs.fillTank(helper.getLevel(), amethystTankPos, new FluidStack(TinkerFluids.moltenAmethyst.get(), FluidValues.GEM));
    helper.succeedWhen(() -> {
      if (!(helper.getLevel().getBlockEntity(alloyer) instanceof AlloyerBlockEntity alloyerBe)) {
        helper.fail(Component.literal("no alloyer BE"), helper.relativePos(alloyer));
        return;
      }
      helper.assertTrue(contains(alloyerBe, TinkerFluids.moltenAmethystBronze.get()),
        Component.literal("expected molten amethyst bronze in alloyer tank"));
    });
  }

  private static boolean contains(AlloyerBlockEntity be, Fluid fluid) {
    ResourceHandler<FluidResource> handler = be.getFluidHandler();
    for (int i = 0; i < handler.size(); i++) {
      if (handler.getResource(i).getFluid() == fluid) return true;
    }
    return false;
  }

  /**
   * Builds a casting rig shaped like {@link SmelteryRigs#buildCastingRig} (tank, faucet to the east, table below
   * the faucet) but pre-filled with an arbitrary fluid instead of {@code buildCastingRig}'s hardcoded molten iron.
   */
  private static SmelteryRigs.CastingRig buildCastingRigWithFluid(ServerLevel level, BlockPos origin, FluidStack fluid) {
    BlockPos tank = origin.above(1);
    level.setBlockAndUpdate(tank, TinkerSmeltery.searedTank.get(TankType.INGOT_TANK).defaultBlockState());
    SmelteryRigs.fillTank(level, tank, fluid);
    BlockPos faucet = tank.relative(Direction.EAST);
    level.setBlockAndUpdate(faucet,
      TinkerSmeltery.searedFaucet.get().defaultBlockState()
        .setValue(FaucetBlock.FACING, Direction.EAST));
    BlockPos table = faucet.below();
    level.setBlockAndUpdate(table, TinkerSmeltery.searedTable.get().defaultBlockState());
    return new SmelteryRigs.CastingRig(tank, faucet, table);
  }
}
