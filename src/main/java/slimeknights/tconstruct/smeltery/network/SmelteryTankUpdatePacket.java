package slimeknights.tconstruct.smeltery.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import slimeknights.mantle.network.packet.IThreadsafePacket;
import slimeknights.mantle.util.BlockEntityHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.smeltery.block.entity.tank.ISmelteryTankHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Packet sent whenever the contents of the smeltery tank change
 */
public record SmelteryTankUpdatePacket(BlockPos pos, List<FluidStack> fluids) implements IThreadsafePacket {
  public static final CustomPacketPayload.Type<SmelteryTankUpdatePacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TConstruct.MOD_ID, "smeltery_tank_update"));
  public static final StreamCodec<RegistryFriendlyByteBuf, SmelteryTankUpdatePacket> STREAM_CODEC = StreamCodec.composite(
    BlockPos.STREAM_CODEC, SmelteryTankUpdatePacket::pos,
    ByteBufCodecs.<RegistryFriendlyByteBuf, FluidStack, List<FluidStack>>collection(ArrayList::new, FluidStack.OPTIONAL_STREAM_CODEC), SmelteryTankUpdatePacket::fluids,
    SmelteryTankUpdatePacket::new);

  @Override
  public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }

  @Override
  public void handleThreadsafe(IPayloadContext context) {
    HandleClient.handle(this);
  }

  private static class HandleClient {
    private static void handle(SmelteryTankUpdatePacket packet) {
      BlockEntityHelper.get(ISmelteryTankHandler.class, Minecraft.getInstance().level, packet.pos()).ifPresent(te -> te.updateFluidsFromPacket(packet.fluids()));
    }
  }
}
