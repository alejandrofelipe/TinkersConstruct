package slimeknights.mantle.transfer;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Saved data parity with NeoForge's legacy {@code FluidTank} and the transaction semantics of Mantle's native single tank (M5 6b(i)) */
class SingleFluidTankTest extends BaseMcTest {
  private static final FluidResource WATER = FluidResource.of(Fluids.WATER);

  /** Tank counting its change notifications */
  private static class CountingTank extends SingleFluidTank {
    int changes = 0;

    CountingTank(int capacity) {
      super(capacity);
    }

    @Override
    protected void onContentsChanged() {
      changes++;
    }
  }

  private static CompoundTag write(SingleFluidTank tank) {
    TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
    tank.serialize(output);
    return output.buildResult();
  }

  /**
   * Fixture of what NeoForge's legacy {@code FluidTank.serialize(ValueOutput)} writes for a non-empty tank (javap on NF 21.10.64: the stack
   * under {@code Fluid} through {@code FluidStack.CODEC}, i.e. {@code {id, amount}} without components; nothing at all when empty). Kept as
   * literal data instead of instantiating the deprecated legacy tank, so the saved data check survives its removal from NeoForge. The native
   * tank's output was verified equal to the legacy tank's own output before the switch (6b(i)).
   */
  private static CompoundTag legacyTankData(String fluid, int amount) {
    CompoundTag stack = new CompoundTag();
    stack.putString("id", fluid);
    stack.putInt("amount", amount);
    CompoundTag tag = new CompoundTag();
    tag.put("Fluid", stack);
    return tag;
  }

  @Test
  void serialize_matchesLegacyTank() {
    SingleFluidTank tank = new SingleFluidTank(1000);
    tank.setFluid(new FluidStack(Fluids.LAVA, 250));
    CompoundTag tag = write(tank);
    assertThat(tag).isEqualTo(legacyTankData("minecraft:lava", 250));
    assertThat(tag.getCompound(SingleFluidTank.TAG_FLUID)).isPresent();
  }

  @Test
  void serialize_empty_writesNothing() {
    // the legacy tank wrote nothing for an empty tank
    CompoundTag tag = write(new SingleFluidTank(1000));
    assertThat(tag.isEmpty()).isTrue();
  }

  @Test
  void deserialize_readsLegacyTankAndClearsWhenMissing() {
    SingleFluidTank tank = new SingleFluidTank(1000);
    tank.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, legacyTankData("minecraft:water", 400)));
    assertThat(tank.getFluid().getFluid()).isEqualTo(Fluids.WATER);
    assertThat(tank.getFluidAmount()).isEqualTo(400);

    tank.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, new CompoundTag()));
    assertThat(tank.isEmpty()).isTrue();
  }

  @Test
  void insert_abortedTransaction_changesNothing() {
    CountingTank tank = new CountingTank(1000);
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(tank.insert(WATER, 600, tx)).isEqualTo(600);
      assertThat(tank.getFluidAmount()).isEqualTo(600);
    }
    assertThat(tank.isEmpty()).isTrue();
    assertThat(tank.changes).isZero();
  }

  @Test
  void insertAndExtract_commit_notifiesOnce() {
    CountingTank tank = new CountingTank(1000);
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(tank.insert(WATER, 1500, tx)).isEqualTo(1000);
      assertThat(tank.extract(WATER, 300, tx)).isEqualTo(300);
      tx.commit();
    }
    assertThat(tank.getFluidAmount()).isEqualTo(700);
    assertThat(tank.changes).isEqualTo(1);
  }

  @Test
  void commit_withoutNetChange_doesNotNotify() {
    CountingTank tank = new CountingTank(1000);
    tank.setFluid(new FluidStack(Fluids.WATER, 500));
    try (Transaction tx = Transaction.openRoot()) {
      tank.insert(WATER, 100, tx);
      tank.extract(WATER, 100, tx);
      tx.commit();
    }
    assertThat(tank.getFluidAmount()).isEqualTo(500);
    assertThat(tank.changes).isZero();
  }

  @Test
  void nestedAbort_keepsOuterChanges() {
    CountingTank tank = new CountingTank(1000);
    try (Transaction tx = Transaction.openRoot()) {
      tank.insert(WATER, 200, tx);
      try (Transaction nested = Transaction.open(tx)) {
        tank.insert(WATER, 300, nested);
      }
      tx.commit();
    }
    assertThat(tank.getFluidAmount()).isEqualTo(200);
    assertThat(tank.changes).isEqualTo(1);
  }

  @Test
  void insert_rejectsOtherFluidAndInvalid() {
    SingleFluidTank tank = new SingleFluidTank(1000, fluid -> fluid.getFluid() != Fluids.LAVA);
    tank.setFluid(new FluidStack(Fluids.WATER, 100));
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(tank.insert(FluidResource.of(Fluids.LAVA), 100, tx)).isZero();
      assertThat(tank.extract(FluidResource.of(Fluids.LAVA), 100, tx)).isZero();
      tank.setFluid(FluidStack.EMPTY);
      assertThat(tank.insert(FluidResource.of(Fluids.LAVA), 100, tx)).isZero();
      assertThat(tank.getCapacityAsLong(0, FluidResource.of(Fluids.LAVA))).isZero();
      assertThat(tank.getCapacityAsLong(0, FluidResource.EMPTY)).isEqualTo(1000);
    }
  }

  @Test
  void extract_toEmpty_usesEmptyStack() {
    SingleFluidTank tank = new SingleFluidTank(1000);
    tank.setFluid(new FluidStack(Fluids.WATER, 100));
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(tank.extract(WATER, 500, tx)).isEqualTo(100);
      tx.commit();
    }
    assertThat(tank.getFluid()).isSameAs(FluidStack.EMPTY);
  }
}
