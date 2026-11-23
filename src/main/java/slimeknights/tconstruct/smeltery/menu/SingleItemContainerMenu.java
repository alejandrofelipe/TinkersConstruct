package slimeknights.tconstruct.smeltery.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import slimeknights.tconstruct.library.transfer.ILegacyItemHandlerProvider;
import net.neoforged.neoforge.items.IItemHandler;
import slimeknights.mantle.inventory.SmartItemHandlerSlot;
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
      // PORT 1.21.10: menu slots need the real stacks, which the native capability's legacy bridge does not give
      IItemHandler handler = level == null ? null : ILegacyItemHandlerProvider.find(level, te.getBlockPos(), null);
      if (handler != null) {
        this.addSlot(new SmartItemHandlerSlot(handler, 0, 80, 20));
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
