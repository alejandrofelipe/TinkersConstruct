package slimeknights.tconstruct.tools.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import slimeknights.mantle.client.SafeClientAccess;
import slimeknights.mantle.data.loadable.Streamable;
import slimeknights.mantle.network.packet.IThreadsafePacket;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.capability.EntityModifierCapability;
import slimeknights.tconstruct.library.tools.capability.PersistentDataCapability;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;

import java.util.Objects;

public record SyncProjectileModifiersPacket(int entityId, ModifierNBT modifiers, CompoundTag persistentData) implements IThreadsafePacket {
  private static final Streamable<ModifierNBT> MODIFIER_LIST = ModifierEntry.LOADABLE.list(0).flatXmap(ModifierNBT::new, ModifierNBT::getModifiers);
  public static final CustomPacketPayload.Type<SyncProjectileModifiersPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TConstruct.MOD_ID, "sync_projectile_modifiers"));
  public static final StreamCodec<RegistryFriendlyByteBuf, SyncProjectileModifiersPacket> STREAM_CODEC = StreamCodec.of(
    (buffer, packet) -> {
      buffer.writeVarInt(packet.entityId);
      MODIFIER_LIST.encode(buffer, packet.modifiers);
      buffer.writeNbt(packet.persistentData);
    },
    buffer -> new SyncProjectileModifiersPacket(buffer.readVarInt(), MODIFIER_LIST.decode(buffer), Objects.requireNonNullElse(buffer.readNbt(), new CompoundTag())));

  public SyncProjectileModifiersPacket(Entity entity) {
    this(entity.getId(), EntityModifierCapability.getOrEmpty(entity), PersistentDataCapability.getOrWarn(entity).getCopy());
  }

  @Override
  public CustomPacketPayload.Type<SyncProjectileModifiersPacket> type() {
    return TYPE;
  }

  @Override
  public void handleThreadsafe(IPayloadContext context) {
    Level level = SafeClientAccess.getLevel();
    if (level != null) {
      Entity entity = level.getEntity(entityId);
      if (entity != null) {
        EntityModifierCapability.getCapability(entity).setModifiers(modifiers);
        PersistentDataCapability.getOrWarn(entity).copyFrom(persistentData);
      }
    }
  }
}
