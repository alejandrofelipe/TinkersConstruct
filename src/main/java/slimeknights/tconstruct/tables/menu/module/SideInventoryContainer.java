package slimeknights.tconstruct.tables.menu.module;

import lombok.Getter;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import slimeknights.mantle.inventory.BaseContainerMenu;
import slimeknights.tconstruct.library.transfer.IModifiableItemHandlerProvider;
import slimeknights.tconstruct.library.transfer.ModifiableItemHandler;

import javax.annotation.Nullable;

public class SideInventoryContainer<TILE extends BlockEntity> extends BaseContainerMenu<TILE> {

  @Getter
  private final int columns;
  @Getter
  private final int slotCount;
  protected final ModifiableItemHandler itemHandler;

  public SideInventoryContainer(MenuType<?> containerType, int windowId, Inventory inv, @Nullable TILE tile, int x, int y, int columns) {
    this(containerType, windowId, inv, tile, (Direction) null, x, y, columns);
  }

  public SideInventoryContainer(MenuType<?> containerType, int windowId, Inventory inv, @Nullable TILE tile, @Nullable Direction inventoryDirection, int x, int y, int columns) {
    this(containerType, windowId, inv, tile, findHandler(tile, inventoryDirection), x, y, columns);
  }

  /**
   * Creates a side inventory over the given item handler
   * @param handler  Handler holding the slots, typically from {@link IModifiableItemHandlerProvider#find}
   */
  public SideInventoryContainer(MenuType<?> containerType, int windowId, Inventory inv, @Nullable TILE tile, ModifiableItemHandler handler, int x, int y, int columns) {
    super(containerType, windowId, inv, tile);
    this.itemHandler = handler;

    // slot properties
    this.slotCount = handler.size();
    this.columns = columns;
    int rows = this.slotCount / columns;
    if (this.slotCount % columns != 0) {
      rows++;
    }

    // add slots
    int index = 0;
    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < columns; c++) {
        if (index >= this.slotCount) {
          break;
        }

        this.addSlot(this.createSlot(handler, index, x + c * 18, y + r * 18));
        index++;
      }
    }
  }

  /** Finds the item handler of the given block entity, empty if it has none */
  private static ModifiableItemHandler findHandler(@Nullable BlockEntity tile, @Nullable Direction inventoryDirection) {
    // must have a TE with a level to resolve the inventory
    if (tile != null) {
      Level level = tile.getLevel();
      if (level != null) {
        ModifiableItemHandler handler = IModifiableItemHandlerProvider.find(level, tile.getBlockPos(), inventoryDirection);
        if (handler != null) {
          return handler;
        }
      }
    }
    return ModifiableItemHandler.EMPTY;
  }

  /**
   * Creates a slot for this inventory
   * @param itemHandler  Item handler
   * @param index        Slot index
   * @param x            Slot X position
   * @param y            Slot Y position
   * @return  Inventory slot
   */
  protected Slot createSlot(ModifiableItemHandler itemHandler, int index, int x, int y) {
    return itemHandler.createSlot(index, x, y);
  }
}
