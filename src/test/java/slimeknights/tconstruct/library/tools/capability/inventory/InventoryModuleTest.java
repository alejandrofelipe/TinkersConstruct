package slimeknights.tconstruct.library.tools.capability.inventory;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.utils.TagUtil;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Regression tests for stacks stored in tool modifier inventories surviving a write → read round trip. */
class InventoryModuleTest extends BaseMcTest {
  /** Reads the slot tag the same way {@link InventoryModule} does (no server running, so it falls back to empty registries) */
  private static ItemStack read(CompoundTag tag) {
    return TagUtil.parseOptionalItem(RegistryAccess.EMPTY, tag);
  }

  @Test
  void writeStack_newTag_roundTrips() {
    ItemStack stack = new ItemStack(Items.DIAMOND, 5);
    CompoundTag tag = InventoryModule.writeStack(stack, 3, new CompoundTag());

    assertThat(tag.getIntOr(InventoryModule.TAG_SLOT, -1)).isEqualTo(3);
    ItemStack read = read(tag);
    assertThat(read.isEmpty()).isFalse();
    assertThat(ItemStack.matches(read, stack)).isTrue();
  }

  @Test
  void writeStack_keepsComponents() {
    ItemStack stack = new ItemStack(Items.IRON_PICKAXE);
    stack.set(DataComponents.DAMAGE, 7);
    CompoundTag tag = InventoryModule.writeStack(stack, 0, new CompoundTag());

    ItemStack read = read(tag);
    assertThat(read.getItem()).isEqualTo(Items.IRON_PICKAXE);
    assertThat(read.getDamageValue()).isEqualTo(7);
  }

  @Test
  void writeStack_existingTag_updatesStackAndKeepsExtraData() {
    // SmeltingModule rewrites the shrunk input into the same slot tag, which also holds its cook time
    CompoundTag tag = InventoryModule.writeStack(new ItemStack(Items.IRON_ORE, 10), 1, new CompoundTag());
    tag.putInt("time", 20);
    InventoryModule.writeStack(new ItemStack(Items.IRON_ORE, 9), 1, tag);

    ItemStack read = read(tag);
    assertThat(read.getItem()).isEqualTo(Items.IRON_ORE);
    assertThat(read.getCount()).isEqualTo(9);
    assertThat(tag.getIntOr("time", -1)).isEqualTo(20);
    assertThat(tag.getIntOr(InventoryModule.TAG_SLOT, -1)).isEqualTo(1);
  }

  @Test
  void writeStack_clearedTag_replacesStack() {
    // setStack clears an existing slot tag in place before rewriting it
    CompoundTag tag = InventoryModule.writeStack(new ItemStack(Items.DIAMOND, 5), 2, new CompoundTag());
    tag.keySet().clear();
    InventoryModule.writeStack(new ItemStack(Items.GOLD_INGOT, 4), 2, tag);

    ItemStack read = read(tag);
    assertThat(read.getItem()).isEqualTo(Items.GOLD_INGOT);
    assertThat(read.getCount()).isEqualTo(4);
    assertThat(tag.getIntOr(InventoryModule.TAG_SLOT, -1)).isEqualTo(2);
  }
}
