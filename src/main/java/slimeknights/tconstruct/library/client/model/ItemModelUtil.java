package slimeknights.tconstruct.library.client.model;

import com.google.common.base.Suppliers;
import net.minecraft.client.renderer.RenderType;
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
import org.joml.Vector3f;
import slimeknights.mantle.client.model.util.MantleItemLayerModel;
import slimeknights.mantle.client.model.util.ModelHelper;

import javax.annotation.Nullable;
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
   * Gets the default render type for dynamic item layer models, matching {@link MantleItemLayerModel#getDefaultRenderType(RenderTypeGroup)}
   * without a render type hint (what the 1.21.4 composite models used).
   */
  public static RenderType defaultRenderType() {
    return MantleItemLayerModel.getDefaultRenderType(RenderTypeGroup.EMPTY).entity();
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
   * A baked item model: quads with their render properties and render type, plus lazily computed extents.
   * Replaces a single-pass {@code BakedModel} for dynamic item models.
   * @param quads       Quads to render
   * @param properties  Item render properties (gui light, particle, transforms)
   * @param renderType  Render type, null to use the stack's default
   * @param extents     Model extents, memoized
   */
  public record BakedItem(List<BakedQuad> quads, ModelRenderProperties properties, @Nullable RenderType renderType, Supplier<Vector3f[]> extents) {
    public BakedItem(List<BakedQuad> quads, ModelRenderProperties properties, @Nullable RenderType renderType) {
      this(quads, properties, renderType, Suppliers.memoize(() -> BlockModelWrapper.computeExtents(quads)));
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
    ModelHelper.addItemLayer(renderState, stack, displayContext, model.quads, model.properties, model.renderType, model.extents);
  }
}
