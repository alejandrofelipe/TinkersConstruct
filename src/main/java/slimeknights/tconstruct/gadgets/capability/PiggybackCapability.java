package slimeknights.tconstruct.gadgets.capability;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.capabilities.EntityCapability;
import slimeknights.tconstruct.TConstruct;

import javax.annotation.Nullable;

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

  private PiggybackCapability() {}

  /**
   * Provider invoked by the central {@code RegisterCapabilitiesEvent} registration to build a handler for an entity.
   * Returns a handler only for players, matching the old attach behavior.
   */
  @Nullable
  public static PiggybackHandler provider(Entity entity, @Nullable Direction side) {
    if (entity instanceof Player player) {
      return new PiggybackHandler(player);
    }
    return null;
  }
}
