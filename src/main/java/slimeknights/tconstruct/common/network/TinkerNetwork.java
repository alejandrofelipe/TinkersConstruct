package slimeknights.tconstruct.common.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import slimeknights.mantle.network.NetworkWrapper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.materials.definition.UpdateMaterialsPacket;
import slimeknights.tconstruct.library.materials.stats.UpdateMaterialStatsPacket;
import slimeknights.tconstruct.library.materials.traits.UpdateMaterialTraitsPacket;
import slimeknights.tconstruct.library.modifiers.UpdateModifiersPacket;
import slimeknights.tconstruct.library.modifiers.fluid.UpdateFluidEffectsPacket;
import slimeknights.tconstruct.library.tools.definition.UpdateToolDefinitionDataPacket;
import slimeknights.tconstruct.library.tools.layout.UpdateTinkerSlotLayoutsPacket;
import slimeknights.tconstruct.shared.network.GeneratePartTexturesPacket;
import slimeknights.tconstruct.smeltery.network.ChannelFlowPacket;
import slimeknights.tconstruct.smeltery.network.FaucetActivationPacket;
import slimeknights.tconstruct.smeltery.network.FluidUpdatePacket;
import slimeknights.tconstruct.smeltery.network.SmelteryFluidClickedPacket;
import slimeknights.tconstruct.smeltery.network.SmelteryTankUpdatePacket;
import slimeknights.tconstruct.smeltery.network.StructureErrorPositionPacket;
import slimeknights.tconstruct.smeltery.network.StructureUpdatePacket;
import slimeknights.tconstruct.tables.network.StationTabPacket;
import slimeknights.tconstruct.tables.network.TinkerStationRenamePacket;
import slimeknights.tconstruct.tables.network.TinkerStationSelectionPacket;
import slimeknights.tconstruct.tables.network.UpdateCraftingRecipePacket;
import slimeknights.tconstruct.tables.network.UpdateStationScreenPacket;
import slimeknights.tconstruct.tables.network.UpdateTinkerStationRecipePacket;
import slimeknights.tconstruct.tools.network.EntityMovementChangePacket;
import slimeknights.tconstruct.tools.network.InteractWithAirPacket;
import slimeknights.tconstruct.tools.network.PushBlockRowPacket;
import slimeknights.tconstruct.tools.network.SyncProjectileModifiersPacket;
import slimeknights.tconstruct.tools.network.TinkerControlPacket;
import slimeknights.tconstruct.tools.network.ToolContainerFluidUpdatePacket;

import javax.annotation.Nullable;

/**
 * Base network class for all tinkers logic
 * <p>
 * In general, if you need to send packets you should use your own network class
 */
public class TinkerNetwork extends NetworkWrapper {
  private static TinkerNetwork instance = null;

  /*
   * Network versions:
   * 1: 3.10.1 and before
   * 2: 3.10.2 - new material stat type; item removal
   * 3: 3.11.2+ - lost track of how much changed but its a lot
   */
  private TinkerNetwork() {
    super(TConstruct.getResource("network"), "3");
  }

  /** Gets the instance of the network */
  public static TinkerNetwork getInstance() {
    if (instance == null) {
      throw new IllegalStateException("Attempt to call network getInstance before network is setup");
    }
    return instance;
  }

  /**
   * Called during mod construction to setup the network instance.
   * Actual payload registration happens in {@link #registerPayloads(RegisterPayloadHandlersEvent)}.
   */
  public static void setup() {
    if (instance == null) {
      instance = new TinkerNetwork();
    }
  }

