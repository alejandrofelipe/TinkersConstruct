package slimeknights.tconstruct.library.materials.definition;

import com.google.common.collect.ImmutableMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import slimeknights.mantle.network.packet.IThreadsafePacket;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.utils.GenericTagUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record UpdateMaterialsPacket(Map<MaterialId,IMaterial> materials, Map<MaterialId,MaterialId> redirects, Map<TagKey<IMaterial>,List<IMaterial>> tags) implements IThreadsafePacket {
  public static final CustomPacketPayload.Type<UpdateMaterialsPacket> TYPE = new CustomPacketPayload.Type<>(TConstruct.getResource("update_materials"));
  public static final StreamCodec<RegistryFriendlyByteBuf,UpdateMaterialsPacket> STREAM_CODEC = StreamCodec.of(UpdateMaterialsPacket::encode, UpdateMaterialsPacket::decode);

  private static UpdateMaterialsPacket decode(RegistryFriendlyByteBuf buffer) {
    int materialCount = buffer.readInt();
    ImmutableMap.Builder<MaterialId,IMaterial> materials = ImmutableMap.builder();
    for (int i = 0; i < materialCount; i++) {
      MaterialId id = new MaterialId(buffer.readResourceLocation());
      int tier = buffer.readVarInt();
      int sortOrder = buffer.readVarInt();
      boolean craftable = buffer.readBoolean();
      boolean hidden = buffer.readBoolean();
      materials.put(id, new Material(id, tier, sortOrder, craftable, hidden));
    }
    Map<MaterialId,IMaterial> materialMap = materials.build();
    // process redirects
    int redirectCount = buffer.readVarInt();
    Map<MaterialId,MaterialId> redirects;
    if (redirectCount == 0) {
      redirects = Collections.emptyMap();
    } else {
      redirects = new HashMap<>(redirectCount);
      for (int i = 0; i < redirectCount; i++) {
        redirects.put(new MaterialId(buffer.readUtf()), new MaterialId(buffer.readUtf()));
      }
    }
    Map<TagKey<IMaterial>,List<IMaterial>> tags = GenericTagUtil.decodeTags(buffer, MaterialManager.REGISTRY_KEY, id -> materialMap.get(new MaterialId(id)));
    return new UpdateMaterialsPacket(materialMap, redirects, tags);
  }

  private static void encode(RegistryFriendlyByteBuf buffer, UpdateMaterialsPacket packet) {
    buffer.writeInt(packet.materials.size());
    packet.materials.values().forEach(material -> {
      buffer.writeResourceLocation(material.getIdentifier());
      buffer.writeVarInt(material.getTier());
      buffer.writeVarInt(material.getSortOrder());
      buffer.writeBoolean(material.isCraftable());
      buffer.writeBoolean(material.isHidden());
    });
    buffer.writeVarInt(packet.redirects.size());
    packet.redirects.forEach((key, value) -> {
      buffer.writeUtf(key.toString());
      buffer.writeUtf(value.toString());
    });
    GenericTagUtil.encodeTags(buffer, IMaterial::getIdentifier, packet.tags);
  }

  @Override
  public CustomPacketPayload.Type<UpdateMaterialsPacket> type() {
    return TYPE;
  }

  @Override
  public void handleThreadsafe(IPayloadContext context) {
    MaterialRegistry.updateMaterialsFromServer(this);
  }
}
