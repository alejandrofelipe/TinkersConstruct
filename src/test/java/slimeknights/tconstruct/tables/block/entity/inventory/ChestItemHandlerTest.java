package slimeknights.tconstruct.tables.block.entity.inventory;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.tables.block.entity.chest.TinkersChestBlockEntity.TinkersChestItemHandler;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Native chest inventories (M5 6b(ii)): saved data parity with NeoForge's legacy {@code ItemStackHandler}, commit-only side effects, visual size */
class ChestItemHandlerTest extends BaseMcTest {
  /** Scaling chest taking anything, 8 per slot, recording changed slots */
  private static class TestScalingHandler extends ScalingChestItemHandler {
    final List<Integer> changed = new ArrayList<>();

    TestScalingHandler(int size) {
      super(size);
    }

    @Override
    public int getSlotLimit(int slot) {
      return 8;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
      return !stack.is(Items.DIRT);
    }

    @Override
    protected void onContentsChanged(int slot) {
      changed.add(slot);
    }
  }

  private static CompoundTag write(ChestItemHandler handler) {
    TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
    handler.serialize(output);
    return output.buildResult();
  }

  @SuppressWarnings("removal")  // the legacy handler is the reference for the saved data
  private static CompoundTag writeLegacy(int size, int slot, ItemStack stack) {
    ItemStackHandler legacy = new ItemStackHandler(size);
    legacy.setStackInSlot(slot, stack);
    TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
    legacy.serialize(output);
    return output.buildResult();
  }

  @Test
  void serialize_matchesLegacyItemStackHandler() {
    TinkersChestItemHandler handler = new TinkersChestItemHandler();
    handler.setStackInSlot(10, new ItemStack(Items.IRON_INGOT, 12));
    CompoundTag tag = write(handler);
    assertThat(tag).isEqualTo(writeLegacy(64, 10, new ItemStack(Items.IRON_INGOT, 12)));
    assertThat(tag.getList("Items")).isPresent();
    assertThat(tag.getInt("Size")).contains(64);
  }

  @Test
  void deserialize_readsLegacyDataKeepsSizeWithoutSizeKey() {
    TestScalingHandler handler = new TestScalingHandler(20);
    handler.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT));
    // the chest block entity saves only Items: the size stays, old contents are cleared
    CompoundTag tag = writeLegacy(20, 7, new ItemStack(Items.DIAMOND, 3));
    tag.remove("Size");
    handler.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, tag));
    assertThat(handler.size()).isEqualTo(20);
    assertThat(handler.getStackInSlot(0).isEmpty()).isTrue();
    assertThat(ItemStack.matches(handler.getStackInSlot(7), new ItemStack(Items.DIAMOND, 3))).isTrue();
    assertThat(handler.getVisualSize()).isEqualTo(9);
  }

  @Test
  void insert_aborted_noSideEffect() {
    TestScalingHandler handler = new TestScalingHandler(10);
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(handler.insert(4, ItemResource.of(Items.DIAMOND), 20, tx)).isEqualTo(8);
    }
    assertThat(handler.getStackInSlot(4).isEmpty()).isTrue();
    assertThat(handler.changed).isEmpty();
    assertThat(handler.getVisualSize()).isEqualTo(1);
  }

  @Test
  void insert_committed_replaysOncePerSlotAndUpdatesVisualSize() {
    TestScalingHandler handler = new TestScalingHandler(10);
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(handler.insert(4, ItemResource.of(Items.DIAMOND), 5, tx)).isEqualTo(5);
      assertThat(handler.insert(4, ItemResource.of(Items.DIAMOND), 5, tx)).isEqualTo(3);
      assertThat(handler.insert(5, ItemResource.of(Items.DIRT), 5, tx)).isZero();
      tx.commit();
    }
    assertThat(ItemStack.matches(handler.getStackInSlot(4), new ItemStack(Items.DIAMOND, 8))).isTrue();
    assertThat(handler.changed).containsExactly(4);
    assertThat(handler.getVisualSize()).isEqualTo(6);

    // extracting everything shrinks the visual size back
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(handler.extract(4, ItemResource.of(Items.DIAMOND), 64, tx)).isEqualTo(8);
      tx.commit();
    }
    assertThat(handler.getStackInSlot(4).isEmpty()).isTrue();
    assertThat(handler.getVisualSize()).isEqualTo(1);
  }
}