  /**
   * Registers all tinkers payloads into the network. Wire this onto {@link RegisterPayloadHandlersEvent} on the mod bus.
   * @param event  Payload registration event
   */
  public static void registerPayloads(RegisterPayloadHandlersEvent event) {
    setup();
    // NOTE: RegisterPayloadHandlersEvent#registrar(String) takes the network VERSION (not a namespace);
    // the payload namespace comes from each packet's CustomPacketPayload.Type ResourceLocation.
    PayloadRegistrar registrar = event.registrar(instance.version);

    // shared
    registrar.playToClient(InventorySlotSyncPacket.TYPE, InventorySlotSyncPacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToClient(UpdateNeighborsPacket.TYPE, UpdateNeighborsPacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToClient(GeneratePartTexturesPacket.TYPE, GeneratePartTexturesPacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToClient(SyncPersistentDataPacket.TYPE, SyncPersistentDataPacket.STREAM_CODEC, (payload, context) -> payload.handle(context));

    // gadgets
    registrar.playToClient(EntityMovementChangePacket.TYPE, EntityMovementChangePacket.STREAM_CODEC, (payload, context) -> payload.handle(context));

    // tables
    registrar.playToServer(StationTabPacket.TYPE, StationTabPacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToServer(TinkerStationRenamePacket.TYPE, TinkerStationRenamePacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToClient(UpdateCraftingRecipePacket.TYPE, UpdateCraftingRecipePacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToServer(TinkerStationSelectionPacket.TYPE, TinkerStationSelectionPacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToClient(UpdateTinkerSlotLayoutsPacket.TYPE, UpdateTinkerSlotLayoutsPacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToClient(UpdateStationScreenPacket.TYPE, UpdateStationScreenPacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToClient(UpdateTinkerStationRecipePacket.TYPE, UpdateTinkerStationRecipePacket.STREAM_CODEC, (payload, context) -> payload.handle(context));

    // tools
    registrar.playToClient(UpdateMaterialsPacket.TYPE, UpdateMaterialsPacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToClient(UpdateMaterialStatsPacket.TYPE, UpdateMaterialStatsPacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToClient(UpdateMaterialTraitsPacket.TYPE, UpdateMaterialTraitsPacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToClient(UpdateToolDefinitionDataPacket.TYPE, UpdateToolDefinitionDataPacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToClient(ToolContainerFluidUpdatePacket.TYPE, ToolContainerFluidUpdatePacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToClient(SyncProjectileModifiersPacket.TYPE, SyncProjectileModifiersPacket.STREAM_CODEC, (payload, context) -> payload.handle(context));

    // modifiers
    registrar.playToServer(TinkerControlPacket.TYPE, TinkerControlPacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToServer(InteractWithAirPacket.TYPE, InteractWithAirPacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToClient(UpdateModifiersPacket.TYPE, UpdateModifiersPacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToClient(UpdateFluidEffectsPacket.TYPE, UpdateFluidEffectsPacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToClient(PushBlockRowPacket.TYPE, PushBlockRowPacket.STREAM_CODEC, (payload, context) -> payload.handle(context));

    // smeltery
    registrar.playToClient(FluidUpdatePacket.TYPE, FluidUpdatePacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToClient(FaucetActivationPacket.TYPE, FaucetActivationPacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToClient(ChannelFlowPacket.TYPE, ChannelFlowPacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToClient(SmelteryTankUpdatePacket.TYPE, SmelteryTankUpdatePacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToClient(StructureUpdatePacket.TYPE, StructureUpdatePacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToServer(SmelteryFluidClickedPacket.TYPE, SmelteryFluidClickedPacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToClient(StructureErrorPositionPacket.TYPE, StructureErrorPositionPacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
  }

  /**
   * Sends a vanilla packet to the given player
   * @param player  Player
   * @param packet  Packet
   */
  public void sendVanillaPacket(Entity player, Packet<?> packet) {
    if (player instanceof ServerPlayer serverPlayer) {
      serverPlayer.connection.send(packet);
    }
  }

  /**
   * Same as {@link #sendToClientsAround(CustomPacketPayload, ServerLevel, BlockPos)}, but checks that the world is a serverworld
   * @param msg       Packet to send
   * @param world     World instance
   * @param position  Target position
   */
  public void sendToClientsAround(CustomPacketPayload msg, @Nullable LevelAccessor world, BlockPos position) {
    if (world instanceof ServerLevel server) {
      sendToClientsAround(msg, server, position);
    }
  }

  /**
   * Sends a packet to the whole player list
   * @param targetedPlayer  Main player to target, if null uses whole list
   * @param playerList      Player list to use if main player is null
   * @param msg             Message to send
   */
  public void sendToPlayerList(@Nullable ServerPlayer targetedPlayer, PlayerList playerList, CustomPacketPayload msg) {
    if (targetedPlayer != null) {
      sendTo(msg, targetedPlayer);
    } else {
      for (ServerPlayer player : playerList.getPlayers()) {
        sendTo(msg, player);
      }
    }
  }
}
