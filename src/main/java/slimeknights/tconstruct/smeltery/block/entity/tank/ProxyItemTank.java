package slimeknights.tconstruct.smeltery.block.entity.tank;

import java.util.Optional;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.EmptyResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import slimeknights.mantle.block.entity.MantleBlockEntity;
import slimeknights.mantle.inventory.SingleItemHandler;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.common.network.InventorySlotSyncPacket;
import slimeknights.tconstruct.common.network.TinkerNetwork;
import slimeknights.tconstruct.library.fluid.IFluidTankUpdater;
import slimeknights.tconstruct.library.transfer.TransferUtil;

/**
 * Item slot whose fluid handler proxies to the native fluid handler of the contained item.
 * PORT 1.21.10 (transfer): the legacy fluid handler view is gone, fluid access goes through {@link #getFluidCapability()} in a transaction.
 */
public class ProxyItemTank<T extends MantleBlockEntity & IFluidTankUpdater> extends SingleItemHandler<T> {
  /** Set while the native fluid handler runs, so the contained item may be exchanged for any container (e.g. an emptied bottle) */
  private boolean exchanging = false;
  /**
   * Native fluid handler, registered as the fluid capability: the native fluid handler of the contained item, accessed through this
   * item handler, so the item changes as part of the transaction and the block syncs only once it commits.
   */
  @Getter
  private final ResourceHandler<FluidResource> fluidCapability = new ResourceHandler<>() {
    private ResourceHandler<FluidResource> handler() {
      return itemFluidHandler();
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

  /** Resolves the native fluid handler of the contained item, accessed through this item handler; empty if there is none */
  private ResourceHandler<FluidResource> itemFluidHandler() {
    ResourceHandler<FluidResource> handler = getStack().isEmpty() ? null : ItemAccess.forHandlerIndex(this, 0).getCapability(Capabilities.Fluid.ITEM);
    return handler != null ? handler : EmptyResourceHandler.instance();
  }

  /**
   * Reads the fluid and capacity of the first tank of the contained item, resolving its fluid handler once (renderers call this every frame).
   * Read only, safe anywhere.
   * @return  Copy of the fluid and the capacity for any fluid, empty and 0 without a tank
   */
  public TankContents getContents() {
    ResourceHandler<FluidResource> handler = itemFluidHandler();
    return new TankContents(TransferUtil.getFluid(handler, 0), TransferUtil.getCapacity(handler, 0));
  }

  /** Fluid and capacity of the first tank of the contained item, see {@link #getContents()} */
  public record TankContents(FluidStack fluid, int capacity) {}

  /** Sets the stack without syncing, for the native handlers inside transactions and their reverts */
  @Override
  protected void setStackSilently(ItemStack newStack) {
    super.setStackSilently(newStack.isEmpty() ? ItemStack.EMPTY : newStack);
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
    ItemStack craftRemaining = Optional.ofNullable(stack.getItem().getCraftingRemainder()).map(ItemStackTemplate::create).orElse(ItemStack.EMPTY);
    return !stack.is(TinkerTags.Items.PROXY_TANK_BLACKLIST)
      && (craftRemaining.isEmpty() || !craftRemaining.is(TinkerTags.Items.PROXY_TANK_BLACKLIST))
      // transaction safe: called by the native item handler while inserting
      && TransferUtil.hasFluidHandler(stack);
  }

  @Override
  protected boolean isItemValid(ItemResource resource) {
    return exchanging || super.isItemValid(resource);
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
      // if the stack instance changed, sync
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
}
