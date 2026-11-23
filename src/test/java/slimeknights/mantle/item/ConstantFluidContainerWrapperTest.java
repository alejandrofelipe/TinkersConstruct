package slimeknights.mantle.item;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Transaction semantics of Mantle's native constant fluid container handler (M5 transfer step 1, D-T3). */
class ConstantFluidContainerWrapperTest extends BaseMcTest {
  private static final int AMOUNT = 250;
  // instance fields: registries are only bootstrapped by BaseMcTest's @BeforeAll, after this class may be initialized
  private final FluidResource WATER = FluidResource.of(Fluids.WATER);
  private final ItemResource FILLED = ItemResource.of(Items.HONEY_BOTTLE);
  private final ItemResource EMPTY = ItemResource.of(Items.GLASS_BOTTLE);

  /** Inventory holding the containers in slot 0, with a free overflow slot for exchanged containers */
  private final ItemStacksResourceHandler inventory = new ItemStacksResourceHandler(2);

  /** Fills slot 0 with the given number of containers and creates a handler accessing them */
  private ConstantFluidContainerWrapper handler(int containers) {
    inventory.set(0, FILLED, containers);
    return new ConstantFluidContainerWrapper(new FluidStack(Fluids.WATER, AMOUNT), ItemAccess.forHandlerIndex(inventory, 0), EMPTY);
  }

  @Test
  void contents_reportFluidAndTotalAmount() {
    ConstantFluidContainerWrapper handler = handler(2);
    assertThat(handler.size()).isEqualTo(1);
    assertThat(handler.getResource(0)).isEqualTo(WATER);
    assertThat(handler.getAmountAsLong(0)).isEqualTo(2L * AMOUNT);
    assertThat(handler.isValid(0, WATER)).isTrue();
    assertThat(handler.isValid(0, FluidResource.of(Fluids.LAVA))).isFalse();
  }

  @Test
  void insert_neverFills() {
    ConstantFluidContainerWrapper handler = handler(1);
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(handler.insert(0, WATER, AMOUNT, tx)).isZero();
      tx.commit();
    }
    assertThat(inventory.getResource(0)).isEqualTo(FILLED);
  }

  @Test
  void extract_commit_drainsWholeContainer() {
    ConstantFluidContainerWrapper handler = handler(1);
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(handler.extract(0, WATER, 1000, tx)).isEqualTo(AMOUNT);
      tx.commit();
    }
    assertThat(inventory.getResource(0)).isEqualTo(EMPTY);
    assertThat(inventory.getAmountAsInt(0)).isEqualTo(1);
    assertThat(handler.getResource(0)).isEqualTo(FluidResource.EMPTY);
    assertThat(handler.getAmountAsLong(0)).isZero();
  }

  @Test
  void extract_lessThanContainer_drainsNothing() {
    ConstantFluidContainerWrapper handler = handler(1);
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(handler.extract(0, WATER, AMOUNT - 1, tx)).isZero();
      tx.commit();
    }
    assertThat(inventory.getResource(0)).isEqualTo(FILLED);
  }

  @Test
  void extract_wrongFluid_drainsNothing() {
    ConstantFluidContainerWrapper handler = handler(1);
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(handler.extract(0, FluidResource.of(Fluids.LAVA), AMOUNT, tx)).isZero();
      tx.commit();
    }
    assertThat(inventory.getResource(0)).isEqualTo(FILLED);
  }

  @Test
  void extract_abortedTransaction_hasNoEffect() {
    ConstantFluidContainerWrapper handler = handler(1);
    try (Transaction tx = Transaction.openRoot()) {
      // simulate: report what would be drained, then close without committing
      assertThat(handler.extract(0, WATER, AMOUNT, tx)).isEqualTo(AMOUNT);
    }
    assertThat(inventory.getResource(0)).isEqualTo(FILLED);
    assertThat(inventory.getAmountAsInt(0)).isEqualTo(1);
    assertThat(handler.getAmountAsLong(0)).isEqualTo(AMOUNT);
  }

  @Test
  void extract_commitAppliesOnce() {
    ConstantFluidContainerWrapper handler = handler(1);
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(handler.extract(0, WATER, AMOUNT, tx)).isEqualTo(AMOUNT);
      tx.commit();
    }
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(handler.extract(0, WATER, AMOUNT, tx)).isZero();
      tx.commit();
    }
    assertThat(inventory.getResource(0)).isEqualTo(EMPTY);
    assertThat(inventory.getAmountAsInt(0)).isEqualTo(1);
    assertThat(inventory.getResource(1)).isEqualTo(ItemResource.EMPTY);
  }

  @Test
  void extract_nestedCommitInsideAbortedRoot_hasNoEffect() {
    ConstantFluidContainerWrapper handler = handler(1);
    try (Transaction root = Transaction.openRoot()) {
      try (Transaction nested = Transaction.open(root)) {
        assertThat(handler.extract(0, WATER, AMOUNT, nested)).isEqualTo(AMOUNT);
        nested.commit();
      }
      // the nested commit is visible inside the root...
      assertThat(handler.getAmountAsLong(0)).isZero();
    }
    // ...but aborting the root reverts it
    assertThat(inventory.getResource(0)).isEqualTo(FILLED);
    assertThat(handler.getAmountAsLong(0)).isEqualTo(AMOUNT);
  }

  @Test
  void extract_nestedAbortInsideCommittedRoot_hasNoEffect() {
    ConstantFluidContainerWrapper handler = handler(1);
    try (Transaction root = Transaction.openRoot()) {
      try (Transaction nested = Transaction.open(root)) {
        assertThat(handler.extract(0, WATER, AMOUNT, nested)).isEqualTo(AMOUNT);
      }
      root.commit();
    }
    assertThat(inventory.getResource(0)).isEqualTo(FILLED);
    assertThat(handler.getAmountAsLong(0)).isEqualTo(AMOUNT);
  }

  @Test
  void extract_stack_drainsOnlyWholeContainers() {
    ConstantFluidContainerWrapper handler = handler(3);
    try (Transaction tx = Transaction.openRoot()) {
      // 600 covers two whole containers, the remainder is not enough for a third
      assertThat(handler.extract(0, WATER, 2 * AMOUNT + 100, tx)).isEqualTo(2 * AMOUNT);
      tx.commit();
    }
    assertThat(inventory.getResource(0)).isEqualTo(FILLED);
    assertThat(inventory.getAmountAsInt(0)).isEqualTo(1);
    assertThat(inventory.getResource(1)).isEqualTo(EMPTY);
    assertThat(inventory.getAmountAsInt(1)).isEqualTo(2);
  }

  @Test
  void extract_consumedContainer_removesItem() {
    inventory.set(0, FILLED, 1);
    ConstantFluidContainerWrapper handler = new ConstantFluidContainerWrapper(new FluidStack(Fluids.WATER, AMOUNT), ItemAccess.forHandlerIndex(inventory, 0), ItemResource.EMPTY);
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(handler.extract(0, WATER, AMOUNT, tx)).isEqualTo(AMOUNT);
      tx.commit();
    }
    assertThat(inventory.getResource(0)).isEqualTo(ItemResource.EMPTY);
  }
}
