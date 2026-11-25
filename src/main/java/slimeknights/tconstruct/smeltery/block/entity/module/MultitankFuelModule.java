package slimeknights.tconstruct.smeltery.block.entity.module;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.EmptyResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import slimeknights.mantle.block.entity.MantleBlockEntity;
import slimeknights.mantle.transfer.TransferHelper;
import slimeknights.tconstruct.library.transfer.TransferUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;

/**
 * Fuel module that supports multiple tanks, selecting just one for the fuel result.
 * PORT 1.21.10 (transfer): also a native {@link ResourceHandler} over the fuel tanks, used by the heating structure menu.
 */
public class MultitankFuelModule extends FuelModule implements ResourceHandler<FluidResource> {
  /** Block position that will never be valid in world, used for sync */
  private static final BlockPos NULL_POS = new BlockPos(0, Short.MIN_VALUE, 0);

  /** Supplier for the list of valid tank positions */
  private final Supplier<List<BlockPos>> tankSupplier;
  /** Position of the last fluid handler */
  private BlockPos lastPos = NULL_POS;

  /** Map of all tank handlers at each relevant position. Used for fast switching between handlers, notably in the UI */
  private Map<BlockPos,ResourceHandler<FluidResource>> tankHandlers;

  public MultitankFuelModule(MantleBlockEntity parent, Supplier<List<BlockPos>> tankSupplier) {
    super(parent);
    this.tankSupplier = tankSupplier;
  }

  /** Resets just the last fluid handler */
  private void clearLastListener() {
    super.resetHandler();
  }

  @Override
  protected void resetHandler() {
    this.lastPos = NULL_POS;
    super.resetHandler();
  }

  /** Called on structure rebuild to clear the gui handler list */
  public void clearFluidListeners() {
    tankHandlers = null;
  }

  /** Called on servant load to ensure the handler is present in the cache */
  public void ensureTankPresent(BlockEntity be) {
    BlockPos pos = be.getBlockPos();
    if (tankHandlers != null && !tankHandlers.containsKey(pos)) {
      Level world = getLevel();
      ResourceHandler<FluidResource> handler = TransferHelper.getFluidResourceHandler(world, pos, null);
      if (handler != null) {
        tankHandlers.put(pos, handler);
      }
    }
  }

  /** Gets the map from position to fluid handler */
  private Map<BlockPos,ResourceHandler<FluidResource>> getTankHandlers() {
    if (tankHandlers == null) {
      tankHandlers = new LinkedHashMap<>();
      Level world = getLevel();
      for (BlockPos pos : tankSupplier.get()) {
        ResourceHandler<FluidResource> handler = TransferHelper.getFluidResourceHandler(world, pos, null);
        if (handler != null) {
          tankHandlers.put(pos, handler);
        }
      }
    }
    return tankHandlers;
  }


  /* Fuel finding */

  /**
   * Tries to consume fuel from the given position
   * @param pos  Position
   * @return   Temperature of the consumed fuel, 0 if none found
   */
  private int tryFuelPosition(BlockPos pos, boolean consume) {
    ResourceHandler<FluidResource> tankCap = getTankHandlers().get(pos);
    if (tankCap != null) {
      // if we find a valid handler, try to consume fuel from it
      int temperature = tryLiquidFuel(tankCap, consume);
      if (temperature > 0) {
        fluidHandler = tankCap;
        lastPos = pos;
        return temperature;
      }
    }
    return 0;
  }

  /**
   * Attempts to consume fuel from one of the tanks
   * @return  temperature of the found fluid, 0 if none
   */
  @Override
  public int findFuel(boolean consume) {
    // only fetch a handler if we haven't done so
    if (fluidHandler != null) {
      // if we have a handler, try to use that if possible
      int temperature = tryLiquidFuel(fluidHandler, consume);
      if (temperature > 0) {
        return temperature;
      }
    } else if (lastPos != NULL_POS) {
      // if no handler, try to find one at the last position
      int posTemp = tryFuelPosition(lastPos, consume);
      if (posTemp > 0) {
        return posTemp;
      }
    }

    // find a new handler among our tanks
    for (BlockPos pos : tankSupplier.get()) {
      // already checked the last position above, no reason to try again
      if (!pos.equals(lastPos)) {
        int posTemp = tryFuelPosition(pos, consume);
        if (posTemp > 0) {
          return posTemp;
        }
      }
    }

    // no handler found, tell client of the lack of fuel
    if (consume) {
      temperature = 0;
      rate = 0;
    }
    return 0;
  }


  /* NBT */
  private static final String TAG_LAST_FUEL = "last_fuel";

  @Override
  public void readFromTag(ValueInput nbt) {
    super.readFromTag(nbt);
    // 1.21: writeBlockPos stores an INT_ARRAY, so no TAG_COMPOUND guard — the Optional handles missing keys
    nbt.read(TAG_LAST_FUEL, BlockPos.CODEC).ifPresent(pos -> lastPos = pos.offset(parent.getBlockPos()));
  }

  @Override
  public void writeToTag(ValueOutput nbt) {
    super.writeToTag(nbt);
    if (lastPos != NULL_POS) {
      // BlockPos.CODEC writes the same int array as TagUtil.writePos
      nbt.store(TAG_LAST_FUEL, BlockPos.CODEC, lastPos.subtract(parent.getBlockPos()));
    }
  }


