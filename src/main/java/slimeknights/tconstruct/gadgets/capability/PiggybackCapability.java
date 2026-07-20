package slimeknights.tconstruct.gadgets.capability;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import slimeknights.tconstruct.TConstruct;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Capability logic for the piggyback handler.
 * <p>
 * The capability lookup object is defined here; registration against players happens centrally in the
 * mod's {@code RegisterCapabilitiesEvent} handler (see {@link #provider(Entity, Direction)}).
 */
public class PiggybackCapability {
  private static final ResourceLocation ID = TConstruct.getResource("piggyback");

  /** Entity capability lookup replacing the old Forge {@code Capability} token */
  public static final EntityCapability<PiggybackHandler, Direction> PIGGYBACK =
    EntityCapability.createSided(ID, PiggybackHandler.class);

  /**
   * Backing attachment so each entity keeps a single persistent handler instance. NeoForge does not memoize
   * entity capabilities (unlike block caps with their {@code BlockCapabilityCache}), so a fresh handler would
   * otherwise be built on every per-tick {@code getCapability} call — losing {@link PiggybackHandler}'s
   * {@code lastPassengers} and re-broadcasting the set-passengers packet every tick instead of only on change.
   */
  private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
    DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, TConstruct.MOD_ID);
  private static final Supplier<AttachmentType<PiggybackHandler>> HANDLER =
    ATTACHMENTS.register("piggyback", () -> AttachmentType.<PiggybackHandler>builder(
      holder -> new PiggybackHandler(holder instanceof Player player ? player : null)).build());

  private PiggybackCapability() {}

  /** Registers the backing attachment on the mod bus; called from the mod constructor. */
  public static void init(IEventBus modBus) {
    ATTACHMENTS.register(modBus);
  }

  /**
   * Provider invoked by the central {@code RegisterCapabilitiesEvent} registration to build a handler for an entity.
   * Returns the entity's single cached handler (via the backing attachment) for players, null otherwise.
   */
  @Nullable
  public static PiggybackHandler provider(Entity entity, @Nullable Direction side) {
    if (entity instanceof Player) {
      return entity.getData(HANDLER.get());
    }
    return null;
  }
}
