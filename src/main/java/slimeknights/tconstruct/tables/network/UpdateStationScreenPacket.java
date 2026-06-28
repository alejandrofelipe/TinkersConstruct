package slimeknights.tconstruct.tables.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import slimeknights.mantle.network.packet.IThreadsafePacket;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.tables.client.inventory.BaseTabbedScreen;

public record UpdateStationScreenPacket() implements IThreadsafePacket {
  public static final UpdateStationScreenPacket INSTANCE = new UpdateStationScreenPacket();
  public static final CustomPacketPayload.Type<UpdateStationScreenPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TConstruct.MOD_ID, "update_station_screen"));
  public static final StreamCodec<RegistryFriendlyByteBuf, UpdateStationScreenPacket> STREAM_CODEC = StreamCodec.unit(INSTANCE);

  @Override
  public CustomPacketPayload.Type<UpdateStationScreenPacket> type() {
    return TYPE;
  }

  @Override
  public void handleThreadsafe(IPayloadContext context) {
    HandleClient.handle();
  }

  /** Safely runs client side only code in a method only called on client */
  private static class HandleClient {
    private static void handle() {
      Screen screen = Minecraft.getInstance().screen;
      if (screen != null) {
        if (screen instanceof BaseTabbedScreen) {
          ((BaseTabbedScreen<?,?>) screen).updateDisplay();
        }
      }
    }
  }
}
