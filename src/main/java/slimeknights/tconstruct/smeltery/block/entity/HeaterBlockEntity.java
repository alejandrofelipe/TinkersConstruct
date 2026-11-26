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
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import slimeknights.tconstruct.library.transfer.IModifiableItemHandlerProvider;
import slimeknights.tconstruct.library.transfer.ModifiableItemHandler;
import slimeknights.mantle.block.entity.NameableBlockEntity;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.utils.TagUtil;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.entity.inventory.HeaterItemHandler;
import slimeknights.tconstruct.smeltery.menu.SingleItemContainerMenu;

import org.jspecify.annotations.Nullable;

/** Tile entity for the heater block below the melter */
public class HeaterBlockEntity extends NameableBlockEntity implements IModifiableItemHandlerProvider {
  private static final String TAG_ITEM = "item";
  private static final Component TITLE = TConstruct.makeTranslation("gui", "heater");

  /** Inventory of the heater, native, also registered as the item capability */
  private final HeaterItemHandler itemHandler = new HeaterItemHandler(this);

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
    return itemHandler;
  }

  @Override
  public ModifiableItemHandler getModifiableItemHandler(@Nullable Direction side) {
    return ModifiableItemHandler.ofSingle(itemHandler);
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
