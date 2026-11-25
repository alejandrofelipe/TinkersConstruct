package slimeknights.tconstruct.smeltery.block.entity.tank;

import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.smeltery.block.entity.ChannelBlockEntity;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** The channel tank journals its lock with the fluid (M5 6b(i) review fixes) */
class ChannelTankTest extends BaseMcTest {
  private static final FluidResource WATER = FluidResource.of(Fluids.WATER);

  @Test
  void abort_revertsFluidAndLock() {
    ChannelBlockEntity parent = mock(ChannelBlockEntity.class);
    ChannelTank tank = new ChannelTank(1000, parent);
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(tank.insert(WATER, 300, tx)).isEqualTo(300);
      assertThat(tank.getMaxUsable()).isZero();
    }
    assertThat(tank.getFluidAmount()).isZero();
    assertThat(tank.getMaxUsable()).isZero();
    verify(parent, never()).sendFluidUpdate();

    // a committed insert keeps its lock until freed, an aborted one on top reverts only its own part
    try (Transaction tx = Transaction.openRoot()) {
      tank.insert(WATER, 200, tx);
      tx.commit();
    }
    verify(parent, times(1)).sendFluidUpdate();
    assertThat(tank.getMaxUsable()).isZero();
    tank.freeFluid();
    assertThat(tank.getMaxUsable()).isEqualTo(200);
    try (Transaction tx = Transaction.openRoot()) {
      tank.insert(WATER, 100, tx);
      try (Transaction nested = Transaction.open(tx)) {
        tank.insert(WATER, 50, nested);
      }
      assertThat(tank.getMaxUsable()).isEqualTo(200);
    }
    assertThat(tank.getFluidAmount()).isEqualTo(200);
    assertThat(tank.getMaxUsable()).isEqualTo(200);
  }
}
