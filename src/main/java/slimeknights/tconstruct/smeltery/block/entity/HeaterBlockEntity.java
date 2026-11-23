package slimeknights.tconstruct.smeltery.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import slimeknights.tconstruct.library.transfer.ILegacyItemHandlerProvider;
import slimeknights.tconstruct.library.transfer.SingleItemResourceHandler;
import slimeknights.mantle.block.entity.NameableBlockEntity;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.utils.TagUtil;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.entity.inventory.HeaterItemHandler;
import slimeknights.tconstruct.smeltery.menu.SingleItemContainerMenu;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Tile entity for the heater block below the melter */
public class HeaterBlockEntity extends NameableBlockEntity implements ILegacyItemHandlerProvider {
  private static final String TAG_ITEM = "item";
  private static final Component TITLE = TConstruct.makeTranslation("gui", "heater");

  private final HeaterItemHandler itemHandler = new HeaterItemHandler(this);
  /** Native view of {@link #itemHandler}, registered as the item capability */
  private final SingleItemResourceHandler itemCapability = new SingleItemResourceHandler(itemHandler, itemHandler::setStack);

  protected HeaterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
    super(type, pos, state, TITLE);
  }

  public HeaterBlockEntity(BlockPos pos, BlockState state) {
    this(TinkerSmeltery.heater.get(), pos, state);
  }

  @Nullable
  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inventory, Player playerEntity) {
    return new SingleItemContainerMenu(id, inventory, this);
  }


  /* Capability */

  /** Exposes the item handler capability; registered centrally in RegisterCapabilitiesEvent */
  @Nullable
  public ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
    return itemCapability;
  }

  @Override
  public IItemHandlerModifiable getLegacyItemHandler(@Nullable Direction side) {
    return itemHandler;
  }


  /* NBT */

  @Override
  public void loadAdditional(ValueInput tags) {
    super.loadAdditional(tags);
    TagUtil.readSingleItem(tags, TAG_ITEM, itemHandler);
  }

  @Override
  public void saveAdditional(ValueOutput tags) {
    super.saveAdditional(tags);
    TagUtil.writeSingleItem(tags, TAG_ITEM, itemHandler);
  }
}
