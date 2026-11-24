package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import slimeknights.mantle.recipe.helper.ItemOutput;
import slimeknights.mantle.recipe.ingredient.FluidContainerIngredient;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;
import slimeknights.mantle.transfer.TransferHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.gadgets.capability.PiggybackCapability;
import slimeknights.tconstruct.gadgets.capability.PiggybackHandler;
import slimeknights.tconstruct.library.recipe.FluidValues;
import slimeknights.tconstruct.library.recipe.casting.ItemCastingRecipe;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.AbstractCastingBlock;
import slimeknights.tconstruct.smeltery.block.component.SearedTankBlock;
import slimeknights.tconstruct.smeltery.block.component.SearedTankBlock.TankType;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.component.TankBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.MelterBlockEntity;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tables.block.entity.chest.TinkersChestBlockEntity;
import slimeknights.tconstruct.tables.block.entity.inventory.IChestItemHandler;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Gametests for our capability registrations on the 1.21.10 transfer API (M5 decision D-T3): the registered handlers are native
 * {@link ResourceHandler}s, so beside resolving at runtime they must honour transactions. Simulating (an aborted transaction) must leave
 * tanks, casting tables and melters untouched with no block update or recipe start, a commit applies once, a nested abort only reverts its
 * own changes, and vanilla/NeoForge machinery (hoppers, bucket interaction) works through them.
 */
public class CapabilityGameTests {

  /* Helpers */

  /** Places a seared fuel tank (4 buckets) at the relative position and returns its block entity */
  private static TankBlockEntity placeTank(GameTestHelper helper, BlockPos relative) {
    BlockPos pos = helper.absolutePos(relative);
    helper.getLevel().setBlockAndUpdate(pos, TinkerSmeltery.searedTank.get(TankType.FUEL_TANK).defaultBlockState());
    if (helper.getLevel().getBlockEntity(pos) instanceof TankBlockEntity tank) {
      return tank;
    }
    throw new IllegalStateException("Expected a tank block entity at " + pos);
  }

  /** Places a seared casting table at the relative position and returns its block entity */
  private static CastingBlockEntity placeTable(GameTestHelper helper, BlockPos relative) {
    BlockPos pos = helper.absolutePos(relative);
    helper.getLevel().setBlockAndUpdate(pos, TinkerSmeltery.searedTable.get().defaultBlockState());
    if (helper.getLevel().getBlockEntity(pos) instanceof CastingBlockEntity table) {
      return table;
    }
    throw new IllegalStateException("Expected a casting table block entity at " + pos);
  }

  /** Gets the registered fluid capability at the relative position */
  private static ResourceHandler<FluidResource> fluidCapability(GameTestHelper helper, BlockPos relative) {
    ResourceHandler<FluidResource> handler = TransferHelper.getFluidResourceHandler(helper.getLevel(), helper.absolutePos(relative), Direction.UP);
    if (handler == null) {
      throw new IllegalStateException("No fluid capability at " + helper.absolutePos(relative));
    }
    return handler;
  }

  /** Gets the registered item capability at the relative position */
  private static ResourceHandler<ItemResource> itemCapability(GameTestHelper helper, BlockPos relative) {
    ResourceHandler<ItemResource> handler = TransferHelper.getItemResourceHandler(helper.getLevel(), helper.absolutePos(relative), Direction.UP);
    if (handler == null) {
      throw new IllegalStateException("No item capability at " + helper.absolutePos(relative));
    }
    return handler;
  }

  /** Gets the light level stored in the tank block state */
  private static int tankLight(GameTestHelper helper, BlockPos relative) {
    return helper.getLevel().getBlockState(helper.absolutePos(relative)).getValue(SearedTankBlock.LIGHT);
  }


  /* Registrations */

