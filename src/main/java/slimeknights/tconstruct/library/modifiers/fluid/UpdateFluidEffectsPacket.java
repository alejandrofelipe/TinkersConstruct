package slimeknights.tconstruct.library.modifiers.fluid;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.ApiStatus.Internal;
import slimeknights.mantle.network.packet.IThreadsafePacket;
import slimeknights.tconstruct.TConstruct;

import java.util.ArrayList;
import java.util.List;

/** Packet to sync fluid predicates to the client */
@Internal
public record UpdateFluidEffectsPacket(List<FluidEffects.Entry> fluids) implements IThreadsafePacket {
  public static final CustomPacketPayload.Type<UpdateFluidEffectsPacket> TYPE = new CustomPacketPayload.Type<>(TConstruct.getResource("update_fluid_effects"));
  public static final StreamCodec<RegistryFriendlyByteBuf, UpdateFluidEffectsPacket> STREAM_CODEC = StreamCodec.ofMember(UpdateFluidEffectsPacket::encode, UpdateFluidEffectsPacket::decode);

  /** Clientside constructor, reading from the buffer */
  public static UpdateFluidEffectsPacket decode(RegistryFriendlyByteBuf buffer) {
    int size = buffer.readVarInt();
    List<FluidEffects.Entry> entries = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      ResourceLocation key = buffer.readResourceLocation();
      FluidEffects effects = FluidEffects.LOADABLE.decode(buffer, FluidEffectManager.contextBuilder(key).build());
      entries.add(new FluidEffects.Entry(key, effects));
    }
    return new UpdateFluidEffectsPacket(List.copyOf(entries));
  }

  public void encode(RegistryFriendlyByteBuf buffer) {
    buffer.writeVarInt(fluids.size());
    for (FluidEffects.Entry entry : fluids) {
      buffer.writeResourceLocation(entry.name());
      FluidEffects.LOADABLE.encode(buffer, entry.effects());
    }
  }

  @Override
  public CustomPacketPayload.Type<UpdateFluidEffectsPacket> type() {
    return TYPE;
  }

  @Override
  public void handleThreadsafe(IPayloadContext context) {
    FluidEffectManager.INSTANCE.updateFromServer(fluids);
  }
}
