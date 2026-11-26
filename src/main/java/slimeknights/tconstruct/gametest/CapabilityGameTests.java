package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import slimeknights.mantle.recipe.helper.ItemOutput;
import slimeknights.mantle.recipe.ingredient.FluidContainerIngredient;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;
import slimeknights.mantle.transfer.TransferHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.gadgets.capability.PiggybackCapability;
import slimeknights.tconstruct.gadgets.capability.PiggybackHandler;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.interaction.InteractionSource;
import slimeknights.tconstruct.library.recipe.FluidValues;
import slimeknights.tconstruct.library.recipe.casting.ItemCastingRecipe;
import slimeknights.tconstruct.library.tools.capability.fluid.ToolTankHelper;
import slimeknights.tconstruct.library.tools.capability.inventory.ToolInventoryCapability;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.AbstractCastingBlock;
import slimeknights.tconstruct.smeltery.block.component.SearedTankBlock;
import slimeknights.tconstruct.smeltery.block.component.SearedTankBlock.TankType;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.component.DuctBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.component.TankBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.HeatingStructureBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.MelterBlockEntity;
import slimeknights.tconstruct.smeltery.item.CopperCanItem;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tables.block.entity.chest.TinkersChestBlockEntity;
import slimeknights.tconstruct.tables.block.entity.inventory.ChestItemHandler;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.data.ModifierIds;
import slimeknights.tconstruct.tools.data.material.MaterialIds;
import slimeknights.tconstruct.tools.menu.ToolContainerMenu;
import slimeknights.tconstruct.tools.modules.interaction.TankInteractionModule;