  /* UI syncing */
  private static final int LAST_X = 4;
  private static final int LAST_Y = 5;
  private static final int LAST_Z = 6;

  @Override
  public int getCount() {
    return 7;
  }

  @Override
  public int get(int index) {
    return switch (index) {
      case LAST_X -> lastPos.getX();
      case LAST_Y -> lastPos.getY();
      case LAST_Z -> lastPos.getZ();
      default -> super.get(index);
    };
  }

  @Override
  public void set(int index, int value) {
    if (LAST_X <= index && index <= LAST_Z) {
      switch (index) {
        case LAST_X -> lastPos = new BlockPos(value, lastPos.getY(), lastPos.getZ());
        case LAST_Y -> lastPos = new BlockPos(lastPos.getX(), value, lastPos.getZ());
        case LAST_Z -> lastPos = new BlockPos(lastPos.getX(), lastPos.getY(), value);
      }
      clearLastListener();
    } else {
      super.set(index, value);
    }
  }

  @Override
  public FuelInfo getFuelInfo() {
    // if there is no position, means we have not yet consumed fuel. Just fetch the first tank
    // TODO: should we try to find a valid fuel tank? might be a bit confusing if they have multiple tanks in the structure before melting
    // however, a valid tank is a lot more effort to find

    // Y of big negative is how the UI syncs null
    BlockPos mainTank = lastPos;
    if (mainTank.getY() == NULL_POS.getY()) {
      // if no first, return no fuel info
      List<BlockPos> positions = tankSupplier.get();
      if (positions.isEmpty()) {
        return FuelInfo.EMPTY;
      }
      mainTank = positions.get(0);
      assert mainTank != null;
    }

    // fetch primary fuel handler
    if (fluidHandler == null) {
      fluidHandler = getTankHandlers().get(mainTank);
    }

    // determine what fluid we have and hpw many other fluids we have
    FuelInfo info = super.getFuelInfo();
    // add extra fluid display
    if (!info.isEmpty()) {
      // add display info from each handler
      FluidStack currentFuel = info.getFluid();
      for (Entry<BlockPos,ResourceHandler<FluidResource>> entry : getTankHandlers().entrySet()) {
        if (!mainTank.equals(entry.getKey())) {
          ResourceHandler<FluidResource> handler = entry.getValue();
          // sum if empty (more capacity) or the same fluid (more amount and capacity)
          FluidStack fluid = TransferUtil.getFluid(handler, 0);
          if (fluid.isEmpty()) {
            info.add(0, TransferUtil.getCapacity(handler, 0));
          } else if (FluidStack.isSameFluidSameComponents(currentFuel, fluid)) {
            info.add(fluid.getAmount(), TransferUtil.getCapacity(handler, 0));
          }
        }
      }
    }

    return info;
  }
  /* Fluid handler */

  /** Gets the most recently used fluid */
  public FluidStack getLastFluid() {
    if (fluidHandler != null) {
      return TransferUtil.getFluid(fluidHandler, 0);
    }
    BlockPos pos;
    if (lastPos.getY() != NULL_POS.getY()) {
      pos = lastPos;
    } else {
      List<BlockPos> positions = tankSupplier.get();
      if (!positions.isEmpty()) {
        pos = positions.get(0);
      } else {
        return FluidStack.EMPTY;
      }
    }
    ResourceHandler<FluidResource> handler = getTankHandlers().get(pos);
    return handler != null ? TransferUtil.getFluid(handler, 0) : FluidStack.EMPTY;
  }

  /*
   * Native view over the fuel tanks: index i is the first tank of the handler at the i-th tank position (empty if none).
   * Inserts and extracts at an index go to that whole handler, like the legacy fill and drain did; the default untargeted insert and
   * extract go through the tanks in structure order.
   */

  /** Gets the tank at the given index */
  private ResourceHandler<FluidResource> getTank(int tank) {
    if (tank >= 0) {
      List<BlockPos> positions = tankSupplier.get();
      if (tank < positions.size()) {
        ResourceHandler<FluidResource> handler = getTankHandlers().get(positions.get(tank));
        if (handler != null) {
          return handler;
        }
      }
    }
    return EmptyResourceHandler.instance();
  }

  @Override
  public int size() {
    return tankSupplier.get().size();
  }

  @Override
  public FluidResource getResource(int index) {
    ResourceHandler<FluidResource> handler = getTank(index);
    return handler.size() > 0 ? handler.getResource(0) : FluidResource.EMPTY;
  }

  @Override
  public long getAmountAsLong(int index) {
    ResourceHandler<FluidResource> handler = getTank(index);
    return handler.size() > 0 ? handler.getAmountAsLong(0) : 0;
  }

  @Override
  public long getCapacityAsLong(int index, FluidResource resource) {
    ResourceHandler<FluidResource> handler = getTank(index);
    return handler.size() > 0 ? handler.getCapacityAsLong(0, resource) : 0;
  }

  @Override
  public boolean isValid(int index, FluidResource resource) {
    ResourceHandler<FluidResource> handler = getTank(index);
    return handler.size() > 0 && handler.isValid(0, resource);
  }

  @Override
  public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
    TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
    return getTank(index).insert(resource, amount, transaction);
  }

  @Override
  public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
    TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
    return getTank(index).extract(resource, amount, transaction);
  }
}
