package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import slimeknights.mantle.fluid.FluidTransferHelper;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.library.recipe.FluidValues;
import slimeknights.tconstruct.library.transfer.TransferUtil;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.FaucetBlock;
import slimeknights.tconstruct.smeltery.block.component.SearedTankBlock.TankType;
import slimeknights.tconstruct.smeltery.block.controller.ControllerBlock;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.FaucetBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.ProxyTankBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.HeatingStructureBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.MelterBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.tank.SmelteryTank;
import slimeknights.tconstruct.smeltery.menu.HeatingStructureContainerMenu;

import java.util.List;

/**
 * Gametests for the smeltery side tanks and handlers once they are native only (M5 phase 6b(i)): the smeltery tank's fluid order and
 * notifications, a faucet draining the bottom fluid of a smeltery, fuel consumption (liquid through the multi tank fuel module, solid
 * through the heater), and the proxy tank's fluid handler.
 */
public class SmelteryTransferGameTests {
  private static final FluidResource GOLD = FluidResource.of(TinkerFluids.moltenGold.get());
  private static final FluidResource COPPER = FluidResource.of(TinkerFluids.moltenCopper.get());
  private static final FluidResource WATER = FluidResource.of(Fluids.WATER);

  /** Gets the formed heating structure at the position, failing the test if missing */
  private static HeatingStructureBlockEntity structure(GameTestHelper helper, BlockPos controller) {
    if (helper.getLevel().getBlockEntity(controller) instanceof HeatingStructureBlockEntity structure && structure.getStructure() != null) {
      return structure;
    }
    throw new IllegalStateException("No formed heating structure at " + controller);
  }

  /** Waits until the heating structure at the position formed */
  private static void assertFormed(GameTestHelper helper, BlockPos controller) {
    helper.assertTrue(helper.getLevel().getBlockEntity(controller) instanceof HeatingStructureBlockEntity structure && structure.getStructure() != null,
      Component.literal("smeltery did not form"));
  }

  /** Inserts fluids into the handler in one committed transaction, each must fit entirely */
  private static void insert(GameTestHelper helper, ResourceHandler<FluidResource> handler, FluidStack... fluids) {
    try (Transaction tx = Transaction.openRoot()) {
      for (FluidStack fluid : fluids) {
        int inserted = handler.insert(FluidResource.of(fluid), fluid.getAmount(), tx);
        helper.assertTrue(inserted == fluid.getAmount(), Component.literal("handler took " + inserted + " of " + fluid));
      }
      tx.commit();
    }
  }

  private static void assertFluids(GameTestHelper helper, SmelteryTank<?> tank, List<FluidStack> expected, String what) {
    List<FluidStack> actual = tank.getFluids();
    boolean matches = actual.size() == expected.size();
    for (int i = 0; matches && i < actual.size(); i++) {
      matches = FluidStack.matches(actual.get(i), expected.get(i));
    }
    helper.assertTrue(matches, Component.literal(what + ": expected " + expected + " but the smeltery has " + actual));
  }

