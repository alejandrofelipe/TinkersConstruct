package slimeknights.tconstruct.smeltery.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import slimeknights.mantle.network.packet.IThreadsafePacket;
import slimeknights.mantle.util.BlockEntityHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.smeltery.block.entity.controller.HeatingStructureBlockEntity;

import javax.annotation.Nullable;

/**
 * Packet to tell a multiblock to render a specific position as the cause of the error
 */
public record StructureErrorPositionPacket(BlockPos controllerPos, @Nullable BlockPos errorPos) implements IThreadsafePacket {
  public static final CustomPacketPayload.Type<StructureErrorPositionPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TConstruct.MOD_ID, "structure_error_position"));
  public static final StreamCodec<RegistryFriendlyByteBuf, StructureErrorPositionPacket> STREAM_CODEC = StreamCodec.of(
    (buffer, packet) -> {
      buffer.writeBlockPos(packet.controllerPos());
      if (packet.errorPos() != null) {
        buffer.writeBoolean(true);
        buffer.writeBlockPos(packet.errorPos());
      } else {
        buffer.writeBoolean(false);
      }
    },
    buffer -> {
      BlockPos controllerPos = buffer.readBlockPos();
      BlockPos errorPos = buffer.readBoolean() ? buffer.readBlockPos() : null;
      return new StructureErrorPositionPacket(controllerPos, errorPos);
    });

  @Override
  public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }

  @Override
  public void handleThreadsafe(IPayloadContext context) {
    HandleClient.handle(this);
  }

  private static class HandleClient {
    private static void handle(StructureErrorPositionPacket packet) {
      BlockEntityHelper.get(HeatingStructureBlockEntity.class, Minecraft.getInstance().level, packet.controllerPos())
                       .ifPresent(te -> te.setErrorPos(packet.errorPos()));
    }
  }
}
