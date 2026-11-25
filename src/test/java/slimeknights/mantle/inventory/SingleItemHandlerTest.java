package slimeknights.mantle.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.block.entity.MantleBlockEntity;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Mantle's native {@link SingleItemHandler} (M5 6b(ii)): legacy saved data shape, side effects only on commit, capacity and validity */
class SingleItemHandlerTest extends BaseMcTest {
  /** Block entity without a level */
  private static class TestBlockEntity extends MantleBlockEntity {
    TestBlockEntity() {
      super(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
    }

    @Override
    public boolean isValidBlockState(BlockState state) {
      return true;
    }
  }

  /** Handler accepting anything but dirt, recording each change with side effects */
  private static class RecordingHandler extends SingleItemHandler<TestBlockEntity> {
    final List<ItemStack> changes = new ArrayList<>();

    RecordingHandler(int maxStackSize) {
      super(new TestBlockEntity(), maxStackSize);
    }

    @Override
    protected boolean isItemValid(ItemStack stack) {
      return !stack.is(Items.DIRT);
    }

    @Override
    public void setStack(ItemStack newStack) {
      changes.add(newStack.copy());
      super.setStack(newStack);
    }
  }

  @Test
  void writeToNBT_matchesItemStackCodec() {
    RecordingHandler handler = new RecordingHandler(64);
    assertThat(handler.writeToNBT(RegistryAccess.EMPTY)).isEqualTo(new CompoundTag());
    ItemStack stack = new ItemStack(Items.DIAMOND, 5);
    handler.setStack(stack);
    CompoundTag tag = handler.writeToNBT(RegistryAccess.EMPTY);
    assertThat(tag).isEqualTo(ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack).getOrThrow());

    RecordingHandler read = new RecordingHandler(64);
    read.readFromNBT(RegistryAccess.EMPTY, tag);
    assertThat(ItemStack.matches(read.getStack(), stack)).isTrue();
    assertThat(read.changes).isEmpty();
    read.readFromNBT(RegistryAccess.EMPTY, new CompoundTag());
    assertThat(read.getStack().isEmpty()).isTrue();
  }

  @Test
  void insert_aborted_noChangeNoSideEffect() {
    RecordingHandler handler = new RecordingHandler(64);
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(handler.insert(0, ItemResource.of(Items.DIAMOND), 10, tx)).isEqualTo(10);
      assertThat(handler.getAmountAsLong(0)).isEqualTo(10);
    }
    assertThat(handler.getStack().isEmpty()).isTrue();
    assertThat(handler.changes).isEmpty();
  }

  @Test
  void insertExtract_committed_oneSideEffectWithFinalStack() {
    RecordingHandler handler = new RecordingHandler(64);
    handler.setStack(new ItemStack(Items.DIAMOND, 4));
    handler.changes.clear();
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(handler.insert(0, ItemResource.of(Items.DIAMOND), 10, tx)).isEqualTo(10);
      assertThat(handler.extract(0, ItemResource.of(Items.DIAMOND), 3, tx)).isEqualTo(3);
      try (Transaction nested = Transaction.open(tx)) {
        assertThat(handler.extract(0, ItemResource.of(Items.DIAMOND), 11, nested)).isEqualTo(11);
      }
      tx.commit();
    }
    assertThat(ItemStack.matches(handler.getStack(), new ItemStack(Items.DIAMOND, 11))).isTrue();
    assertThat(handler.changes).hasSize(1);
    assertThat(ItemStack.matches(handler.changes.getFirst(), new ItemStack(Items.DIAMOND, 11))).isTrue();
  }

  @Test
  void capacity_cappedByMaxStackSize() {
    RecordingHandler handler = new RecordingHandler(64);
    assertThat(handler.getCapacityAsLong(0, ItemResource.EMPTY)).isEqualTo(64);
    assertThat(handler.getCapacityAsLong(0, ItemResource.of(Items.ENDER_PEARL))).isEqualTo(16);
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(handler.insert(0, ItemResource.of(Items.WATER_BUCKET), 5, tx)).isEqualTo(1);
    }
    RecordingHandler single = new RecordingHandler(1);
    assertThat(single.getCapacityAsLong(0, ItemResource.of(Items.DIAMOND))).isEqualTo(1);
  }

  @Test
  void validity_rejectsInvalidAndOtherItems() {
    RecordingHandler handler = new RecordingHandler(64);
    assertThat(handler.isValid(0, ItemResource.of(Items.DIRT))).isFalse();
    assertThat(handler.getCapacityAsLong(0, ItemResource.of(Items.DIRT))).isZero();
    handler.setStack(new ItemStack(Items.DIAMOND, 2));
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(handler.insert(0, ItemResource.of(Items.DIRT), 5, tx)).isZero();
      assertThat(handler.insert(0, ItemResource.of(Items.EMERALD), 5, tx)).isZero();
      assertThat(handler.extract(0, ItemResource.of(Items.EMERALD), 5, tx)).isZero();
      tx.commit();
    }
    assertThat(ItemStack.matches(handler.getStack(), new ItemStack(Items.DIAMOND, 2))).isTrue();
  }
}
