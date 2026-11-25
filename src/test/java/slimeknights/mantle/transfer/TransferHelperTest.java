package slimeknights.mantle.transfer;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
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
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Mantle's {@link TransferHelper} over the NeoForge transfer API: isolated item copies (per container or whole stack, never touching the
 * passed stack) and the transaction helpers. The legacy bridges it used to test were removed in M5 phase 6b(v).
 */
class TransferHelperTest extends BaseMcTest {
  private static final int AMOUNT = 250;
  /** Honey bottles hold a constant 250 water and become glass bottles */
  private static final Function<ItemAccess, ResourceHandler<FluidResource>> HONEY_WATER =
    access -> new ConstantFluidContainerWrapper(new FluidStack(Fluids.WATER, AMOUNT), access, ItemResource.of(Items.GLASS_BOTTLE));

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
  void isolate_simulatedDrain_leavesContainer() {
    ItemStacksResourceHandler slot = TransferHelper.isolate(new ItemStack(Items.HONEY_BOTTLE));
    ResourceHandler<FluidResource> handler = HONEY_WATER.apply(TransferHelper.access(slot));
    assertThat(handler.size()).isEqualTo(1);
    assertThat(handler.getResource(0)).isEqualTo(FluidResource.of(Fluids.WATER));
    assertThat(handler.getAmountAsInt(0)).isEqualTo(AMOUNT);
    // closing without commit is a simulation
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(handler.extract(FluidResource.of(Fluids.WATER), AMOUNT, tx)).isEqualTo(AMOUNT);
    }
    assertThat(TransferHelper.getStack(slot).is(Items.HONEY_BOTTLE)).isTrue();
  }

  @Test
  void isolate_oneItem_isPerContainer() {
    ItemStack stack = new ItemStack(Items.HONEY_BOTTLE, 3);
    ItemStacksResourceHandler slot = TransferHelper.isolate(stack);
    ResourceHandler<FluidResource> handler = HONEY_WATER.apply(TransferHelper.access(slot));
    // one container worth, not the stack total, even when draining "everything"
    assertThat(handler.getAmountAsInt(0)).isEqualTo(AMOUNT);
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(handler.extract(FluidResource.of(Fluids.WATER), Integer.MAX_VALUE, tx)).isEqualTo(AMOUNT);
      tx.commit();
    }
    assertThat(TransferHelper.getStack(slot).is(Items.GLASS_BOTTLE)).isTrue();
    assertThat(TransferHelper.getStack(slot).getCount()).isEqualTo(1);
    assertThat(stack.getCount()).isEqualTo(3);
  }

  @Test
  void isolate_wholeStack_reportsTotals() {
    ItemStack stack = new ItemStack(Items.HONEY_BOTTLE, 3);
    ItemStacksResourceHandler slot = TransferHelper.isolate(stack, stack.getCount());
    ResourceHandler<FluidResource> handler = HONEY_WATER.apply(TransferHelper.access(slot));
    assertThat(handler.getAmountAsInt(0)).isEqualTo(3 * AMOUNT);
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(handler.extract(FluidResource.of(Fluids.WATER), Integer.MAX_VALUE, tx)).isEqualTo(3 * AMOUNT);
      tx.commit();
    }
    assertThat(TransferHelper.getStack(slot).is(Items.GLASS_BOTTLE)).isTrue();
    assertThat(TransferHelper.getStack(slot).getCount()).isEqualTo(3);
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
  void openNestedOrRoot_insideOpenTransaction_nests() {
    FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, 1000);
    FluidResource water = FluidResource.of(Fluids.WATER);
    try (Transaction outer = Transaction.openRoot()) {
      tank.insert(water, 400, outer);
      // a nested change closed without commit only reverts itself
      try (Transaction nested = TransferHelper.openNestedOrRoot()) {
        assertThat(TransferHelper.isTransactionOpen()).isTrue();
        tank.insert(water, 100, nested);
        assertThat(tank.getAmountAsInt(0)).isEqualTo(500);
      }
      assertThat(tank.getAmountAsInt(0)).isEqualTo(400);
      outer.commit();
    }
    assertThat(tank.getAmountAsInt(0)).isEqualTo(400);
    assertThat(TransferHelper.isTransactionOpen()).isFalse();
  }

  @Test
  void insideClosingTransaction_reportsNotOpen_andThrowsDescriptively() {
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
      }
    };
    try (Transaction tx = Transaction.openRoot()) {
      journal.updateSnapshots(tx);
      // closing without commit reverts the journal
    }
    assertThat(seen).hasSize(3);
    assertThat(seen.get(0)).isEqualTo(Transaction.Lifecycle.CLOSING);
    assertThat(seen.get(1)).isEqualTo(false);
    assertThat(seen.get(2)).isInstanceOf(IllegalStateException.class);
    assertThat(((Throwable) seen.get(2)).getMessage()).contains("while a transaction is closing");
    // a root transaction can be opened again afterwards
    try (Transaction tx = TransferHelper.openNestedOrRoot()) {
      assertThat(TransferHelper.isTransactionOpen()).isTrue();
    }
  }
}