  /**
   * The smeltery tank through its native handler: fluids stack from the bottom in insertion order and merge by fluid, an aborted transaction
   * changes nothing, the first drainable fluid is the bottom one, emptying a fluid removes it from the list, and reordering keeps working.
   */
  @TinkerGameTest(template = "gametest/empty_9x9x9", timeoutTicks = 300)
  public static void smeltery_tank_native_fluid_order(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos controller = SmelteryRigs.buildSearedHeatingStructure(level, helper.absolutePos(new BlockPos(1, 1, 1)));
    helper.startSequence()
      .thenWaitUntil(() -> assertFormed(helper, controller))
      .thenExecute(() -> {
        SmelteryTank<?> tank = structure(helper, controller).getTank();
        ResourceHandler<FluidResource> handler = level.getCapability(Capabilities.Fluid.BLOCK, controller, null);
        helper.assertTrue(handler == tank, Component.literal("controller capability is not the smeltery tank"));
        insert(helper, tank, new FluidStack(TinkerFluids.moltenGold.get(), FluidValues.INGOT), new FluidStack(Fluids.WATER, 500));
        insert(helper, tank, new FluidStack(TinkerFluids.moltenGold.get(), FluidValues.INGOT));
        assertFluids(helper, tank, List.of(new FluidStack(TinkerFluids.moltenGold.get(), FluidValues.INGOT * 2), new FluidStack(Fluids.WATER, 500)), "after inserting");
        helper.assertTrue(tank.getContained() == FluidValues.INGOT * 2 + 500, Component.literal("wrong contained amount " + tank.getContained()));

        // aborted: no change
        try (Transaction tx = Transaction.openRoot()) {
          tank.insert(COPPER, FluidValues.INGOT, tx);
          tank.extract(GOLD, FluidValues.INGOT, tx);
        }
        assertFluids(helper, tank, List.of(new FluidStack(TinkerFluids.moltenGold.get(), FluidValues.INGOT * 2), new FluidStack(Fluids.WATER, 500)), "after the aborted transaction");

        // the bottom fluid drains first
        FluidStack drainable = FluidTransferHelper.getDrainableFluid(tank);
        helper.assertTrue(FluidStack.matches(drainable, new FluidStack(TinkerFluids.moltenGold.get(), FluidValues.INGOT * 2)), Component.literal("first drainable fluid is " + drainable));

        // emptying the bottom fluid moves the next one down
        helper.assertTrue(FluidStack.matches(TransferUtil.drain(tank, new FluidStack(TinkerFluids.moltenGold.get(), 1000), false), new FluidStack(TinkerFluids.moltenGold.get(), FluidValues.INGOT * 2)),
          Component.literal("did not drain all the gold"));
        assertFluids(helper, tank, List.of(new FluidStack(Fluids.WATER, 500)), "after draining the gold");

        // a new fluid goes on top, and can be moved to the bottom
        insert(helper, tank, new FluidStack(TinkerFluids.moltenCopper.get(), FluidValues.INGOT));
        tank.moveFluidToBottom(1);
        assertFluids(helper, tank, List.of(new FluidStack(TinkerFluids.moltenCopper.get(), FluidValues.INGOT), new FluidStack(Fluids.WATER, 500)), "after moving copper to the bottom");
        helper.assertTrue(FluidTransferHelper.getDrainableFluid(tank).getFluid() == TinkerFluids.moltenCopper.get(), Component.literal("copper should drain first after reordering"));
      })
      .thenSucceed();
  }

  /**
   * Fluids poured into a smeltery through its native handler alloy. The smeltery caches the alloy recipes matching its fluids while it looks
   * for fuel, so this needs the tank to report a new fluid ({@code FluidChange.ADDED}) when a native insert commits; the transfer step 1
   * handler reported every native change as {@code CHANGED}, so fluids poured in through a drain never alloyed.
   */
  @TinkerGameTest(template = "gametest/empty_9x9x9", timeoutTicks = 600)
  public static void smeltery_alloys_fluids_poured_through_drain(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos controller = SmelteryRigs.buildSearedHeatingStructure(level, helper.absolutePos(new BlockPos(1, 1, 1)));
    BlockPos drain = helper.absolutePos(new BlockPos(2, 2, 1));
    helper.startSequence()
      .thenWaitUntil(() -> assertFormed(helper, controller))
      // let the empty smeltery look for fuel a few times, which caches the (empty) list of matching alloy recipes
      .thenIdle(20)
      .thenExecute(() -> {
        ResourceHandler<FluidResource> handler = level.getCapability(Capabilities.Fluid.BLOCK, drain, Direction.NORTH);
        helper.assertTrue(handler != null, Component.literal("drain has no fluid capability"));
        insert(helper, handler, new FluidStack(TinkerFluids.moltenCopper.get(), FluidValues.INGOT), new FluidStack(TinkerFluids.moltenGold.get(), FluidValues.INGOT));
      })
      .thenWaitUntil(() -> {
        SmelteryTank<?> tank = structure(helper, controller).getTank();
        boolean alloyed = tank.getFluids().stream().anyMatch(fluid -> fluid.getFluid() == TinkerFluids.moltenRoseGold.get());
        helper.assertTrue(alloyed, Component.literal("copper and gold did not alloy, smeltery has " + tank.getFluids()));
      })
      .thenSucceed();
  }

