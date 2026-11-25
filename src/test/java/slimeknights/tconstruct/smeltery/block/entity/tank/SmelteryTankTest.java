package slimeknights.tconstruct.smeltery.block.entity.tank;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.block.entity.MantleBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.tank.ISmelteryTankHandler.FluidChange;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Notifications of the native smeltery tank (M5 6b(i) review fixes): no-op commits notify nothing, reverts of a layout change run the list listeners */
class SmelteryTankTest extends BaseMcTest {
  private static final FluidResource WATER = FluidResource.of(Fluids.WATER);
  private static final FluidResource LAVA = FluidResource.of(Fluids.LAVA);

  /** Smeltery stand in, only mocked */
  private static abstract class Parent extends MantleBlockEntity implements ISmelteryTankHandler {
    Parent() {
      super(null, BlockPos.ZERO, null);
    }
  }

  /** Listener owner, kept strongly referenced by the test as the listener list is weak */
  private static class Counter {
    final AtomicInteger runs = new AtomicInteger();
  }

  private static SmelteryTank<Parent> tank(Parent parent) {
    SmelteryTank<Parent> tank = new SmelteryTank<>(parent);
    tank.setCapacity(1000);
    return tank;
  }

  @Test
  void commit_noNetChange_notifiesNothing() {
    Parent parent = mock(Parent.class);
    SmelteryTank<Parent> tank = tank(parent);
    Counter counter = new Counter();
    tank.addTankListListener(counter, c -> c.runs.incrementAndGet());
    try (Transaction tx = Transaction.openRoot()) {
      tank.insert(WATER, 100, tx);
      tx.commit();
    }
    verify(parent, times(1)).notifyFluidsChanged(eq(FluidChange.ADDED), argThat(fluid -> FluidStack.matches(fluid, new FluidStack(Fluids.WATER, 100))));
    assertThat(counter.runs.get()).isEqualTo(1);

    // insert and extract the same amount: committed, but nothing changed
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(tank.insert(WATER, 50, tx)).isEqualTo(50);
      assertThat(tank.extract(WATER, 50, tx)).isEqualTo(50);
      tx.commit();
    }
    verify(parent, times(1)).notifyFluidsChanged(any(), any());
    assertThat(counter.runs.get()).isEqualTo(1);
    assertThat(tank.getFluids()).hasSize(1);
    assertThat(tank.getContained()).isEqualTo(100);
  }

  @Test
  void abort_layoutChange_runsListListeners() {
    Parent parent = mock(Parent.class);
    SmelteryTank<Parent> tank = tank(parent);
    try (Transaction tx = Transaction.openRoot()) {
      tank.insert(WATER, 100, tx);
      tank.insert(LAVA, 100, tx);
      tx.commit();
    }
    Counter counter = new Counter();
    tank.addTankListListener(counter, c -> c.runs.incrementAndGet());

    // amounts only: same layout, no listener on revert
    try (Transaction tx = Transaction.openRoot()) {
      tank.extract(WATER, 10, tx);
    }
    assertThat(counter.runs.get()).isZero();

    // removing a fluid changes the indices, the revert puts them back so cached mappings must be refreshed
    try (Transaction tx = Transaction.openRoot()) {
      tank.extract(WATER, 100, tx);
      assertThat(tank.getFluids()).hasSize(1);
    }
    assertThat(counter.runs.get()).isEqualTo(1);
    assertThat(tank.getFluids()).hasSize(2);
    assertThat(FluidStack.matches(tank.getFluidInTank(0), new FluidStack(Fluids.WATER, 100))).isTrue();
    // reverts restore, they never notify the parent; only the setup commit did
    verify(parent, times(1)).notifyFluidsChanged(any(), any());
  }
}
