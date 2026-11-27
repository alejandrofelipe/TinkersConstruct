package slimeknights.tconstruct.library.client.modifiers.model;

import com.mojang.math.Transformation;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.primitive.StringLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.loadable.record.SingletonLoader;
import slimeknights.mantle.data.registry.GenericLoaderRegistry;
import slimeknights.mantle.data.registry.GenericLoaderRegistry.IHaveLoader;
import slimeknights.mantle.util.ItemLayerPixels;
import slimeknights.tconstruct.library.client.modifiers.IBakedModifierModel;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import org.jspecify.annotations.Nullable;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;

/** Represents a model defined for the given tool */
public interface ModifierModel extends IBakedModifierModel, IHaveLoader {
  ModifierModel EMPTY = SingletonLoader.singleton(loader -> new ModifierModel() {
    @Override
    public void addQuads(IToolStackView tool, ModifierEntry modifier, Function<SpriteId, TextureAtlasSprite> spriteGetter, Transformation transforms, boolean isLarge, int startTintIndex, Consumer<Collection<BakedQuad>> quadConsumer, @Nullable ItemLayerPixels pixels) {}

    @Nullable
    @Override
    public Object getCacheKey(IToolStackView tool, ModifierEntry modifier) {
      return null;
    }

    @Override
    public RecordLoadable<? extends ModifierModel> getLoader() {
      return loader;
    }

    @Override
    public void validate(Function<SpriteId, TextureAtlasSprite> spriteGetter) {}
  });

  /** Loader for registering modifier models */
  GenericLoaderRegistry<ModifierModel> LOADER = new GenericLoaderRegistry<>("Modifier Model", EMPTY, false);
  /** Loadable for reading materials, a common feature of modifier models */
  StringLoadable<SpriteId> MATERIAL_LOADABLE = Loadables.RESOURCE_LOCATION.flatXmap(ModifierModel::blockAtlas, SpriteId::texture);

  @Override
  RecordLoadable<? extends ModifierModel> getLoader();

  /** Validates that all textures in this model exist. */
  void validate(Function<SpriteId, TextureAtlasSprite> spriteGetter);

  static SpriteId blockAtlas(Identifier path) {
    return new SpriteId(TextureAtlas.LOCATION_BLOCKS, path);
  }
}
