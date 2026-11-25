package slimeknights.tconstruct.library.fluid;

import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.ResourceHandler;
import slimeknights.mantle.block.entity.MantleBlockEntity;
import slimeknights.mantle.transfer.SingleFluidTank;
import slimeknights.tconstruct.common.network.TinkerNetwork;
import slimeknights.tconstruct.smeltery.network.FluidUpdatePacket;

/**
 * Fluid tank owned by a block entity, syncing changes to the client.
 * <p>
 * PORT 1.21.10 (transfer): a native {@link ResourceHandler} (one tank), the form registered as the block's fluid capability, on Mantle's
 * {@link SingleFluidTank}. Inserts and extracts change the fluid silently inside the transaction, journaled, and run {@link #onContentsChanged()}
 * (block entity update, packets, light, comparators) only once the root transaction commits, so a simulation never notifies the block entity.
 * Internal changes go through transactions as well (e.g. {@code TransferUtil.fill}); the NBT is the legacy tank's ({@code Fluid}).
 */
public class FluidTankBase<T extends MantleBlockEntity> extends SingleFluidTank {

  protected T parent;

  public FluidTankBase(int capacity, T parent) {
    super(capacity);
    this.parent = parent;
  }

  @Override
  public void onContentsChanged() {
    if (parent instanceof IFluidTankUpdater) {
      ((IFluidTankUpdater) parent).onTankContentsChanged();
    }

    parent.setChanged();
    Level level = parent.getLevel();
    if(level != null && !level.isClientSide()) {
      TinkerNetwork.getInstance().sendToClientsAround(new FluidUpdatePacket(parent.getBlockPos(), this.getFluid()), level, parent.getBlockPos());
    }
  }
}