  /** A faucet on a smeltery drain pours the bottom fluid only into the basin below. */
  @TinkerGameTest(template = "gametest/empty_9x9x9", timeoutTicks = 400)
  public static void faucet_pours_bottom_fluid_from_drain(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos controller = SmelteryRigs.buildSearedHeatingStructure(level, helper.absolutePos(new BlockPos(1, 1, 1)));
    BlockPos faucet = helper.absolutePos(new BlockPos(2, 2, 0));
    BlockPos basin = faucet.below();
    level.setBlockAndUpdate(basin, TinkerSmeltery.searedBasin.get().defaultBlockState());
    level.setBlockAndUpdate(faucet, TinkerSmeltery.searedFaucet.get().defaultBlockState().setValue(FaucetBlock.FACING, Direction.NORTH));
    helper.startSequence()
      .thenWaitUntil(() -> assertFormed(helper, controller))
      .thenExecute(() -> {
        insert(helper, structure(helper, controller).getTank(), new FluidStack(TinkerFluids.moltenGold.get(), FluidValues.INGOT), new FluidStack(Fluids.WATER, 500));
        if (level.getBlockEntity(faucet) instanceof FaucetBlockEntity faucetBE) {
          faucetBE.activate();
        } else {
          helper.fail(Component.literal("no faucet BE"), helper.relativePos(faucet));
        }
      })
      .thenWaitUntil(() -> {
        FluidStack poured = level.getBlockEntity(basin) instanceof CastingBlockEntity basinBE ? basinBE.getTank().getFluid() : FluidStack.EMPTY;
        helper.assertTrue(FluidStack.matches(poured, new FluidStack(TinkerFluids.moltenGold.get(), FluidValues.INGOT)), Component.literal("basin holds " + poured));
      })
      .thenExecute(() -> assertFluids(helper, structure(helper, controller).getTank(), List.of(new FluidStack(Fluids.WATER, 500)), "after pouring"))
      .thenSucceed();
  }

  /** Smeltery menu fuel buttons: a held bucket fills from the fuel tanks, and a filled bucket empties back into them. */
  @TinkerGameTest(template = "gametest/empty_9x9x9", timeoutTicks = 300)
  public static void smeltery_menu_fuel_buttons(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos controller = SmelteryRigs.buildSearedHeatingStructure(level, helper.absolutePos(new BlockPos(1, 1, 1)));
    helper.startSequence()
      .thenWaitUntil(() -> assertFormed(helper, controller))
      // let the smeltery look for fuel, which selects the filled tank as the last fuel tank
      .thenIdle(10)
      .thenExecute(() -> {
        HeatingStructureBlockEntity structure = structure(helper, controller);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        HeatingStructureContainerMenu menu = new HeatingStructureContainerMenu(0, player.getInventory(), structure);
        // fuel module view: one index per tank position
        helper.assertTrue(structure.getFuelModule().size() == 2, Component.literal("fuel module should see 2 tanks, sees " + structure.getFuelModule().size()));

        menu.setCarried(new ItemStack(Items.BUCKET));
        helper.assertTrue(menu.clickMenuButton(player, 1), Component.literal("fuel drain click was not handled"));
        helper.assertTrue(menu.getCarried().is(Items.LAVA_BUCKET), Component.literal("held bucket was not filled with lava, holds " + menu.getCarried()));
        helper.assertTrue(fuelAmount(structure) == 3000, Component.literal("fuel tanks should hold 3000 mB, hold " + fuelAmount(structure)));

        helper.assertTrue(menu.clickMenuButton(player, 2), Component.literal("fuel fill click was not handled"));
        helper.assertTrue(menu.getCarried().is(Items.BUCKET), Component.literal("held lava bucket was not emptied, holds " + menu.getCarried()));
        helper.assertTrue(fuelAmount(structure) == 4000, Component.literal("fuel tanks should hold 4000 mB again, hold " + fuelAmount(structure)));
      })
      .thenSucceed();
  }

  /** Total lava in the fuel tanks, through the fuel module's native view */
  private static long fuelAmount(HeatingStructureBlockEntity structure) {
    ResourceHandler<FluidResource> fuel = structure.getFuelModule();
    long total = 0;
    for (int i = 0; i < fuel.size(); i++) {
      if (fuel.getResource(i).getFluid() == Fluids.LAVA) {
        total += fuel.getAmountAsLong(i);
      }
    }
    return total;
  }

