package slimeknights.tconstruct.library.client.model;

import com.google.common.base.Suppliers;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.cuboid.ItemTransforms;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import slimeknights.mantle.client.model.util.ModelHelper;

import java.util.List;
import java.util.function.Supplier;

/**
 * Shared helpers for the item-model system.
 * <p>
 * PORT 1.21.4: the old {@code ItemOverrides.resolve} returned a baked model to render; the new {@code ItemModel.update}
 * instead populates {@link ItemStackRenderState} layers.
 * <p>
 * 1.21.5: {@code BakedModel} is gone, so dynamically rebaked item models are kept as a {@link BakedItem} (quads and render
 * properties), attached as a layer the same way vanilla's {@link CuboidItemModelWrapper} does.
 * <p>
 * 26.1: every quad carries its own item render type ({@link BakedQuad.MaterialInfo#itemRenderType()}), picked when the quad is baked, so the
 * 1.21.11 render type group is gone.
 */
public class ItemModelUtil {
  private ItemModelUtil() {}

  /** Gets the item render properties from a resolved model (gui light and transforms), with the given particle */
  public static ModelRenderProperties properties(ResolvedModel model, TextureAtlasSprite particle) {
    return new ModelRenderProperties(model.getTopGuiLight().lightLikeBlock(), new Material.Baked(particle, false), model.getTopTransforms());
  }

  /** Creates item render properties for the given values */
  public static ModelRenderProperties properties(boolean usesBlockLight, TextureAtlasSprite particle, ItemTransforms transforms) {
    return new ModelRenderProperties(usesBlockLight, new Material.Baked(particle, false), transforms);
  }

  /**
   * A baked item model: quads with their render properties, plus lazily computed extents.
   * Replaces a single-pass {@code BakedModel} for dynamic item models.
   * @param quads        Quads to render, each with its own item render type
   * @param properties   Item render properties (gui light, particle, transforms)
   * @param extents      Model extents, memoized
   * @param animated     Whether any quad uses an animated sprite, computed once at bake time
   */
  public record BakedItem(List<BakedQuad> quads, ModelRenderProperties properties, Supplier<Vector3fc[]> extents, boolean animated) {
    public BakedItem(List<BakedQuad> quads, ModelRenderProperties properties) {
      this(quads, properties, Suppliers.memoize(() -> CuboidItemModelWrapper.computeExtents(quads)), ModelHelper.hasAnimatedSprite(quads));
    }

    /** Gets the particle sprite of this model */
    public TextureAtlasSprite particle() {
      return properties.particleMaterial().sprite();
    }
  }

  /**
   * Attaches the given baked item model to the render state as a layer, applying foil as needed.
   * @param renderState     Render state to populate
   * @param stack           Stack being rendered
   * @param displayContext  Display context, selects the item transform
   * @param model           Resolved baked model (already selected/rebaked for this stack)
   * @param transformation  Transformation the item model was baked with, see {@link ModelHelper#addItemLayer}
   */
  public static void renderModel(ItemStackRenderState renderState, ItemStack stack, ItemDisplayContext displayContext, BakedItem model, Matrix4fc transformation) {
    ModelHelper.addItemLayer(renderState, stack, displayContext, model.quads, model.properties, transformation, model.extents, model.animated);
  }
}
