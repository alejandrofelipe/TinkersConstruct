package slimeknights.tconstruct.tables.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import slimeknights.mantle.network.packet.IThreadsafePacket;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;
import slimeknights.tconstruct.tables.menu.TinkerStationContainerMenu;

/** Packet to send to the server to update the name in the UI */
public record TinkerStationRenamePacket(String name) implements IThreadsafePacket {
  public static final CustomPacketPayload.Type<TinkerStationRenamePacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TConstruct.MOD_ID, "tinker_station_rename"));
  public static final StreamCodec<RegistryFriendlyByteBuf, TinkerStationRenamePacket> STREAM_CODEC = StreamCodec.composite(
    ByteBufCodecs.STRING_UTF8, TinkerStationRenamePacket::name,
    TinkerStationRenamePacket::new);

  @Override
  public CustomPacketPayload.Type<TinkerStationRenamePacket> type() {
    return TYPE;
  }

  @Override
  public void handleThreadsafe(IPayloadContext context) {
    if (context.player() instanceof ServerPlayer sender && sender.containerMenu instanceof TinkerStationContainerMenu station) {
      TinkerStationBlockEntity tile = station.getTile();
      if (tile != null) {
        station.getTile().setItemName(name);
      }
    }
  }
}
