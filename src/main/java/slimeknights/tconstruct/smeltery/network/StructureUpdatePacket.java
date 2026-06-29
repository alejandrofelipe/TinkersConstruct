package slimeknights.tconstruct.smeltery.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import slimeknights.mantle.network.packet.IThreadsafePacket;
import slimeknights.mantle.util.BlockEntityHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.smeltery.block.entity.controller.HeatingStructureBlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Packet sent when the smeltery or foundry structure changes
 */
public record StructureUpdatePacket(BlockPos pos, BlockPos minPos, BlockPos maxPos, List<BlockPos> tanks) implements IThreadsafePacket {
  public static final CustomPacketPayload.Type<StructureUpdatePacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TConstruct.MOD_ID, "structure_update"));
  public static final StreamCodec<RegistryFriendlyByteBuf, StructureUpdatePacket> STREAM_CODEC = StreamCodec.composite(
    BlockPos.STREAM_CODEC, StructureUpdatePacket::pos,
    BlockPos.STREAM_CODEC, StructureUpdatePacket::minPos,
    BlockPos.STREAM_CODEC, StructureUpdatePacket::maxPos,
    ByteBufCodecs.<RegistryFriendlyByteBuf, BlockPos>collection(ArrayList::new, BlockPos.STREAM_CODEC), StructureUpdatePacket::tanks,
    StructureUpdatePacket::new);

  @Override
  public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }

  @Override
  public void handleThreadsafe(IPayloadContext context) {
    HandleClient.handle(this);
  }

  private static class HandleClient {
    private static void handle(StructureUpdatePacket packet) {
      BlockEntityHelper.get(HeatingStructureBlockEntity.class, Minecraft.getInstance().level, packet.pos())
                       .ifPresent(te -> te.setStructureSize(packet.minPos(), packet.maxPos(), packet.tanks()));
    }
  }
}
