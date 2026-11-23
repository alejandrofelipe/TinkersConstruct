package slimeknights.mantle.transfer;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.item.ConstantFluidContainerWrapper;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/** Null safety and usability of Mantle's legacy bridges over the NeoForge transfer API (M5 transfer step 1, D-T1). */
@SuppressWarnings("removal")  // the legacy interfaces are what the bridges return
class TransferHelperTest extends BaseMcTest {
  @Test
  void bridges_null_returnNull() {
    assertThat(TransferHelper.bridgeFluid(null)).isNull();
    assertThat(TransferHelper.bridgeItem(null)).isNull();
  }

  @Test
  void bridgeFluid_fillsNativeHandler() {
    FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, 1000);
    IFluidHandler bridge = TransferHelper.bridgeFluid(tank);
    assertThat(bridge).isNotNull();
    assertThat(bridge.getTanks()).isEqualTo(1);
    assertThat(bridge.getTankCapacity(0)).isEqualTo(1000);

    assertThat(bridge.fill(new FluidStack(Fluids.WATER, 400), FluidAction.SIMULATE)).isEqualTo(400);
    assertThat(tank.getAmountAsLong(0)).isZero();

    assertThat(bridge.fill(new FluidStack(Fluids.WATER, 400), FluidAction.EXECUTE)).isEqualTo(400);
    assertThat(tank.getResource(0)).isEqualTo(FluidResource.of(Fluids.WATER));
    assertThat(tank.getAmountAsLong(0)).isEqualTo(400);

    FluidStack drained = bridge.drain(1000, FluidAction.EXECUTE);
    assertThat(drained.getFluid()).isEqualTo(Fluids.WATER);
    assertThat(drained.getAmount()).isEqualTo(400);
    assertThat(tank.getAmountAsLong(0)).isZero();
  }

  @Test
  void bridgeItem_insertsIntoNativeHandler() {
    ItemStacksResourceHandler inventory = new ItemStacksResourceHandler(1);
    IItemHandler bridge = TransferHelper.bridgeItem(inventory);
    assertThat(bridge).isNotNull();
    assertThat(bridge.getSlots()).isEqualTo(1);

    assertThat(bridge.insertItem(0, new ItemStack(Items.DIAMOND, 3), true).isEmpty()).isTrue();
    assertThat(inventory.getAmountAsInt(0)).isZero();

    assertThat(bridge.insertItem(0, new ItemStack(Items.DIAMOND, 3), false).isEmpty()).isTrue();
    assertThat(inventory.getResource(0)).isEqualTo(ItemResource.of(Items.DIAMOND));
    assertThat(inventory.getAmountAsInt(0)).isEqualTo(3);
  }

  @Test
  void getFluidHandler_emptyStack_returnsNull() {
    assertThat(TransferHelper.getFluidHandler(ItemStack.EMPTY, access -> fail("lookup must not run for an empty stack"))).isNull();
  }

  @Test
  void getFluidHandler_noHandler_returnsNull() {
    assertThat(TransferHelper.getFluidHandler(new ItemStack(Items.STICK), access -> null)).isNull();
  }

  @Test
  void getFluidHandler_drain_returnsContainerWithoutTouchingStack() {
    ItemStack stack = new ItemStack(Items.HONEY_BOTTLE);
    IFluidHandlerItem handler = TransferHelper.getFluidHandler(stack, access -> new ConstantFluidContainerWrapper(new FluidStack(Fluids.WATER, 250), access, ItemResource.of(Items.GLASS_BOTTLE)));
    assertThat(handler).isNotNull();
    assertThat(handler.getTanks()).isEqualTo(1);
    assertThat(handler.getFluidInTank(0).getFluid()).isEqualTo(Fluids.WATER);
    assertThat(handler.getFluidInTank(0).getAmount()).isEqualTo(250);

    // simulate leaves the container alone
    assertThat(handler.drain(250, FluidAction.SIMULATE).getAmount()).isEqualTo(250);
    assertThat(handler.getContainer().is(Items.HONEY_BOTTLE)).isTrue();

    // execute swaps the container in the bridge, never in the passed stack
    assertThat(handler.drain(250, FluidAction.EXECUTE).getAmount()).isEqualTo(250);
    assertThat(handler.getContainer().is(Items.GLASS_BOTTLE)).isTrue();
    assertThat(handler.getContainer().getCount()).isEqualTo(1);
    assertThat(stack.is(Items.HONEY_BOTTLE)).isTrue();
    assertThat(stack.getCount()).isEqualTo(1);
  }
}
