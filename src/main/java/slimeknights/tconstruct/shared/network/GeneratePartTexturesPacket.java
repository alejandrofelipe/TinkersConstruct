package slimeknights.tconstruct.shared.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import slimeknights.mantle.network.packet.IThreadsafePacket;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.shared.client.ClientGeneratePartTexturesCommand;

/** Packet to tell the client to generate tool textures */
public record GeneratePartTexturesPacket(Operation operation, String modId, String materialPath) implements IThreadsafePacket {
  public static final CustomPacketPayload.Type<GeneratePartTexturesPacket> TYPE = new CustomPacketPayload.Type<>(TConstruct.getResource("generate_part_textures"));
  public static final StreamCodec<RegistryFriendlyByteBuf,GeneratePartTexturesPacket> STREAM_CODEC = StreamCodec.composite(
    NeoForgeStreamCodecs.enumCodec(Operation.class), GeneratePartTexturesPacket::operation,
    ByteBufCodecs.STRING_UTF8, GeneratePartTexturesPacket::modId,
    ByteBufCodecs.STRING_UTF8, GeneratePartTexturesPacket::materialPath,
    GeneratePartTexturesPacket::new);

  @Override
  public CustomPacketPayload.Type<GeneratePartTexturesPacket> type() {
    return TYPE;
  }

  @Override
  public void handleThreadsafe(IPayloadContext context) {
    ClientGeneratePartTexturesCommand.generateTextures(operation, modId, materialPath);
  }

  public enum Operation { ALL, MISSING }
}
