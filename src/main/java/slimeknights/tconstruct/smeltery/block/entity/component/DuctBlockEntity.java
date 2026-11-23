package slimeknights.tconstruct.smeltery.block.entity.component;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import slimeknights.mantle.util.RetexturedHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.model.ModelProperties;
import slimeknights.tconstruct.library.transfer.ILegacyItemHandlerProvider;
import slimeknights.tconstruct.library.transfer.SingleItemResourceHandler;
import slimeknights.tconstruct.library.utils.TagUtil;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.entity.component.SmelteryInputOutputBlockEntity.SmelteryFluidIO;
import slimeknights.tconstruct.smeltery.block.entity.inventory.DuctItemHandler;
import slimeknights.tconstruct.smeltery.block.entity.inventory.DuctTankWrapper;
import slimeknights.tconstruct.smeltery.menu.SingleItemContainerMenu;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Filtered drain tile entity
 */
public class DuctBlockEntity extends SmelteryFluidIO implements MenuProvider, ILegacyItemHandlerProvider {
  private static final String TAG_ITEM = "item";
  private static final Component TITLE = TConstruct.makeTranslation("gui", "duct");

  @Getter
  private final DuctItemHandler itemHandler = new DuctItemHandler(this);
  /** Native view of {@link #itemHandler}, registered as the item capability */
  private final SingleItemResourceHandler itemCapability = new SingleItemResourceHandler(itemHandler, itemHandler::setStackSilently);

  public DuctBlockEntity(BlockPos pos, BlockState state) {
    this(TinkerSmeltery.duct.get(), pos, state);
  }

  protected DuctBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
    super(type, pos, state);
  }


  /* Container */

  @Override
  public Component getDisplayName() {
    return TITLE;
  }

  @Nullable
  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inventory, Player playerEntity) {
    return new SingleItemContainerMenu(id, inventory, this);
  }


  /* Capability */

  /** Gets the item handler for this duct; registered centrally on {@code RegisterCapabilitiesEvent}. */
  public ResourceHandler<ItemResource> getItemCapability() {
    return itemCapability;
  }

  @Override
  public IItemHandlerModifiable getLegacyItemHandler(@Nullable Direction side) {
    return itemHandler;
  }

  @Override
  protected ResourceHandler<FluidResource> makeWrapper(ResourceHandler<FluidResource> capability) {
    return new DuctTankWrapper(capability, itemHandler);
  }

  @Nonnull
  @Override
  public ModelData getModelData() {
    return RetexturedHelper.getModelDataBuilder(getTexture()).with(ModelProperties.FLUID_STACK, itemHandler.getFluid().copy()).build();
  }

  /** Updates the fluid in model data */
  public void updateFluid() {
    requestModelDataUpdate();
    assert level != null;
    BlockState state = getBlockState();
    level.sendBlockUpdated(worldPosition, state, state, 48);
  }


  /* NBT */

  @Override
  protected boolean shouldSyncOnUpdate() {
    return true;
  }

  @Override
  public void loadAdditional(ValueInput tags) {
    super.loadAdditional(tags);
    TagUtil.readSingleItem(tags, TAG_ITEM, itemHandler);
  }

  @Override
  public void handleUpdateTag(ValueInput tag) {
    super.handleUpdateTag(tag);
    if (level != null && level.isClientSide()) {
      updateFluid();
    }
  }

  @Override
  public void saveSynced(ValueOutput tags) {
    super.saveSynced(tags);
    TagUtil.writeSingleItem(tags, TAG_ITEM, itemHandler);
  }
}
