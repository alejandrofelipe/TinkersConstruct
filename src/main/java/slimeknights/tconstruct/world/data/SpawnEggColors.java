package slimeknights.tconstruct.world.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Colors of the Tinkers spawn eggs: the primary color tinted the vanilla template's base layer and the secondary color its overlay
 * until 1.21.4. Since 1.21.5 eggs have no colors, so {@link SpawnEggTextureProvider} bakes them into one texture per egg; the
 * texture test checks against the same values.
 */
@Getter
@RequiredArgsConstructor
public enum SpawnEggColors {
  SKY_SLIME("sky_slime", 0x47eff5, 0xacfff4),
  ENDER_SLIME("ender_slime", 0x6300B0, 0xD37CFF),
  TERRACUBE("terracube", 0xAFB9D6, 0xA1A7B1);

  /** Entity name, registered in {@code TinkerWorld} with its egg {@code tconstruct:<entity>_spawn_egg} */
  private final String entity;
  /** Color of the base layer, RGB */
  private final int primary;
  /** Color of the overlay layer, RGB */
  private final int secondary;

  /** Gets the name of the egg item, also the name of its texture */
  public String getEggName() {
    return entity + "_spawn_egg";
  }
}
