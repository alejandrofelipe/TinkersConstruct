package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import slimeknights.mantle.inventory.WrapperSlot;
import slimeknights.tconstruct.library.transfer.ModifiableItemHandler;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.entity.component.DuctBlockEntity;
import slimeknights.tconstruct.smeltery.menu.SingleItemContainerMenu;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tables.block.entity.chest.TinkersChestBlockEntity;
import slimeknights.tconstruct.tables.block.entity.table.CraftingStationBlockEntity;
import slimeknights.tconstruct.tables.menu.CraftingStationContainerMenu;
import slimeknights.tconstruct.tables.menu.TinkerChestContainerMenu;
import slimeknights.tconstruct.tables.menu.module.SideInventoryContainer;

/**
 * Gametests for menu slots over native item handlers (M5 transfer phase 6b(ii)): NeoForge's {@code ResourceHandlerSlot} through
 * {@link ModifiableItemHandler}, for side inventories (an inventory exposing only a native handler, a vanilla container), single item
 * blocks and chests. Slots are driven through {@link AbstractContainerMenu#clicked} like a player would.
 */
public class MenuSlotGameTests {
  private static final BlockPos POS = new BlockPos(2, 1, 2);

  /** Places a block at the relative position and returns its block entity */
  private static <T> T place(GameTestHelper helper, BlockPos relative, Block block, Class<T> type) {
    BlockPos pos = helper.absolutePos(relative);
    helper.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
    if (type.isInstance(helper.getLevel().getBlockEntity(pos))) {
      return type.cast(helper.getLevel().getBlockEntity(pos));
    }
    throw new IllegalStateException("Expected a " + type.getSimpleName() + " at " + pos);
  }

  private static void assertStack(GameTestHelper helper, ItemStack actual, ItemStack expected, String what) {
    helper.assertTrue(ItemStack.matches(actual, expected), Component.literal(what + ": expected " + expected + " but got " + actual));
  }

  /** Clicks the slot with the given carried stack, returns the carried stack after the click */
  private static ItemStack click(AbstractContainerMenu menu, Player player, int slot, int button, ItemStack carried) {
    menu.setCarried(carried);
    menu.clicked(slot, button, ClickType.PICKUP, player);
    return menu.getCarried();
  }