  /** The vanilla powder snow bucket must expose a fluid handler that drains powdered_snow into an empty bucket. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void powder_snow_bucket_fluid_handler(GameTestHelper helper) {
    ItemStacksResourceHandler slot = new ItemStacksResourceHandler(1);
    slot.set(0, ItemResource.of(Items.POWDER_SNOW_BUCKET), 1);
    ResourceHandler<FluidResource> handler = ItemAccess.forHandlerIndex(slot, 0).getCapability(Capabilities.Fluid.ITEM);
    if (handler == null) {
      helper.fail(Component.literal("powder snow bucket exposes no fluid handler capability"));
      return;
    }
    FluidResource powderedSnow = FluidResource.of(TinkerFluids.powderedSnow.get());
    helper.assertTrue(handler.getResource(0).equals(powderedSnow), Component.literal("powder snow bucket did not expose the powdered_snow fluid"));
    // simulate: drains a bucket worth, but the item stays
    try (Transaction tx = Transaction.openRoot()) {
      helper.assertTrue(handler.extract(0, powderedSnow, FluidType.BUCKET_VOLUME, tx) == FluidType.BUCKET_VOLUME,
        Component.literal("powder snow bucket did not drain a bucket of powdered_snow"));
    }
    helper.assertTrue(slot.getResource(0).is(Items.POWDER_SNOW_BUCKET), Component.literal("simulated drain changed the powder snow bucket"));
    // commit: the item becomes an empty bucket
    try (Transaction tx = Transaction.openRoot()) {
      handler.extract(0, powderedSnow, FluidType.BUCKET_VOLUME, tx);
      tx.commit();
    }
    helper.assertTrue(slot.getResource(0).is(Items.BUCKET), Component.literal("drained powder snow bucket did not become an empty bucket"));
    helper.succeed();
  }

  /** A player must expose the piggyback capability so carried-passenger upkeep runs each tick. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void player_piggyback_capability(GameTestHelper helper) {
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    PiggybackHandler handler = player.getCapability(PiggybackCapability.PIGGYBACK, null);
    if (handler == null) {
      helper.fail(Component.literal("player exposes no piggyback capability"));
      return;
    }
    // the handler must be a single cached instance per entity: entity caps are not memoized by NeoForge, so
    // without the backing attachment a fresh handler would be built each lookup, resetting lastPassengers and
    // re-broadcasting the set-passengers packet every tick while carrying a passenger
    if (player.getCapability(PiggybackCapability.PIGGYBACK, null) != handler) {
      helper.fail(Component.literal("piggyback handler is rebuilt per lookup; carried-passenger sync would spam every tick"));
      return;
    }
    helper.succeed();
  }


  /* Transaction semantics */

