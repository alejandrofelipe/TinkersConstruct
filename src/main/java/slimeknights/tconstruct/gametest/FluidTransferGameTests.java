package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import slimeknights.mantle.fluid.FluidTransferHelper;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.library.recipe.FluidValues;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.component.SearedTankBlock.TankType;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.CastingTankBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.FluidCannonBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.HeatingStructureBlockEntity;
import slimeknights.tconstruct.smeltery.item.CopperCanItem;
import slimeknights.tconstruct.smeltery.menu.HeatingStructureContainerMenu;

/**
 * Gametests for Mantle's native {@link FluidTransferHelper} (M5 phase 6a) against real blocks and datapacks: player interactions with Tinkers
 * tanks through the item capability and through each {@code mantle:fluid_transfer} type loaded from data ({@code empty_item}, {@code fill_item},
 * {@code empty_potion}, {@code fill_nbt}), and the Tinkers call sites that moved to the native overloads (casting table, casting tank, fluid cannon,
 * smeltery menu).
 */
public class FluidTransferGameTests {
  private static final BlockPos POS = new BlockPos(2, 1, 2);

  /** Places a block at the relative position and returns its block entity */
  private static <T> T place(GameTestHelper helper, BlockPos relative, net.minecraft.world.level.block.Block block, Class<T> type) {
    BlockPos pos = helper.absolutePos(relative);
    helper.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
    if (type.isInstance(helper.getLevel().getBlockEntity(pos))) {
      return type.cast(helper.getLevel().getBlockEntity(pos));
    }
    throw new IllegalStateException("Expected a " + type.getSimpleName() + " at " + pos);
  }

