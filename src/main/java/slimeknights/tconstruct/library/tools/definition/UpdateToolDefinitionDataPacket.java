package slimeknights.tconstruct.library.tools.definition;

import com.google.common.collect.ImmutableMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import slimeknights.mantle.network.packet.IThreadsafePacket;
import slimeknights.tconstruct.TConstruct;

import java.util.Map;
import java.util.Map.Entry;

/** Packet to sync tool definitions to the client */
public record UpdateToolDefinitionDataPacket(Map<ResourceLocation, ToolDefinitionData> dataMap) implements IThreadsafePacket {
  public static final CustomPacketPayload.Type<UpdateToolDefinitionDataPacket> TYPE = new CustomPacketPayload.Type<>(TConstruct.getResource("update_tool_definition_data"));
  public static final StreamCodec<RegistryFriendlyByteBuf, UpdateToolDefinitionDataPacket> STREAM_CODEC = StreamCodec.ofMember(UpdateToolDefinitionDataPacket::encode, UpdateToolDefinitionDataPacket::decode);

  /** Decodes the packet from the buffer */
  private static UpdateToolDefinitionDataPacket decode(RegistryFriendlyByteBuf buffer) {
    int size = buffer.readVarInt();
    ImmutableMap.Builder<ResourceLocation, ToolDefinitionData> builder = ImmutableMap.builder();
    for (int i = 0; i < size; i++) {
      ResourceLocation name = buffer.readResourceLocation();
      ToolDefinitionData data = ToolDefinitionData.LOADABLE.decode(buffer, ToolDefinitionLoader.contextBuilder(name).build());
      builder.put(name, data);
    }
    return new UpdateToolDefinitionDataPacket(builder.build());
  }

  /** Encodes the packet to the buffer */
  private void encode(RegistryFriendlyByteBuf buffer) {
    buffer.writeVarInt(dataMap.size());
    for (Entry<ResourceLocation, ToolDefinitionData> entry : dataMap.entrySet()) {
      buffer.writeResourceLocation(entry.getKey());
      ToolDefinitionData.LOADABLE.encode(buffer, entry.getValue());
    }
  }

  @Override
  public CustomPacketPayload.Type<UpdateToolDefinitionDataPacket> type() {
    return TYPE;
  }

  @Override
  public void handleThreadsafe(IPayloadContext context) {
    ToolDefinitionLoader.getInstance().updateDataFromServer(dataMap);
  }
}
