package slimeknights.tconstruct.common.data.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;

/**
 * Builds the inner {@code tconstruct:material} item-model-definition object consumed by
 * {@link slimeknights.tconstruct.library.client.model.tools.MaterialModel.Unbaked}.
 * <p>
 * PORT 1.21.4: the removed NeoForge model-datagen DSL / {@code CustomLoaderBuilder} is gone; the material model is now an
 * {@link net.minecraft.client.renderer.item.ItemModel.Unbaked} emitted as raw JSON into {@code assets/<ns>/items/<id>.json}.
 * Fields mirror {@code MaterialModel.Unbaked.MAP_CODEC} exactly: {@code model} (required base model),
 * {@code index} (opt, default 0), {@code material} (opt variant id), {@code offset} (opt 2-float, default [0,0]).
 */
public class MaterialModelBuilder {
  /** The {@code tconstruct:material} model object; held live so fluent setters mutate the already-stored definition. */
  private final JsonObject model;

  public MaterialModelBuilder(JsonObject model, ResourceLocation baseModel) {
    this.model = model;
    model.addProperty("type", "tconstruct:material");
    model.addProperty("model", baseModel.toString());
  }

  /** Sets the pixel offset applied to the material layer. */
  public MaterialModelBuilder offset(int x, int y) {
    JsonArray offset = new JsonArray(2);
    offset.add(x);
    offset.add(y);
    model.add("offset", offset);
    return this;
  }

  /** Sets the part index (position in a multi-material tool). */
  public MaterialModelBuilder index(int index) {
    if (index != 0) {
      model.addProperty("index", index);
    }
    return this;
  }

  /** Fixes the rendered material instead of reading it from the stack. */
  public MaterialModelBuilder material(MaterialVariantId material) {
    model.addProperty("material", material.toString());
    return this;
  }
}
