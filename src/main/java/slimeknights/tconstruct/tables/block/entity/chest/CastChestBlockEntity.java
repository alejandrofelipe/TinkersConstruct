package slimeknights.tconstruct.tables.block.entity.chest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueOutput;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tables.block.entity.inventory.IChestItemHandler;
import slimeknights.tconstruct.tables.block.entity.inventory.ScalingChestItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Chest that holds casts, up to 4 of every type.
 * The inventory is kept when broken through the vanilla {@link DataComponents#CONTAINER} component, like a shulker box: the loot table
 * copies it from the block entity and placing the item applies it back.
 */
public class CastChestBlockEntity extends AbstractChestBlockEntity {
  private static final Component NAME = TConstruct.makeTranslation("gui", "cast_chest");
  public CastChestBlockEntity(BlockPos pos, BlockState state) {
    super(TinkerTables.castChestTile.get(), pos, state, NAME, new CastChestIItemHandler());
  }


  /* Components */

  @Override
  protected void applyImplicitComponents(DataComponentGetter components) {
    super.applyImplicitComponents(components);
    // only replace the inventory when the item has contents, so block entity data with items (or the legacy custom data read on placement) still applies
    ItemContainerContents contents = components.get(DataComponents.CONTAINER);
    if (contents != null) {
      IChestItemHandler inventory = getItemHandler();
      NonNullList<ItemStack> items = NonNullList.withSize(inventory.getSlots(), ItemStack.EMPTY);
      contents.copyInto(items);
      for (int i = 0; i < items.size(); i++) {
        inventory.setStackInSlot(i, items.get(i));
      }
    }
  }

  @Override
  protected void collectImplicitComponents(DataComponentMap.Builder components) {
    super.collectImplicitComponents(components);
    // an empty chest has no component, so it stacks with crafted ones
    IChestItemHandler inventory = getItemHandler();
    List<ItemStack> items = new ArrayList<>(inventory.getSlots());
    for (int i = 0; i < inventory.getSlots(); i++) {
      items.add(inventory.getStackInSlot(i));
    }
    ItemContainerContents contents = ItemContainerContents.fromItems(items);
    if (contents != ItemContainerContents.EMPTY) {
      components.set(DataComponents.CONTAINER, contents);
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  public void removeComponentsFromTag(ValueOutput output) {
    super.removeComponentsFromTag(output);
    output.discard(KEY_ITEMS);
  }

  /** Item handler for cast chests */
  public static class CastChestIItemHandler extends ScalingChestItemHandler {
    @Override
    public int getSlotLimit(int slot) {
      return 4;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
      for (int i = 0; i < this.getSlots(); i++) {
        if (ItemStack.isSameItem(stack, this.getStackInSlot(i))) {
          return i == slot;
        }
      }
      return stack.is(TinkerTags.Items.CASTS);
    }
  }
}
