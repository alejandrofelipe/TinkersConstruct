package slimeknights.tconstruct.smeltery.menu;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import slimeknights.tconstruct.library.transfer.IModifiableItemHandlerProvider;
import slimeknights.tconstruct.library.transfer.ModifiableItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import slimeknights.tconstruct.library.transfer.TransferUtil;
import slimeknights.mantle.fluid.FluidTransferHelper;
import slimeknights.mantle.fluid.transfer.IFluidContainerTransfer.TransferDirection;
import slimeknights.mantle.fluid.transfer.IFluidContainerTransfer.TransferResult;
import slimeknights.mantle.util.sync.ValidZeroDataSlot;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.shared.inventory.TriggeringBaseContainerMenu;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.entity.controller.MelterBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.module.MeltingModuleInventory;

import org.jspecify.annotations.Nullable;
import java.util.function.Consumer;

public class MelterContainerMenu extends TriggeringBaseContainerMenu<MelterBlockEntity> {
  public static final Identifier TOOLTIP_FORMAT = TConstruct.getResource("melter");

  @SuppressWarnings("MismatchedReadAndWriteOfArray")
  @Getter
  private final Slot[] inputs;
  @Getter
  private boolean hasFuelSlot = false;
  public MelterContainerMenu(int id, @Nullable Inventory inv, @Nullable MelterBlockEntity melter) {
    super(TinkerSmeltery.melterContainer.get(), id, inv, melter);

    // create slots
    if (melter != null) {
      MeltingModuleInventory inventory = melter.getItemHandler();
      ModifiableItemHandler inputHandler = melter.getModifiableItemHandler(null);
      inputs = new Slot[inventory.getSlots()];
      for (int i = 0; i < inputs.length; i++) {
        inputs[i] = this.addSlot(inputHandler.createSlot(inv == null ? null : inv.player, i, 22, 16 + (i * 18)));
      }

      // add fuel slot if present, we only add for the melter though
      Level world = melter.getLevel();
      BlockPos down = melter.getBlockPos().below();
      if (world != null && world.getBlockState(down).is(TinkerTags.Blocks.FUEL_TANKS)) {
        // PORT 1.21.10: menu slots need a setter for the real stacks, not just the native capability
        ModifiableItemHandler handler = IModifiableItemHandlerProvider.find(world, down, null);
        if (handler != null && handler.size() > 0) {
          this.addSlot(handler.createSlot(inv == null ? null : inv.player, 0, 151, 32));
          hasFuelSlot = true;
        }
      }

      this.addInventorySlots();

      // syncing
      Consumer<DataSlot> referenceConsumer = this::addDataSlot;
      ValidZeroDataSlot.trackIntArray(referenceConsumer, melter.getFuelModule());
      inventory.trackInts(array -> ValidZeroDataSlot.trackIntArray(referenceConsumer, array));
    } else {
      inputs = new Slot[0];
    }
  }

  public MelterContainerMenu(int id, Inventory inv, FriendlyByteBuf buf) {
    this(id, inv, getTileEntityFromBuf(buf, MelterBlockEntity.class));
  }

  @Override
  public boolean clickMenuButton(Player player, int id) {
    if (0 <= id && id <= 3 && !player.isSpectator()) {
      ItemStack held = getCarried();
      if (!held.isEmpty()) {
        if (!player.level().isClientSide() && tile != null) {
          ResourceHandler<FluidResource> tank = id < 2 ? tile.getTank() : tile.getFuelModule().getTank();
          TransferResult result;
          // even means drain fluid, odd means fill
          if ((id & 1) == 0) {
            result = FluidTransferHelper.fillStack(tank, held, TransferUtil.getFluid(tank, 0));
          } else {
            result = FluidTransferHelper.interactWithStack(tank, held, TransferDirection.EMPTY_ITEM);
          }
          setCarried(FluidTransferHelper.handleUIResult(player, held, result));
        }
        return true;
      }
    }
    return false;
  }
}
