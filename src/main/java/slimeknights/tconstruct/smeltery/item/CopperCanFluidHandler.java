package slimeknights.tconstruct.smeltery.item;

import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.ItemAccessResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import slimeknights.tconstruct.library.recipe.FluidValues;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;

/**
 * Fluid handler of a copper can: holds exactly one ingot of any fluid, filled and drained whole.
 * <p>
 * PORT 1.21.10 (transfer step 1): native {@link ItemAccessResourceHandler}, the fluid lives in the item's components and every change goes
 * through the {@link ItemAccess} (exchanging the item), so it is transactional. Like the legacy handler, a stack of cans is filled or drained
 * all together: amounts are totals over the accessed items, each holding one ingot.
 */
public class CopperCanFluidHandler extends ItemAccessResourceHandler<FluidResource> {
  public CopperCanFluidHandler(ItemAccess access) {
    super(access, 1);
  }

  /** Gets the fluid content of the item, empty if not a can */
  private static SimpleFluidContent getContent(ItemResource item) {
    if (!item.is(TinkerSmeltery.copperCan)) {
      return SimpleFluidContent.EMPTY;
    }
    return item.getOrDefault(TinkerSmeltery.COPPER_CAN_FLUID.get(), SimpleFluidContent.EMPTY);
  }

  @Override
  protected FluidResource getResourceFrom(ItemResource item, int index) {
    SimpleFluidContent content = getContent(item);
    return content.isEmpty() ? FluidResource.EMPTY : FluidResource.of(content.copy());
  }

  @Override
  protected int getAmountFrom(ItemResource item, int index) {
    return getContent(item).isEmpty() ? 0 : FluidValues.INGOT;
  }

  @Override
  protected ItemResource update(ItemResource item, int index, FluidResource resource, int amount) {
    if (amount == 0) {
      return item.without(TinkerSmeltery.COPPER_CAN_FLUID.get());
    }
    // cans only hold a whole ingot
    if (amount != FluidValues.INGOT) {
      return ItemResource.EMPTY;
    }
    return item.with(TinkerSmeltery.COPPER_CAN_FLUID.get(), SimpleFluidContent.copyOf(resource.toStack(FluidValues.INGOT)));
  }

  @Override
  public boolean isValid(int index, FluidResource resource) {
    return itemAccess.getResource().is(TinkerSmeltery.copperCan);
  }

  @Override
  protected int getCapacity(int index, FluidResource resource) {
    return FluidValues.INGOT;
  }
}
