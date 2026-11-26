package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import slimeknights.mantle.inventory.WrapperSlot;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.tools.capability.fluid.ToolTankHelper;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.transfer.ModifiableItemHandler;
import slimeknights.tconstruct.library.transfer.TransferUtil;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.component.SearedTankBlock.TankType;
import slimeknights.tconstruct.smeltery.item.TankItem;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.data.ModifierIds;
import slimeknights.tconstruct.tools.data.material.MaterialIds;
import slimeknights.tconstruct.smeltery.block.entity.component.DuctBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.HeatingStructureBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.module.MeltingModuleInventory;
import slimeknights.tconstruct.smeltery.menu.HeatingStructureContainerMenu;
import slimeknights.tconstruct.smeltery.menu.SingleItemContainerMenu;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tables.block.entity.chest.TinkersChestBlockEntity;
import slimeknights.tconstruct.tables.block.entity.table.CraftingStationBlockEntity;
import slimeknights.tconstruct.tables.menu.CraftingStationContainerMenu;
import slimeknights.tconstruct.tables.menu.TinkerChestContainerMenu;
import slimeknights.tconstruct.tables.menu.module.SideInventoryContainer;

import java.util.ArrayList;
import java.util.List;

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

  /**
   * Native handler that validates everything but refuses some transfers, like a machine: slot 1 takes no inserts (an output slot that
   * already holds its item), slot 2 gives at most one item per extract, and no slot takes dirt.
   */
  private static ItemStacksResourceHandler refusingInventory() {
    return new ItemStacksResourceHandler(3) {
      @Override
      public boolean isValid(int index, ItemResource resource) {
        return true;
      }

      @Override
      protected int getCapacity(int index, ItemResource resource) {
        return 16;
      }

      @Override
      public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (index == 1 || resource.is(Items.DIRT)) {
          return 0;
        }
        return super.insert(index, resource, amount, transaction);
      }

      @Override
      public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return super.extract(index, resource, index == 2 ? Math.min(1, amount) : amount, transaction);
      }
    };
  }

  /** Side inventory container exposing the menu's stack moving logic, as used by shift clicks from the player inventory */
  private static class MovingSideInventory extends SideInventoryContainer<BlockEntity> {
    final Player player;

    MovingSideInventory(Player player, ModifiableItemHandler handler) {
      super(TinkerTables.craftingStationContainer.get(), 0, player.getInventory(), null, handler, 0, 0, 1);
      this.player = player;
    }

    /** Moves the stack into the side inventory slots like a shift click from the player inventory */
    void moveIn(ItemStack stack) {
      moveItemStackTo(stack, 0, slots.size(), false);
    }
  }

  /**
   * A native-only side inventory whose handler refuses transfers its validity allows must never lose or duplicate items: placing a stack
   * the slot refuses leaves it on the cursor, swapping in a refused item keeps both stacks, a shift click skips a slot that refuses more of
   * its item, and a slot that gives one item per extract cannot be taken from (taking more would split items the handler keeps).
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void side_inventory_native_handler_refusing(GameTestHelper helper) {
    ItemStacksResourceHandler inventory = refusingInventory();
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    MovingSideInventory menu = new MovingSideInventory(player, ModifiableItemHandler.transactional(inventory));

    // placing into a slot that takes no inserts: the cursor keeps everything
    ItemStack carried = click(menu, player, 1, 0, new ItemStack(Items.APPLE, 10));
    assertStack(helper, carried, new ItemStack(Items.APPLE, 10), "carried after placing into the refusing slot");
    assertStack(helper, inventory.copyToList().get(1), ItemStack.EMPTY, "refusing slot after placing");
    carried = click(menu, player, 1, 1, new ItemStack(Items.APPLE, 10));
    assertStack(helper, carried, new ItemStack(Items.APPLE, 10), "carried after right clicking into the refusing slot");

    // swapping a refused item into an occupied slot: both stacks stay where they are
    inventory.set(0, ItemResource.of(Items.APPLE), 5);
    carried = click(menu, player, 0, 0, new ItemStack(Items.DIRT, 3));
    assertStack(helper, carried, new ItemStack(Items.DIRT, 3), "carried after swapping in dirt");
    assertStack(helper, inventory.copyToList().get(0), new ItemStack(Items.APPLE, 5), "slot 0 after swapping in dirt");

    // shift click: the slot holding apples but refusing more is skipped, the apples go into the free slot
    inventory.set(0, ItemResource.EMPTY, 0);
    inventory.set(1, ItemResource.of(Items.APPLE), 5);
    ItemStack moved = new ItemStack(Items.APPLE, 10);
    menu.moveIn(moved);
    assertStack(helper, inventory.copyToList().get(1), new ItemStack(Items.APPLE, 5), "refusing slot after the shift click");
    assertStack(helper, inventory.copyToList().get(0), new ItemStack(Items.APPLE, 10), "free slot after the shift click");
    helper.assertTrue(moved.isEmpty(), Component.literal("shift clicked stack should be empty, holds " + moved));
    // dirt fits nowhere: the source keeps it
    ItemStack dirt = new ItemStack(Items.DIRT, 4);
    menu.moveIn(dirt);
    assertStack(helper, dirt, new ItemStack(Items.DIRT, 4), "dirt after the shift click");

    // a slot giving one item per extract: taking the stack or half of it would duplicate, so nothing is taken
    inventory.set(2, ItemResource.of(Items.DIAMOND), 6);
    carried = click(menu, player, 2, 0, ItemStack.EMPTY);
    helper.assertTrue(carried.isEmpty(), Component.literal("took " + carried + " from the one-per-extract slot"));
    carried = click(menu, player, 2, 1, ItemStack.EMPTY);
    helper.assertTrue(carried.isEmpty(), Component.literal("took half " + carried + " from the one-per-extract slot"));
    assertStack(helper, inventory.copyToList().get(2), new ItemStack(Items.DIAMOND, 6), "one-per-extract slot after clicking");
    // throwing one goes through a single extract of one: allowed, nothing duplicated
    ItemStack thrown = menu.getSlot(2).safeTake(1, 1, player);
    helper.assertTrue(thrown.isEmpty() || (thrown.is(Items.DIAMOND) && thrown.getCount() + inventory.copyToList().get(2).getCount() == 6),
      Component.literal("took " + thrown + ", slot holds " + inventory.copyToList().get(2)));
    helper.succeed();
  }

  /**
   * Native handler that ignores the requested index for some slots, like a compacting storage or a void: inserting into slot 1 or
   * extracting from it goes to a hidden internal storage, slot 2 accepts any insert and keeps nothing.
   */
  private static class MisdirectingInventory extends ItemStacksResourceHandler {
    /** Where slot 1 really stores and takes items, not exposed as a slot */
    final ItemStacksResourceHandler storage = new ItemStacksResourceHandler(1);

    MisdirectingInventory() {
      super(3);
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
      return true;
    }

    @Override
    protected int getCapacity(int index, ItemResource resource) {
      return 16;
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
      return switch (index) {
        case 1 -> storage.insert(0, resource, amount, transaction);
        case 2 -> amount;
        default -> super.insert(index, resource, amount, transaction);
      };
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
      return index == 1 ? storage.extract(0, resource, amount, transaction) : super.extract(index, resource, amount, transaction);
    }
  }

  /**
   * A native-only side inventory whose handler moves items somewhere other than the requested slot (or voids them) must never lose or
   * duplicate items: the slot only reports what really reaches or leaves it, so vanilla never takes items for a set the handler then refuses.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void side_inventory_native_handler_misdirecting(GameTestHelper helper) {
    MisdirectingInventory inventory = new MisdirectingInventory();
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    MovingSideInventory menu = new MovingSideInventory(player, ModifiableItemHandler.transactional(inventory));

    // placing into the slots that put items elsewhere: the cursor keeps everything, nothing appears anywhere
    for (int slot = 1; slot <= 2; slot++) {
      ItemStack carried = click(menu, player, slot, 0, new ItemStack(Items.APPLE, 10));
      assertStack(helper, carried, new ItemStack(Items.APPLE, 10), "carried after placing into misdirecting slot " + slot);
      carried = click(menu, player, slot, 1, new ItemStack(Items.APPLE, 10));
      assertStack(helper, carried, new ItemStack(Items.APPLE, 10), "carried after right clicking into misdirecting slot " + slot);
    }
    assertStack(helper, inventory.storage.copyToList().get(0), ItemStack.EMPTY, "hidden storage after placing");

    // shift click with the only regular slot full of another item: the source keeps the stack
    inventory.set(0, ItemResource.of(Items.DIAMOND), 16);
    ItemStack moved = new ItemStack(Items.APPLE, 10);
    menu.moveIn(moved);
    assertStack(helper, moved, new ItemStack(Items.APPLE, 10), "shift clicked stack");
    assertStack(helper, inventory.storage.copyToList().get(0), ItemStack.EMPTY, "hidden storage after the shift click");
    assertStack(helper, inventory.copyToList().get(1), ItemStack.EMPTY, "slot 1 after the shift click");

    // taking from a slot whose extracts come from the hidden storage: nothing is taken (it would duplicate)
    inventory.set(1, ItemResource.of(Items.APPLE), 5);
    inventory.storage.set(0, ItemResource.of(Items.APPLE), 5);
    ItemStack carried = click(menu, player, 1, 0, ItemStack.EMPTY);
    helper.assertTrue(carried.isEmpty(), Component.literal("took " + carried + " from the misdirecting slot"));
    carried = click(menu, player, 1, 1, ItemStack.EMPTY);
    helper.assertTrue(carried.isEmpty(), Component.literal("took half " + carried + " from the misdirecting slot"));
    assertStack(helper, inventory.copyToList().get(1), new ItemStack(Items.APPLE, 5), "misdirecting slot after taking");
    assertStack(helper, inventory.storage.copyToList().get(0), new ItemStack(Items.APPLE, 5), "hidden storage after taking");
    helper.succeed();
  }

  /** Builds a rock/wood/wood pickaxe with a tank, holding the given fluid */
  private static ItemStack tankPickaxe(FluidStack fluid) {
    ToolStack tool = ToolStack.createTool(TinkerTools.pickaxe.get(), TinkerTools.pickaxe.get().getToolDefinition(),
      MaterialNBT.of(MaterialVariant.of(MaterialIds.rock, ""), MaterialVariant.of(MaterialIds.wood, ""), MaterialVariant.of(MaterialIds.wood, "")));
    tool.addModifier(ModifierIds.tank, 1);
    ToolTankHelper.TANK_HELPER.setFluid(tool, fluid);
    return tool.createStack();
  }

  private static FluidStack toolFluid(ItemStack stack) {
    return ToolTankHelper.TANK_HELPER.getFluid(ToolStack.from(stack));
  }

  /**
   * Tool tanks and tank items right clicked onto a container in a side inventory slot that refuses the filled container: neither the held
   * tank nor the slot changes (before, the held tank lost its fluid and the slot kept the empty container).
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tank_held_over_refusing_slot(GameTestHelper helper) {
    // takes anything except water buckets
    ItemStacksResourceHandler inventory = new ItemStacksResourceHandler(1) {
      @Override
      public boolean isValid(int index, ItemResource resource) {
        return !resource.is(Items.WATER_BUCKET);
      }

      @Override
      protected int getCapacity(int index, ItemResource resource) {
        return 16;
      }
    };
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    MovingSideInventory menu = new MovingSideInventory(player, ModifiableItemHandler.transactional(inventory));
    // the interactions update the cursor of the open menu
    player.containerMenu = menu;
    FluidStack bucket = new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME);

    // tool with a bucket of water: filling the empty bucket would need the slot to take a water bucket
    inventory.set(0, ItemResource.of(Items.BUCKET), 1);
    ItemStack carried = click(menu, player, 0, 1, tankPickaxe(bucket));
    helper.assertTrue(FluidStack.matches(toolFluid(carried), bucket), Component.literal("held tool holds " + toolFluid(carried)));
    assertStack(helper, inventory.copyToList().get(0), new ItemStack(Items.BUCKET), "slot after the tool interaction");

    // same with a tank item
    ItemStack tank = TankItem.setTank(new ItemStack(TinkerSmeltery.searedTank.get(TankType.FUEL_TANK)), bucket);
    carried = click(menu, player, 0, 1, tank.copy());
    assertStack(helper, carried, tank, "held tank after the interaction");
    assertStack(helper, inventory.copyToList().get(0), new ItemStack(Items.BUCKET), "slot after the tank interaction");
    helper.succeed();
  }

  /**
   * A container right clicked onto a tool tank or tank item in a Tinkers chest (whose menu slots work on copies of the stacks) fills the
   * tank in the chest: the change must be written back to the chest, not just applied to a copy while the held container empties.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void container_on_tank_in_chest(GameTestHelper helper) {
    TinkersChestBlockEntity chest = place(helper, POS, TinkerTables.tinkersChest.get(), TinkersChestBlockEntity.class);
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    TinkerChestContainerMenu menu = new TinkerChestContainerMenu(0, player.getInventory(), chest);
    // the interactions update the cursor of the open menu
    player.containerMenu = menu;
    FluidStack water = new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME);

    chest.getItemHandler().setStackInSlot(0, tankPickaxe(FluidStack.EMPTY));
    ItemStack carried = click(menu, player, 0, 1, new ItemStack(Items.WATER_BUCKET));
    assertStack(helper, carried, new ItemStack(Items.BUCKET), "carried after emptying into the tool");
    helper.assertTrue(FluidStack.matches(toolFluid(chest.getItemHandler().getStackInSlot(0)), water),
      Component.literal("tool in the chest holds " + toolFluid(chest.getItemHandler().getStackInSlot(0))));

    chest.getItemHandler().setStackInSlot(1, new ItemStack(TinkerSmeltery.searedTank.get(TankType.FUEL_TANK)));
    carried = click(menu, player, 1, 1, new ItemStack(Items.WATER_BUCKET));
    assertStack(helper, carried, new ItemStack(Items.BUCKET), "carried after emptying into the tank");
    FluidStack inTank = TankItem.getTank(chest.getItemHandler().getStackInSlot(1), 1).getFluid();
    helper.assertTrue(FluidStack.matches(inTank, water), Component.literal("tank in the chest holds " + inTank));
    helper.succeed();
  }

  /**
   * A smeltery shrinking while its menu is open (re-formed smaller) must not crash the menu: slots past the new size read as empty and
   * clicking them changes nothing. The legacy slot guarded the index, NeoForge's native slot throws.
   */
  @TinkerGameTest(template = "gametest/empty_9x9x9", timeoutTicks = 300)
  public static void smeltery_menu_survives_shrinking(GameTestHelper helper) {
    BlockPos controller = SmelteryRigs.buildSearedHeatingStructure(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)));
    helper.startSequence()
      .thenWaitUntil(() -> helper.assertTrue(helper.getLevel().getBlockEntity(controller) instanceof HeatingStructureBlockEntity structure && structure.getStructure() != null,
        Component.literal("smeltery did not form")))
      .thenExecute(() -> {
        HeatingStructureBlockEntity structure = (HeatingStructureBlockEntity) helper.getLevel().getBlockEntity(controller);
        MeltingModuleInventory inventory = structure.getMeltingInventory();
        int size = inventory.getSlots();
        helper.assertTrue(size >= 1, Component.literal("smeltery has no melting slots"));
        inventory.setStackInSlot(size - 1, new ItemStack(Items.IRON_INGOT));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        HeatingStructureContainerMenu menu = new HeatingStructureContainerMenu(0, player.getInventory(), structure);
        SideInventoryContainer<?> side = menu.getSubContainer(SideInventoryContainer.class);
        if (side == null) {
          helper.fail(Component.literal("smeltery menu has no melting slots"));
          return;
        }
        int lastSlot = -1;
        for (int i = 0; i < menu.slots.size(); i++) {
          if (menu.slots.get(i) instanceof WrapperSlot wrapper && wrapper.parent == side.getSlot(size - 1)) {
            lastSlot = i;
          }
        }
        helper.assertTrue(lastSlot >= 0, Component.literal("last melting slot not found in the menu"));
        menu.broadcastChanges();

        // re-formed smaller while the menu stays open
        List<ItemStack> dropped = new ArrayList<>();
        inventory.resize(size - 1, dropped::add);
        menu.broadcastChanges();
        Slot removed = menu.getSlot(lastSlot);
        helper.assertTrue(removed.getItem().isEmpty() && !removed.mayPickup(player) && !removed.mayPlace(new ItemStack(Items.GOLD_INGOT)),
          Component.literal("removed slot still usable: " + removed.getItem()));
        ItemStack carried = click(menu, player, lastSlot, 0, new ItemStack(Items.GOLD_INGOT, 2));
        assertStack(helper, carried, new ItemStack(Items.GOLD_INGOT, 2), "carried after clicking the removed slot");
        carried = click(menu, player, lastSlot, 0, ItemStack.EMPTY);
        helper.assertTrue(carried.isEmpty(), Component.literal("took " + carried + " from the removed slot"));
        helper.assertTrue(inventory.getSlots() == size - 1, Component.literal("inventory size changed to " + inventory.getSlots()));
        helper.assertTrue(dropped.size() == 1 && dropped.get(0).is(Items.IRON_INGOT), Component.literal("resize dropped " + dropped));
      })
      .thenSucceed();
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

  /**
   * {@link TransferUtil#trySetSlot} succeeds only if the slot ends up holding exactly the requested stack: a slot storing something else
   * (here a container capping every stack at 4) is put back as it was, so the caller never changes the held stack for a set that did not happen.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void slot_safety_try_set_slot_exact(GameTestHelper helper) {
    SimpleContainer container = new SimpleContainer(1) {
      @Override
      public void setItem(int index, ItemStack stack) {
        super.setItem(index, stack.copyWithCount(Math.min(stack.getCount(), 4)));
      }
    };
    Slot slot = new Slot(container, 0, 0, 0);
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    helper.assertFalse(TransferUtil.trySetSlot(slot, player, new ItemStack(Items.APPLE, 8)), Component.literal("trySetSlot reported success for a capped set"));
    assertStack(helper, container.getItem(0), ItemStack.EMPTY, "slot after the capped set");
    // an exact set still succeeds
    helper.assertTrue(TransferUtil.trySetSlot(slot, player, new ItemStack(Items.APPLE, 3)), Component.literal("trySetSlot refused an exact set"));
    assertStack(helper, container.getItem(0), new ItemStack(Items.APPLE, 3), "slot after the exact set");
    helper.succeed();
  }

  /**
   * A full slot of a native-only side inventory clicked with the same item behaves like a full chest slot: nothing moves (before, the slot
   * counted as not accepting the item, so vanilla pulled the stack onto the cursor). A slot refusing extracts but with room still takes more.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void slot_safety_full_slot_same_item(GameTestHelper helper) {
    // at most 16 per slot, slot 1 gives nothing back
    ItemStacksResourceHandler inventory = new ItemStacksResourceHandler(2) {
      @Override
      protected int getCapacity(int index, ItemResource resource) {
        return 16;
      }

      @Override
      public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return index == 1 ? 0 : super.extract(index, resource, amount, transaction);
      }
    };
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    MovingSideInventory menu = new MovingSideInventory(player, ModifiableItemHandler.transactional(inventory));

    inventory.set(0, ItemResource.of(Items.APPLE), 16);
    for (int button = 0; button <= 1; button++) {
      ItemStack carried = click(menu, player, 0, button, new ItemStack(Items.APPLE, 5));
      assertStack(helper, carried, new ItemStack(Items.APPLE, 5), "carried after clicking the full slot with button " + button);
      assertStack(helper, inventory.copyToList().get(0), new ItemStack(Items.APPLE, 16), "full slot after clicking with button " + button);
    }

    // no extracts, but room: a shift click still refills it
    inventory.set(1, ItemResource.of(Items.APPLE), 5);
    ItemStack moved = new ItemStack(Items.APPLE, 3);
    menu.moveIn(moved);
    helper.assertTrue(moved.isEmpty(), Component.literal("shift clicked stack after refilling the insert only slot: " + moved));
    assertStack(helper, inventory.copyToList().get(1), new ItemStack(Items.APPLE, 8), "insert only slot after the shift click");
    helper.succeed();
  }

  /**
   * A tank tool sitting in a native-only side inventory slot (full, as tools stack to 1) is still modifiable: right clicking it with a water
   * bucket fills the tool instead of swapping the bucket in.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void slot_safety_tank_tool_in_native_slot(GameTestHelper helper) {
    ItemStacksResourceHandler inventory = new ItemStacksResourceHandler(1);
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    MovingSideInventory menu = new MovingSideInventory(player, ModifiableItemHandler.transactional(inventory));
    // the interactions update the cursor of the open menu
    player.containerMenu = menu;
    FluidStack water = new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME);

    inventory.set(0, ItemResource.of(tankPickaxe(FluidStack.EMPTY)), 1);
    helper.assertTrue(menu.getSlot(0).allowModification(player), Component.literal("tool slot does not allow modification"));
    ItemStack carried = click(menu, player, 0, 1, new ItemStack(Items.WATER_BUCKET));
    assertStack(helper, carried, new ItemStack(Items.BUCKET), "carried after emptying into the tool");
    ItemStack tool = inventory.copyToList().get(0);
    helper.assertTrue(tool.is(TinkerTools.pickaxe.get()) && FluidStack.matches(toolFluid(tool), water),
      Component.literal("slot holds " + tool + " with " + (tool.is(TinkerTools.pickaxe.get()) ? toolFluid(tool) : "no tank")));
    helper.succeed();
  }

  /**
   * Native handler spreading inserts non-linearly: an insert into slot 0 or 1 puts half of it (rounded down) there and the rest into the
   * next slot, slot 2 keeps all. Simulating a large insert into slot 0 shows half of it arriving, but no set of slot 0 or 1 ever ends with
   * exactly the requested stack, so the transactional setter refuses every one of them.
   */
  private static ItemStacksResourceHandler spreadingInventory() {
    return new ItemStacksResourceHandler(3) {
      @Override
      protected int getCapacity(int index, ItemResource resource) {
        return 16;
      }

      @Override
      public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (index >= 2) {
          return super.insert(index, resource, amount, transaction);
        }
        int here = amount / 2;
        int inserted = super.insert(index, resource, here, transaction);
        return inserted + super.insert(index + 1, resource, amount - here, transaction);
      }
    };
  }

  /** Counts the given item in the handler, the cursor and the player inventory */
  private static int countEverywhere(ItemStacksResourceHandler inventory, AbstractContainerMenu menu, Player player, Item item) {
    int count = player.getInventory().countItem(item);
    if (menu.getCarried().is(item)) {
      count += menu.getCarried().getCount();
    }
    for (ItemStack stack : inventory.copyToList()) {
      if (stack.is(item)) {
        count += stack.getCount();
      }
    }
    return count;
  }

  /** Sets up a spreading side inventory menu for a fresh mock player, with the player's menu set so cursor updates apply */
  private static MovingSideInventory spreadingMenu(GameTestHelper helper, ItemStacksResourceHandler inventory) {
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    MovingSideInventory menu = new MovingSideInventory(player, ModifiableItemHandler.transactional(inventory));
    player.containerMenu = menu;
    return menu;
  }

  private static void assertCount(GameTestHelper helper, ItemStacksResourceHandler inventory, MovingSideInventory menu, Item item, int expected, String what) {
    int actual = countEverywhere(inventory, menu, menu.player, item);
    helper.assertTrue(actual == expected, Component.literal(what + ": expected " + expected + " " + item + " in total, found " + actual
                                                            + " (cursor " + menu.getCarried() + ", handler " + inventory.copyToList() + ")"));
  }

  /** Clicking and right clicking a spreading slot: the slot refuses the set, and the cursor keeps everything */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void slot_safety_spreading_click(GameTestHelper helper) {
    ItemStacksResourceHandler inventory = spreadingInventory();
    MovingSideInventory menu = spreadingMenu(helper, inventory);
    for (int button = 0; button <= 1; button++) {
      ItemStack carried = click(menu, menu.player, 0, button, new ItemStack(Items.APPLE, 10));
      assertStack(helper, carried, new ItemStack(Items.APPLE, 10), "carried after clicking the spreading slot with button " + button);
      assertCount(helper, inventory, menu, Items.APPLE, 10, "after clicking with button " + button);
    }
    helper.succeed();
  }

  /** Swapping a stack into a spreading slot holding another item: no item is lost or duplicated */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void slot_safety_spreading_swap(GameTestHelper helper) {
    ItemStacksResourceHandler inventory = spreadingInventory();
    MovingSideInventory menu = spreadingMenu(helper, inventory);
    inventory.set(0, ItemResource.of(Items.DIRT), 3);
    click(menu, menu.player, 0, 0, new ItemStack(Items.APPLE, 6));
    assertCount(helper, inventory, menu, Items.APPLE, 6, "after swapping");
    assertCount(helper, inventory, menu, Items.DIRT, 3, "after swapping");
    helper.succeed();
  }

  /** Dragging a stack over a spreading slot and a regular one: the share the spreading slot refuses goes back to the player */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void slot_safety_spreading_drag(GameTestHelper helper) {
    ItemStacksResourceHandler inventory = spreadingInventory();
    MovingSideInventory menu = spreadingMenu(helper, inventory);
    Player player = menu.player;
    menu.setCarried(new ItemStack(Items.APPLE, 10));
    menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, 0), ClickType.QUICK_CRAFT, player);
    menu.clicked(0, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT, player);
    menu.clicked(2, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT, player);
    menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, 0), ClickType.QUICK_CRAFT, player);
    assertStack(helper, inventory.copyToList().get(2), new ItemStack(Items.APPLE, 5), "regular slot after the drag");
    assertCount(helper, inventory, menu, Items.APPLE, 10, "after the drag");
    helper.succeed();
  }

  /** Shift clicking into spreading slots: the source keeps what they refuse, the rest lands in the regular slot */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void slot_safety_spreading_shift_click(GameTestHelper helper) {
    ItemStacksResourceHandler inventory = spreadingInventory();
    MovingSideInventory menu = spreadingMenu(helper, inventory);
    ItemStack moved = new ItemStack(Items.APPLE, 10);
    menu.moveIn(moved);
    int inHandler = inventory.copyToList().stream().filter(stack -> stack.is(Items.APPLE)).mapToInt(ItemStack::getCount).sum();
    helper.assertTrue(moved.getCount() + inHandler == 10, Component.literal("shift click left " + moved + ", handler holds " + inventory.copyToList()));
    assertStack(helper, inventory.copyToList().get(2), new ItemStack(Items.APPLE, 10), "regular slot after the shift click");
    helper.succeed();
  }

  /** Number key swap from the hotbar into a spreading slot: the refused stack goes back to the player */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void slot_safety_spreading_hotbar_swap(GameTestHelper helper) {
    ItemStacksResourceHandler inventory = spreadingInventory();
    MovingSideInventory menu = spreadingMenu(helper, inventory);
    Player player = menu.player;
    player.getInventory().setItem(0, new ItemStack(Items.APPLE, 6));
    menu.clicked(0, 0, ClickType.SWAP, player);
    assertCount(helper, inventory, menu, Items.APPLE, 6, "after the hotbar swap");
    helper.succeed();
  }

  /** A plain set of a spreading slot (not a player placing items taken from somewhere) is refused without giving anything back */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void slot_safety_spreading_plain_set(GameTestHelper helper) {
    ItemStacksResourceHandler inventory = spreadingInventory();
    MovingSideInventory menu = spreadingMenu(helper, inventory);
    menu.getSlot(0).set(new ItemStack(Items.APPLE, 4));
    assertCount(helper, inventory, menu, Items.APPLE, 0, "after a plain set");
    helper.succeed();
  }

  /** Sets up a side inventory menu over the native handler for a fresh mock player, with the player's menu set so cursor updates apply */
  private static MovingSideInventory transactionalMenu(GameTestHelper helper, ItemStacksResourceHandler inventory) {
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    MovingSideInventory menu = new MovingSideInventory(player, ModifiableItemHandler.transactional(inventory));
    player.containerMenu = menu;
    return menu;
  }

  /**
   * Native handler giving only whole stacks: extracting part of a slot's stack takes nothing. A simulated extract of the whole stack
   * succeeds, so the slot can be picked up from, but a set lowering the count (an exact partial extract) is refused.
   */
  private static ItemStacksResourceHandler wholeStackInventory() {
    return new ItemStacksResourceHandler(1) {
      @Override
      protected int getCapacity(int index, ItemResource resource) {
        return 16;
      }

      @Override
      public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return amount < getAmountAsInt(index) ? 0 : super.extract(index, resource, amount, transaction);
      }
    };
  }

  /**
   * Number key swap of the same item from a smaller hotbar stack: vanilla hands the slot's whole stack to the hotbar and sets the slot to
   * the smaller stack, which a handler giving only whole stacks refuses. No item may be duplicated (before, the slot kept its stack while
   * the hotbar got a copy of it).
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void slot_safety_same_item_hotbar_swap(GameTestHelper helper) {
    ItemStacksResourceHandler inventory = wholeStackInventory();
    MovingSideInventory menu = transactionalMenu(helper, inventory);
    inventory.set(0, ItemResource.of(Items.APPLE), 10);
    menu.player.getInventory().setItem(0, new ItemStack(Items.APPLE, 3));
    menu.clicked(0, 0, ClickType.SWAP, menu.player);
    assertCount(helper, inventory, menu, Items.APPLE, 13, "after the same item hotbar swap");
    helper.succeed();
  }

  /**
   * Native handler counting its extracts, one of which (1-based, -1 for none) takes nothing although the very same extract was simulated
   * successfully just before, like a handler whose state changed in between. Optionally spreads inserts like {@link #spreadingInventory()}.
   */
  private static class FlakyInventory extends ItemStacksResourceHandler {
    private final boolean spreading;
    private final int failAt;
    int extracts = 0;

    FlakyInventory(boolean spreading, int failAt) {
      super(3);
      this.spreading = spreading;
      this.failAt = failAt;
    }

    @Override
    protected int getCapacity(int index, ItemResource resource) {
      return 16;
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
      if (!spreading || index >= 2) {
        return super.insert(index, resource, amount, transaction);
      }
      int here = amount / 2;
      int inserted = super.insert(index, resource, here, transaction);
      return inserted + super.insert(index + 1, resource, amount - here, transaction);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
      extracts++;
      return extracts == failAt ? 0 : super.extract(index, resource, amount, transaction);
    }
  }

  /** Sets up a flaky inventory and its menu, then runs a click on them */
  @FunctionalInterface
  private interface FlakyScenario {
    void run(FlakyInventory inventory, MovingSideInventory menu);
  }

  /**
   * Runs the scenario once counting the extracts, then again on a fresh inventory and menu whose last extract fails. Every simulation
   * comes before the final write, so only the real change of the slot contradicts what the slot simulated.
   * @return  Menu of the second run
   */
  private static MovingSideInventory withFailingFinalExtract(GameTestHelper helper, boolean spreading, FlakyScenario scenario) {
    FlakyInventory counting = new FlakyInventory(spreading, -1);
    scenario.run(counting, transactionalMenu(helper, counting));
    helper.assertTrue(counting.extracts > 0, Component.literal("scenario made no extract"));
    FlakyInventory failing = new FlakyInventory(spreading, counting.extracts);
    MovingSideInventory menu = transactionalMenu(helper, failing);
    scenario.run(failing, menu);
    return menu;
  }

  /** Swapping into a spreading slot refuses the set, and then emptying the slot fails too: the old stack must not be duplicated */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void slot_safety_swap_refused_not_emptied(GameTestHelper helper) {
    // cursor swap: vanilla put the old stack on the cursor
    FlakyInventory[] inventory = new FlakyInventory[1];
    MovingSideInventory menu = withFailingFinalExtract(helper, true, (inv, m) -> {
      inventory[0] = inv;
      inv.set(0, ItemResource.of(Items.DIRT), 3);
      click(m, m.player, 0, 0, new ItemStack(Items.APPLE, 6));
    });
    assertCount(helper, inventory[0], menu, Items.DIRT, 3, "after the cursor swap");
    assertCount(helper, inventory[0], menu, Items.APPLE, 6, "after the cursor swap");

    // number key swap: vanilla put the old stack in the hotbar
    menu = withFailingFinalExtract(helper, true, (inv, m) -> {
      inventory[0] = inv;
      inv.set(0, ItemResource.of(Items.DIRT), 3);
      m.player.getInventory().setItem(0, new ItemStack(Items.APPLE, 6));
      m.clicked(0, 0, ClickType.SWAP, m.player);
    });
    assertCount(helper, inventory[0], menu, Items.DIRT, 3, "after the hotbar swap");
    assertCount(helper, inventory[0], menu, Items.APPLE, 6, "after the hotbar swap");
    helper.succeed();
  }

  /**
   * Taking from a slot whose final extract fails although simulated: nothing may be handed out while the slot keeps its stack (before,
   * NeoForge's slot ignored the refused set of the remainder and returned the split stack anyway).
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void slot_safety_take_refused(GameTestHelper helper) {
    FlakyInventory[] inventory = new FlakyInventory[1];
    // picking up all and half
    for (int button = 0; button <= 1; button++) {
      int clickButton = button;
      MovingSideInventory menu = withFailingFinalExtract(helper, false, (inv, m) -> {
        inventory[0] = inv;
        inv.set(0, ItemResource.of(Items.APPLE), 10);
        click(m, m.player, 0, clickButton, ItemStack.EMPTY);
      });
      assertCount(helper, inventory[0], menu, Items.APPLE, 10, "after picking up with button " + button);
    }

    // a direct take, as throwing and JEI do
    ItemStack[] taken = new ItemStack[1];
    MovingSideInventory menu = withFailingFinalExtract(helper, false, (inv, m) -> {
      inventory[0] = inv;
      inv.set(0, ItemResource.of(Items.APPLE), 10);
      taken[0] = m.getSlot(0).safeTake(1, Integer.MAX_VALUE, m.player);
    });
    assertCount(helper, inventory[0], menu, Items.APPLE, 10 - taken[0].getCount(), "after taking one directly (took " + taken[0] + ")");

    // number key swap into an empty hotbar slot: vanilla moved the stack to the hotbar, then the slot refuses to be emptied
    menu = withFailingFinalExtract(helper, false, (inv, m) -> {
      inventory[0] = inv;
      inv.set(0, ItemResource.of(Items.APPLE), 10);
      m.clicked(0, 0, ClickType.SWAP, m.player);
    });
    assertCount(helper, inventory[0], menu, Items.APPLE, 10, "after swapping into an empty hotbar slot");
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