  /**
   * A melter burns a lava bucket from the heater below as solid fuel. The heater only takes fuels, so the empty bucket left over does not fit
   * back and is dropped next to it, like the legacy item handler insert.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 600)
  public static void melter_burns_solid_fuel_dropping_container(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos heater = helper.absolutePos(new BlockPos(2, 1, 2));
    BlockPos melter = heater.above();
    level.setBlockAndUpdate(heater, TinkerSmeltery.searedHeater.get().defaultBlockState());
    level.setBlockAndUpdate(melter, TinkerSmeltery.searedMelter.get().defaultBlockState().setValue(ControllerBlock.IN_STRUCTURE, true));
    ResourceHandler<ItemResource> heaterItems = level.getCapability(Capabilities.Item.BLOCK, heater, Direction.UP);
    ResourceHandler<ItemResource> melterItems = level.getCapability(Capabilities.Item.BLOCK, melter, Direction.UP);
    helper.assertTrue(heaterItems != null && melterItems != null, Component.literal("heater or melter has no item capability"));
    try (Transaction tx = Transaction.openRoot()) {
      helper.assertTrue(heaterItems.insert(ItemResource.of(Items.LAVA_BUCKET), 1, tx) == 1, Component.literal("heater did not take the lava bucket"));
      helper.assertTrue(melterItems.insert(ItemResource.of(Items.COPPER_INGOT), 1, tx) == 1, Component.literal("melter did not take the copper ingot"));
      tx.commit();
    }
    helper.succeedWhen(() -> {
      if (!(level.getBlockEntity(melter) instanceof MelterBlockEntity melterBE)) {
        helper.fail(Component.literal("no melter BE"), helper.relativePos(melter));
        return;
      }
      helper.assertTrue(melterBE.getFuelModule().hasFuel(), Component.literal("melter did not take fuel from the heater"));
      helper.assertTrue(heaterItems.getResource(0).isEmpty(), Component.literal("heater should be empty, holds " + heaterItems.getResource(0)));
      List<ItemEntity> buckets = level.getEntitiesOfClass(ItemEntity.class, new AABB(heater).inflate(2), entity -> entity.getItem().is(Items.BUCKET));
      helper.assertTrue(buckets.size() == 1 && buckets.get(0).getItem().getCount() == 1, Component.literal("expected one dropped empty bucket, found " + buckets));
      FluidStack molten = melterBE.getTank().getFluid();
      helper.assertTrue(molten.getFluid() == TinkerFluids.moltenCopper.get() && molten.getAmount() >= FluidValues.INGOT, Component.literal("melter holds " + molten));
    });
  }

  /** Proxy tank: buckets empty into and fill from the tank item inside it through its native fluid handler, and the block stays in sync. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void proxy_tank_bucket_interaction(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
    level.setBlockAndUpdate(pos, TinkerSmeltery.scorchedProxyTank.get().defaultBlockState());
    if (!(level.getBlockEntity(pos) instanceof ProxyTankBlockEntity proxy)) {
      throw new IllegalStateException("Expected a proxy tank at " + pos);
    }
    proxy.getItemTank().setStack(new ItemStack(TinkerSmeltery.searedTank.get(TankType.FUEL_TANK)));
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
    proxy.interact(player, InteractionHand.MAIN_HAND, true);
    FluidStack contained = TransferUtil.getFluid(proxy.getItemTank().getFluidCapability(), 0);
    helper.assertTrue(FluidStack.matches(contained, new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME)), Component.literal("proxied tank holds " + contained));
    helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).is(Items.BUCKET), Component.literal("bucket was not emptied, holds " + player.getItemInHand(InteractionHand.MAIN_HAND)));
    helper.assertTrue(proxy.getItemTank().getStack().is(TinkerSmeltery.searedTank.get(TankType.FUEL_TANK).asItem()), Component.literal("proxy lost its tank item: " + proxy.getItemTank().getStack()));
    helper.assertTrue(proxy.getComparatorStrength() == 1 + 14 * FluidType.BUCKET_VOLUME / TankType.FUEL_TANK.getCapacity(),
      Component.literal("comparator strength not updated: " + proxy.getComparatorStrength()));

    proxy.interact(player, InteractionHand.MAIN_HAND, true);
    contained = TransferUtil.getFluid(proxy.getItemTank().getFluidCapability(), 0);
    helper.assertTrue(contained.isEmpty(), Component.literal("proxied tank should be empty, holds " + contained));
    helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).is(Items.WATER_BUCKET), Component.literal("bucket was not filled, holds " + player.getItemInHand(InteractionHand.MAIN_HAND)));
    helper.succeed();
  }
}
