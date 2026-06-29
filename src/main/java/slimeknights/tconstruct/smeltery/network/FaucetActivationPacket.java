package slimeknights.tconstruct.smeltery.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import slimeknights.mantle.network.packet.IThreadsafePacket;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.smeltery.block.entity.FaucetBlockEntity;

/** Sent to clients to activate the faucet animation clientside **/
public record FaucetActivationPacket(BlockPos pos, FluidStack fluid, boolean isPouring) implements IThreadsafePacket {
  public static final CustomPacketPayload.Type<FaucetActivationPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TConstruct.MOD_ID, "faucet_activation"));
  public static final StreamCodec<RegistryFriendlyByteBuf, FaucetActivationPacket> STREAM_CODEC = StreamCodec.composite(
    BlockPos.STREAM_CODEC, FaucetActivationPacket::pos,
    FluidStack.OPTIONAL_STREAM_CODEC, FaucetActivationPacket::fluid,
    ByteBufCodecs.BOOL, FaucetActivationPacket::isPouring,
    FaucetActivationPacket::new);

  @Override
  public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }

  @Override
  public void handleThreadsafe(IPayloadContext context) {
    HandleClient.handle(this);
  }

  /** Safely runs client side only code in a method only called on client */
  private static class HandleClient {
    private static void handle(FaucetActivationPacket packet) {
      assert Minecraft.getInstance().level != null;
      BlockEntity te = Minecraft.getInstance().level.getBlockEntity(packet.pos());
      if (te instanceof FaucetBlockEntity) {
        ((FaucetBlockEntity) te).onActivationPacket(packet.fluid(), packet.isPouring());
      }
    }
  }
}
