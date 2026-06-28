package slimeknights.tconstruct.tables.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import slimeknights.mantle.network.packet.IThreadsafePacket;
import slimeknights.mantle.util.BlockEntityHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationRecipe;
import slimeknights.tconstruct.tables.client.inventory.TinkerStationScreen;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;

import java.util.Optional;

/**
 * Packet to send the current crafting recipe to a player who opens the tinker station
 */
public record UpdateTinkerStationRecipePacket(BlockPos pos, ResourceLocation recipe) implements IThreadsafePacket {
  public static final CustomPacketPayload.Type<UpdateTinkerStationRecipePacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TConstruct.MOD_ID, "update_tinker_station_recipe"));
  public static final StreamCodec<RegistryFriendlyByteBuf, UpdateTinkerStationRecipePacket> STREAM_CODEC = StreamCodec.composite(
    BlockPos.STREAM_CODEC, UpdateTinkerStationRecipePacket::pos,
    ResourceLocation.STREAM_CODEC, UpdateTinkerStationRecipePacket::recipe,
    UpdateTinkerStationRecipePacket::new);

  @Override
  public CustomPacketPayload.Type<UpdateTinkerStationRecipePacket> type() {
    return TYPE;
  }

  @Override
  public void handleThreadsafe(IPayloadContext context) {
    HandleClient.handle(this);
  }

  /** Safely runs client side only code in a method only called on client */
  private static class HandleClient {
    private static void handle(UpdateTinkerStationRecipePacket packet) {
      Level world = Minecraft.getInstance().level;
      if (world != null) {
        @SuppressWarnings("unchecked")
        Optional<RecipeHolder<ITinkerStationRecipe>> recipe = world.getRecipeManager().byKey(packet.recipe)
          .filter(holder -> holder.value() instanceof ITinkerStationRecipe)
          .map(holder -> (RecipeHolder<ITinkerStationRecipe>) (RecipeHolder<?>) holder);

        // if the screen is open, use that to get the TE and update the screen
        boolean handled = false;
        if (Minecraft.getInstance().screen instanceof TinkerStationScreen stationScreen) {
          TinkerStationBlockEntity te = stationScreen.getTileEntity();
          if (te.getBlockPos().equals(packet.pos)) {
            recipe.ifPresent(te::updateRecipe);
            stationScreen.updateDisplay();
            handled = true;
          }
        }
        // if the wrong screen is open or no screen, use the tile directly
        if (!handled) {
          recipe.ifPresent(r -> BlockEntityHelper.get(TinkerStationBlockEntity.class, world, packet.pos).ifPresent(te -> te.updateRecipe(r)));
        }
      }
    }
  }
}
