package slimeknights.tconstruct.world.client;

import net.minecraft.client.renderer.entity.state.SlimeRenderState;

/**
 * Render state for Tinkers' armored slimes.
 * <p>
 * PORT 1.21.4: entity renderers moved to the extracted-render-state model, so per-entity data read at render time (here
 * the metal-skin flag used to pick the texture) must be captured into a state object during {@code extractRenderState}
 * rather than read from the entity in {@code getTextureLocation}.
 */
public class ArmoredSlimeRenderState extends SlimeRenderState {
  /** If true, the slime renders with its metal texture variant. */
  public boolean isMetal = false;
}
