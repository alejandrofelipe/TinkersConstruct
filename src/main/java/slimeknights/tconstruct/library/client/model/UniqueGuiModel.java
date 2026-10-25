package slimeknights.tconstruct.library.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import slimeknights.mantle.client.model.util.SimpleBlockModel;

/**
 * Model providing a variant for the GUI.
 * <p>
 * PORT 1.21.4: the GUI geometry swap is the item model's responsibility, the baked model only adjusted the GUI pose.
 * <p>
 * 1.21.5: models no longer bake themselves, they only provide static geometry. This model bakes its regular elements
 * like a {@link SimpleBlockModel} and keeps the GUI elements as data ({@link #getGui()}) for an item model to select on
 * {@link ItemDisplayContext#GUI}.
 * Like 1.21.4, no item model selects the GUI variant yet, so items render the regular elements.
 */
public class UniqueGuiModel extends SimpleBlockModel {
  /** Shared loader instance */
  public static final UnbakedModelLoader<UniqueGuiModel> LOADER = UniqueGuiModel::deserialize;

  protected final SimpleBlockModel gui;

  public UniqueGuiModel(SimpleBlockModel model, SimpleBlockModel gui) {
    super(model);
    this.gui = gui;
  }

  /** Gets the GUI variant, for an item model to select on {@link ItemDisplayContext#GUI} */
  public SimpleBlockModel getGui() {
    return gui;
  }

  /** Loader for this model */
  public static UniqueGuiModel deserialize(JsonObject json, JsonDeserializationContext context) {
    return new UniqueGuiModel(
      SimpleBlockModel.deserialize(json, context),
      SimpleBlockModel.deserialize(GsonHelper.getAsJsonObject(json, "gui"), context)
    );
  }
}
