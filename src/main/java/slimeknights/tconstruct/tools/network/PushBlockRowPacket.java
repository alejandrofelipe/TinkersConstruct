package slimeknights.tconstruct.tools.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import slimeknights.mantle.network.packet.IThreadsafePacket;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.modifiers.fluid.block.MoveBlocksFluidEffect;

/** Packet handling {@link MoveBlocksFluidEffect} syncing to the client */
public record PushBlockRowPacket(BlockPos pos, Direction direction, boolean push, int moving) implements IThreadsafePacket {
  public static final CustomPacketPayload.Type<PushBlockRowPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TConstruct.MOD_ID, "push_block_row"));
  public static final StreamCodec<RegistryFriendlyByteBuf, PushBlockRowPacket> STREAM_CODEC = StreamCodec.composite(
    BlockPos.STREAM_CODEC, PushBlockRowPacket::pos,
    NeoForgeStreamCodecs.enumCodec(Direction.class), PushBlockRowPacket::direction,
    ByteBufCodecs.BOOL, PushBlockRowPacket::push,
    ByteBufCodecs.VAR_INT, PushBlockRowPacket::moving,
    PushBlockRowPacket::new);

  /** Gets the facing value for this packet */
  private Direction facing() {
    return push ? direction : direction.getOpposite();
  }

  @Override
  public CustomPacketPayload.Type<PushBlockRowPacket> type() {
    return TYPE;
  }

  @Override
  public void handleThreadsafe(IPayloadContext context) {
    HandleClient.handle(this);
  }

  /** Accesses client only safely */
  private static class HandleClient {
    public static void handle(PushBlockRowPacket packet) {
      Level level = Minecraft.getInstance().level;
      if (level != null) {
        MoveBlocksFluidEffect.moveBlocks(level, packet.pos, level.getBlockState(packet.pos), packet.facing(), packet.direction, packet.moving);
      }
    }
  }
}