import org.jspecify.annotations.Nullable;
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


  /* Tool capabilities (M5 decision D-C1: never registered since the 1.21.1 port) */

  /** Builds a rock/wood/wood pickaxe with the given modifiers */
  private static ItemStack pickaxe(ModifierId... modifiers) {
    ToolStack tool = ToolStack.createTool(TinkerTools.pickaxe.get(), TinkerTools.pickaxe.get().getToolDefinition(),
      MaterialNBT.of(MaterialVariant.of(MaterialIds.rock, ""), MaterialVariant.of(MaterialIds.wood, ""), MaterialVariant.of(MaterialIds.wood, "")));
    for (ModifierId modifier : modifiers) {
      tool.addModifier(modifier, 1);
    }
    return tool.createStack();
  }

  /** Puts the stack in a one slot handler, standing in for a pipe or machine inventory */
  private static ItemStacksResourceHandler slotWith(ItemStack stack) {
    ItemStacksResourceHandler slot = new ItemStacksResourceHandler(1);
    slot.set(0, ItemResource.of(stack), stack.getCount());
    return slot;
  }

  /** A tool without a tank, inventory or energy modifier exposes none of the transfer capabilities. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tool_without_modifiers_has_no_capabilities(GameTestHelper helper) {
    ItemAccess access = ItemAccess.forHandlerIndex(slotWith(pickaxe()), 0);
    helper.assertTrue(access.getCapability(Capabilities.Fluid.ITEM) == null, Component.literal("plain pickaxe exposes a fluid handler"));
    helper.assertTrue(access.getCapability(Capabilities.Item.ITEM) == null, Component.literal("plain pickaxe exposes an item handler"));
    helper.assertTrue(access.getCapability(Capabilities.Energy.ITEM) == null, Component.literal("plain pickaxe exposes an energy handler"));
    helper.succeed();
  }

  /** A tool with the tank modifier exposes a fluid handler that pipes can fill through the access: a simulation leaves it empty, a commit fills it. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tool_tank_fluid_capability(GameTestHelper helper) {
    ItemStacksResourceHandler slot = slotWith(pickaxe(ModifierIds.tank));
    ResourceHandler<FluidResource> handler = ItemAccess.forHandlerIndex(slot, 0).getCapability(Capabilities.Fluid.ITEM);
    if (handler == null) {
      helper.fail(Component.literal("pickaxe with a tank exposes no fluid handler"));
      return;
    }
    helper.assertTrue(handler.size() > 0, Component.literal("tool fluid handler has no tanks"));
    FluidResource water = FluidResource.of(Fluids.WATER);
    try (Transaction tx = Transaction.openRoot()) {
      helper.assertTrue(handler.insert(0, water, FluidType.BUCKET_VOLUME, tx) == FluidType.BUCKET_VOLUME, Component.literal("tool tank did not accept a bucket of water"));
    }
    helper.assertTrue(handler.getAmountAsLong(0) == 0, Component.literal("simulated fill changed the tool tank"));
    try (Transaction tx = Transaction.openRoot()) {
      handler.insert(0, water, FluidType.BUCKET_VOLUME, tx);
      tx.commit();
    }
    helper.assertTrue(handler.getResource(0).equals(water) && handler.getAmountAsLong(0) == FluidType.BUCKET_VOLUME,
      Component.literal("committed fill did not store a bucket of water, found " + handler.getAmountAsLong(0) + " of " + handler.getResource(0)));
    // the slot holds the updated tool, readable through the tool's own tank
    ItemStack filled = slot.getResource(0).toStack();
    ResourceHandler<FluidResource> again = ItemAccess.forStack(filled).getCapability(Capabilities.Fluid.ITEM);
    helper.assertTrue(again != null && again.getAmountAsLong(0) == FluidType.BUCKET_VOLUME, Component.literal("filled tool stack does not hold the water"));
    helper.succeed();
  }

  /**
   * A tool with an inventory modifier exposes an item handler with its slots, accepts items through it, and its inventory opens, both through
   * {@link ToolInventoryCapability#tryOpenContainer} and when used, on a menu showing the real slots. Uses a mock (non network) player:
   * {@code makeMockServerPlayerInLevel} has no negotiated payload channels, so the datapack sync on join and the menu open packet are refused;
   * the client round trip is covered by the {@code tconstruct:tool_inventory} uitest.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tool_inventory_item_capability(GameTestHelper helper) {
    ItemStacksResourceHandler slot = slotWith(pickaxe(ModifierIds.pocket));
    ResourceHandler<ItemResource> handler = ItemAccess.forHandlerIndex(slot, 0).getCapability(Capabilities.Item.ITEM);
    if (handler == null) {
      helper.fail(Component.literal("pickaxe with pockets exposes no item handler"));
      return;
    }
    helper.assertTrue(handler.size() == 3, Component.literal("expected 3 pocket slots, got " + handler.size()));
    try (Transaction tx = Transaction.openRoot()) {
      helper.assertTrue(handler.insert(0, ItemResource.of(Items.COBBLESTONE), 16, tx) == 16, Component.literal("tool inventory did not accept cobblestone"));
      tx.commit();
    }
    helper.assertTrue(handler.getResource(0).is(Items.COBBLESTONE) && handler.getAmountAsLong(0) == 16, Component.literal("tool inventory does not hold the inserted cobblestone"));
    ItemStack tool = slot.getResource(0).toStack();

    // the menu sees the real slots
    helper.assertTrue(ToolInventoryCapability.getMenuHandler(tool).size() == 3, Component.literal("menu handler does not see the pocket slots"));
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    player.setItemInHand(InteractionHand.MAIN_HAND, tool);
    InteractionResult opened = ToolInventoryCapability.tryOpenContainer(tool, ToolStack.from(tool), player, EquipmentSlot.MAINHAND);
    helper.assertTrue(opened.consumesAction(), Component.literal("tryOpenContainer did not open the tool inventory: " + opened));
    // using the tool opens it too (pocket opens on any key)
    InteractionResult used = tool.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
    helper.assertTrue(used.consumesAction(), Component.literal("using the tool did not open its inventory: " + used));
    // the menu the server opens, see tryOpenContainer
    ToolContainerMenu menu = new ToolContainerMenu(0, player.getInventory(), tool, ToolInventoryCapability.getMenuHandler(tool), player.getInventory().getSelectedSlot());
    helper.assertTrue(menu.getItemHandler().size() == 3 && menu.getItemHandler().getStack(0).is(Items.COBBLESTONE),
      Component.literal("tool container menu does not show the tool inventory"));
    helper.succeed();
  }


  /**
   * Draining a tool tank through its native fluid handler: a simulation or abort leaves the tool full, a commit takes only the requested
   * amount of the matching fluid, another fluid drains nothing.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tool_tank_fluid_capability_extract(GameTestHelper helper) {
    ItemStacksResourceHandler slot = slotWith(pickaxe(ModifierIds.tank));
    ResourceHandler<FluidResource> handler = ItemAccess.forHandlerIndex(slot, 0).getCapability(Capabilities.Fluid.ITEM);
    if (handler == null) {
      helper.fail(Component.literal("pickaxe with a tank exposes no fluid handler"));
      return;
    }
    FluidResource water = FluidResource.of(Fluids.WATER);
    try (Transaction tx = Transaction.openRoot()) {
      handler.insert(0, water, FluidType.BUCKET_VOLUME, tx);
      tx.commit();
    }
    try (Transaction tx = Transaction.openRoot()) {
      helper.assertTrue(handler.extract(0, FluidResource.of(Fluids.LAVA), 100, tx) == 0, Component.literal("tool tank drained lava it does not hold"));
      helper.assertTrue(handler.extract(0, water, 300, tx) == 300, Component.literal("tool tank did not drain 300 mB of water"));
    }
    helper.assertTrue(handler.getAmountAsLong(0) == FluidType.BUCKET_VOLUME, Component.literal("aborted drain changed the tool tank: " + handler.getAmountAsLong(0)));
    try (Transaction tx = Transaction.openRoot()) {
      helper.assertTrue(handler.extract(0, water, 300, tx) == 300, Component.literal("tool tank did not drain 300 mB of water"));
      tx.commit();
    }
    helper.assertTrue(handler.getAmountAsLong(0) == 700, Component.literal("committed drain left " + handler.getAmountAsLong(0)));
    ToolStack tool = ToolStack.from(slot.getResource(0).toStack());
    helper.assertTrue(FluidStack.matches(ToolTankHelper.TANK_HELPER.getFluid(tool), new FluidStack(Fluids.WATER, 700)),
      Component.literal("tool data holds " + ToolTankHelper.TANK_HELPER.getFluid(tool)));
    helper.succeed();
  }

  /**
   * Taking from and putting into a tool inventory through its native item handler: aborts change nothing, extracts take exactly the stored
   * item, items with their own item handler are refused (no nesting), and a stack only merges with the exact same item and components.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tool_inventory_item_capability_rules(GameTestHelper helper) {
    ItemStacksResourceHandler slot = slotWith(pickaxe(ModifierIds.pocket));
    ResourceHandler<ItemResource> handler = ItemAccess.forHandlerIndex(slot, 0).getCapability(Capabilities.Item.ITEM);
    if (handler == null) {
      helper.fail(Component.literal("pickaxe with pockets exposes no item handler"));
      return;
    }
    ItemResource cobblestone = ItemResource.of(Items.COBBLESTONE);
    try (Transaction tx = Transaction.openRoot()) {
      handler.insert(0, cobblestone, 20, tx);
      tx.commit();
    }
    // aborted extract: nothing changes
    try (Transaction tx = Transaction.openRoot()) {
      helper.assertTrue(handler.extract(0, cobblestone, 5, tx) == 5, Component.literal("could not extract 5 cobblestone"));
    }
    helper.assertTrue(handler.getAmountAsLong(0) == 20, Component.literal("aborted extract changed the slot: " + handler.getAmountAsLong(0)));
    // wrong item, then a committed extract
    try (Transaction tx = Transaction.openRoot()) {
      helper.assertTrue(handler.extract(0, ItemResource.of(Items.DIRT), 5, tx) == 0, Component.literal("extracted dirt from a cobblestone slot"));
      helper.assertTrue(handler.extract(0, cobblestone, 64, tx) == 20, Component.literal("did not extract all 20 cobblestone"));
      tx.commit();
    }
    helper.assertTrue(handler.getResource(0).isEmpty(), Component.literal("slot not empty after extracting everything: " + handler.getResource(0)));

    // no nesting: another tool with an inventory is refused
    try (Transaction tx = Transaction.openRoot()) {
      helper.assertTrue(handler.insert(1, ItemResource.of(pickaxe(ModifierIds.pocket)), 1, tx) == 0, Component.literal("tool inventory took a tool with an inventory"));
    }
    // same item but other components does not merge
    ItemStack named = new ItemStack(Items.COBBLESTONE, 4);
    named.set(DataComponents.CUSTOM_NAME, Component.literal("named"));
    try (Transaction tx = Transaction.openRoot()) {
      helper.assertTrue(handler.insert(2, cobblestone, 4, tx) == 4, Component.literal("could not insert cobblestone"));
      helper.assertTrue(handler.insert(2, ItemResource.of(named), 4, tx) == 0, Component.literal("named cobblestone merged into plain cobblestone"));
      helper.assertTrue(handler.insert(2, cobblestone, 4, tx) == 4, Component.literal("could not add more cobblestone"));
      tx.commit();
    }
    helper.assertTrue(handler.getResource(2).equals(cobblestone) && handler.getAmountAsLong(2) == 8, Component.literal("slot 2 holds " + handler.getAmountAsLong(2) + " " + handler.getResource(2)));
    helper.succeed();
  }

  /**
   * Tank modifier slot interactions (right click in an inventory): a water bucket clicked onto a tool in a slot empties into the tool's tank,
   * and the tool held on a slot with a water bucket drains it, leaving the empty bucket in the slot.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tool_tank_slot_interactions(GameTestHelper helper) {
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    InventoryMenu menu = player.inventoryMenu;
    // inventory index 9 is menu slot 9
    player.getInventory().setItem(9, pickaxe(ModifierIds.tank));
    menu.setCarried(new ItemStack(Items.WATER_BUCKET));
    menu.clicked(9, 1, ClickType.PICKUP, player);
    helper.assertTrue(menu.getCarried().is(Items.BUCKET), Component.literal("carried bucket was not emptied: " + menu.getCarried()));
    FluidStack inTool = ToolTankHelper.TANK_HELPER.getFluid(ToolStack.from(player.getInventory().getItem(9)));
    helper.assertTrue(FluidStack.matches(inTool, new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME)), Component.literal("tool in the slot holds " + inTool));

    // empty tool held over a water bucket in a slot
    player.getInventory().setItem(10, new ItemStack(Items.WATER_BUCKET));
    menu.setCarried(pickaxe(ModifierIds.tank));
    menu.clicked(10, 1, ClickType.PICKUP, player);
    helper.assertTrue(player.getInventory().getItem(10).is(Items.BUCKET), Component.literal("slot bucket was not emptied: " + player.getInventory().getItem(10)));
    FluidStack held = ToolTankHelper.TANK_HELPER.getFluid(ToolStack.from(menu.getCarried()));
    helper.assertTrue(FluidStack.matches(held, new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME)), Component.literal("held tool holds " + held));
    helper.succeed();
  }

  /**
   * Tank interaction module on a block with a fluid handler: using the tool drains the block into the empty tool tank (the first fluid it
   * gives, up to the tank capacity), tops up a tank holding the same fluid, and sneaking empties the tool back into the block.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tank_interaction_module_fills_and_drains(GameTestHelper helper) {
    BlockPos relative = new BlockPos(2, 1, 2);
    TankBlockEntity tank = placeTank(helper, relative);
    BlockPos pos = helper.absolutePos(relative);
    SmelteryRigs.fillTank(helper.getLevel(), pos, new FluidStack(Fluids.LAVA, 1500));
    ItemStack stack = pickaxe(ModifierIds.tank);
    ToolStack tool = ToolStack.from(stack);
    ModifierEntry entry = tool.getModifiers().getEntry(ModifierIds.tank);
    TankInteractionModule module = new TankInteractionModule(null);
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    int capacity = ToolTankHelper.TANK_HELPER.getCapacity(tool);

    // empty tool: drains up to its capacity
    helper.assertTrue(module.beforeBlockUse(tool, entry, context, InteractionSource.RIGHT_CLICK).consumesAction(), Component.literal("tank interaction was not handled"));
    helper.assertTrue(FluidStack.matches(ToolTankHelper.TANK_HELPER.getFluid(tool), new FluidStack(Fluids.LAVA, capacity)),
      Component.literal("tool holds " + ToolTankHelper.TANK_HELPER.getFluid(tool) + " after draining, capacity " + capacity));
    helper.assertTrue(tank.getTank().getFluidAmount() == 1500 - capacity, Component.literal("tank holds " + tank.getTank().getFluidAmount()));

    // sneaking: empties the tool into the tank
    player.setShiftKeyDown(true);
    module.beforeBlockUse(tool, entry, context, InteractionSource.RIGHT_CLICK);
    helper.assertTrue(ToolTankHelper.TANK_HELPER.getFluid(tool).isEmpty(), Component.literal("tool still holds " + ToolTankHelper.TANK_HELPER.getFluid(tool)));
    helper.assertTrue(tank.getTank().getFluidAmount() == 1500, Component.literal("tank holds " + tank.getTank().getFluidAmount() + " after filling"));

    // partially filled with the same fluid: tops up to the capacity
    ToolTankHelper.TANK_HELPER.setFluid(tool, new FluidStack(Fluids.LAVA, 100));
    player.setShiftKeyDown(false);
    module.beforeBlockUse(tool, entry, context, InteractionSource.RIGHT_CLICK);
    helper.assertTrue(ToolTankHelper.TANK_HELPER.getFluid(tool).getAmount() == capacity, Component.literal("tool holds " + ToolTankHelper.TANK_HELPER.getFluid(tool) + " after topping up"));
    helper.assertTrue(tank.getTank().getFluidAmount() == 1500 - (capacity - 100), Component.literal("tank holds " + tank.getTank().getFluidAmount() + " after topping up"));
    helper.succeed();
  }


  /* Fluid container items */

  /**
   * A stack of copper cans exposes one fluid handler for the whole stack: amounts are totals, each can holds exactly one ingot, so cans are
   * filled and drained all together and in whole ingots (an amount that would leave a can partially filled moves nothing).
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void copper_can_item_fluid_capability(GameTestHelper helper) {
    ItemStacksResourceHandler slot = slotWith(new ItemStack(TinkerSmeltery.copperCan, 3));
    ResourceHandler<FluidResource> handler = ItemAccess.forHandlerIndex(slot, 0).getCapability(Capabilities.Fluid.ITEM);
    if (handler == null) {
      helper.fail(Component.literal("copper can exposes no fluid handler"));
      return;
    }
    FluidResource copper = FluidResource.of(TinkerFluids.moltenCopper.get());
    int stackAmount = FluidValues.INGOT * 3;
    helper.assertTrue(handler.size() == 1 && handler.getCapacityAsLong(0, copper) == stackAmount,
      Component.literal("expected one tank of 3 ingots for 3 cans, got " + handler.size() + " tanks of " + handler.getCapacityAsLong(0, copper)));
    // one ingot would be a third of an ingot per can
    try (Transaction tx = Transaction.openRoot()) {
      helper.assertTrue(handler.insert(0, copper, FluidValues.INGOT, tx) == 0, Component.literal("cans accepted an ingot split over 3 cans"));
    }
    // simulate: the stack stays empty
    try (Transaction tx = Transaction.openRoot()) {
      helper.assertTrue(handler.insert(0, copper, stackAmount, tx) == stackAmount, Component.literal("cans refused a simulated ingot each"));
    }
    helper.assertTrue(CopperCanItem.getFluidStack(slot.getResource(0).toStack()).isEmpty(), Component.literal("simulated fill changed the cans"));
    // commit: more than fits fills each can with one ingot
    try (Transaction tx = Transaction.openRoot()) {
      helper.assertTrue(handler.insert(0, copper, FluidValues.INGOT * 4, tx) == stackAmount, Component.literal("cans did not take exactly one ingot each"));
      tx.commit();
    }
    ItemStack filled = slot.getResource(0).toStack(slot.getAmountAsInt(0));
    FluidStack perCan = CopperCanItem.getFluidStack(filled);
    helper.assertTrue(filled.getCount() == 3 && perCan.is(TinkerFluids.moltenCopper.get()) && perCan.getAmount() == FluidValues.INGOT,
      Component.literal("expected 3 cans holding an ingot of copper each, got " + filled.getCount() + " holding " + perCan.getAmount() + " of " + perCan.getFluid()));
    helper.assertTrue(handler.getResource(0).equals(copper) && handler.getAmountAsLong(0) == stackAmount, Component.literal("handler does not report the 3 ingots"));
    try (Transaction tx = Transaction.openRoot()) {
      helper.assertTrue(handler.insert(0, FluidResource.of(Fluids.LAVA), stackAmount, tx) == 0, Component.literal("filled cans accepted another fluid"));
      helper.assertTrue(handler.extract(0, copper, FluidValues.INGOT, tx) == 0, Component.literal("cans gave out an ingot split over 3 cans"));
      // simulated drain leaves the cans filled
      helper.assertTrue(handler.extract(0, copper, stackAmount, tx) == stackAmount, Component.literal("cans did not drain an ingot each"));
    }
    helper.assertTrue(CopperCanItem.getFluidStack(slot.getResource(0).toStack()).getAmount() == FluidValues.INGOT, Component.literal("simulated drain changed the cans"));
    // commit: back to plain cans, which stack with new ones
    try (Transaction tx = Transaction.openRoot()) {
      helper.assertTrue(handler.extract(0, copper, stackAmount, tx) == stackAmount, Component.literal("cans did not drain an ingot each"));
      tx.commit();
    }
    helper.assertTrue(slot.getAmountAsInt(0) == 3 && ItemStack.isSameItemSameComponents(slot.getResource(0).toStack(), new ItemStack(TinkerSmeltery.copperCan)),
      Component.literal("drained cans are not 3 plain cans: " + slot.getResource(0) + " x" + slot.getAmountAsInt(0)));
    helper.succeed();
  }

  /**
   * A stack of tank items exposes one fluid handler for the whole stack: amounts and capacity are totals, each item stores its own tank (not
   * the stack total), partial amounts split evenly, and stacks over 16 cannot be filled.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tank_item_fluid_capability(GameTestHelper helper) {
    Item tankItem = TinkerSmeltery.searedTank.get(TankType.FUEL_TANK).asItem();
    int capacity = TankBlockEntity.getCapacity(tankItem);
    ItemStacksResourceHandler slot = slotWith(new ItemStack(tankItem, 2));
    ResourceHandler<FluidResource> handler = ItemAccess.forHandlerIndex(slot, 0).getCapability(Capabilities.Fluid.ITEM);
    if (handler == null) {
      helper.fail(Component.literal("tank item exposes no fluid handler"));
      return;
    }
    FluidResource lava = FluidResource.of(Fluids.LAVA);
    helper.assertTrue(handler.getCapacityAsLong(0, lava) == capacity * 2L, Component.literal("expected the capacity of 2 tanks, got " + handler.getCapacityAsLong(0, lava)));
    try (Transaction tx = Transaction.openRoot()) {
      helper.assertTrue(handler.insert(0, lava, capacity, tx) == capacity, Component.literal("tanks refused a simulated fill"));
    }
    helper.assertTrue(!slot.getResource(0).has(TinkerSmeltery.TANK_FLUID.get()), Component.literal("simulated fill changed the tank items"));
    // more than fits: each tank fills up
    try (Transaction tx = Transaction.openRoot()) {
      helper.assertTrue(handler.insert(0, lava, capacity * 2 + 500, tx) == capacity * 2, Component.literal("tanks did not fill up exactly"));
      tx.commit();
    }
    SimpleFluidContent perItem = slot.getResource(0).getOrDefault(TinkerSmeltery.TANK_FLUID.get(), SimpleFluidContent.EMPTY);
    helper.assertTrue(slot.getAmountAsInt(0) == 2 && perItem.getAmount() == capacity, Component.literal("each tank item should hold " + capacity + ", holds " + perItem.getAmount()));
    // a partial drain takes the same amount from each tank
    try (Transaction tx = Transaction.openRoot()) {
      helper.assertTrue(handler.extract(0, lava, 1000, tx) == 1000, Component.literal("tanks did not drain 1000 mB"));
      tx.commit();
    }
    perItem = slot.getResource(0).getOrDefault(TinkerSmeltery.TANK_FLUID.get(), SimpleFluidContent.EMPTY);
    helper.assertTrue(perItem.getAmount() == capacity - 500 && handler.getAmountAsLong(0) == (capacity - 500) * 2L,
      Component.literal("expected " + (capacity - 500) + " per tank after the drain, got " + perItem.getAmount()));
    try (Transaction tx = Transaction.openRoot()) {
      helper.assertTrue(handler.extract(0, lava, capacity * 2, tx) == (capacity - 500) * 2, Component.literal("tanks did not drain the rest"));
      tx.commit();
    }
    helper.assertTrue(!slot.getResource(0).has(TinkerSmeltery.TANK_FLUID.get()), Component.literal("drained tank items kept the fluid component"));

    // size limited tanks: a stack of 17 cannot be filled
    ItemStacksResourceHandler large = slotWith(new ItemStack(tankItem, 17));
    ResourceHandler<FluidResource> largeHandler = ItemAccess.forHandlerIndex(large, 0).getCapability(Capabilities.Fluid.ITEM);
    try (Transaction tx = Transaction.openRoot()) {
      helper.assertTrue(largeHandler != null && largeHandler.insert(0, lava, capacity * 17, tx) == 0, Component.literal("a stack of 17 tanks was filled"));
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
    Identifier id = TConstruct.getResource("gametest/fluid_container_cast");
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

  /** Places a hopper facing the given direction at the relative position holding the given stacks in its first slots */
  private static HopperBlockEntity placeHopper(GameTestHelper helper, BlockPos relative, Direction facing, ItemStack... stacks) {
    BlockPos pos = helper.absolutePos(relative);
    helper.getLevel().setBlockAndUpdate(pos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, facing));
    if (!(helper.getLevel().getBlockEntity(pos) instanceof HopperBlockEntity hopper)) {
      throw new IllegalStateException("no hopper block entity at " + pos);
    }
    for (int i = 0; i < stacks.length; i++) {
      hopper.setItem(i, stacks[i]);
    }
    return hopper;
  }

  /** Gets the fluid capability at the relative position on the given side, like a pipe connected to that side */
  private static ResourceHandler<FluidResource> fluidCapability(GameTestHelper helper, BlockPos relative, Direction side) {
    ResourceHandler<FluidResource> handler = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(relative), side);
    if (handler == null) {
      throw new IllegalStateException("No fluid capability at " + helper.absolutePos(relative) + " on " + side);
    }
    return handler;
  }

  /** Moves fluid between two handlers like a pipe: the first fluid the source gives that the target takes, committed; null if nothing moved */
  @Nullable
  private static ResourceStack<FluidResource> pipe(ResourceHandler<FluidResource> from, ResourceHandler<FluidResource> to, int amount) {
    try (Transaction tx = Transaction.openRoot()) {
      ResourceStack<FluidResource> moved = ResourceHandlerUtil.moveFirst(from, to, resource -> true, amount, tx);
      tx.commit();
      return moved;
    }
  }

  /**
   * Tanks hold no items: a hopper above keeps its stack and one below gets nothing, as they see no item capability. Their fluid capability,
   * seen from any side, lets a pipe move fluid out of one tank into another, once.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tank_hopper_and_pipe_interop(GameTestHelper helper) {
    BlockPos sourcePos = new BlockPos(1, 2, 2);
    BlockPos targetPos = new BlockPos(3, 2, 2);
    TankBlockEntity source = placeTank(helper, sourcePos);
    TankBlockEntity target = placeTank(helper, targetPos);
    SmelteryRigs.fillTank(helper.getLevel(), helper.absolutePos(sourcePos), new FluidStack(Fluids.LAVA, 2000));
    HopperBlockEntity above = placeHopper(helper, sourcePos.above(), Direction.DOWN, new ItemStack(Items.LAVA_BUCKET));
    HopperBlockEntity below = placeHopper(helper, sourcePos.below(), Direction.DOWN);
    for (Direction side : Direction.values()) {
      helper.assertTrue(helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(sourcePos), side) == null,
        Component.literal("tank exposes an item capability on " + side));
    }

    ResourceStack<FluidResource> moved = pipe(fluidCapability(helper, sourcePos, Direction.EAST), fluidCapability(helper, targetPos, Direction.WEST), 1000);
    helper.assertTrue(moved != null && moved.resource().equals(FluidResource.of(Fluids.LAVA)) && moved.amount() == 1000, Component.literal("pipe did not move 1000 mB of lava, moved " + moved));
    helper.assertTrue(source.getTank().getFluidAmount() == 1000 && target.getTank().getFluid().is(Fluids.LAVA) && target.getTank().getFluidAmount() == 1000,
      Component.literal("expected 1000 mB in each tank, got " + source.getTank().getFluidAmount() + " and " + target.getTank().getFluid().getAmount()));
    helper.assertTrue(tankLight(helper, targetPos) > 0, Component.literal("pipe fill did not update the target tank light"));

    // a few hopper cooldowns later nothing moved
    helper.runAfterDelay(30, () -> {
      helper.assertTrue(above.getItem(0).is(Items.LAVA_BUCKET) && above.getItem(0).getCount() == 1, Component.literal("hopper above the tank lost its bucket"));
      helper.assertTrue(below.isEmpty(), Component.literal("hopper below the tank pulled something out of it"));
      helper.assertTrue(source.getTank().getFluidAmount() == 1000 && target.getTank().getFluidAmount() == 1000, Component.literal("tank contents changed without a pipe"));
      helper.succeed();
    });
  }

  /**
   * A duct's filter slot takes items through its item capability: a hopper above skips an empty bucket and pushes in a filled copper can (one
   * item fits), and a hopper below pulls the can back out.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 200)
  public static void duct_hopper_moves_filter(GameTestHelper helper) {
    BlockPos ductPos = new BlockPos(2, 2, 2);
    helper.getLevel().setBlockAndUpdate(helper.absolutePos(ductPos), TinkerSmeltery.searedDuct.get().defaultBlockState());
    if (!(helper.getLevel().getBlockEntity(helper.absolutePos(ductPos)) instanceof DuctBlockEntity duct)) {
      helper.fail(Component.literal("no duct block entity"), ductPos);
      return;
    }
    ItemStack can = CopperCanItem.setFluid(new ItemStack(TinkerSmeltery.copperCan, 2), new FluidStack(TinkerFluids.moltenCopper.get(), FluidValues.INGOT));
    HopperBlockEntity above = placeHopper(helper, ductPos.above(), Direction.DOWN, new ItemStack(Items.BUCKET), can.copy());
    helper.startSequence()
      .thenWaitUntil(() -> {
        helper.assertTrue(ItemStack.isSameItemSameComponents(duct.getItemHandler().getStack(), can) && duct.getItemHandler().getStack().getCount() == 1,
          Component.literal("hopper did not push one filled can into the duct, duct holds " + duct.getItemHandler().getStack()));
        helper.assertTrue(duct.getItemHandler().getFluid().is(TinkerFluids.moltenCopper.get()), Component.literal("duct filter is not molten copper"));
      })
      .thenIdle(20)
      .thenExecute(() -> {
        helper.assertTrue(above.getItem(0).is(Items.BUCKET), Component.literal("hopper pushed the empty bucket into the duct"));
        helper.assertTrue(above.getItem(1).getCount() == 1 && duct.getItemHandler().getStack().getCount() == 1, Component.literal("duct took more than one can"));
        // remove the source, then pull the can out from below
        helper.getLevel().setBlockAndUpdate(helper.absolutePos(ductPos.above()), Blocks.AIR.defaultBlockState());
        placeHopper(helper, ductPos.below(), Direction.DOWN);
      })
      .thenWaitUntil(() -> {
        helper.assertTrue(duct.getItemHandler().getStack().isEmpty(), Component.literal("hopper below did not pull the can out of the duct"));
        if (helper.getLevel().getBlockEntity(helper.absolutePos(ductPos.below())) instanceof HopperBlockEntity below) {
          helper.assertTrue(ItemStack.isSameItemSameComponents(below.getItem(0), can) && below.getItem(0).getCount() == 1,
            Component.literal("hopper below does not hold the can, holds " + below.getItem(0)));
        }
        helper.assertTrue(duct.getItemHandler().getFluid().isEmpty(), Component.literal("duct kept the filter fluid after the can left"));
      })
      .thenSucceed();
  }

  /**
   * Through a formed smeltery: a drain exposes the whole smeltery tank to a pipe, a duct only the fluid of its filter (inserts too), a hopper
   * cannot push items into a drain, and a hopper above the chute feeds the melting inventory.
   */
  @TinkerGameTest(template = "gametest/empty_9x9x9", timeoutTicks = 300)
  public static void smeltery_drain_and_duct_pipe_interop(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    // structure at (1..3, 1..3, 1..3): drain (2,2,1) with the chute above, duct (3,2,2), controller (2,2,3)
    BlockPos controller = SmelteryRigs.buildSearedHeatingStructure(level, helper.absolutePos(new BlockPos(1, 1, 1)));
    BlockPos drainPos = new BlockPos(2, 2, 1);
    BlockPos ductPos = new BlockPos(3, 2, 2);
    BlockPos chutePos = new BlockPos(2, 3, 1);
    BlockPos ductTargetPos = new BlockPos(6, 1, 6);
    BlockPos drainTargetPos = new BlockPos(6, 1, 4);
    TankBlockEntity ductTarget = placeTank(helper, ductTargetPos);
    TankBlockEntity drainTarget = placeTank(helper, drainTargetPos);
    FluidResource gold = FluidResource.of(TinkerFluids.moltenGold.get());
    FluidResource copper = FluidResource.of(TinkerFluids.moltenCopper.get());
    helper.startSequence()
      .thenWaitUntil(() -> helper.assertTrue(level.getBlockEntity(controller) instanceof HeatingStructureBlockEntity structure && structure.getStructure() != null,
        Component.literal("smeltery did not form")))
      .thenExecute(() -> {
        ResourceHandler<FluidResource> smeltery = level.getCapability(Capabilities.Fluid.BLOCK, controller, null);
        helper.assertTrue(smeltery != null, Component.literal("smeltery has no fluid capability"));
        try (Transaction tx = Transaction.openRoot()) {
          helper.assertTrue(smeltery.insert(gold, FluidValues.INGOT * 2, tx) == FluidValues.INGOT * 2 && smeltery.insert(copper, FluidValues.INGOT * 2, tx) == FluidValues.INGOT * 2,
            Component.literal("smeltery did not take 2 ingots each of gold and copper"));
          tx.commit();
        }
        // duct without a filter: nothing goes through
        ResourceHandler<FluidResource> duct = fluidCapability(helper, ductPos, Direction.EAST);
        helper.assertTrue(pipe(duct, fluidCapability(helper, ductTargetPos, Direction.UP), FluidValues.INGOT) == null, Component.literal("unfiltered duct gave out fluid"));
        // copper can filter through the duct's item capability
        ResourceHandler<ItemResource> ductItems = level.getCapability(Capabilities.Item.BLOCK, helper.absolutePos(ductPos), Direction.EAST);
        helper.assertTrue(ductItems != null, Component.literal("duct has no item capability"));
        ItemStack can = CopperCanItem.setFluid(new ItemStack(TinkerSmeltery.copperCan), new FluidStack(TinkerFluids.moltenCopper.get(), FluidValues.INGOT));
        try (Transaction tx = Transaction.openRoot()) {
          helper.assertTrue(ductItems.insert(ItemResource.of(can), 1, tx) == 1, Component.literal("duct did not take the copper can filter"));
          tx.commit();
        }
        // the pipe only gets copper out of the duct, all of it
        ResourceStack<FluidResource> fromDuct = pipe(duct, fluidCapability(helper, ductTargetPos, Direction.UP), FluidValues.INGOT * 4);
        helper.assertTrue(fromDuct != null && fromDuct.resource().equals(copper) && fromDuct.amount() == FluidValues.INGOT * 2, Component.literal("duct did not give out the 2 ingots of copper, gave " + fromDuct));
        helper.assertTrue(pipe(duct, fluidCapability(helper, ductTargetPos, Direction.UP), FluidValues.INGOT) == null, Component.literal("duct gave out gold through a copper filter"));
        // inserts are filtered too: gold is refused, copper goes back into the smeltery
        try (Transaction tx = Transaction.openRoot()) {
          helper.assertTrue(duct.insert(gold, FluidValues.INGOT, tx) == 0, Component.literal("copper filtered duct accepted gold"));
        }
        ResourceStack<FluidResource> intoDuct = pipe(fluidCapability(helper, ductTargetPos, Direction.UP), duct, FluidValues.INGOT);
        helper.assertTrue(intoDuct != null && intoDuct.resource().equals(copper) && intoDuct.amount() == FluidValues.INGOT, Component.literal("duct did not take an ingot of copper back, took " + intoDuct));
        helper.assertTrue(ductTarget.getTank().getFluidAmount() == FluidValues.INGOT, Component.literal("duct target tank should hold 1 ingot, holds " + ductTarget.getTank().getFluidAmount()));

        // the drain gives out any fluid of the smeltery
        ResourceHandler<FluidResource> drain = fluidCapability(helper, drainPos, Direction.NORTH);
        ResourceStack<FluidResource> fromDrain = pipe(drain, fluidCapability(helper, drainTargetPos, Direction.UP), FluidValues.INGOT * 2);
        helper.assertTrue(fromDrain != null && fromDrain.amount() > 0 && (fromDrain.resource().equals(gold) || fromDrain.resource().equals(copper)),
          Component.literal("drain did not give out a smeltery fluid, gave " + fromDrain));
        helper.assertTrue(drainTarget.getTank().getFluidAmount() == fromDrain.amount(), Component.literal("drain target tank does not hold what the drain gave out"));
        // what is left in the smeltery: 4 ingots minus 1 net through the duct, minus the drained amount
        long left = 0;
        for (int i = 0; i < smeltery.size(); i++) {
          left += smeltery.getAmountAsLong(i);
        }
        helper.assertTrue(left == FluidValues.INGOT * 3L - fromDrain.amount(), Component.literal("smeltery should hold " + (FluidValues.INGOT * 3 - fromDrain.amount()) + " mB, holds " + left));

        // hoppers: a drain takes no items, the chute feeds the melting inventory
        helper.assertTrue(level.getCapability(Capabilities.Item.BLOCK, helper.absolutePos(drainPos), Direction.NORTH) == null, Component.literal("drain exposes an item capability"));
        placeHopper(helper, drainPos.north(), Direction.SOUTH, new ItemStack(Items.IRON_INGOT));
        placeHopper(helper, chutePos.above(), Direction.DOWN, new ItemStack(Items.IRON_INGOT));
      })
      .thenWaitUntil(() -> {
        helper.assertTrue(level.getBlockEntity(helper.absolutePos(chutePos.above())) instanceof HopperBlockEntity chuteHopper && chuteHopper.isEmpty(),
          Component.literal("hopper above the chute did not push its ingot"));
        ResourceHandler<ItemResource> melting = level.getCapability(Capabilities.Item.BLOCK, controller, null);
        boolean inInventory = false;
        for (int i = 0; melting != null && i < melting.size(); i++) {
          inInventory |= melting.getResource(i).is(Items.IRON_INGOT);
        }
        ResourceHandler<FluidResource> smeltery = level.getCapability(Capabilities.Fluid.BLOCK, controller, null);
        boolean melted = smeltery != null && ResourceHandlerUtil.contains(smeltery, FluidResource.of(TinkerFluids.moltenIron.get()));
        helper.assertTrue(inInventory || melted, Component.literal("the chute's ingot is neither in the melting inventory nor melted"));
      })
      .thenIdle(20)
      .thenExecute(() -> {
        if (level.getBlockEntity(helper.absolutePos(drainPos.north())) instanceof HopperBlockEntity drainHopper) {
          helper.assertTrue(drainHopper.getItem(0).is(Items.IRON_INGOT) && drainHopper.getItem(0).getCount() == 1, Component.literal("hopper pushed its ingot into the drain"));
        }
      })
      .thenSucceed();
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
    ChestItemHandler inventory = chest.getItemHandler();
    inventory.setStackInSlot(0, new ItemStack(Items.DIAMOND, 5));
    level.destroyBlock(pos, true);
    helper.assertTrue(inventory.getStackInSlot(0).isEmpty(), Component.literal("broken chest still holds its items"));
    List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2), entity -> entity.getItem().is(Items.DIAMOND));
    helper.assertTrue(drops.stream().mapToInt(entity -> entity.getItem().getCount()).sum() == 5, Component.literal("broken chest did not drop its 5 diamonds"));
    helper.succeed();
  }
}
