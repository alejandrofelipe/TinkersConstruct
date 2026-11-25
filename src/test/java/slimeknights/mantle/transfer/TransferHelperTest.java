package slimeknights.mantle.transfer;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.item.ConstantFluidContainerWrapper;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.fail;

/**
 * Null safety, semantics and transaction guard of Mantle's legacy bridges over the NeoForge transfer API (M5 transfer step 1, D-T1).
 * Runs in the Gradle {@code test} task once Tinkers compiles on 1.21.10 (phase 2E); verified earlier in a scratch harness.
 */
@SuppressWarnings({"removal", "deprecation"})  // the legacy interfaces are what the (deprecated) bridges return
class TransferHelperTest extends BaseMcTest {
  private static final int AMOUNT = 250;
  /** Honey bottles hold a constant 250 water and become glass bottles */
  private static final Function<ItemAccess, ResourceHandler<FluidResource>> HONEY_WATER =
    access -> new ConstantFluidContainerWrapper(new FluidStack(Fluids.WATER, AMOUNT), access, ItemResource.of(Items.GLASS_BOTTLE));

  @Test
  void bridges_null_returnNull() {
    assertThat(TransferHelper.asLegacyFluidHandler(null)).isNull();
    assertThat(TransferHelper.asLegacyItemHandler(null)).isNull();
  }

  @Test
  void asLegacyFluidHandler_fillsNativeHandler() {
    FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, 1000);
    IFluidHandler bridge = TransferHelper.asLegacyFluidHandler(tank);
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
  void asLegacyItemHandler_insertsIntoNativeHandler() {
    ItemStacksResourceHandler inventory = new ItemStacksResourceHandler(1);
    IItemHandler bridge = TransferHelper.asLegacyItemHandler(inventory);
    assertThat(bridge).isNotNull();
    assertThat(bridge.getSlots()).isEqualTo(1);

    assertThat(bridge.insertItem(0, new ItemStack(Items.DIAMOND, 3), true).isEmpty()).isTrue();
    assertThat(inventory.getAmountAsInt(0)).isZero();

    assertThat(bridge.insertItem(0, new ItemStack(Items.DIAMOND, 3), false).isEmpty()).isTrue();
    assertThat(inventory.getResource(0)).isEqualTo(ItemResource.of(Items.DIAMOND));
    assertThat(inventory.getAmountAsInt(0)).isEqualTo(3);
  }

  @Test
  void getLegacyFluidHandler_emptyStack_returnsNull() {
    assertThat(TransferHelper.getLegacyFluidHandler(ItemStack.EMPTY, access -> fail("lookup must not run for an empty stack"))).isNull();
    assertThat(TransferHelper.getLegacyFluidHandlerForWholeStack(ItemStack.EMPTY, access -> fail("lookup must not run for an empty stack"))).isNull();
  }

  @Test
  void getLegacyFluidHandler_noHandler_returnsNull() {
    assertThat(TransferHelper.getLegacyFluidHandler(new ItemStack(Items.STICK), access -> null)).isNull();
  }

  @Test
  void getLegacyFluidHandler_drain_returnsContainerWithoutTouchingStack() {
    ItemStack stack = new ItemStack(Items.HONEY_BOTTLE);
    IFluidHandlerItem handler = TransferHelper.getLegacyFluidHandler(stack, HONEY_WATER);
    assertThat(handler).isNotNull();
    assertThat(handler.getTanks()).isEqualTo(1);
    assertThat(handler.getFluidInTank(0).getFluid()).isEqualTo(Fluids.WATER);
    assertThat(handler.getFluidInTank(0).getAmount()).isEqualTo(AMOUNT);

    // simulate leaves the container alone
    assertThat(handler.drain(AMOUNT, FluidAction.SIMULATE).getAmount()).isEqualTo(AMOUNT);
    assertThat(handler.getContainer().is(Items.HONEY_BOTTLE)).isTrue();

    // execute swaps the container in the bridge, never in the passed stack
    assertThat(handler.drain(AMOUNT, FluidAction.EXECUTE).getAmount()).isEqualTo(AMOUNT);
    assertThat(handler.getContainer().is(Items.GLASS_BOTTLE)).isTrue();
    assertThat(handler.getContainer().getCount()).isEqualTo(1);
    assertThat(stack.is(Items.HONEY_BOTTLE)).isTrue();
    assertThat(stack.getCount()).isEqualTo(1);
  }

  @Test
  void getLegacyFluidHandler_stack_isPerContainer() {
    ItemStack stack = new ItemStack(Items.HONEY_BOTTLE, 3);
    IFluidHandlerItem handler = TransferHelper.getLegacyFluidHandler(stack, HONEY_WATER);
    assertThat(handler).isNotNull();
    // one container worth, not the stack total
    assertThat(handler.getFluidInTank(0).getAmount()).isEqualTo(AMOUNT);
    assertThat(handler.drain(AMOUNT, FluidAction.EXECUTE).getAmount()).isEqualTo(AMOUNT);
    assertThat(handler.getContainer().is(Items.GLASS_BOTTLE)).isTrue();
    assertThat(handler.getContainer().getCount()).isEqualTo(1);

    // draining "everything" still drains a single container
    IFluidHandlerItem fresh = TransferHelper.getLegacyFluidHandler(stack, HONEY_WATER);
    assertThat(fresh).isNotNull();
    assertThat(fresh.drain(Integer.MAX_VALUE, FluidAction.EXECUTE).getAmount()).isEqualTo(AMOUNT);
    assertThat(fresh.getContainer().is(Items.GLASS_BOTTLE)).isTrue();
    assertThat(fresh.getContainer().getCount()).isEqualTo(1);

    assertThat(stack.is(Items.HONEY_BOTTLE)).isTrue();
    assertThat(stack.getCount()).isEqualTo(3);
  }

