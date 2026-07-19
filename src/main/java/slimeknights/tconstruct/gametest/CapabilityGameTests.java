package slimeknights.tconstruct.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.gadgets.capability.PiggybackCapability;
import slimeknights.tconstruct.gadgets.capability.PiggybackHandler;

/**
 * Gametests asserting the two {@code RegisterCapabilitiesEvent} registrations wired in this pass actually resolve
 * at runtime. Both are 1-line registrations whose only failure mode is "not registered" → capability resolves null;
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
    helper.succeed();
  }
}
