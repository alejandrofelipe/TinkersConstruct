package slimeknights.tconstruct.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.gadgets.capability.PiggybackCapability;
import slimeknights.tconstruct.gadgets.capability.PiggybackHandler;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.capability.inventory.ToolInventoryCapability;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.data.ModifierIds;
import slimeknights.tconstruct.tools.data.material.MaterialIds;

/**
 * Gametests asserting the {@code RegisterCapabilitiesEvent} registrations actually resolve at runtime. Their only
 * failure mode is "not registered" → capability resolves null;
 * running on a live server (where the mod-bus event has fired) is the level that exercises that.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(TConstruct.MOD_ID)
public class CapabilityGameTests {

  /** The vanilla powder snow bucket must expose a fluid handler that drains powdered_snow. */
  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void powder_snow_bucket_fluid_handler(GameTestHelper helper) {
    ItemStack bucket = new ItemStack(Items.POWDER_SNOW_BUCKET);
    IFluidHandlerItem handler = bucket.getCapability(Capabilities.FluidHandler.ITEM);
    if (handler == null) {
      helper.fail("powder snow bucket exposes no fluid handler capability");
      return;
    }
    FluidStack drained = handler.drain(FluidType.BUCKET_VOLUME, FluidAction.SIMULATE);
    if (drained.isEmpty() || drained.getFluid() != TinkerFluids.powderedSnow.get()) {
      helper.fail("powder snow bucket did not expose the powdered_snow fluid");
      return;
    }
    helper.succeed();
  }

  /** A player must expose the piggyback capability so carried-passenger upkeep runs each tick. */
  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void player_piggyback_capability(GameTestHelper helper) {
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    PiggybackHandler handler = player.getCapability(PiggybackCapability.PIGGYBACK, null);
    if (handler == null) {
      helper.fail("player exposes no piggyback capability");
      return;
    }
    // the handler must be a single cached instance per entity: entity caps are not memoized by NeoForge, so
    // without the backing attachment a fresh handler would be built each lookup, resetting lastPassengers and
    // re-broadcasting the set-passengers packet every tick while carrying a passenger
    if (player.getCapability(PiggybackCapability.PIGGYBACK, null) != handler) {
      helper.fail("piggyback handler is rebuilt per lookup; carried-passenger sync would spam every tick");
      return;
    }
    helper.succeed();
  }

  /**
   * Tools expose the item handler, fluid handler and energy capabilities their modifiers provide (served by
   * {@link slimeknights.tconstruct.library.tools.capability.ToolCapabilityProvider}). They were never registered on
   * {@code RegisterCapabilitiesEvent}, so tool inventories did not open and tool tanks were invisible to other blocks.
   */
  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tool_item_capabilities(GameTestHelper helper) {
    // no inventory, tank or energy modifier: no capabilities
    ItemStack plain = ToolBuildHandler.createSingleMaterial(TinkerTools.pickaxe.get(), MaterialVariant.of((MaterialVariantId) MaterialIds.iron));
    check(helper, !plain.isEmpty(), "could not build an iron pickaxe");
    check(helper, plain.getCapability(Capabilities.ItemHandler.ITEM) == null, "plain pickaxe exposes an item handler");
    check(helper, plain.getCapability(Capabilities.FluidHandler.ITEM) == null, "plain pickaxe exposes a fluid handler");
    check(helper, plain.getCapability(Capabilities.EnergyStorage.ITEM) == null, "plain pickaxe exposes an energy storage");

    // pocket: a 3 slot inventory, which opens the tool container
    ItemStack pocket = withModifier(plain, ModifierIds.pocket);
    IItemHandler items = pocket.getCapability(Capabilities.ItemHandler.ITEM);
    check(helper, items != null && items.getSlots() == 3, "pickaxe with pocket should expose a 3 slot item handler, got " + (items == null ? "none" : items.getSlots() + " slots"));
    // the server side open path: PASS when the tool exposes no item handler. A fake player (a server player that never logs
    // in; a mock connection rejects mod payloads) does not actually open menus, so only the result is checked
    ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
    try {
      player.setItemInHand(InteractionHand.MAIN_HAND, pocket);
      InteractionResult result = ToolInventoryCapability.tryOpenContainer(pocket, ToolStack.from(pocket), player, EquipmentSlot.MAINHAND);
      check(helper, result.consumesAction(), "tool inventory did not open: " + result);
    } finally {
      player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    // tank: a fluid handler that accepts fluid
    ItemStack tank = withModifier(plain, ModifierIds.tank);
    IFluidHandlerItem fluids = tank.getCapability(Capabilities.FluidHandler.ITEM);
    check(helper, fluids != null, "pickaxe with tank exposes no fluid handler");
    int filled = fluids.fill(new FluidStack(Fluids.WATER, 500), FluidAction.EXECUTE);
    check(helper, filled == 500, "pickaxe tank accepted " + filled + "mb of 500mb water");
    helper.succeed();
  }

  /** Copies the tool with the given modifier added at level 1 */
  private static ItemStack withModifier(ItemStack stack, ModifierId modifier) {
    ToolStack tool = ToolStack.copyFrom(stack);
    tool.addModifier(modifier, 1);
    return tool.createStack();
  }

  private static void check(GameTestHelper helper, boolean condition, String message) {
    helper.assertTrue(condition, message);
  }
}
