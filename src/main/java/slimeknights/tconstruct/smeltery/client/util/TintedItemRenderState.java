package slimeknights.tconstruct.smeltery.client.util;

import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Item render state that can tint and fade the resolved item after extraction.
 * 1.21.9: replaces {@code CastingItemRenderTypeBuffer}, which wrapped the {@link net.minecraft.client.renderer.MultiBufferSource}
 * to swap render types and tint vertices. Items are now drawn by the feature dispatcher from the {@link ItemStackRenderState},
 * so instead the resolved layers are edited: every quad gets a tint index whose color includes the fade, and the render type
 * becomes translucent while the item is not fully opaque.
 * Layers are tracked through {@link #newLayer()}, which every item model uses to add its layers.
 * Layers drawn by a special model renderer (no quads) cannot be tinted and draw normally.
 */
public class TintedItemRenderState extends ItemStackRenderState {
  private final List<LayerRenderState> trackedLayers = new ArrayList<>();

  @Override
  public LayerRenderState newLayer() {
    LayerRenderState layer = super.newLayer();
    trackedLayers.add(layer);
    return layer;
  }

  @Override
  public void clear() {
    super.clear();
    trackedLayers.clear();
  }

  /**
   * Gets the tint for a cooling casting output, matching the pre-1.21.9 casting fade.
   * @param alpha        Opacity of the item from 0 to 255. 255 is the end of the animation.
   * @param temperature  Temperature of the item from 0 to 255. 0 is the end of the animation when the item is "cool"/untinted
   * @return  ARGB tint
   */
  public static int coolingTint(int alpha, int temperature) {
    // alpha is a direct fade from 0 to 255
    alpha = Mth.clamp(alpha, 0, 0xFF);
    // RGB based on temperature, fades from 0xB06020 tint to 0xFFFFFF
    temperature = Mth.clamp(temperature, 0, 0xFF);
    int red   = 0xFF - (temperature * (0xFF - 0xB0) / 0xFF);
    int green = 0xFF - (temperature * (0xFF - 0x60) / 0xFF);
    int blue  = 0xFF - (temperature * (0xFF - 0x20) / 0xFF);
    return ARGB.color(alpha, red, green, blue);
  }

  /**
   * Multiplies the given color into every quad of the resolved item. Call after the item was resolved during extraction.
   * @param color  ARGB color to multiply in. If the alpha is below 255, quads switch to a translucent render type.
   */
  public void tint(int color) {
    boolean translucent = ARGB.alpha(color) < 0xFF;
    for (LayerRenderState layer : trackedLayers) {
      List<BakedQuad> quads = layer.prepareQuadList();
      // special model renderers have no quads
      if (quads.isEmpty()) {
        continue;
      }
      // find a free tint index for untinted quads
      int[] oldTints = layer.prepareTintLayers(0).clone();
      int untinted = oldTints.length;
      for (BakedQuad quad : quads) {
        untinted = Math.max(untinted, quad.tintIndex() + 1);
      }
      // multiply the color into the existing tints, -1 (white) is used for missing tints
      int[] tints = layer.prepareTintLayers(untinted + 1);
      for (int i = 0; i < untinted; i++) {
        tints[i] = ARGB.multiply(i < oldTints.length ? oldTints[i] : -1, color);
      }
      tints[untinted] = color;
      // quad list belongs to this state, so it is safe to swap in quads using the new tint index
      for (int i = 0; i < quads.size(); i++) {
        BakedQuad quad = quads.get(i);
        if (!quad.isTinted()) {
          // 1.21.11: quads are records of positions and packed UVs plus NeoForge's normals and colors, copy every field but the tint
          quads.set(i, new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
                                     quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(),
                                     untinted, quad.direction(), quad.sprite(), quad.shade(), quad.lightEmission(),
                                     quad.bakedNormals(), quad.bakedColors(), quad.hasAmbientOcclusion()));
        }
      }
      // fade requires a translucent render type, solid and cutout ignore vertex alpha
      if (translucent) {
        layer.setRenderType(RenderTypes.itemEntityTranslucentCull(quads.getFirst().sprite().atlasLocation()));
      }
    }
  }
}
