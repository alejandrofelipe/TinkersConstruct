package slimeknights.tconstruct.common.network;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import slimeknights.mantle.network.packet.IThreadsafePacket;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.tools.capability.PersistentDataCapability;

/** Packet to sync player persistent data to the client */
public record SyncPersistentDataPacket(CompoundTag data) implements IThreadsafePacket {
  public static final CustomPacketPayload.Type<SyncPersistentDataPacket> TYPE = new CustomPacketPayload.Type<>(TConstruct.getResource("sync_persistent_data"));
  public static final StreamCodec<RegistryFriendlyByteBuf,SyncPersistentDataPacket> STREAM_CODEC = StreamCodec.composite(
    ByteBufCodecs.COMPOUND_TAG, SyncPersistentDataPacket::data,
    SyncPersistentDataPacket::new);

  @Override
  public CustomPacketPayload.Type<SyncPersistentDataPacket> type() {
    return TYPE;
  }

  @Override
  public void handleThreadsafe(IPayloadContext context) {
    HandleClient.handle(this);
  }

  /** Handles client side only code safely */
  private static class HandleClient {
    private static void handle(SyncPersistentDataPacket packet) {
      Player player = Minecraft.getInstance().player;
      if (player != null) {
        PersistentDataCapability.getData(player).copyFrom(packet.data);
      }
    }
  }
}
