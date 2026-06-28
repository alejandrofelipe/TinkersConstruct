package slimeknights.tconstruct.library.materials.stats;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.apache.logging.log4j.Logger;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.network.packet.IThreadsafePacket;
import slimeknights.mantle.util.typed.TypedMapBuilder;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.utils.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record UpdateMaterialStatsPacket(Map<MaterialId,Collection<IMaterialStats>> materialToStats) implements IThreadsafePacket {
  private static final Logger log = Util.getLogger("NetworkSync");

  public static final CustomPacketPayload.Type<UpdateMaterialStatsPacket> TYPE = new CustomPacketPayload.Type<>(TConstruct.getResource("update_material_stats"));
  public static final StreamCodec<RegistryFriendlyByteBuf,UpdateMaterialStatsPacket> STREAM_CODEC = StreamCodec.of(UpdateMaterialStatsPacket::encode, UpdateMaterialStatsPacket::decode);

  private static UpdateMaterialStatsPacket decode(RegistryFriendlyByteBuf buffer) {
    return decode(buffer, MaterialRegistry.getInstance().getStatTypeLoader());
  }

  /** Decodes the packet, exposed for testing with a custom stat type loader */
  public static UpdateMaterialStatsPacket decode(RegistryFriendlyByteBuf buffer, Loadable<MaterialStatType<?>> statTypeLoader) {
    int materialCount = buffer.readInt();
    Map<MaterialId,Collection<IMaterialStats>> materialToStats = new HashMap<>(materialCount);
    for (int i = 0; i < materialCount; i++) {
      MaterialId id = new MaterialId(buffer.readResourceLocation());
      int statCount = buffer.readInt();
      List<IMaterialStats> statList = new ArrayList<>();
      for (int j = 0; j < statCount; j++) {
        try {
          MaterialStatType<?> statType = statTypeLoader.decode(buffer);
          statList.add(statType.getLoadable().decode(buffer, TypedMapBuilder.builder().put(MaterialStatType.CONTEXT_KEY, statType).build()));
        } catch (Exception e) {
          log.error("Could not deserialize stat. Are client and server in sync?", e);
        }
      }
      materialToStats.put(id, statList);
    }
    return new UpdateMaterialStatsPacket(materialToStats);
  }

  private static void encode(RegistryFriendlyByteBuf buffer, UpdateMaterialStatsPacket packet) {
    buffer.writeInt(packet.materialToStats.size());
    packet.materialToStats.forEach((materialId, stats) -> {
      buffer.writeResourceLocation(materialId);
      buffer.writeInt(stats.size());
      stats.forEach(stat -> encodeStat(buffer, stat, stat.getType()));
    });
  }

  /**
   * Encodes a single material stat
   * @param buffer  Buffer instance
   * @param stat    Stat to encode
   */
  @SuppressWarnings("unchecked")
  private static <T extends IMaterialStats> void encodeStat(RegistryFriendlyByteBuf buffer, IMaterialStats stat, MaterialStatType<T> type) {
    MaterialStatsId.PARSER.encode(buffer, type.getId());
    type.getLoadable().encode(buffer, (T) stat);
  }

  @Override
  public CustomPacketPayload.Type<UpdateMaterialStatsPacket> type() {
    return TYPE;
  }

  @Override
  public void handleThreadsafe(IPayloadContext context) {
    MaterialRegistry.updateMaterialStatsFromServer(this);
  }
}
