package slimeknights.tconstruct.smeltery.block.entity.module;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.EmptyResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import slimeknights.mantle.block.entity.MantleBlockEntity;
import slimeknights.mantle.transfer.TransferHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.fuel.MeltingFuel;
import slimeknights.tconstruct.library.recipe.fuel.MeltingFuelLookup;

import javax.annotation.Nullable;

/**
 * Fuel module variant that supports both item and fluid fuels. Only supports a single fluid position which should not change.
 * PORT 1.21.10 (transfer): native fluid and item handlers; consuming fuel is a root transaction (ticking never runs inside one).
 */
public class SolidFuelModule extends FuelModule {
  /** Location of the fuel tank */
  private final BlockPos fuelPos;
  /** Whether the handlers have been fetched since the last reset */
  private boolean handlersFetched = false;
  /** Last item handler where items were extracted */
  @Nullable
  private ResourceHandler<ItemResource> itemHandler;

  public SolidFuelModule(MantleBlockEntity parent, BlockPos fuelPos) {
    super(parent);
    this.fuelPos = fuelPos;
  }

  @Override
  protected void resetHandler() {
    super.resetHandler();
    itemHandler = null;
    handlersFetched = false;
  }


  /* Fuel updating */

  /**
   * Tries to consume fuel from the given fluid handler
   * @param handler  Handler to consume fuel from
   * @return   Temperature of the consumed fuel, 0 if none found
   */
  private int trySolidFuel(ResourceHandler<ItemResource> handler, boolean consume) {
    for (int i = 0; i < handler.size(); i++) {
      ItemResource resource = handler.getResource(i);
      if (resource.isEmpty()) {
        continue;
      }
      ItemStack stack = resource.toStack(handler.getAmountAsInt(i));
      int time = stack.getBurnTime(TinkerRecipeTypes.FUEL.get(), parent.getLevel().fuelValues()) / 4;
      if (time > 0) {
        MeltingFuel solid = MeltingFuelLookup.getSolid();
        if (consume) {
          // extract the item and put back its container in one transaction, anything that does not fit back is dropped
          boolean extracted = false;
          ItemStack container = resource.getItem().getCraftingRemainder();
          int notInserted = 0;
          try (Transaction tx = Transaction.openRoot()) {
            if (handler.extract(i, resource, 1, tx) == 1) {
              extracted = true;
              if (!container.isEmpty()) {
                notInserted = container.getCount() - handler.insert(ItemResource.of(container), container.getCount(), tx);
              }
              tx.commit();
            }
          }
          if (extracted) {
            fuel += time;
            fuelQuality = time;
            temperature = solid.getTemperature();
            rate = solid.getRate();
            parent.setChangedFast();
            // if we cannot insert the container back, spit it on the ground
            if (notInserted > 0) {
              Level world = getLevel();
              double x = (world.random.nextFloat() * 0.5F) + 0.25D;
              double y = (world.random.nextFloat() * 0.5F) + 0.25D;
              double z = (world.random.nextFloat() * 0.5F) + 0.25D;
              ItemEntity itementity = new ItemEntity(world, fuelPos.getX() + x, fuelPos.getY() + y, fuelPos.getZ() + z, container.copyWithCount(notInserted));
              itementity.setDefaultPickUpDelay();
              world.addFreshEntity(itementity);
            }
          } else {
            TConstruct.LOG.error("Invalid item removed from solid fuel handler");
          }
        }
        return solid.getTemperature();
      }
    }
    return 0;
  }

  /** Fetches any relevant fuel handlers from the target position */
  private void fetchHandlers() {
    // if we have handlers, nothing to do
    if (handlersFetched) {
      return;
    }
    handlersFetched = true;
    Level level = getLevel();
    // first, identify a capability that has what we need
    // on the chance both are present, we prioritize fluid; we don't expect that to change
    fluidHandler = TransferHelper.getFluidResourceHandler(level, fuelPos, null);
    itemHandler = TransferHelper.getItemResourceHandler(level, fuelPos, null);
  }

  @Override
  public int findFuel(boolean consume) {
    fetchHandlers();

    // prioritize liquid fuel - it usually goes hotter
    int temperature = 0;
    if (fluidHandler != null) {
      temperature = tryLiquidFuel(fluidHandler, consume);
    }
    // next, try solid fuel
    if (temperature == 0 && itemHandler != null) {
      temperature = trySolidFuel(itemHandler, consume);
    }
    // no handler found, tell client of the lack of fuel
    if (temperature == 0 && consume) {
      this.temperature = 0;
      this.rate = 0;
    }
    return temperature;
  }


  /* UI Syncing */

  @Override
  public FuelInfo getFuelInfo() {
    fetchHandlers();

    FuelInfo info = super.getFuelInfo();
    if (info.isEmpty() && itemHandler != null) {
      return FuelInfo.ITEM;
    }
    return info;
  }


  /* Fluid handler */

  /** Gets the fluid handler for proxy */
  public ResourceHandler<FluidResource> getTank() {
    fetchHandlers();
    return fluidHandler != null ? fluidHandler : EmptyResourceHandler.instance();
  }
}
