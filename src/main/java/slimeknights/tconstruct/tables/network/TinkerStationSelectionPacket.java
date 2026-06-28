package slimeknights.tconstruct.tables.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import slimeknights.mantle.network.packet.IThreadsafePacket;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.tools.layout.StationSlotLayoutLoader;
import slimeknights.tconstruct.tables.menu.TinkerStationContainerMenu;

public record TinkerStationSelectionPacket(ResourceLocation layoutName) implements IThreadsafePacket {
  public static final CustomPacketPayload.Type<TinkerStationSelectionPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TConstruct.MOD_ID, "tinker_station_selection"));
  public static final StreamCodec<RegistryFriendlyByteBuf, TinkerStationSelectionPacket> STREAM_CODEC = StreamCodec.composite(
    ResourceLocation.STREAM_CODEC, TinkerStationSelectionPacket::layoutName,
    TinkerStationSelectionPacket::new);

  @Override
  public CustomPacketPayload.Type<TinkerStationSelectionPacket> type() {
    return TYPE;
  }

  @Override
  public void handleThreadsafe(IPayloadContext context) {
    if (context.player() instanceof ServerPlayer sender) {
      AbstractContainerMenu container = sender.containerMenu;
      if (container instanceof TinkerStationContainerMenu tinker) {
        tinker.setToolSelection(StationSlotLayoutLoader.getInstance().get(layoutName));
      }
    }
  }
}
