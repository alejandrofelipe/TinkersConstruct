package slimeknights.tconstruct.tables.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import slimeknights.mantle.network.packet.IThreadsafePacket;
import slimeknights.mantle.util.BlockEntityHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.tables.block.entity.table.CraftingStationBlockEntity;

/**
 * Packet to send the current crafting recipe to a player who opens the crafting station
 */
public record UpdateCraftingRecipePacket(BlockPos pos, ResourceLocation recipe) implements IThreadsafePacket {
  public static final CustomPacketPayload.Type<UpdateCraftingRecipePacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TConstruct.MOD_ID, "update_crafting_recipe"));
  public static final StreamCodec<RegistryFriendlyByteBuf, UpdateCraftingRecipePacket> STREAM_CODEC = StreamCodec.composite(
    BlockPos.STREAM_CODEC, UpdateCraftingRecipePacket::pos,
    ResourceLocation.STREAM_CODEC, UpdateCraftingRecipePacket::recipe,
    UpdateCraftingRecipePacket::new);

  @Override
  public CustomPacketPayload.Type<UpdateCraftingRecipePacket> type() {
    return TYPE;
  }

  @Override
  public void handleThreadsafe(IPayloadContext context) {
    HandleClient.handle(this);
  }

  /** Safely runs client side only code in a method only called on client */
  private static class HandleClient {
    private static void handle(UpdateCraftingRecipePacket packet) {
      Level world = Minecraft.getInstance().level;
      if (world != null) {
        BlockEntityHelper.get(CraftingStationBlockEntity.class, world, packet.pos).ifPresent(te ->
          world.getRecipeManager().byKey(packet.recipe)
               .filter(holder -> holder.value() instanceof CraftingRecipe)
               .ifPresent(holder -> {
                 @SuppressWarnings("unchecked")
                 RecipeHolder<CraftingRecipe> craftingHolder = (RecipeHolder<CraftingRecipe>) (RecipeHolder<?>) holder;
                 te.updateRecipe(craftingHolder);
               }));
      }
    }
  }
}