  @Test
  void getLegacyFluidHandlerForWholeStack_reportsTotals() {
    ItemStack stack = new ItemStack(Items.HONEY_BOTTLE, 3);
    IFluidHandlerItem handler = TransferHelper.getLegacyFluidHandlerForWholeStack(stack, HONEY_WATER);
    assertThat(handler).isNotNull();
    assertThat(handler.getFluidInTank(0).getAmount()).isEqualTo(3 * AMOUNT);
    assertThat(handler.drain(Integer.MAX_VALUE, FluidAction.EXECUTE).getAmount()).isEqualTo(3 * AMOUNT);
    assertThat(handler.getContainer().is(Items.GLASS_BOTTLE)).isTrue();
    assertThat(handler.getContainer().getCount()).isEqualTo(3);
    assertThat(stack.is(Items.HONEY_BOTTLE)).isTrue();
    assertThat(stack.getCount()).isEqualTo(3);
  }

  @Test
  void isolate_copiesWithoutTouchingTheStack() {
    ItemStack stack = new ItemStack(Items.HONEY_BOTTLE, 3);
    ItemStacksResourceHandler one = TransferHelper.isolate(stack);
    assertThat(TransferHelper.getStack(one).is(Items.HONEY_BOTTLE)).isTrue();
    assertThat(TransferHelper.getStack(one).getCount()).isEqualTo(1);
    ItemStacksResourceHandler two = TransferHelper.isolate(stack, 2);
    assertThat(TransferHelper.getStack(two).getCount()).isEqualTo(2);
    // exchanging through the access changes the copy only
    ResourceHandler<FluidResource> handler = HONEY_WATER.apply(TransferHelper.access(one));
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(handler.extract(FluidResource.of(Fluids.WATER), AMOUNT, tx)).isEqualTo(AMOUNT);
      tx.commit();
    }
    assertThat(TransferHelper.getStack(one).is(Items.GLASS_BOTTLE)).isTrue();
    assertThat(stack.is(Items.HONEY_BOTTLE)).isTrue();
    assertThat(stack.getCount()).isEqualTo(3);
  }

  @Test
  void isolate_emptyStack_hasNoHandler() {
    ItemStacksResourceHandler slot = TransferHelper.isolate(ItemStack.EMPTY);
    assertThat(TransferHelper.getStack(slot).isEmpty()).isTrue();
    assertThat(TransferHelper.getFluidResourceHandler(slot)).isNull();
    // no capability registered for sticks
    assertThat(TransferHelper.getFluidResourceHandler(TransferHelper.isolate(new ItemStack(Items.STICK)))).isNull();
  }
  @Test
  void legacyLookups_insideOpenTransaction_throw() {
    ItemStack stack = new ItemStack(Items.HONEY_BOTTLE);
    FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, 1000);
    try (Transaction tx = Transaction.openRoot()) {
      assertThatThrownBy(() -> TransferHelper.getLegacyFluidHandler(stack, HONEY_WATER)).isInstanceOf(IllegalStateException.class);
      assertThatThrownBy(() -> TransferHelper.getLegacyFluidHandlerForWholeStack(stack, HONEY_WATER)).isInstanceOf(IllegalStateException.class);
      assertThatThrownBy(() -> TransferHelper.asLegacyFluidHandler(tank)).isInstanceOf(IllegalStateException.class);
      assertThatThrownBy(() -> TransferHelper.asLegacyItemHandler(new ItemStacksResourceHandler(1))).isInstanceOf(IllegalStateException.class);
    }
    // fine again once the transaction is closed
    assertThat(TransferHelper.asLegacyFluidHandler(tank)).isNotNull();
  }

  @Test
  void insideClosingTransaction_reportsNotOpen_andThrowsDescriptively() {
    FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, 1000);
    List<Object> seen = new ArrayList<>();
    // a journal's revert callback runs while its transaction is closing
    SnapshotJournal<Integer> journal = new SnapshotJournal<>() {
      @Override
      protected Integer createSnapshot() {
        return 0;
      }

      @Override
      protected void revertToSnapshot(Integer snapshot) {
        seen.add(Transaction.getLifecycle());
        seen.add(TransferHelper.isTransactionOpen());
        seen.add(catchThrowable(TransferHelper::openNestedOrRoot));
        seen.add(catchThrowable(() -> TransferHelper.asLegacyFluidHandler(tank)));
      }
    };
    try (Transaction tx = Transaction.openRoot()) {
      journal.updateSnapshots(tx);
      // closing without commit reverts the journal
    }
    assertThat(seen).hasSize(4);
    assertThat(seen.get(0)).isEqualTo(Transaction.Lifecycle.CLOSING);
    assertThat(seen.get(1)).isEqualTo(false);
    for (Object thrown : seen.subList(2, 4)) {
      assertThat(thrown).isInstanceOf(IllegalStateException.class);
      assertThat(((Throwable) thrown).getMessage()).contains("while a transaction is closing");
    }
    // a root transaction can be opened again afterwards
    try (Transaction tx = TransferHelper.openNestedOrRoot()) {
      assertThat(TransferHelper.isTransactionOpen()).isTrue();
    }
  }
}