  /** Simulating a fill leaves a tank empty and its light untouched; committing applies the fill once and updates the light. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tank_simulate_has_no_side_effects(GameTestHelper helper) {
    BlockPos pos = new BlockPos(2, 1, 2);
    TankBlockEntity tank = placeTank(helper, pos);
    ResourceHandler<FluidResource> handler = fluidCapability(helper, pos);
    FluidResource lava = FluidResource.of(Fluids.LAVA);
    try (Transaction tx = Transaction.openRoot()) {
      helper.assertTrue(handler.insert(lava, FluidType.BUCKET_VOLUME, tx) == FluidType.BUCKET_VOLUME, Component.literal("tank refused a simulated bucket of lava"));
      // inside the transaction the tank sees the fluid, but the block was not updated yet
      helper.assertTrue(handler.getAmountAsLong(0) == FluidType.BUCKET_VOLUME, Component.literal("tank does not see its own uncommitted fill"));
      helper.assertTrue(tankLight(helper, pos) == 0, Component.literal("tank light changed inside the transaction"));
    }
    helper.assertTrue(tank.getTank().isEmpty(), Component.literal("aborted fill left fluid in the tank"));
    helper.assertTrue(tankLight(helper, pos) == 0, Component.literal("aborted fill changed the tank light"));
    try (Transaction tx = Transaction.openRoot()) {
      handler.insert(lava, FluidType.BUCKET_VOLUME, tx);
      tx.commit();
    }
    helper.assertTrue(tank.getTank().getFluidAmount() == FluidType.BUCKET_VOLUME, Component.literal("committed fill was not applied exactly once, tank has " + tank.getTank().getFluidAmount()));
    helper.assertTrue(tankLight(helper, pos) > 0, Component.literal("committed lava fill did not update the tank light"));
    helper.succeed();
  }

  /** A nested transaction that aborts only reverts its own change, the rest of the root transaction commits. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tank_nested_abort(GameTestHelper helper) {
    BlockPos pos = new BlockPos(2, 1, 2);
    TankBlockEntity tank = placeTank(helper, pos);
    ResourceHandler<FluidResource> handler = fluidCapability(helper, pos);
    FluidResource water = FluidResource.of(Fluids.WATER);
    try (Transaction root = Transaction.openRoot()) {
      try (Transaction kept = Transaction.open(root)) {
        handler.insert(water, 250, kept);
        kept.commit();
      }
      try (Transaction aborted = Transaction.open(root)) {
        handler.insert(water, 500, aborted);
      }
      helper.assertTrue(handler.getAmountAsLong(0) == 250, Component.literal("nested abort did not revert its insert, tank has " + handler.getAmountAsLong(0)));
      root.commit();
    }
    helper.assertTrue(tank.getTank().getFluidAmount() == 250, Component.literal("expected 250 mB after the root commit, got " + tank.getTank().getFluidAmount()));
    helper.succeed();
  }

  /**
   * Simulating a fill of a casting table holding a cast must not start a recipe nor touch the table; simulating an item insert into an
   * empty table must not change its block state.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void casting_table_simulate_has_no_side_effects(GameTestHelper helper) {
    BlockPos emptyPos = new BlockPos(1, 1, 2);
    CastingBlockEntity empty = placeTable(helper, emptyPos);
    // simulated item insert: no item, no has_item block state
    ResourceHandler<ItemResource> items = itemCapability(helper, emptyPos);
    try (Transaction tx = Transaction.openRoot()) {
      helper.assertTrue(items.insert(ItemResource.of(TinkerSmeltery.ingotCast.get()), 1, tx) == 1, Component.literal("empty casting table refused a simulated cast"));
    }
    helper.assertTrue(empty.getItem(CastingBlockEntity.INPUT).isEmpty(), Component.literal("aborted insert left the cast in the table"));
    helper.assertTrue(!helper.getLevel().getBlockState(helper.absolutePos(emptyPos)).getValue(AbstractCastingBlock.HAS_ITEM),
      Component.literal("aborted insert changed the casting table block state"));

    // simulated fill: no fluid, no recipe, no cooling
    BlockPos castPos = new BlockPos(3, 1, 2);
    CastingBlockEntity table = placeTable(helper, castPos);
    table.setItem(CastingBlockEntity.INPUT, new ItemStack(TinkerSmeltery.ingotCast.get()));
    ResourceHandler<FluidResource> fluids = fluidCapability(helper, castPos);
    FluidResource iron = FluidResource.of(TinkerFluids.moltenIron.get());
    try (Transaction tx = Transaction.openRoot()) {
      helper.assertTrue(fluids.insert(iron, FluidValues.METAL_BLOCK, tx) == FluidValues.INGOT,
        Component.literal("casting table did not size its tank to an ingot in the simulation"));
    }
    helper.assertTrue(table.getTank().isEmpty() && table.getTank().getCapacity() == 0, Component.literal("aborted fill left fluid or capacity in the casting table"));
    helper.assertTrue(table.getRecipeOutput().isEmpty(), Component.literal("aborted fill started a casting recipe"));
    helper.assertTrue(table.getCoolingTime() < 0, Component.literal("aborted fill started the cooling timer"));
    helper.succeed();
  }

  /** Committing a fill of a casting table starts the recipe for real, and the cast completes. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 300)
  public static void casting_table_commit_starts_recipe(GameTestHelper helper) {
    BlockPos pos = new BlockPos(2, 1, 2);
    CastingBlockEntity table = placeTable(helper, pos);
    table.setItem(CastingBlockEntity.INPUT, new ItemStack(TinkerSmeltery.ingotCast.get()));
    ResourceHandler<FluidResource> fluids = fluidCapability(helper, pos);
    try (Transaction tx = Transaction.openRoot()) {
      fluids.insert(FluidResource.of(TinkerFluids.moltenIron.get()), FluidValues.INGOT, tx);
      tx.commit();
    }
    helper.assertTrue(table.getTank().getFluid().getAmount() == FluidValues.INGOT, Component.literal("committed fill was not applied exactly once"));
    helper.assertTrue(table.getRecipeOutput().is(Items.IRON_INGOT), Component.literal("committed fill did not start the iron ingot recipe"));
    helper.assertTrue(table.getCoolingTime() >= 0, Component.literal("committed fill did not start the cooling timer"));
    helper.succeedWhen(() -> helper.assertTrue(table.getItem(CastingBlockEntity.OUTPUT).is(Items.IRON_INGOT),
      Component.literal("expected the cast iron ingot in the output")));
  }

  /**
   * A casting recipe whose cast is a Mantle {@code fluid_container} ingredient is matched from inside the native fill's transaction
   * (fill → {@code initNewCasting(SIMULATE)} → recipe lookup → cast {@code test}). The ingredient used to look up a legacy bridge there,
   * which throws inside a transaction, so pouring onto such a cast crashed the server. No shipped recipe uses one, so the recipe is
   * built here and primed as the table's last matched recipe, which the lookup tries first.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void casting_table_fluid_container_cast_in_transaction(GameTestHelper helper) {
    BlockPos pos = new BlockPos(2, 1, 2);
    CastingBlockEntity table = placeTable(helper, pos);
    table.setItem(CastingBlockEntity.INPUT, new ItemStack(Items.WATER_BUCKET));
    Ingredient waterBucketCast = FluidContainerIngredient.fromIngredient(FluidIngredient.of(new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME))).toVanilla();
    ResourceLocation id = TConstruct.getResource("gametest/fluid_container_cast");
    ItemCastingRecipe recipe = new ItemCastingRecipe(TinkerSmeltery.tableRecipeSerializer.get(), id, "", waterBucketCast,
                                                     FluidIngredient.of(new FluidStack(TinkerFluids.moltenIron.get(), FluidValues.INGOT)),
                                                     ItemOutput.fromItem(Items.IRON_INGOT), 20, false, false);
    try {
      Field last = CastingBlockEntity.class.getDeclaredField("lastCastingRecipe");
      last.setAccessible(true);
      last.set(table, recipe);
      Field lastName = CastingBlockEntity.class.getDeclaredField("lastCastingRecipeName");
      lastName.setAccessible(true);
      lastName.set(table, ResourceKey.create(Registries.RECIPE, id));
    } catch (ReflectiveOperationException e) {
      helper.fail(Component.literal("could not prime the casting recipe: " + e));
      return;
    }
    ResourceHandler<FluidResource> fluids = fluidCapability(helper, pos);
    FluidResource iron = FluidResource.of(TinkerFluids.moltenIron.get());
    // simulate: the fluid_container cast is tested inside the transaction, sizing the tank to the recipe
    try (Transaction tx = Transaction.openRoot()) {
      helper.assertTrue(fluids.insert(iron, FluidValues.METAL_BLOCK, tx) == FluidValues.INGOT,
        Component.literal("casting table did not match the fluid_container cast inside the transaction"));
    }
    helper.assertTrue(table.getTank().isEmpty() && table.getRecipeOutput().isEmpty(), Component.literal("aborted fill changed the casting table"));
    // commit: the recipe starts, and testing the cast never drained the bucket
    try (Transaction tx = Transaction.openRoot()) {
      fluids.insert(iron, FluidValues.INGOT, tx);
      tx.commit();
    }
    helper.assertTrue(table.getRecipeOutput().is(Items.IRON_INGOT), Component.literal("committed fill did not start the fluid_container cast recipe"));
    helper.assertTrue(table.getItem(CastingBlockEntity.INPUT).is(Items.WATER_BUCKET), Component.literal("testing the fluid_container cast changed the bucket"));
    helper.succeed();
  }

  /** Simulating an item insert into a melter must not reach the melting module; committing does, once. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void melter_simulate_has_no_side_effects(GameTestHelper helper) {
    BlockPos pos = new BlockPos(2, 1, 2);
    ServerLevel level = helper.getLevel();
    level.setBlockAndUpdate(helper.absolutePos(pos), TinkerSmeltery.searedMelter.get().defaultBlockState());
    if (!(level.getBlockEntity(helper.absolutePos(pos)) instanceof MelterBlockEntity melter)) {
      helper.fail(Component.literal("no melter block entity"), pos);
      return;
    }
    ResourceHandler<ItemResource> items = itemCapability(helper, pos);
    ItemResource iron = ItemResource.of(Items.IRON_INGOT);
    try (Transaction tx = Transaction.openRoot()) {
      helper.assertTrue(items.insert(0, iron, 1, tx) == 1, Component.literal("melter refused a simulated iron ingot"));
    }
    helper.assertTrue(melter.getItemHandler().getStackInSlot(0).isEmpty(), Component.literal("aborted insert left the ingot in the melter"));
    helper.assertTrue(melter.getItemHandler().getRequiredTime(0) == 0, Component.literal("aborted insert looked up a melting recipe"));
    try (Transaction tx = Transaction.openRoot()) {
      helper.assertTrue(items.insert(0, iron, 2, tx) == 1, Component.literal("melter slot accepted more than one item"));
      tx.commit();
    }
    helper.assertTrue(melter.getItemHandler().getStackInSlot(0).getCount() == 1, Component.literal("committed insert did not put one ingot in the melter"));
    helper.assertTrue(melter.getItemHandler().getRequiredTime(0) > 0, Component.literal("committed insert did not look up the melting recipe"));
    helper.succeed();
  }


  /* Interop */

