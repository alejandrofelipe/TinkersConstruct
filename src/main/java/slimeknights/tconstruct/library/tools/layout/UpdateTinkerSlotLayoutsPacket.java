package slimeknights.tconstruct.library.tools.layout;

import com.google.common.collect.ImmutableList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import slimeknights.mantle.network.packet.IThreadsafePacket;
import slimeknights.tconstruct.TConstruct;

import java.util.Collection;

/**
 * Packet to update the slot layouts for the tinker station
 */
public record UpdateTinkerSlotLayoutsPacket(Collection<StationSlotLayout> layouts) implements IThreadsafePacket {
  public static final CustomPacketPayload.Type<UpdateTinkerSlotLayoutsPacket> TYPE = new CustomPacketPayload.Type<>(TConstruct.getResource("update_tinker_slot_layouts"));
  public static final StreamCodec<RegistryFriendlyByteBuf, UpdateTinkerSlotLayoutsPacket> STREAM_CODEC = StreamCodec.ofMember(UpdateTinkerSlotLayoutsPacket::encode, UpdateTinkerSlotLayoutsPacket::decode);

  /** Decodes the packet from the buffer */
  private static UpdateTinkerSlotLayoutsPacket decode(RegistryFriendlyByteBuf buffer) {
    ImmutableList.Builder<StationSlotLayout> builder = ImmutableList.builder();
    int max = buffer.readVarInt();
    for (int i = 0; i < max; i++) {
      builder.add(StationSlotLayout.read(buffer));
    }
    return new UpdateTinkerSlotLayoutsPacket(builder.build());
  }

  /** Encodes the packet to the buffer */
  private void encode(RegistryFriendlyByteBuf buffer) {
    buffer.writeVarInt(layouts.size());
    for (StationSlotLayout layout : layouts) {
      layout.write(buffer);
    }
  }

  @Override
  public CustomPacketPayload.Type<UpdateTinkerSlotLayoutsPacket> type() {
    return TYPE;
  }

  @Override
  public void handleThreadsafe(IPayloadContext context) {
    StationSlotLayoutLoader.getInstance().setSlots(layouts);
  }
}
