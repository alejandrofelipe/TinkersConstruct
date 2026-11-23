package slimeknights.tconstruct.common.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import slimeknights.mantle.network.packet.IThreadsafePacket;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.transfer.ILegacyItemHandlerProvider;

public record InventorySlotSyncPacket(ItemStack itemStack, int slot, BlockPos pos) implements IThreadsafePacket {
  public static final CustomPacketPayload.Type<InventorySlotSyncPacket> TYPE = new CustomPacketPayload.Type<>(TConstruct.getResource("inventory_slot_sync"));
  public static final StreamCodec<RegistryFriendlyByteBuf,InventorySlotSyncPacket> STREAM_CODEC = StreamCodec.composite(
    ItemStack.OPTIONAL_STREAM_CODEC, InventorySlotSyncPacket::itemStack,
    ByteBufCodecs.VAR_INT, InventorySlotSyncPacket::slot,
    BlockPos.STREAM_CODEC, InventorySlotSyncPacket::pos,
    InventorySlotSyncPacket::new);

  @Override
  public CustomPacketPayload.Type<InventorySlotSyncPacket> type() {
    return TYPE;
  }

  @Override
  public void handleThreadsafe(IPayloadContext context) {
    HandleClient.handle(this);
  }

  /** Safely runs client side only code in a method only called on client */
  private static class HandleClient {
    private static void handle(InventorySlotSyncPacket packet) {
      Level world = Minecraft.getInstance().level;
      if (world != null) {
        // PORT 1.21.10: the capability is native now and its legacy bridge is not modifiable, so update the real stacks of the block
        IItemHandlerModifiable modifiable = ILegacyItemHandlerProvider.find(world, packet.pos, null);
        if (modifiable != null) {
          modifiable.setStackInSlot(packet.slot, packet.itemStack);
          //noinspection ConstantConditions
          Minecraft.getInstance().levelRenderer.blockChanged(null, packet.pos, null, null, 0);
        }
      }
    }
  }
}
