package slimeknights.tconstruct.tables.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import slimeknights.mantle.network.packet.IThreadsafePacket;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.network.TinkerNetwork;
import slimeknights.tconstruct.tables.block.ITabbedBlock;

public record StationTabPacket(BlockPos pos) implements IThreadsafePacket {
  public static final CustomPacketPayload.Type<StationTabPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TConstruct.MOD_ID, "station_tab"));
  public static final StreamCodec<RegistryFriendlyByteBuf, StationTabPacket> STREAM_CODEC = StreamCodec.composite(
    BlockPos.STREAM_CODEC, StationTabPacket::pos,
    StationTabPacket::new);

  @Override
  public CustomPacketPayload.Type<StationTabPacket> type() {
    return TYPE;
  }

  @Override
  public void handleThreadsafe(IPayloadContext context) {
    if (context.player() instanceof ServerPlayer sender) {
      ItemStack heldStack = sender.containerMenu.getCarried();
      if (!heldStack.isEmpty()) {
        // set it to empty, so it's doesn't get dropped
        sender.containerMenu.setCarried(ItemStack.EMPTY);
      }

      Level world = sender.getCommandSenderWorld();
      if (!world.hasChunkAt(pos)) {
        return;
      }
      BlockState state = world.getBlockState(pos);
      if (state.getBlock() instanceof ITabbedBlock) {
        ((ITabbedBlock) state.getBlock()).openGui(sender, sender.getCommandSenderWorld(), pos);
      } else {
        MenuProvider provider = state.getMenuProvider(sender.getCommandSenderWorld(), pos);
        if (provider != null) {
          sender.openMenu(provider, pos);
        }
      }

      if (!heldStack.isEmpty()) {
        sender.containerMenu.setCarried(heldStack);
        TinkerNetwork.getInstance().sendVanillaPacket(sender, new ClientboundContainerSetSlotPacket(-1, -1, -1, heldStack));
      }
    }
  }
}