  /** A vanilla hopper pushes a cast into a casting table, which updates its block state. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 200)
  public static void hopper_inserts_into_casting_table(GameTestHelper helper) {
    BlockPos tablePos = new BlockPos(2, 1, 2);
    CastingBlockEntity table = placeTable(helper, tablePos);
    BlockPos hopperPos = helper.absolutePos(tablePos.above());
    helper.getLevel().setBlockAndUpdate(hopperPos, Blocks.HOPPER.defaultBlockState());
    if (helper.getLevel().getBlockEntity(hopperPos) instanceof HopperBlockEntity hopper) {
      hopper.setItem(0, new ItemStack(TinkerSmeltery.ingotCast.get()));
    } else {
      helper.fail(Component.literal("no hopper block entity"), tablePos.above());
      return;
    }
    helper.succeedWhen(() -> {
      helper.assertTrue(table.getItem(CastingBlockEntity.INPUT).is(TinkerSmeltery.ingotCast.get()), Component.literal("hopper did not insert the cast"));
      helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(tablePos)).getValue(AbstractCastingBlock.HAS_ITEM),
        Component.literal("casting table block state did not update after the hopper insert"));
    });
  }

  /** NeoForge's bucket interaction fills a tank through its native capability and empties the player's bucket. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void bucket_interaction_fills_tank(GameTestHelper helper) {
    BlockPos pos = new BlockPos(2, 1, 2);
    TankBlockEntity tank = placeTank(helper, pos);
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LAVA_BUCKET));
    helper.assertTrue(FluidUtil.interactWithFluidHandler(player, InteractionHand.MAIN_HAND, helper.getLevel(), helper.absolutePos(pos), Direction.UP),
      Component.literal("bucket interaction with the tank failed"));
    helper.assertTrue(tank.getTank().getFluid().is(Fluids.LAVA) && tank.getTank().getFluidAmount() == FluidType.BUCKET_VOLUME,
      Component.literal("tank did not receive the bucket of lava, has " + tank.getTank().getFluid()));
    helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).is(Items.BUCKET), Component.literal("player's bucket was not emptied"));
    helper.succeed();
  }

  /** Breaking a Tinkers chest drops its items and empties it, rather than dropping copies through the bridged capability. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void breaking_chest_empties_it(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
    ServerLevel level = helper.getLevel();
    level.setBlockAndUpdate(pos, TinkerTables.tinkersChest.get().defaultBlockState());
    if (!(level.getBlockEntity(pos) instanceof TinkersChestBlockEntity chest)) {
      helper.fail(Component.literal("no tinkers chest block entity"), new BlockPos(2, 1, 2));
      return;
    }
    IChestItemHandler inventory = chest.getItemHandler();
    inventory.setStackInSlot(0, new ItemStack(Items.DIAMOND, 5));
    level.destroyBlock(pos, true);
    helper.assertTrue(inventory.getStackInSlot(0).isEmpty(), Component.literal("broken chest still holds its items"));
    List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2), entity -> entity.getItem().is(Items.DIAMOND));
    helper.assertTrue(drops.stream().mapToInt(entity -> entity.getItem().getCount()).sum() == 5, Component.literal("broken chest did not drop its 5 diamonds"));
    helper.succeed();
  }
}
