package slimeknights.mantle.inventory;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Mantle's {@link BaseContainerMenu} stack moving (shift click): the refill pass must respect {@link Slot#mayPlace} like the empty slot pass */
class BaseContainerMenuTest extends BaseMcTest {
  /** Menu with a slot refusing every placement (like a result slot) followed by a regular slot */
  private static class TestMenu extends BaseContainerMenu<BlockEntity> {
    final SimpleContainer container = new SimpleContainer(2);

    TestMenu() {
      super(null, 0, null, null);
      addSlot(new Slot(container, 0, 0, 0) {
        @Override
        public boolean mayPlace(ItemStack stack) {
          return false;
        }
      });
      addSlot(new Slot(container, 1, 0, 0));
    }

    boolean move(ItemStack stack) {
      return moveItemStackTo(stack, 0, slots.size(), false);
    }
  }

  @Test
  void refill_skipsSlotRefusingPlacement() {
    TestMenu menu = new TestMenu();
    menu.container.setItem(0, new ItemStack(Items.APPLE, 5));
    menu.container.setItem(1, new ItemStack(Items.APPLE, 5));
    ItemStack stack = new ItemStack(Items.APPLE, 10);

    assertThat(menu.move(stack)).isTrue();
    assertThat(menu.container.getItem(0).getCount()).as("refusing slot").isEqualTo(5);
    assertThat(menu.container.getItem(1).getCount()).as("regular slot").isEqualTo(15);
    assertThat(stack.isEmpty()).isTrue();
  }

  @Test
  void refill_onlyRefusingSlotHoldsItem_keepsSourceForEmptyPass() {
    TestMenu menu = new TestMenu();
    menu.container.setItem(0, new ItemStack(Items.APPLE, 5));
    ItemStack stack = new ItemStack(Items.APPLE, 10);

    assertThat(menu.move(stack)).isTrue();
    // the refill pass skipped the refusing slot, the empty slot pass put everything in the regular slot
    assertThat(menu.container.getItem(0).getCount()).as("refusing slot").isEqualTo(5);
    assertThat(menu.container.getItem(1).getCount()).as("regular slot").isEqualTo(10);
    assertThat(stack.isEmpty()).isTrue();
  }

  @Test
  void refill_noSlotAccepts_sourceUnchanged() {
    TestMenu menu = new TestMenu();
    menu.container.setItem(0, new ItemStack(Items.APPLE, 5));
    menu.container.setItem(1, new ItemStack(Items.DIAMOND, 64));
    ItemStack stack = new ItemStack(Items.APPLE, 10);

    assertThat(menu.move(stack)).isFalse();
    assertThat(menu.container.getItem(0).getCount()).as("refusing slot").isEqualTo(5);
    assertThat(stack.getCount()).isEqualTo(10);
  }
}
