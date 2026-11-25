package slimeknights.tconstruct.tables.block.entity.chest;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import slimeknights.mantle.block.entity.NameableBlockEntity;
import slimeknights.tconstruct.library.transfer.ILegacyItemHandlerProvider;
import slimeknights.tconstruct.library.transfer.ItemHandlerResourceHandler;
import slimeknights.tconstruct.library.utils.TagUtil;
import slimeknights.tconstruct.tables.block.entity.inventory.IChestItemHandler;
import slimeknights.tconstruct.tables.menu.TinkerChestContainerMenu;

import javax.annotation.Nullable;

/** Shared base logic for all Tinkers' chest tile entities */
public abstract class AbstractChestBlockEntity extends NameableBlockEntity implements ILegacyItemHandlerProvider {
  protected static final String KEY_ITEMS = "Items";

  @Getter
  private final IChestItemHandler itemHandler;
  /**
   * Native view of {@link #itemHandler}, registered as the item capability.
   * PORT 1.21.10 (transfer step 1): changes are silent until the root transaction commits, then replayed through the legacy handler.
   */
  @Getter
  private final ResourceHandler<ItemResource> itemCapability;
  protected AbstractChestBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Component name, IChestItemHandler itemHandler) {
    super(type, pos, state, name);
    itemHandler.setParent(this);
    this.itemHandler = itemHandler;
    this.itemCapability = new ItemHandlerResourceHandler(itemHandler, itemHandler::setStackSilently);
  }

  @Override
  public IItemHandlerModifiable getLegacyItemHandler(@Nullable Direction side) {
    return itemHandler;
  }

  @Nullable
  @Override
  public AbstractContainerMenu createMenu(int menuId, Inventory playerInventory, Player playerEntity) {
    return new TinkerChestContainerMenu(menuId, playerInventory, this);
  }

  /**
   * Checks if the given item should be inserted into the chest on interact
   * @param player    Player inserting
   * @param heldItem  Stack to insert
   * @return  Return true
   */
  public boolean canInsert(Player player, ItemStack heldItem) {
    return true;
  }

  @Override
  public void saveAdditional(ValueOutput tags) {
    super.saveAdditional(tags);
    // write just the items list, same as the handler's serialized "Items" (ItemStackWithSlot)
    // we don't care about the size and need it here for compat with old worlds
    ValueOutput.TypedOutputList<ItemStackWithSlot> items = tags.list(KEY_ITEMS, ItemStackWithSlot.CODEC);
    for (int i = 0; i < itemHandler.getSlots(); i++) {
      ItemStack stack = itemHandler.getStackInSlot(i);
      if (!stack.isEmpty()) {
        items.add(new ItemStackWithSlot(i, stack));
      }
    }
  }

  /** Reads the inventory from a raw tag, used for the legacy item data */
  public void readInventory(CompoundTag tags, HolderLookup.Provider registries) {
    TagUtil.readTag(problemPath(), registries, tags, this::readInventory);
  }

  /** Reads the inventory from the given input */
  public void readInventory(ValueInput tags) {
    // copy in just the items key for deserializing, don't want to change the size
    CompoundTag handlerNBT = new CompoundTag();
    tags.read(KEY_ITEMS, ExtraCodecs.NBT).ifPresent(items -> handlerNBT.put(KEY_ITEMS, items));
    TagUtil.readTag(problemPath(), tags.lookup(), handlerNBT, itemHandler::deserialize);
  }

  @Override
  protected void loadAdditional(ValueInput tags) {
    super.loadAdditional(tags);
    readInventory(tags);
  }
}
