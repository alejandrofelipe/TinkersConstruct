package slimeknights.tconstruct.tools.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import slimeknights.mantle.network.packet.IThreadsafePacket;
import slimeknights.tconstruct.TConstruct;

public record EntityMovementChangePacket(int entityID, double x, double y, double z, float yRot, float xRot) implements IThreadsafePacket {
  public static final CustomPacketPayload.Type<EntityMovementChangePacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TConstruct.MOD_ID, "entity_movement_change"));
  public static final StreamCodec<RegistryFriendlyByteBuf, EntityMovementChangePacket> STREAM_CODEC = StreamCodec.of(
    (buffer, packet) -> {
      buffer.writeInt(packet.entityID);
      buffer.writeDouble(packet.x);
      buffer.writeDouble(packet.y);
      buffer.writeDouble(packet.z);
      buffer.writeFloat(packet.yRot);
      buffer.writeFloat(packet.xRot);
    },
    buffer -> new EntityMovementChangePacket(buffer.readInt(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat(), buffer.readFloat()));

  public EntityMovementChangePacket(Entity entity) {
    this(entity.getId(), entity.getDeltaMovement().x, entity.getDeltaMovement().y, entity.getDeltaMovement().z, entity.getYRot(), entity.getXRot());
  }

  @Override
  public CustomPacketPayload.Type<EntityMovementChangePacket> type() {
    return TYPE;
  }

  @Override
  public void handleThreadsafe(IPayloadContext context) {
    HandleClient.handle(this);
  }

  /** Safely runs client side only code in a method only called on client */
  private static class HandleClient {
    private static void handle(EntityMovementChangePacket packet) {
      assert Minecraft.getInstance().level != null;
      Entity entity = Minecraft.getInstance().level.getEntity(packet.entityID);
      if (entity != null) {
        entity.setDeltaMovement(packet.x, packet.y, packet.z);
        entity.setYRot(packet.yRot);
        entity.setXRot(packet.xRot);
      }
    }
  }
}
