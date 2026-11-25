package slimeknights.tconstruct.library.transfer;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;

/** The transactional setter {@link ModifiableItemHandler#transactional} uses for inventories exposing only a native handler (M5 6b(ii)) */
class ModifiableItemHandlerTest extends BaseMcTest {
  /** Two slots, no dirt, at most 16 per slot */
  private static ItemStacksResourceHandler inventory() {
    return new ItemStacksResourceHandler(2) {
      @Override
      public boolean isValid(int index, ItemResource resource) {
        return !resource.is(Items.DIRT);
      }

      @Override
      protected int getCapacity(int index, ItemResource resource) {
        return 16;
      }
    };
  }

  private static void assertStack(ItemStacksResourceHandler inventory, int slot, ItemStack expected) {
    ItemStack actual = inventory.copyToList().get(slot);
    assertThat(ItemStack.matches(actual, expected)).as("slot %d: expected %s, got %s", slot, expected, actual).isTrue();
  }

  @Test
  void setStack_sameItem_movesDifference() {
    ItemStacksResourceHandler inventory = inventory();
    ModifiableItemHandler handler = ModifiableItemHandler.transactional(inventory);
    handler.setStack(0, new ItemStack(Items.DIAMOND, 5));
    assertStack(inventory, 0, new ItemStack(Items.DIAMOND, 5));
    handler.setStack(0, new ItemStack(Items.DIAMOND, 9));
    assertStack(inventory, 0, new ItemStack(Items.DIAMOND, 9));
    handler.setStack(0, new ItemStack(Items.DIAMOND, 2));
    assertStack(inventory, 0, new ItemStack(Items.DIAMOND, 2));
    assertThat(ItemStack.matches(handler.getStack(0), new ItemStack(Items.DIAMOND, 2))).isTrue();
  }

  @Test
  void setStack_otherItemAndEmpty_replace() {
    ItemStacksResourceHandler inventory = inventory();
    ModifiableItemHandler handler = ModifiableItemHandler.transactional(inventory);
    handler.setStack(1, new ItemStack(Items.DIAMOND, 5));
    handler.setStack(1, new ItemStack(Items.EMERALD, 3));
    assertStack(inventory, 1, new ItemStack(Items.EMERALD, 3));
    handler.setStack(1, ItemStack.EMPTY);
    assertStack(inventory, 1, ItemStack.EMPTY);
  }

  @Test
  void setStack_rejected_leavesSlotUnchanged() {
    ItemStacksResourceHandler inventory = inventory();
    ModifiableItemHandler handler = ModifiableItemHandler.transactional(inventory);
    handler.setStack(0, new ItemStack(Items.DIAMOND, 5));
    // invalid item: the old stack must not be lost
    handler.setStack(0, new ItemStack(Items.DIRT, 1));
    assertStack(inventory, 0, new ItemStack(Items.DIAMOND, 5));
    // over capacity: all or nothing
    handler.setStack(0, new ItemStack(Items.DIAMOND, 20));
    assertStack(inventory, 0, new ItemStack(Items.DIAMOND, 5));
    // out of range indices are ignored
    handler.setStack(5, new ItemStack(Items.DIAMOND, 1));
    assertThat(handler.getStack(5).isEmpty()).isTrue();
  }
}