  /**
   * A side inventory over an inventory exposing only a native handler (not one of ours, not a {@code Container}), which had no side
   * inventory before phase 6: the handler's validity and capacity limit what the player places, picking up and setting slots change the
   * real contents through transactions.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void side_inventory_native_handler(GameTestHelper helper) {
    // diamonds only, at most 16 per slot
    ItemStacksResourceHandler inventory = new ItemStacksResourceHandler(4) {
      @Override
      public boolean isValid(int index, ItemResource resource) {
        return resource.is(Items.DIAMOND);
      }

      @Override
      protected int getCapacity(int index, ItemResource resource) {
        return 16;
      }
    };
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    SideInventoryContainer<BlockEntity> menu = new SideInventoryContainer<>(TinkerTables.craftingStationContainer.get(), 0, player.getInventory(), null,
                                                                            ModifiableItemHandler.transactional(inventory), 0, 0, 2);
    helper.assertTrue(menu.getSlotCount() == 4 && menu.slots.size() == 4, Component.literal("expected 4 side inventory slots, got " + menu.slots.size()));
    for (int i = 0; i < 4; i++) {
      helper.assertTrue(menu.getSlot(i).getSlotIndex() == i, Component.literal("slot " + i + " reports index " + menu.getSlot(i).getSlotIndex()));
    }

    // only 16 of the 64 diamonds fit
    ItemStack carried = click(menu, player, 0, 0, new ItemStack(Items.DIAMOND, 64));
    assertStack(helper, inventory.copyToList().get(0), new ItemStack(Items.DIAMOND, 16), "slot 0 after placing diamonds");
    assertStack(helper, carried, new ItemStack(Items.DIAMOND, 48), "carried after placing diamonds");
    // dirt is not valid
    carried = click(menu, player, 1, 0, new ItemStack(Items.DIRT, 5));
    assertStack(helper, inventory.copyToList().get(1), ItemStack.EMPTY, "slot 1 after clicking dirt");
    assertStack(helper, carried, new ItemStack(Items.DIRT, 5), "carried after clicking dirt");
    // right click picks up half
    carried = click(menu, player, 0, 1, ItemStack.EMPTY);
    assertStack(helper, carried, new ItemStack(Items.DIAMOND, 8), "carried after picking up half");
    assertStack(helper, inventory.copyToList().get(0), new ItemStack(Items.DIAMOND, 8), "slot 0 after picking up half");
    // adding more of the same item moves just the difference
    carried = click(menu, player, 0, 0, new ItemStack(Items.DIAMOND, 4));
    assertStack(helper, inventory.copyToList().get(0), new ItemStack(Items.DIAMOND, 12), "slot 0 after adding 4 diamonds");
    helper.assertTrue(carried.isEmpty(), Component.literal("carried should be empty after adding 4 diamonds, got " + carried));

    // setting a slot directly, as client sync does, changes the real contents
    Slot slot = menu.getSlot(2);
    slot.set(new ItemStack(Items.DIAMOND, 3));
    assertStack(helper, inventory.copyToList().get(2), new ItemStack(Items.DIAMOND, 3), "slot 2 after set");
    assertStack(helper, slot.getItem(), new ItemStack(Items.DIAMOND, 3), "slot 2 item after set");
    slot.set(ItemStack.EMPTY);
    assertStack(helper, inventory.copyToList().get(2), ItemStack.EMPTY, "slot 2 after clearing");
    helper.succeed();
  }

  /** The crafting station side inventory over a vanilla barrel: slots set the barrel's real contents directly. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void side_inventory_vanilla_container(GameTestHelper helper) {
    CraftingStationBlockEntity station = place(helper, POS, TinkerTables.craftingStation.get(), CraftingStationBlockEntity.class);
    BarrelBlockEntity barrel = place(helper, POS.east(), Blocks.BARREL, BarrelBlockEntity.class);
    barrel.setItem(5, new ItemStack(Items.IRON_INGOT, 7));
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    // the barrel must be in reach to be usable as a side inventory
    player.setPos(helper.absoluteVec(new Vec3(2.5, 1, 1.5)));
    CraftingStationContainerMenu menu = new CraftingStationContainerMenu(0, player.getInventory(), station);
    SideInventoryContainer<?> side = menu.getSubContainer(SideInventoryContainer.class);
    if (side == null) {
      helper.fail(Component.literal("crafting station found no side inventory next to the barrel"));
      return;
    }
    helper.assertTrue(side.getSlotCount() == barrel.getContainerSize(), Component.literal("side inventory has " + side.getSlotCount() + " slots, barrel " + barrel.getContainerSize()));
    assertStack(helper, side.getSlot(5).getItem(), new ItemStack(Items.IRON_INGOT, 7), "side inventory slot 5");

    // click through the station menu, whose slots wrap the side inventory slots
    int sideStart = -1;
    for (int i = 0; i < menu.slots.size(); i++) {
      if (menu.slots.get(i) instanceof WrapperSlot wrapper && wrapper.parent == side.getSlot(0)) {
        sideStart = i;
        break;
      }
    }
    helper.assertTrue(sideStart >= 0, Component.literal("side inventory slots not found in the station menu"));
    ItemStack carried = click(menu, player, sideStart, 0, new ItemStack(Items.GOLD_INGOT, 3));
    helper.assertTrue(carried.isEmpty(), Component.literal("gold ingots were not placed, carried " + carried));
    assertStack(helper, barrel.getItem(0), new ItemStack(Items.GOLD_INGOT, 3), "barrel slot 0 after placing");
    carried = click(menu, player, sideStart + 5, 0, ItemStack.EMPTY);
    assertStack(helper, carried, new ItemStack(Items.IRON_INGOT, 7), "carried after picking up the iron");
    helper.assertTrue(barrel.getItem(5).isEmpty(), Component.literal("barrel slot 5 still holds " + barrel.getItem(5)));
    helper.succeed();
  }

  /** The duct menu slot sets the duct's real stack, running its side effects (fluid filter), and only takes valid containers. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void single_item_menu_duct(GameTestHelper helper) {
    DuctBlockEntity duct = place(helper, POS, TinkerSmeltery.searedDuct.get(), DuctBlockEntity.class);
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    SingleItemContainerMenu menu = new SingleItemContainerMenu(0, player.getInventory(), duct);
    // an empty bucket holds no fluid, so it is not a filter
    ItemStack carried = click(menu, player, 0, 0, new ItemStack(Items.BUCKET));
    helper.assertTrue(duct.getItemHandler().getStack().isEmpty(), Component.literal("duct took an empty bucket"));
    assertStack(helper, carried, new ItemStack(Items.BUCKET), "carried after clicking an empty bucket");
    // a lava bucket sets the filter
    carried = click(menu, player, 0, 0, new ItemStack(Items.LAVA_BUCKET));
    helper.assertTrue(carried.isEmpty(), Component.literal("lava bucket was not placed, carried " + carried));
    assertStack(helper, duct.getItemHandler().getStack(), new ItemStack(Items.LAVA_BUCKET), "duct stack");
    helper.assertTrue(duct.getItemHandler().getFluid().getFluid().isSame(Fluids.LAVA),
      Component.literal("duct filter is not lava: " + duct.getItemHandler().getFluid()));
    // picking it up clears the filter
    carried = click(menu, player, 0, 0, ItemStack.EMPTY);
    assertStack(helper, carried, new ItemStack(Items.LAVA_BUCKET), "carried after picking up");
    helper.assertTrue(duct.getItemHandler().getStack().isEmpty() && duct.getItemHandler().getFluid().isEmpty(),
      Component.literal("duct kept " + duct.getItemHandler().getStack() + " / " + duct.getItemHandler().getFluid()));
    helper.succeed();
  }

  /** Tinkers chest menu: slots report their chest index, respect the slot limit, and shift clicking moves stacks out of the chest. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void chest_menu_slots(GameTestHelper helper) {
    TinkersChestBlockEntity chest = place(helper, POS, TinkerTables.tinkersChest.get(), TinkersChestBlockEntity.class);
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    TinkerChestContainerMenu menu = new TinkerChestContainerMenu(0, player.getInventory(), chest);
    int size = chest.getItemHandler().size();
    for (int i = 0; i < size; i++) {
      helper.assertTrue(menu.getSlot(i).getSlotIndex() == i, Component.literal("chest menu slot " + i + " reports index " + menu.getSlot(i).getSlotIndex()));
    }
    // tinkers chests hold 16 per slot
    ItemStack carried = click(menu, player, 3, 0, new ItemStack(Items.COBBLESTONE, 64));
    assertStack(helper, chest.getItemHandler().getStackInSlot(3), new ItemStack(Items.COBBLESTONE, 16), "chest slot 3 after placing");
    assertStack(helper, carried, new ItemStack(Items.COBBLESTONE, 48), "carried after placing");
    // shift click moves the stack to the player inventory
    menu.setCarried(ItemStack.EMPTY);
    menu.quickMoveStack(player, 3);
    helper.assertTrue(chest.getItemHandler().getStackInSlot(3).isEmpty(), Component.literal("shift click left " + chest.getItemHandler().getStackInSlot(3) + " in the chest"));
    helper.assertTrue(player.getInventory().countItem(Items.COBBLESTONE) == 16, Component.literal("player got " + player.getInventory().countItem(Items.COBBLESTONE) + " cobblestone, expected 16"));
    helper.succeed();
  }
}
