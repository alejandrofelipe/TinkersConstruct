package slimeknights.tconstruct.smeltery.item;

import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.transfer.SingleFluidTank;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Native transfers of a stack of tank items move fluid in multiples of the stack size, like the legacy fill and drain (M5 6b(i)) */
class ScaledFluidTankTest extends BaseMcTest {
  private static final FluidResource WATER = FluidResource.of(Fluids.WATER);

  @Test
  void create_scaleOne_isPlainTank() {
    SingleFluidTank tank = ScaledFluidTank.create(1000, 1);
    assertThat(tank).isNotInstanceOf(ScaledFluidTank.class);
    assertThat(tank.getCapacity()).isEqualTo(1000);
  }

  @Test
  void insertAndExtract_truncatedToScale() {
    SingleFluidTank tank = ScaledFluidTank.create(1000, 4);
    assertThat(tank.getCapacity()).isEqualTo(4000);
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(tank.insert(WATER, 250, tx)).isEqualTo(248);
      assertThat(tank.extract(WATER, 103, tx)).isEqualTo(100);
      // less than the scale moves nothing
      assertThat(tank.insert(WATER, 3, tx)).isZero();
      assertThat(tank.extract(WATER, 3, tx)).isZero();
      tx.commit();
    }
    assertThat(tank.getFluidAmount()).isEqualTo(148);
  }

  @Test
  void setFluid_truncatedToScale() {
    SingleFluidTank tank = ScaledFluidTank.create(1000, 3);
    tank.setFluid(new FluidStack(Fluids.WATER, 100));
    assertThat(tank.getFluidAmount()).isEqualTo(99);
  }
}
