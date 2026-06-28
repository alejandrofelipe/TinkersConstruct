package slimeknights.tconstruct.library.tools.capability;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.EventPriority;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.network.SyncPersistentDataPacket;
import slimeknights.tconstruct.common.network.TinkerNetwork;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Capability to store persistent NBT data on an entity. For players, this is automatically synced to the client on load, but not during gameplay.
 * Persists after death, will reassess if we need some data to not persist death
 */
public class PersistentDataCapability {
  private PersistentDataCapability() {}

  /** Capability ID */
  private static final ResourceLocation ID = TConstruct.getResource("persistent_data");
  /**
   * Capability instance. Under NeoForge this is an {@link EntityCapability} registered for supported entity types on
   * {@link RegisterCapabilitiesEvent}.
   * <p>
   * PORT M3: the Forge capability serialized its {@link ModDataNBT} so persistent data survived death/relog. NeoForge
   * entity capabilities are stateless, so the per-entity state lives in a {@link WeakHashMap} below and is no longer
   * persisted to disk (the in-memory copy is still synced to clients via the player events). For full parity, register a
   * {@code net.neoforged.neoforge.attachment.AttachmentType<ModDataNBT>} (with {@code copyOnDeath}) on the entity and
   * back {@link #getOrCreate} with it; the AttachmentType registration belongs in the entrypoint.
   */
  public static final EntityCapability<ModDataNBT,Void> CAPABILITY = EntityCapability.createVoid(ID, ModDataNBT.class);

  /** Per-entity persistent data storage. See PORT note on {@link #CAPABILITY}. */
  private static final Map<Entity,ModDataNBT> DATA = new WeakHashMap<>();

  /** Gets (creating if needed) the data for the given entity */
  private static ModDataNBT getOrCreate(Entity entity) {
    return DATA.computeIfAbsent(entity, e -> new ModDataNBT());
  }

  /** Gets the data or warns if its missing */
  public static ModDataNBT getOrWarn(Entity entity) {
    ModDataNBT data = entity.getCapability(CAPABILITY);
    if (data == null) {
      TConstruct.LOG.warn("Missing Tinkers NBT on entity {}, this should not happen", entity.getType());
      return new ModDataNBT();
    }
    return data;
  }

  /** Registers this capability's game-bus event listeners. Wire onto the game bus centrally (M3). */
  public static void registerListeners() {
    NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerEvent.Clone.class, PersistentDataCapability::playerClone);
    NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerEvent.PlayerRespawnEvent.class, PersistentDataCapability::playerRespawn);
    NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerEvent.PlayerChangedDimensionEvent.class, PersistentDataCapability::playerChangeDimension);
    NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerEvent.PlayerLoggedInEvent.class, PersistentDataCapability::playerLoggedIn);
  }

  /**
   * Registers the capability for supported entity types. Wire onto {@link RegisterCapabilitiesEvent} centrally (M3).
   * Attaches to living entities (used for potions) and anything supporting {@link EntityModifierCapability}.
   */
  public static void register(RegisterCapabilitiesEvent event) {
    for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
      event.registerEntity(CAPABILITY, type, (entity, ctx) -> {
        // must be on living entities as we use this for potions, but also support anything else with modifiers, this is their data
        if (entity instanceof LivingEntity || EntityModifierCapability.supportCapability(entity)) {
          return getOrCreate(entity);
        }
        return null;
      });
    }
  }

  /** Syncs the data to the given player */
  private static void sync(Player player) {
    ModDataNBT data = player.getCapability(CAPABILITY);
    if (data != null) {
      TinkerNetwork.getInstance().sendTo(new SyncPersistentDataPacket(data.getCopy()), player);
    }
  }

  /** copy caps when the player respawns/returns from the end */
  private static void playerClone(PlayerEvent.Clone event) {
    ModDataNBT oldData = event.getOriginal().getCapability(CAPABILITY);
    if (oldData != null) {
      CompoundTag nbt = oldData.getCopy();
      if (!nbt.isEmpty()) {
        ModDataNBT newData = event.getEntity().getCapability(CAPABILITY);
        if (newData != null) {
          newData.copyFrom(nbt);
        }
      }
    }
  }

  /** sync caps when the player respawns/returns from the end */
  private static void playerRespawn(PlayerEvent.PlayerRespawnEvent event) {
    sync(event.getEntity());
  }

  /** sync caps when the player changes dimensions */
  private static void playerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
    sync(event.getEntity());
  }

  /** sync caps when the player logs in */
  private static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
    sync(event.getEntity());
  }
}
