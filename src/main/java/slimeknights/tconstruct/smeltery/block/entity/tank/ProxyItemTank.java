package slimeknights.tconstruct.smeltery.block.entity.tank;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.transfer.EmptyResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import slimeknights.mantle.transfer.TransferHelper;
import slimeknights.mantle.block.entity.MantleBlockEntity;
import slimeknights.mantle.inventory.SingleItemHandler;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.common.network.InventorySlotSyncPacket;
import slimeknights.tconstruct.common.network.TinkerNetwork;
import slimeknights.tconstruct.library.fluid.EmptyFluidHandlerItem;
import slimeknights.tconstruct.library.fluid.IFluidTankUpdater;
import slimeknights.tconstruct.library.transfer.SingleItemResourceHandler;
import slimeknights.tconstruct.library.transfer.TransferUtil;

/** Fluid handler that proxies to an item stack tank */
public class ProxyItemTank<T extends MantleBlockEntity & IFluidTankUpdater> extends SingleItemHandler<T> implements IFluidHandler {
  private IFluidHandlerItem itemTank;
  /** Set while the native fluid handler runs, so the contained item may be exchanged for any container (e.g. an emptied bottle) */
  private boolean exchanging = false;
  /**
   * Native view of the item slot, registered as the item capability.
   * PORT 1.21.10 (transfer step 1): changes are silent until the root transaction commits, then replayed through {@link #setStack(ItemStack)}.
   */
  @Getter
  private final SingleItemResourceHandler itemCapability = new SingleItemResourceHandler(this, this::setStackSilently) {
    @Override
    protected boolean isItemValid(int slot, ItemResource resource) {
      return exchanging || super.isItemValid(slot, resource);
    }
  };
  /**
   * Native fluid handler, registered as the fluid capability: the native fluid handler of the contained item, accessed through
   * {@link #itemCapability}, so the item changes as part of the transaction and the block syncs only once it commits.
   */
  @Getter
  private final ResourceHandler<FluidResource> fluidCapability = new ResourceHandler<>() {
    private ResourceHandler<FluidResource> handler() {
      ResourceHandler<FluidResource> handler = getStack().isEmpty() ? null : ItemAccess.forHandlerIndex(itemCapability, 0).getCapability(Capabilities.Fluid.ITEM);
      return handler != null ? handler : EmptyResourceHandler.instance();
    }

    @Override
    public int size() {
      return handler().size();
    }

    @Override
    public FluidResource getResource(int index) {
      return handler().getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
      return handler().getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
      return handler().getCapacityAsLong(index, resource);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
      return handler().isValid(index, resource);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
      exchanging = true;
      try {
        return handler().insert(index, resource, amount, transaction);
      } finally {
        exchanging = false;
      }
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
      exchanging = true;
      try {
        return handler().extract(index, resource, amount, transaction);
      } finally {
        exchanging = false;
      }
    }
  };

  /** Sets the stack without syncing, for the native handlers inside transactions and their reverts */
  private void setStackSilently(ItemStack newStack) {
    // discard the legacy handler cache, it is bound to the old stack
    itemTank = null;
    super.setStack(newStack.isEmpty() ? ItemStack.EMPTY : newStack);
  }
  public ProxyItemTank(T parent) {
    super(parent, 1);
  }

  @SuppressWarnings("deprecation")
  @Override
  protected boolean isItemValid(ItemStack stack) {
    // can only store items that are fluid handlers, though allow blacklist in case something is really broken
    // blacklist is mostly used for items that don't support incremental filling, as this block really isn't good at working with them
    // we check the container item so we don't have to put every bucket in the tag. Not bothering with complex container items; odds are item stack sensitive just returns the same item
    ItemStack craftRemaining = stack.getItem().getCraftingRemainder();
    return !stack.is(TinkerTags.Items.PROXY_TANK_BLACKLIST)
      && (craftRemaining.isEmpty() || !craftRemaining.is(TinkerTags.Items.PROXY_TANK_BLACKLIST))
      // transaction safe: called by the native item handler while inserting
      && TransferUtil.hasFluidHandler(stack);
  }

  /** Used by the fluid handler logic to sync changes as we directly mutate the internal stack */
  private void setStack(ItemStack newStack, boolean syncSame) {
    // if swapping to an empty stack, switch to the empty stack instance
    // prevents accidently having a 0 stack size capability
    if (newStack.isEmpty()) {
      newStack = ItemStack.EMPTY;
    }
    // update stack
    ItemStack oldStack = getStack();
    super.setStack(newStack);

    // server side may need to sync
    Level world = parent.getLevel();
    boolean needsUpdate = world != null && !world.isClientSide();
    if (oldStack != newStack) {
      // if the stack instance changed, discard cached cap and sync
      itemTank = null;
      if (needsUpdate) {
        // both stacks being empty means our stack shrunk by 1 and is being replaced with ItemStack.EMPTY
        needsUpdate = (oldStack.isEmpty() && newStack.isEmpty()) || !ItemStack.isSameItemSameComponents(oldStack, newStack);
      }
    } else if (needsUpdate) {
      needsUpdate = syncSame;
    }
    // sync changes
    if (needsUpdate) {
      parent.onTankContentsChanged();
      BlockPos pos = parent.getBlockPos();
      TinkerNetwork.getInstance().sendToClientsAround(new InventorySlotSyncPacket(newStack, 0, pos), world, pos);
    }
  }

  @Override
  public void setStack(ItemStack newStack) {
    setStack(newStack, false);
  }

  /** Gets the fluid handler for the item */
  private IFluidHandlerItem getItemTank() {
    if (itemTank == null) {
      ItemStack stack = getStack();
      // legacy handler for internal use outside transactions, works on a copy of the stack returned by getContainer()
      IFluidHandlerItem handler = TransferHelper.getLegacyFluidHandler(stack);
      itemTank = handler != null ? handler : new EmptyFluidHandlerItem(stack);
    }
    return itemTank;
  }

  @Override
  public int getTanks() {
    return getItemTank().getTanks();
  }

  @Override
  public FluidStack getFluidInTank(int tank) {
    return getItemTank().getFluidInTank(tank);
  }

  @Override
  public int getTankCapacity(int tank) {
    return getItemTank().getTankCapacity(tank);
  }

  @Override
  public boolean isFluidValid(int tank, FluidStack stack) {
    return getItemTank().isFluidValid(tank, stack);
  }

  @Override
  public int fill(FluidStack resource, FluidAction action) {
    IFluidHandlerItem tank = getItemTank();
    int filled = tank.fill(resource, action);
    // if something happened, force a sync of the item stack
    // hopefully it's the same instance, but we still need a client sync likely
    if (filled > 0 && action.execute()) {
      setStack(tank.getContainer(), true);
    }
    return filled;
  }

  @Override
  public FluidStack drain(FluidStack resource, FluidAction action) {
    IFluidHandlerItem tank = getItemTank();
    FluidStack drained = tank.drain(resource, action);
    if (!drained.isEmpty() && action.execute()) {
      setStack(tank.getContainer(), true);
    }
    return drained;
  }

  @Override
  public FluidStack drain(int maxDrain, FluidAction action) {
    IFluidHandlerItem tank = getItemTank();
    FluidStack drained = tank.drain(maxDrain, action);
    if (!drained.isEmpty() && action.execute()) {
      setStack(tank.getContainer(), true);
    }
    return drained;
  }
}
