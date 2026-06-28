package slimeknights.tconstruct.common.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import slimeknights.mantle.network.packet.IThreadsafePacket;
import slimeknights.tconstruct.TConstruct;

/**
 * Packet to notify neighbors that a block changed, used when breaking blocks in weird contexts that vanilla suppresses updates in for some reason
 */
public record UpdateNeighborsPacket(BlockState state, BlockPos pos) implements IThreadsafePacket {
  public static final CustomPacketPayload.Type<UpdateNeighborsPacket> TYPE = new CustomPacketPayload.Type<>(TConstruct.getResource("update_neighbors"));
  /** Stream codec for a block state, encoded by its global state id (matching Mantle's BlockStateLoadable network format) */
  private static final StreamCodec<RegistryFriendlyByteBuf,BlockState> STATE_CODEC = StreamCodec.of(
    (buffer, state) -> buffer.writeVarInt(Block.getId(state)),
    buffer -> Block.stateById(buffer.readVarInt()));
  public static final StreamCodec<RegistryFriendlyByteBuf,UpdateNeighborsPacket> STREAM_CODEC = StreamCodec.composite(
    STATE_CODEC, UpdateNeighborsPacket::state,
    BlockPos.STREAM_CODEC, UpdateNeighborsPacket::pos,
    UpdateNeighborsPacket::new);

  @Override
  public CustomPacketPayload.Type<UpdateNeighborsPacket> type() {
    return TYPE;
  }

  @Override
  public void handleThreadsafe(IPayloadContext context) {
    HandleClient.handle(this);
  }

  private static class HandleClient {
    private static void handle(UpdateNeighborsPacket packet) {
      Level level = Minecraft.getInstance().level;
      if (level != null) {
        packet.state.updateNeighbourShapes(level, packet.pos, Block.UPDATE_CLIENTS, 511);
        packet.state.updateIndirectNeighbourShapes(level, packet.pos, Block.UPDATE_CLIENTS, 511);
      }
    }
  }
}
