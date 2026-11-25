package slimeknights.tconstruct.tables.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType.BlockEntitySupplier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import slimeknights.tconstruct.library.utils.TagUtil;
import slimeknights.tconstruct.tables.block.entity.chest.AbstractChestBlockEntity;

import javax.annotation.Nullable;

/**
 * Shared block logic for all chest types
 */
public class ChestBlock extends TabbedTableBlock {
  private static final VoxelShape SHAPE = Shapes.or(
    Block.box(0.0D, 15.0D, 0.0D, 16.0D, 16.0D, 16.0D), //top
    Block.box(1.0D, 3.0D, 1.0D, 15.0D, 16.0D, 15.0D), //middle
    Block.box(0.5D, 0.0D, 0.5D, 2.5D, 15.0D, 2.5D), //leg
    Block.box(13.5D, 0.0D, 0.5D, 15.5D, 15.0D, 2.5D), //leg
    Block.box(13.5D, 0.0D, 13.5D, 15.5D, 15.0D, 15.5D), //leg
    Block.box(0.5D, 0.0D, 13.5D, 2.5D, 15.0D, 15.5D) //leg
                                                        );

  /** Custom data key holding the inventory on items dropped before 1.21.10 */
  private static final String LEGACY_DATA = "TinkerData";

  private final BlockEntitySupplier<? extends BlockEntity> blockEntity;
  private final boolean dropsItems;
  public ChestBlock(Properties builder, BlockEntitySupplier<? extends BlockEntity> blockEntity, boolean dropsItems) {
    super(builder);
    this.blockEntity = blockEntity;
    this.dropsItems = dropsItems;
  }

  @Nullable
  @Override
  public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
    return blockEntity.create(pPos, pState);
  }

  @Override
  public void setPlacedBy(Level worldIn, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
    super.setPlacedBy(worldIn, pos, state, placer, stack);
    // the cast chest inventory is the minecraft:container component, which BlockItem already applied (CastChestBlockEntity#applyImplicitComponents)
    // items dropped before 1.21.10 hold it in custom data instead (1.20 root tag, moved by the data fixer, and the 1.21.1 - 1.21.8 loot table)
    CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
    if (customData != null) {
      CompoundTag tag = customData.copyTag();
      if (TagUtil.contains(tag, LEGACY_DATA, Tag.TAG_COMPOUND) && worldIn.getBlockEntity(pos) instanceof AbstractChestBlockEntity chest) {
        chest.readInventory(tag.getCompoundOrEmpty(LEGACY_DATA), worldIn.registryAccess());
      }
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  @Deprecated
  public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
    return SHAPE;
  }

  @Override
  protected InteractionResult useItemOn(ItemStack heldItem, BlockState state, Level worldIn, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
    BlockEntity te = worldIn.getBlockEntity(pos);
    if (!heldItem.isEmpty() && te instanceof AbstractChestBlockEntity chest && chest.canInsert(player, heldItem)) {
      IItemHandlerModifiable itemHandler = chest.getItemHandler();
      ItemStack rest = ItemHandlerHelper.insertItem(itemHandler, heldItem, false);
      if (rest.isEmpty() || rest.getCount() < heldItem.getCount()) {
        player.setItemInHand(hand, rest);
        return InteractionResult.SUCCESS;
      }
    }
    // fall through to useWithoutItem (opens the GUI)
    return InteractionResult.TRY_WITH_EMPTY_HAND;
  }

  @Override
  public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
    // chests that keep their contents in the dropped item (cast chest) drop no loot when broken in creative, so drop the item with the
    // contents like ShulkerBoxBlock does; the component is only there when the chest has contents
    if (!dropsItems && !level.isClientSide() && player.preventsBlockDrops() && level.getBlockEntity(pos) instanceof AbstractChestBlockEntity chest) {
      DataComponentMap components = chest.collectComponents();
      if (components.has(DataComponents.CONTAINER)) {
        ItemStack stack = new ItemStack(this);
        stack.applyComponents(components);
        ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        entity.setDefaultPickUpDelay();
        level.addFreshEntity(entity);
      }
    }
    return super.playerWillDestroy(level, pos, state, player);
  }

  @Override
  public void preRemoveSideEffects(BlockState state, Level level, BlockPos pos, BlockEntity blockEntity) {
    // PORT 1.21.10: drop the real stacks rather than going through the item capability, whose legacy bridge hands out copies and would
    // leave the items in the chest; dropping splits each stack, and the slot is cleared, so the chest ends up empty
    if (blockEntity instanceof AbstractChestBlockEntity chest) {
      if (dropsItems) {
        IItemHandlerModifiable inventory = chest.getItemHandler();
        dropInventoryItems(level, pos, inventory);
        for (int i = 0; i < inventory.getSlots(); i++) {
          inventory.setStackInSlot(i, ItemStack.EMPTY);
        }
      }
      return;
    }
    super.preRemoveSideEffects(state, level, pos, blockEntity);
  }

  @Override
  protected void dropInventoryItems(BlockState state, Level worldIn, BlockPos pos, IItemHandler inventory) {
    if (dropsItems) {
      dropInventoryItems(worldIn, pos, inventory);
    }
  }
}
