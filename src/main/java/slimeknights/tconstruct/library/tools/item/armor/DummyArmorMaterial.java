package slimeknights.tconstruct.library.tools.item.armor;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import slimeknights.mantle.registration.object.IdAwareObject;

/**
 * Lightweight armor material descriptor used as a container for Tinkers' armor tool definitions.
 * <p>
 * PORT M3 (armor subsystem): {@code net.minecraft.world.item.ArmorMaterial} became a {@code final record} in 1.21
 * (defense/toughness/knockback moved to attribute-based data components and {@code Holder<ArmorMaterial>}). This class
 * therefore can no longer {@code implements ArmorMaterial}; it now only carries the id + equip sound that the rest of
 * Tinkers relies on. The integration with vanilla armor (building a real {@code Holder<ArmorMaterial>} / layers) is
 * deferred to the armor-layer port.
 */
@RequiredArgsConstructor
@Getter
public class DummyArmorMaterial implements IdAwareObject {
  private final ResourceLocation id;
  private final SoundEvent equipSound;

  public String getName() {
    return id.toString();
  }
}
