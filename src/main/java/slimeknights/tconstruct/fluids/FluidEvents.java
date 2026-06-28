package slimeknights.tconstruct.fluids;

import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.fluids.util.ConstantFluidContainerWrapper;

/**
 * Event subscriber for fluid game events.
 */
@SuppressWarnings("unused")
@EventBusSubscriber(modid = TConstruct.MOD_ID, bus = Bus.GAME)
public class FluidEvents {
  @SubscribeEvent
  static void onFurnaceFuel(FurnaceFuelBurnTimeEvent event) {
    if (event.getItemStack().getItem() == TinkerFluids.blazingBlood.asItem()) {
      // 150% efficiency compared to lava bucket, compare to casting blaze rods, which cast into 120%
      event.setBurnTime(30000);
    }
  }

  /**
   * Creates the fluid handler for the vanilla powder snow bucket.
   * PORT M3: register this on RegisterCapabilitiesEvent via
   * {@code event.registerItem(Capabilities.FluidHandler.ITEM, FluidEvents::powderSnowHandler, Items.POWDER_SNOW_BUCKET)}.
   */
  public static IFluidHandlerItem powderSnowHandler(net.minecraft.world.item.ItemStack stack, Void ctx) {
    return new ConstantFluidContainerWrapper(new FluidStack(TinkerFluids.powderedSnow.get(), FluidType.BUCKET_VOLUME), stack, Items.BUCKET.getDefaultInstance());
  }
}
