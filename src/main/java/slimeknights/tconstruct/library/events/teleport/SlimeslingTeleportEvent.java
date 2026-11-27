package slimeknights.tconstruct.library.events.teleport;

import lombok.Getter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;

/** @deprecated No longer used. See {@link SlingModifierTeleportEvent} */
@Deprecated(forRemoval = true)
public class SlimeslingTeleportEvent extends EntityTeleportEvent {
  @Getter
  private final ItemStack sling;
  public SlimeslingTeleportEvent(Entity entity, double targetX, double targetY, double targetZ, ItemStack sling) {
    // 26.1: the event takes the target level; Tinkers teleports stay in the entity's level and only run on the server
    super(entity, (ServerLevel) entity.level(), targetX, targetY, targetZ);
    this.sling = sling;
  }
}
