package slimeknights.tconstruct.library.client.model;

import com.google.common.base.Suppliers;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.item.BlockModelWrapper;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.RenderTypeGroup;
import org.joml.Vector3fc;
import slimeknights.mantle.client.model.util.MantleItemLayerModel;
import slimeknights.mantle.client.model.util.ModelHelper;

import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.function.Supplier;

/**
 * Shared helpers for the item-model system.
 * <p>
 * PORT 1.21.4: the old {@code ItemOverrides.resolve} returned a baked model to render; the new {@code ItemModel.update}
 * instead populates {@link ItemStackRenderState} layers.
 * <p>
 * 1.21.5: {@code BakedModel} is gone, so dynamically rebaked item models are kept as a {@link BakedItem} (quads, render
 * properties and render type), attached as a layer the same way vanilla's {@link BlockModelWrapper} does.
 */
public class ItemModelUtil {
  private ItemModelUtil() {}

  /**
   * Gets the default render types for dynamic item layer models, matching {@link MantleItemLayerModel#getDefaultRenderType(RenderTypeGroup)}
   * without a render type hint (what the 1.21.4 composite models used).
   * 1.21.11: returns the group rather than one entity type, as blocks and items have separate atlases; each layer picks the
   * type for the atlas of its quads, see {@link ModelHelper#addItemLayers}.
   */
  public static RenderTypeGroup defaultRenderTypes() {
    return MantleItemLayerModel.getDefaultRenderType(RenderTypeGroup.EMPTY);
  }

  /** Gets the item render properties from a resolved model (gui light and transforms), with the given particle */
  public static ModelRenderProperties properties(ResolvedModel model, TextureAtlasSprite particle) {
    return new ModelRenderProperties(model.getTopGuiLight().lightLikeBlock(), particle, model.getTopTransforms());
  }

  /** Creates item render properties for the given values */
  public static ModelRenderProperties properties(boolean usesBlockLight, TextureAtlasSprite particle, ItemTransforms transforms) {
    return new ModelRenderProperties(usesBlockLight, particle, transforms);
  }

  /**
   * A baked item model: quads with their render properties and render types, plus lazily computed extents.
   * Replaces a single-pass {@code BakedModel} for dynamic item models.
   * 1.21.11: holds a render type group instead of one render type, so quads mixing the blocks and items atlases render as
   * one layer per atlas, each with the group's type for that atlas.
   * @param quads        Quads to render
   * @param properties   Item render properties (gui light, particle, transforms)
   * @param renderTypes  Render type group, null to use the stack's default for each atlas
   * @param extents      Model extents, memoized
   * @param animated     Whether any quad uses an animated sprite, computed once at bake time
   */
  public record BakedItem(List<BakedQuad> quads, ModelRenderProperties properties, @Nullable RenderTypeGroup renderTypes, Supplier<Vector3fc[]> extents, boolean animated) {
    public BakedItem(List<BakedQuad> quads, ModelRenderProperties properties, @Nullable RenderTypeGroup renderTypes) {
      this(quads, properties, renderTypes, Suppliers.memoize(() -> BlockModelWrapper.computeExtents(quads)), ModelHelper.hasAnimatedSprite(quads));
    }

    /** Gets the particle sprite of this model */
    public TextureAtlasSprite particle() {
      return properties.particleIcon();
    }
  }

  /**
   * Attaches the given baked item model to the render state as a layer, applying foil as needed.
   * @param renderState     Render state to populate
   * @param stack           Stack being rendered
   * @param displayContext  Display context, selects the item transform
   * @param model           Resolved baked model (already selected/rebaked for this stack)
   */
  public static void renderModel(ItemStackRenderState renderState, ItemStack stack, ItemDisplayContext displayContext, BakedItem model) {
    ModelHelper.addItemLayers(renderState, stack, displayContext, model.quads, model.properties, model.renderTypes, model.extents, model.animated);
  }
}
