package slimeknights.tconstruct.library.client.model;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState.FoilType;
import net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;

/**
 * Shared helpers for the 1.21.4 item-model system.
 * <p>
 * PORT 1.21.4: the old {@code ItemOverrides.resolve} returned a {@link BakedModel} to render; the new
 * {@code ItemModel.update} instead populates {@link ItemStackRenderState} layers. This util attaches a (possibly
 * dynamically rebaked) {@link BakedModel}'s render passes as layers, mirroring vanilla's {@code BlockModelWrapper}.
 */
public class ItemModelUtil {
  private ItemModelUtil() {}

  /**
   * Attaches all of the given baked model's render passes to the render state as layers, applying foil as needed.
   * The model's own {@link BakedModel#applyTransform} handles the display-context pose at render time.
   * @param renderState  Render state to populate
   * @param stack        Stack being rendered
   * @param model        Resolved baked model (already selected/rebaked for this stack)
   */
  public static void renderModel(ItemStackRenderState renderState, ItemStack stack, BakedModel model) {
    boolean foil = stack.hasFoil();
    for (BakedModel pass : model.getRenderPasses(stack)) {
      LayerRenderState layer = renderState.newLayer();
      if (foil) {
        layer.setFoilType(FoilType.STANDARD);
      }
      layer.setupBlockModel(pass, pass.getRenderType(stack));
    }
  }
}
