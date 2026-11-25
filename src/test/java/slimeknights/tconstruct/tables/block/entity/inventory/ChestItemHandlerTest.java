package slimeknights.tconstruct.tables.block.entity.inventory;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.tables.block.entity.chest.TinkersChestBlockEntity.TinkersChestItemHandler;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

  /**
   * Fixture of what NeoForge's legacy {@code ItemStackHandler.serialize(ValueOutput)} writes (javap on NF 21.10.64): {@code Items}, a list of
   * {@code ItemStackWithSlot} ({@code Slot} as an unsigned byte, then the stack's {@code id} and {@code count}) for the non-empty slots in
   * slot order, and the {@code Size} int. Kept as literal data instead of instantiating the deprecated legacy handler, so the saved data check
   * survives its removal from NeoForge. The native chest's output was verified equal to the legacy handler's own output before the switch (6b(ii)).
   */
  private static CompoundTag legacyHandlerData(int size, Map<Integer, ItemStack> stacks) {
    ListTag items = new ListTag();
    stacks.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
      CompoundTag item = new CompoundTag();
      item.putByte("Slot", (byte) (int) entry.getKey());
      item.putString("id", BuiltInRegistries.ITEM.getKey(entry.getValue().getItem()).toString());
      item.putInt("count", entry.getValue().getCount());
      items.add(item);
    });
    CompoundTag tag = new CompoundTag();
    tag.put("Items", items);
    tag.putInt("Size", size);
    return tag;
  }

  @Test
  void serialize_matchesLegacyItemStackHandler() {
    TinkersChestItemHandler handler = new TinkersChestItemHandler();
    handler.setStackInSlot(10, new ItemStack(Items.IRON_INGOT, 12));
    CompoundTag tag = write(handler);
    assertThat(tag).isEqualTo(legacyHandlerData(64, Map.of(10, new ItemStack(Items.IRON_INGOT, 12))));
    assertThat(tag.getList("Items")).isPresent();
    assertThat(tag.getInt("Size")).contains(64);
  }

  @Test
  void serialize_slotsPastByteRange_matchLegacyAndRoundTrip() {
    // ItemStackWithSlot stores the slot as an unsigned byte, slots 128+ must come out the same as the legacy handler and read back
    TestScalingHandler handler = new TestScalingHandler(200);
    Map<Integer, ItemStack> expected = new HashMap<>();
    for (int slot : new int[] { 10, 127, 128, 150, 199 }) {
      ItemStack stack = new ItemStack(Items.IRON_INGOT, slot % 7 + 1);
      handler.setStackInSlot(slot, stack.copy());
      expected.put(slot, stack.copy());
    }
    CompoundTag tag = write(handler);
    assertThat(tag).isEqualTo(legacyHandlerData(200, expected));

    TestScalingHandler read = new TestScalingHandler(200);
    read.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, tag));
    for (int slot = 0; slot < 200; slot++) {
      assertThat(ItemStack.matches(read.getStackInSlot(slot), handler.getStackInSlot(slot))).as("slot %d", slot).isTrue();
    }
  }

  @Test
  void deserialize_readsLegacyDataKeepsSizeWithoutSizeKey() {
    TestScalingHandler handler = new TestScalingHandler(20);
    handler.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT));
    // the chest block entity saves only Items: the size stays, old contents are cleared
    CompoundTag tag = legacyHandlerData(20, Map.of(7, new ItemStack(Items.DIAMOND, 3)));
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
