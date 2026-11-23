package slimeknights.mantle.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.test.BaseMcTest;

import javax.annotation.Nullable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The native item handler {@link InventoryBlockEntity} exposes as its capability (M5 transfer step 1, D-T1/D-T3):
 * NeoForge's transactional container wrapper by default.
 */
class InventoryBlockEntityItemHandlerTest extends BaseMcTest {
  // instance field: registries are only bootstrapped by BaseMcTest's @BeforeAll
  private final ItemResource DIAMOND = ItemResource.of(Items.DIAMOND);

  /** Minimal concrete inventory, no level */
  private static class TestInventory extends InventoryBlockEntity {
    TestInventory(int size, int maxStackSize) {
      super(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState(), Component.empty(), false, size, maxStackSize);
    }

    @Override
    public boolean isValidBlockState(BlockState state) {
      return true;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
      return null;
    }
  }

  @Test
  void getItemHandler_wrapsContainer() {
    TestInventory inventory = new TestInventory(3, 64);
    ResourceHandler<ItemResource> handler = inventory.getItemHandler();
    assertThat(handler.size()).isEqualTo(3);
    assertThat(inventory.getItemHandler(Direction.UP)).isSameAs(handler);
    assertThat(inventory.getItemHandler(null)).isSameAs(handler);
  }

  @Test
  void getItemHandler_followsResize() {
    TestInventory inventory = new TestInventory(3, 64);
    assertThat(inventory.getItemHandler().size()).isEqualTo(3);
    inventory.resize(5);
    assertThat(inventory.getItemHandler().size()).isEqualTo(5);
  }

  @Test
  void insert_commit_setsSlot() {
    TestInventory inventory = new TestInventory(3, 64);
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(inventory.getItemHandler().insert(1, DIAMOND, 5, tx)).isEqualTo(5);
      tx.commit();
    }
    assertThat(inventory.getItem(1).is(Items.DIAMOND)).isTrue();
    assertThat(inventory.getItem(1).getCount()).isEqualTo(5);
  }

  @Test
  void insert_respectsStackLimit() {
    TestInventory inventory = new TestInventory(1, 16);
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(inventory.getItemHandler().insert(0, DIAMOND, 64, tx)).isEqualTo(16);
      tx.commit();
    }
    assertThat(inventory.getItem(0).getCount()).isEqualTo(16);
  }

  @Test
  void insert_abortedTransaction_hasNoEffect() {
    TestInventory inventory = new TestInventory(3, 64);
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(inventory.getItemHandler().insert(0, DIAMOND, 5, tx)).isEqualTo(5);
    }
    assertThat(inventory.getItem(0).isEmpty()).isTrue();
  }

  @Test
  void extract_commitAppliesOnce() {
    TestInventory inventory = new TestInventory(1, 64);
    inventory.setItem(0, new ItemStack(Items.DIAMOND, 5));
    try (Transaction tx = Transaction.openRoot()) {
      assertThat(inventory.getItemHandler().extract(0, DIAMOND, 3, tx)).isEqualTo(3);
      tx.commit();
    }
    assertThat(inventory.getItem(0).getCount()).isEqualTo(2);
  }

  @Test
  void extract_nestedCommitInsideAbortedRoot_restoresSlot() {
    TestInventory inventory = new TestInventory(1, 64);
    inventory.setItem(0, new ItemStack(Items.DIAMOND, 5));
    try (Transaction root = Transaction.openRoot()) {
      try (Transaction nested = Transaction.open(root)) {
        assertThat(inventory.getItemHandler().extract(0, DIAMOND, 3, nested)).isEqualTo(3);
        nested.commit();
      }
      assertThat(inventory.getItem(0).getCount()).isEqualTo(2);
    }
    assertThat(inventory.getItem(0).is(Items.DIAMOND)).isTrue();
    assertThat(inventory.getItem(0).getCount()).isEqualTo(5);
  }

  @Test
  void extract_nestedAbortInsideCommittedRoot_restoresSlot() {
    TestInventory inventory = new TestInventory(1, 64);
    inventory.setItem(0, new ItemStack(Items.DIAMOND, 5));
    try (Transaction root = Transaction.openRoot()) {
      try (Transaction nested = Transaction.open(root)) {
        assertThat(inventory.getItemHandler().extract(0, DIAMOND, 3, nested)).isEqualTo(3);
      }
      root.commit();
    }
    assertThat(inventory.getItem(0).getCount()).isEqualTo(5);
  }
}
