package slimeknights.tconstruct.smeltery.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import slimeknights.tconstruct.library.transfer.IModifiableItemHandlerProvider;
import slimeknights.tconstruct.library.transfer.ModifiableItemHandler;
import slimeknights.tconstruct.shared.inventory.TriggeringBaseContainerMenu;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;

import javax.annotation.Nullable;

/**
 * Container for a block with a single item inventory
 */
public class SingleItemContainerMenu extends TriggeringBaseContainerMenu<BlockEntity> {
  public SingleItemContainerMenu(int id, @Nullable Inventory inv, @Nullable BlockEntity te) {
    super(TinkerSmeltery.singleItemContainer.get(), id, inv, te);
    if (te != null) {
      Level level = te.getLevel();
      // PORT 1.21.10: menu slots need a setter for the real stacks, not just the native capability
      ModifiableItemHandler handler = level == null ? null : IModifiableItemHandlerProvider.find(level, te.getBlockPos(), null);
      if (handler != null && handler.size() > 0) {
        this.addSlot(handler.createSlot(0, 80, 20));
      }
      this.addInventorySlots();
    }
  }

  public SingleItemContainerMenu(int id, Inventory inv, FriendlyByteBuf buf) {
    this(id, inv, getTileEntityFromBuf(buf, BlockEntity.class));
  }

  @Override
  protected int getInventoryYOffset() {
    return 51;
  }
}
