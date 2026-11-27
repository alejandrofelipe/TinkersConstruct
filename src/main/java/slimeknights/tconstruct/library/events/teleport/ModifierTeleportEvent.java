package slimeknights.tconstruct.library.events.teleport;

import lombok.Getter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;

/** Event fired when an entity teleports via a tool modifier. Subclasses may have more context. */
@Getter
public class ModifierTeleportEvent extends EntityTeleportEvent {
  private final ModifierEntry modifier;
  public ModifierTeleportEvent(Entity entity, double targetX, double targetY, double targetZ, ModifierEntry modifier) {
    // 26.1: the event takes the target level; Tinkers teleports stay in the entity's level and only run on the server
    super(entity, (ServerLevel) entity.level(), targetX, targetY, targetZ);
    this.modifier = modifier;
  }
}
