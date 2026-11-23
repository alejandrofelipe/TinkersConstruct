package slimeknights.tconstruct.fluids;

import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.fml.common.EventBusSubscriber;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.fluids.util.ConstantFluidContainerWrapper;

/**
 * Event subscriber for fluid game events.
 */
@SuppressWarnings("unused")
@EventBusSubscriber(modid = TConstruct.MOD_ID)
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
   * Registered on RegisterCapabilitiesEvent via
   * {@code event.registerItem(Capabilities.Fluid.ITEM, FluidEvents::powderSnowHandler, Items.POWDER_SNOW_BUCKET)}.
   */
  public static ResourceHandler<FluidResource> powderSnowHandler(net.minecraft.world.item.ItemStack stack, ItemAccess access) {
    return new ConstantFluidContainerWrapper(new FluidStack(TinkerFluids.powderedSnow.get(), FluidType.BUCKET_VOLUME), access, ItemResource.of(Items.BUCKET));
  }
}
