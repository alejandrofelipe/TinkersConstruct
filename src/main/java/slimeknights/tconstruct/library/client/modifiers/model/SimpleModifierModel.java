package slimeknights.tconstruct.library.client.modifiers.model;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.record.RecordLoadable;

import org.jspecify.annotations.Nullable;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Modifier model that just requires a texture */
public interface SimpleModifierModel extends ModifierModel {
  /** field for small texture */
  LoadableField<SpriteId, SimpleModifierModel> TEXTURE_FIELD = ModifierModel.MATERIAL_LOADABLE.requiredField("texture", SimpleModifierModel::small);
  /** field for large texture */
  LoadableField<SpriteId, SimpleModifierModel> LARGE_TEXTURE_FIELD = ModifierModel.MATERIAL_LOADABLE.nullableField("texture_large", SimpleModifierModel::large);

  /** Small texture location */
  @Nullable
  SpriteId small();
  /** Large texture location */
  @Nullable
  SpriteId large();

  @Override
  default void validate(Function<SpriteId, TextureAtlasSprite> spriteGetter) {
    SpriteId material = small();
    if (material != null) {
      spriteGetter.apply(material);
    }
    material = large();
    if (material != null) {
      spriteGetter.apply(material);
    }
  }

  /** Creates a simple loader */
  static <T extends SimpleModifierModel> RecordLoadable<T> loader(BiFunction<SpriteId, SpriteId, T> constructor) {
    return RecordLoadable.create(TEXTURE_FIELD, LARGE_TEXTURE_FIELD, constructor);
  }
}