  /** Gets the fluid in the first slot of the block's fluid capability */
  private static FluidStack fluid(GameTestHelper helper, BlockPos relative) {
    ResourceHandler<FluidResource> handler = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(relative), Direction.UP);
    if (handler == null) {
      throw new IllegalStateException("No fluid capability at " + helper.absolutePos(relative));
    }
    return FluidUtil.getStack(handler, 0);
  }

  /** Uses the held item on the tank at the relative position through Mantle's tank interaction */
  private static boolean use(GameTestHelper helper, Player player, ItemStack held) {
    player.setItemInHand(InteractionHand.MAIN_HAND, held);
    return FluidTransferHelper.interactWithTank(helper.getLevel(), helper.absolutePos(POS), player, InteractionHand.MAIN_HAND, Direction.UP, Direction.UP);
  }

  private static void assertFluid(GameTestHelper helper, FluidStack actual, FluidStack expected, String what) {
    helper.assertTrue(FluidStack.matches(actual, expected), Component.literal(what + ": expected " + expected + " but got " + actual));
  }

  private static void assertHeld(GameTestHelper helper, Player player, ItemStack expected, String what) {
    ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
    helper.assertTrue(ItemStack.matches(held, expected), Component.literal(what + ": expected " + expected + " in hand but got " + held));
  }

  /** A bucket goes into a tank and back out through the item capability. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tank_bucket_round_trip(GameTestHelper helper) {
    place(helper, POS, TinkerSmeltery.searedTank.get(TankType.FUEL_TANK), Object.class);
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    helper.assertTrue(use(helper, player, new ItemStack(Items.LAVA_BUCKET)), Component.literal("lava bucket did not interact with the tank"));
    assertFluid(helper, fluid(helper, POS), new FluidStack(Fluids.LAVA, FluidType.BUCKET_VOLUME), "after emptying the bucket");
    assertHeld(helper, player, new ItemStack(Items.BUCKET), "after emptying the bucket");

    helper.assertTrue(use(helper, player, new ItemStack(Items.BUCKET)), Component.literal("empty bucket did not interact with the tank"));
    assertFluid(helper, fluid(helper, POS), FluidStack.EMPTY, "after filling the bucket");
    assertHeld(helper, player, new ItemStack(Items.LAVA_BUCKET), "after filling the bucket");

    // nothing to fill from: no change, the bucket still counts as a container
    helper.assertTrue(use(helper, player, new ItemStack(Items.BUCKET)), Component.literal("empty bucket on an empty tank was not handled as a container"));
    assertHeld(helper, player, new ItemStack(Items.BUCKET), "on an empty tank");
    helper.succeed();
  }

  /** The datapack's {@code empty_item}/{@code fill_item} transfers: honey bottles into a tank and back. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tank_json_honey_bottle_round_trip(GameTestHelper helper) {
    place(helper, POS, TinkerSmeltery.searedTank.get(TankType.FUEL_TANK), Object.class);
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    helper.assertTrue(use(helper, player, new ItemStack(Items.HONEY_BOTTLE)), Component.literal("honey bottle did not interact with the tank"));
    assertFluid(helper, fluid(helper, POS), new FluidStack(TinkerFluids.honey.get(), FluidValues.BOTTLE), "after emptying the honey bottle");
    assertHeld(helper, player, new ItemStack(Items.GLASS_BOTTLE), "after emptying the honey bottle");

    helper.assertTrue(use(helper, player, new ItemStack(Items.GLASS_BOTTLE)), Component.literal("glass bottle did not interact with the honey tank"));
    assertFluid(helper, fluid(helper, POS), FluidStack.EMPTY, "after filling the glass bottle");
    assertHeld(helper, player, new ItemStack(Items.HONEY_BOTTLE), "after filling the glass bottle");
    helper.succeed();
  }

  /** The datapack's {@code empty_potion}/{@code fill_nbt} transfers keep the potion contents through the tank, and a water bottle empties water. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tank_json_potion_round_trip(GameTestHelper helper) {
    place(helper, POS, TinkerSmeltery.searedTank.get(TankType.FUEL_TANK), Object.class);
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    ItemStack swiftness = PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS);
    helper.assertTrue(use(helper, player, swiftness.copy()), Component.literal("potion did not interact with the tank"));
    FluidStack contained = fluid(helper, POS);
    helper.assertTrue(contained.is(TinkerFluids.potion.get()) && contained.getAmount() == FluidValues.BOTTLE,
      Component.literal("tank did not receive a bottle of potion fluid, has " + contained));
    helper.assertTrue(new PotionContents(Potions.SWIFTNESS).equals(contained.get(DataComponents.POTION_CONTENTS)),
      Component.literal("potion fluid lost the potion contents: " + contained.get(DataComponents.POTION_CONTENTS)));
    assertHeld(helper, player, new ItemStack(Items.GLASS_BOTTLE), "after emptying the potion");

    helper.assertTrue(use(helper, player, new ItemStack(Items.GLASS_BOTTLE)), Component.literal("glass bottle did not interact with the potion tank"));
    assertFluid(helper, fluid(helper, POS), FluidStack.EMPTY, "after filling the potion back");
    assertHeld(helper, player, swiftness, "after filling the potion back");

    // water bottles empty plain water
    helper.assertTrue(use(helper, player, PotionContents.createItemStack(Items.POTION, Potions.WATER)), Component.literal("water bottle did not interact with the tank"));
    assertFluid(helper, fluid(helper, POS), new FluidStack(Fluids.WATER, FluidValues.BOTTLE), "after emptying the water bottle");
    helper.succeed();
  }

  /** Casting table interaction pours a held container natively and starts the casting recipe on commit. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 300)
  public static void casting_table_pours_held_container(GameTestHelper helper) {
    CastingBlockEntity table = place(helper, POS, TinkerSmeltery.searedTable.get(), CastingBlockEntity.class);
    table.setItem(CastingBlockEntity.INPUT, new ItemStack(TinkerSmeltery.ingotCast.get()));
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    player.setItemInHand(InteractionHand.MAIN_HAND, CopperCanItem.setFluid(new ItemStack(TinkerSmeltery.copperCan), new FluidStack(TinkerFluids.moltenIron.get(), FluidValues.INGOT)));
    table.interact(player, InteractionHand.MAIN_HAND);
    assertFluid(helper, table.getTank().getFluid(), new FluidStack(TinkerFluids.moltenIron.get(), FluidValues.INGOT), "casting table tank");
    helper.assertTrue(table.getRecipeOutput().is(Items.IRON_INGOT), Component.literal("pouring did not start the iron ingot recipe"));
    assertHeld(helper, player, new ItemStack(TinkerSmeltery.copperCan), "after pouring");
    helper.succeedWhen(() -> helper.assertTrue(table.getItem(CastingBlockEntity.OUTPUT).is(Items.IRON_INGOT), Component.literal("expected the cast iron ingot in the output")));
  }

  /** Casting tank: an inserted bucket is emptied into the tank, and clicking the tank with a bucket fills it. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void casting_tank_processes_items(GameTestHelper helper) {
    CastingTankBlockEntity tank = place(helper, POS, TinkerSmeltery.searedCastingTank.get(), CastingTankBlockEntity.class);
    tank.setItem(CastingTankBlockEntity.INPUT, new ItemStack(Items.LAVA_BUCKET));
    assertFluid(helper, fluid(helper, POS), new FluidStack(Fluids.LAVA, FluidType.BUCKET_VOLUME), "after inserting the lava bucket");
    helper.assertTrue(tank.getItem(CastingTankBlockEntity.INPUT).isEmpty() && tank.getItem(CastingTankBlockEntity.OUTPUT).is(Items.BUCKET),
      Component.literal("emptied bucket did not move to the output"));

    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
    tank.interact(player, InteractionHand.MAIN_HAND, true);
    assertFluid(helper, fluid(helper, POS), FluidStack.EMPTY, "after filling the held bucket");
    assertHeld(helper, player, new ItemStack(Items.LAVA_BUCKET), "after filling the held bucket");
    helper.succeed();
  }

  /** Fluid cannon: clicking the tank with a bucket empties it into the cannon. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void fluid_cannon_takes_bucket(GameTestHelper helper) {
    FluidCannonBlockEntity cannon = place(helper, POS, TinkerSmeltery.searedFluidCannon.get(), FluidCannonBlockEntity.class);
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
    cannon.interact(player, InteractionHand.MAIN_HAND, true);
    assertFluid(helper, cannon.getTank().getFluid(), new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME), "fluid cannon tank");
    assertHeld(helper, player, new ItemStack(Items.BUCKET), "after emptying into the cannon");
    helper.succeed();
  }

  /** Smeltery menu: clicking a fluid with a held bucket fills it from that fluid only. */
  @TinkerGameTest(template = "gametest/empty_9x9x9", timeoutTicks = 300)
  public static void smeltery_menu_fills_held_bucket(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos controller = SmelteryRigs.buildSearedHeatingStructure(level, helper.absolutePos(new BlockPos(1, 1, 1)));
    helper.startSequence()
      .thenWaitUntil(() -> helper.assertTrue(level.getBlockEntity(controller) instanceof HeatingStructureBlockEntity structure && structure.getStructure() != null,
        Component.literal("smeltery did not form")))
      .thenExecute(() -> {
        HeatingStructureBlockEntity structure = (HeatingStructureBlockEntity) level.getBlockEntity(controller);
        ResourceHandler<FluidResource> smeltery = level.getCapability(Capabilities.Fluid.BLOCK, controller, null);
        helper.assertTrue(smeltery != null, Component.literal("smeltery has no fluid capability"));
        try (Transaction tx = Transaction.openRoot()) {
          helper.assertTrue(smeltery.insert(FluidResource.of(TinkerFluids.moltenGold.get()), FluidValues.INGOT, tx) == FluidValues.INGOT
                            && smeltery.insert(FluidResource.of(Fluids.WATER), FluidType.BUCKET_VOLUME * 2, tx) == FluidType.BUCKET_VOLUME * 2,
            Component.literal("smeltery did not take the gold and water"));
          tx.commit();
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        HeatingStructureContainerMenu menu = new HeatingStructureContainerMenu(0, player.getInventory(), structure);
        // fluid index 1 is the water, gold went in first
        menu.setCarried(new ItemStack(Items.BUCKET));
        helper.assertTrue(menu.clickMenuButton(player, 4 + 1), Component.literal("fluid click was not handled"));
        helper.assertTrue(menu.getCarried().is(Items.WATER_BUCKET), Component.literal("held bucket was not filled with water, holds " + menu.getCarried()));
        FluidStack water = structure.getTank().getFluidInTank(1);
        helper.assertTrue(FluidStack.matches(structure.getTank().getFluidInTank(0), new FluidStack(TinkerFluids.moltenGold.get(), FluidValues.INGOT))
                          && FluidStack.matches(water, new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME)),
          Component.literal("smeltery should keep the gold and one bucket of water, has " + structure.getTank().getFluids()));
      })
      .thenSucceed();
  }
}
