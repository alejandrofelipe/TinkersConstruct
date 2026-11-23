package slimeknights.tconstruct.smeltery.item;

import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.ItemAccessResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.entity.component.TankBlockEntity;

/**
 * Fluid handler of tank block items, storing the tank fluid of a single tank in the item's components.
 * <p>
 * PORT 1.21.10 (transfer step 1): native {@link ItemAccessResourceHandler}; every change exchanges the item through the {@link ItemAccess},
 * so it is transactional. Amounts and capacities are totals over the accessed items, each storing its own tank (the legacy handler wrote
 * the scaled total of a stack into each item). Like before, stacks larger than 16 of size limited tanks cannot be filled.
 */
public class TankItemFluidHandler extends ItemAccessResourceHandler<FluidResource> {
  private final TankItem tankItem;

  public TankItemFluidHandler(TankItem tankItem, ItemAccess access) {
    super(access, 1);
    this.tankItem = tankItem;
  }

  /** Gets the tank content of the item, empty if not this tank */
  private SimpleFluidContent getContent(ItemResource item) {
    if (!item.is(tankItem)) {
      return SimpleFluidContent.EMPTY;
    }
    return item.getOrDefault(TinkerSmeltery.TANK_FLUID.get(), SimpleFluidContent.EMPTY);
  }

  @Override
  protected FluidResource getResourceFrom(ItemResource item, int index) {
    SimpleFluidContent content = getContent(item);
    return content.isEmpty() ? FluidResource.EMPTY : FluidResource.of(content.copy());
  }

  @Override
  protected int getAmountFrom(ItemResource item, int index) {
    return getContent(item).getAmount();
  }

  @Override
  protected ItemResource update(ItemResource item, int index, FluidResource resource, int amount) {
    if (amount == 0) {
      return item.without(TinkerSmeltery.TANK_FLUID.get());
    }
    return item.with(TinkerSmeltery.TANK_FLUID.get(), SimpleFluidContent.copyOf(resource.toStack(amount)));
  }

  @Override
  public boolean isValid(int index, FluidResource resource) {
    return itemAccess.getResource().is(tankItem) && tankItem.canFill(itemAccess.getAmount());
  }

  @Override
  protected int getCapacity(int index, FluidResource resource) {
    return TankBlockEntity.getCapacity(tankItem);
  }
}
