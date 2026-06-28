package slimeknights.tconstruct.library.materials.traits;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import slimeknights.mantle.network.packet.IThreadsafePacket;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialId;

import java.util.HashMap;
import java.util.Map;

public record UpdateMaterialTraitsPacket(Map<MaterialId,MaterialTraits> materialToTraits) implements IThreadsafePacket {
  public static final CustomPacketPayload.Type<UpdateMaterialTraitsPacket> TYPE = new CustomPacketPayload.Type<>(TConstruct.getResource("update_material_traits"));
  public static final StreamCodec<RegistryFriendlyByteBuf,UpdateMaterialTraitsPacket> STREAM_CODEC = StreamCodec.of(UpdateMaterialTraitsPacket::encode, UpdateMaterialTraitsPacket::decode);

  private static UpdateMaterialTraitsPacket decode(RegistryFriendlyByteBuf buffer) {
    int materialCount = buffer.readInt();
    Map<MaterialId,MaterialTraits> materialToTraits = new HashMap<>(materialCount);
    for (int i = 0; i < materialCount; i++) {
      MaterialId id = new MaterialId(buffer.readResourceLocation());
      MaterialTraits traits = MaterialTraits.read(buffer);
      materialToTraits.put(id, traits);
    }
    return new UpdateMaterialTraitsPacket(materialToTraits);
  }

  private static void encode(RegistryFriendlyByteBuf buffer, UpdateMaterialTraitsPacket packet) {
    buffer.writeInt(packet.materialToTraits.size());
    packet.materialToTraits.forEach((materialId, traits) -> {
      buffer.writeResourceLocation(materialId);
      traits.write(buffer);
    });
  }

  @Override
  public CustomPacketPayload.Type<UpdateMaterialTraitsPacket> type() {
    return TYPE;
  }

  @Override
  public void handleThreadsafe(IPayloadContext context) {
    MaterialRegistry.updateMaterialTraitsFromServer(this);
  }
}
