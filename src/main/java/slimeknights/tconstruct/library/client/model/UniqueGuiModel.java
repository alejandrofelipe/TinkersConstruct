package slimeknights.tconstruct.library.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.DelegateBakedModel;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.ResolvableModel.Resolver;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.AbstractUnbakedModel;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import slimeknights.mantle.client.model.util.SimpleBlockModel;

/**
 * Model providing a variant for the GUI.
 * <p>
 * PORT 1.21.4: ported to the {@link AbstractUnbakedModel} API. In 1.21.4 {@link BakedModel#applyTransform} can only
 * adjust the pose, not swap which model renders, so the GUI geometry swap is now the item-model's responsibility (the
 * baked GUI variant is exposed via {@link Baked#getGui()} for the item {@code ItemModel} to select on
 * {@link ItemDisplayContext#GUI}). {@code applyTransform} still routes GUI pose through the GUI model for correctness.
 */
public class UniqueGuiModel extends AbstractUnbakedModel {
  /** Shared loader instance */
  public static final UnbakedModelLoader<UniqueGuiModel> LOADER = UniqueGuiModel::deserialize;

  protected final SimpleBlockModel model;
  protected final SimpleBlockModel gui;

  public UniqueGuiModel(SimpleBlockModel model, SimpleBlockModel gui) {
    super(model.getParameters());
    this.model = model;
    this.gui = gui;
  }

  @Override
  public void resolveDependencies(Resolver resolver) {
    super.resolveDependencies(resolver);
    model.resolveDependencies(resolver);
    gui.resolveDependencies(resolver);
  }

  @Override
  public BakedModel bake(TextureSlots textures, ModelBaker baker, ModelState transform, boolean useAmbientOcclusion, boolean useBlockLight, ItemTransforms itemTransforms, ContextMap additionalProperties) {
    return new Baked(
      model.bake(textures, baker, transform, useAmbientOcclusion, useBlockLight, itemTransforms, additionalProperties),
      gui.bake(textures, baker, transform, useAmbientOcclusion, useBlockLight, itemTransforms, additionalProperties)
    );
  }

  /**
   * Wrapper exposing a distinct GUI variant.
   */
  public static class Baked extends DelegateBakedModel {
    private final BakedModel gui;

    public Baked(BakedModel base, BakedModel gui) {
      super(base);
      this.gui = gui;
    }

    /** Gets the GUI variant, for an item model to select on {@link ItemDisplayContext#GUI} */
    public BakedModel getGui() {
      return gui;
    }

    @Override
    public void applyTransform(ItemDisplayContext itemDisplay, PoseStack mat, boolean applyLeftHandTransform) {
      // PORT 1.21.4: pose-only; the GUI quad swap now belongs to the item model (see class javadoc)
      if (itemDisplay == ItemDisplayContext.GUI) {
        gui.applyTransform(itemDisplay, mat, applyLeftHandTransform);
      } else {
        parent.applyTransform(itemDisplay, mat, applyLeftHandTransform);
      }
    }
  }

  /** Loader for this model */
  public static UniqueGuiModel deserialize(JsonObject json, JsonDeserializationContext context) {
    return new UniqueGuiModel(
      SimpleBlockModel.deserialize(json, context),
      SimpleBlockModel.deserialize(GsonHelper.getAsJsonObject(json, "gui"), context)
    );
  }
}
