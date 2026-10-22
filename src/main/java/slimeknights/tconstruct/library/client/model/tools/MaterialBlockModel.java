package slimeknights.tconstruct.library.client.model.tools;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.math.Transformation;
import lombok.Getter;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.ResolvableModel.Resolver;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.model.AbstractUnbakedModel;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.NeoForgeModelProperties;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.UnbakedElementsHelper;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import slimeknights.mantle.client.model.RetexturedModel;
import slimeknights.mantle.client.model.util.ColoredBlockModel;
import slimeknights.mantle.client.model.util.ModelHelper;
import slimeknights.mantle.client.model.util.SimpleBlockModel;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.array.ArrayLoadable;
import slimeknights.mantle.data.loadable.primitive.StringLoadable;
import slimeknights.mantle.util.RetexturedHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfo;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfo.TintedSprite;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfoLoader;
import slimeknights.tconstruct.library.client.model.ModelProperties;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.nbt.MaterialIdNBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/**
 * Model that handles dynamic materials using the block model elements style.
 * When used for tools, notably does not handle modifier models, just materials are considered.
 * <p>
 * PORT 1.21.4: ported to {@link AbstractUnbakedModel}. Dynamic retexturing rebakes through the Mantle replacement-map
 * primitive ({@link SimpleBlockModel#bakeReplaced} / the {@code bakePart} replacement overloads) instead of the removed
 * ExtraTextureContext/RetexturedContext. The block-side ({@link ModelData}) path lives here; the item-display override
 * (reading the material/block from the stack) moves to the item-model system, which wraps the {@link AbstractBaked} and
 * calls {@link AbstractBaked#getCachedModel} from {@code ItemModel.update}.
 * @see MaterialModel
 * @see ToolModel
 */
public class MaterialBlockModel extends AbstractUnbakedModel {
  /** Loadable for the list of material textures */
  private static final Loadable<Set<String>> MATERIAL = StringLoadable.DEFAULT.set(ArrayLoadable.COMPACT);
  /** Loadable for the list of parts, each a list of material textures */
  private static final Loadable<List<Set<String>>> PARTS = StringLoadable.DEFAULT.set(ArrayLoadable.COMPACT_OR_EMPTY).list(1);
  /** Shared loader instance */
  public static final UnbakedModelLoader<MaterialBlockModel> LOADER = MaterialBlockModel::deserialize;

  public enum ModelType { TOOL, PART, ANVIL }

  /** Block model to retexture. Expected each element is a single material or not a material, no multiple or mixed. */
  private final SimpleBlockModel model;
  /** List of parts, each is a set of materials to retexture */
  private final List<Set<String>> parts;
  /** Type of model to create. */
  private final ModelType type;

  public MaterialBlockModel(SimpleBlockModel model, List<Set<String>> parts, ModelType type) {
    super(model.getParameters());
    this.model = model;
    this.parts = parts;
    this.type = type;
  }

  /** Loads a material block model from JSON */
  public static MaterialBlockModel deserialize(JsonObject json, JsonDeserializationContext context) {
    SimpleBlockModel model = SimpleBlockModel.deserialize(json, context);

    // if retextured is set, using the anvil style model
    if (json.has("retextured")) {
      return new MaterialBlockModel(model, List.of(MATERIAL.getIfPresent(json, "retextured")), ModelType.ANVIL);
    }
    // if material is set, using the parts model
    if (json.has("material")) {
      return new MaterialBlockModel(model, List.of(MATERIAL.getIfPresent(json, "material")), ModelType.PART);
    }
    // otherwise, using the tool model
    return new MaterialBlockModel(model, PARTS.getIfPresent(json, "parts"), ModelType.TOOL);
  }

  @Override
  public void resolveDependencies(Resolver resolver) {
    super.resolveDependencies(resolver);
    model.resolveDependencies(resolver);
  }

  @Override
  public BakedModel bake(TextureSlots textures, ModelBaker baker, ModelState transform, boolean useAmbientOcclusion, boolean useBlockLight, ItemTransforms itemTransforms, ContextMap additionalProperties) {
    BakedModel baked = model.bake(textures, baker, transform, useAmbientOcclusion, useBlockLight, itemTransforms, additionalProperties);
    Transformation rootTransform = additionalProperties.getOrDefault(NeoForgeModelProperties.TRANSFORM, Transformation.identity());
    RenderTypeGroup renderType = additionalProperties.getOrDefault(NeoForgeModelProperties.RENDER_TYPE, RenderTypeGroup.EMPTY);
    List<Set<String>> parts = this.parts.stream().map(part -> RetexturedModel.getAllRetextured(model, part)).toList();
    BakeContext ctx = new BakeContext(model, textures, transform, useAmbientOcclusion, useBlockLight, itemTransforms, rootTransform, renderType);

    // part model - fetches material from NBT field
    if (type == ModelType.PART) {
      return new BakedPart(baked, ctx, parts.get(0));
    }
    // anvil model - fetches material from NBT field, but can also choose block texture
    if (type == ModelType.ANVIL) {
      return new BakedAnvil(baked, ctx, parts.get(0));
    }
    // for tools with just one material, use simpler tool model
    if (parts.size() == 1) {
      return new SimpleBakedTool(baked, ctx, parts.get(0));
    }
    // standard tool with multiple materials
    boolean particleRetextured = parts.stream().anyMatch(set -> set.contains("particle"));
    return new BakedTool(baked, ctx, parts, particleRetextured);
  }

  /** Captures everything needed to dynamically rebake the model at runtime (replaces the old IGeometryBakingContext) */
  public record BakeContext(SimpleBlockModel model, TextureSlots textures, ModelState transform, boolean useAmbientOcclusion,
                            boolean useBlockLight, ItemTransforms itemTransforms, Transformation rootTransform, RenderTypeGroup renderType) {}

  /** Common logic between all variants of baking */
  public static abstract class AbstractBaked<T,P> extends slimeknights.mantle.client.model.util.DynamicBakedWrapper<BakedModel> {
    protected final BakeContext ctx;
    protected final boolean particleRetextured;
    protected final ModelProperty<P> property;

    private AbstractBaked(BakedModel original, BakeContext ctx, boolean particleRetextured, ModelProperty<P> property) {
      super(original);
      this.ctx = ctx;
      this.particleRetextured = particleRetextured;
      this.property = property;
    }

    /** Fetches textures from the materials for baking */
    protected abstract void fetchMaterials(T materials, Function<Material, TextureAtlasSprite> spriteGetter, Map<String, Material> replacements, Map<String, TintedSprite> tints);

    /** Fetches a single material. For use in {@link #fetchMaterials(Object, Function, Map, Map)} */
    protected void fetchMaterial(MaterialVariantId material, Set<String> retextured, Function<Material, TextureAtlasSprite> spriteGetter, Map<String, Material> replacements, Map<String, TintedSprite> tints) {
      // fetch render info
      Optional<MaterialRenderInfo> optional = MaterialRenderInfoLoader.INSTANCE.getRenderInfo(material);
      if (optional.isPresent()) {
        MaterialRenderInfo info = optional.get();

        // may have multiple names going to the same texture
        Map<Material, TintedSprite> seen = new HashMap<>();
        for (String name : retextured) {
          Material texture = ctx.textures().getMaterial(name);
          if (texture == null) {
            continue;
          }
          TintedSprite tinted = seen.get(texture);
          if (tinted == null) {
            tinted = info.getSprite(texture, spriteGetter);
            seen.put(texture, tinted);
          }
          TextureAtlasSprite sprite = tinted.sprite();
          // bit annoying that we have to unwrap the sprite, but its needed to be fetched to check if its present
          replacements.put(name, new Material(sprite.atlasLocation(), sprite.contents().name()));
          // if we have a transform, save that with the name too
          if (tinted.color() != -1 || tinted.emissivity() > 0) {
            tints.put('#' + name, tinted);
          }
        }
      }
    }

    /** Retextures with the given materials list */
    protected BakedModel bakeWith(T materials) {
      // map of name to replacement texture
      Map<String,Material> replacements = new HashMap<>();
      // map of name to transformation to apply, will likely be smaller than replacement textures
      Map<String,TintedSprite> tints = new HashMap<>();

      // delegate material fetching
      Function<Material,TextureAtlasSprite> spriteGetter = Material::sprite;
      fetchMaterials(materials, spriteGetter, replacements, tints);

      // quick exit in case we found nothing, should never happen
      if (replacements.isEmpty()) {
        return parent;
      }

      // if no parts need colors, we can just use standard replacement baking
      if (tints.isEmpty()) {
        return ctx.model().bakeReplaced(ctx.textures(), replacements, ctx.transform(), ctx.useAmbientOcclusion(), ctx.useBlockLight(), ctx.itemTransforms(), ctx.rootTransform(), ctx.renderType());
      }

      // need to tint or apply light to some textures: manual per-part bake
      ModelState state = ctx.rootTransform().isIdentity() ? ctx.transform() : UnbakedElementsHelper.composeRootTransformIntoModelState(ctx.transform(), ctx.rootTransform());
      Transformation rotation = state.getRotation();
      boolean uvlock = state.isUvLocked();
      TextureAtlasSprite particle = SimpleBlockModel.findSprite(spriteGetter, ctx.textures(), replacements, "particle");
      SimpleBakedModel.Builder builder = SimpleBlockModel.bakedBuilder(ctx.useAmbientOcclusion(), ctx.useBlockLight(), ctx.itemTransforms()).particle(particle);
      List<BlockElement> elements = ctx.model().getElements();
      int size = elements.size();
      for (int i = 0; i < size; i++) {
        BlockElement part = elements.get(i);
        // determine if any of the faces needs a tint
        // for simplicity, assume the whole part is tinted if so. Build your model to separate distinct material faces if needed
        TintedSprite tint = null;
        for (BlockElementFace face : part.faces.values()) {
          TintedSprite faceTint = tints.get(face.texture());
          if (faceTint != null) {
            tint = faceTint;
            break;
          }
        }
        // apply color if we have it
        if (tint != null) {
          IQuadTransformer partTransformer = tint.color() == -1 ? QuadTransformers.empty() : ColoredBlockModel.applyColorQuadTransformer(tint.color());
          ColoredBlockModel.bakePart(builder, ctx.textures(), replacements, part, tint.emissivity(), spriteGetter, rotation, partTransformer, uvlock);
        } else {
          SimpleBlockModel.bakePart(builder, ctx.textures(), replacements, part, spriteGetter, state, QuadTransformers.empty());
        }
      }
      return builder.build(ctx.renderType());
    }

    /** Gets the cached model for the given materials. */
    public abstract BakedModel getCachedModel(P materials);

    @Override
    public TextureAtlasSprite getParticleIcon(@NotNull ModelData data) {
      if (particleRetextured) {
        P materials = data.get(property);
        if (materials != null) {
          return getCachedModel(materials).getParticleIcon(data);
        }
      }
      return parent.getParticleIcon(data);
    }

    @Nonnull
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType) {
      P materials = extraData.get(property);
      if (materials != null) {
        return getCachedModel(materials).getQuads(state, side, rand, extraData, renderType);
      }
      return parent.getQuads(state, side, rand, extraData, renderType);
    }
  }

  /** Baked model for tools with multiple materials. */
  public static class BakedTool extends AbstractBaked<MaterialIdNBT,MaterialIdNBT> {
    /** Cache of texture name to baked model */
    private final Cache<MaterialIdNBT, BakedModel> cache = CacheBuilder
      .newBuilder()
      // ensure we can display every single tool that shows in JEI, plus a couple extra
      .maximumSize(MaterialRenderInfoLoader.INSTANCE.getAllRenderInfos().size() * 3L / 2)
      .build();

    private final List<Set<String>> parts;
    private BakedTool(BakedModel original, BakeContext ctx, List<Set<String>> parts, boolean particleRetextured) {
      super(original, ctx, particleRetextured, ModelProperties.MATERIALS);
      this.parts = parts;
    }

    @Override
    protected void fetchMaterials(MaterialIdNBT materials, Function<Material, TextureAtlasSprite> spriteGetter, Map<String, Material> replacements, Map<String, TintedSprite> tints) {
      for (int i = 0; i < parts.size(); i++) {
        fetchMaterial(materials.getMaterial(i), parts.get(i), spriteGetter, replacements, tints);
      }
    }

    @Override
    public BakedModel getCachedModel(MaterialIdNBT materials) {
      if (!materials.getMaterials().isEmpty()) {
        try {
          return cache.get(materials, () -> bakeWith(materials));
        } catch (ExecutionException e) {
          TConstruct.LOG.error("Failed to get tool model from cache", e);
        }
      }
      return parent;
    }
  }

  /** Common logic between tool parts and tools with just 1 material. */
  public static abstract class BakedSingleMaterial<P> extends AbstractBaked<MaterialVariantId,P> {
    /** Cache of texture name to baked model */
    private final Map<MaterialVariantId, BakedModel> cache = new ConcurrentHashMap<>();

    protected final Set<String> retexture;
    private final Function<MaterialVariantId, BakedModel> baker = this::bakeWith;

    private BakedSingleMaterial(BakedModel original, BakeContext ctx, Set<String> retexture, ModelProperty<P> property) {
      super(original, ctx, retexture.contains("particle"), property);
      this.retexture = retexture;
    }

    /** Gets the model for the given material */
    public BakedModel getCachedModel(MaterialVariantId material) {
      if (IMaterial.UNKNOWN_ID.equals(material)) {
        return parent;
      }
      return cache.computeIfAbsent(material, baker);
    }

    @Override
    protected void fetchMaterials(MaterialVariantId materials, Function<Material, TextureAtlasSprite> spriteGetter, Map<String, Material> replacements, Map<String, TintedSprite> tints) {
      fetchMaterial(materials, retexture, spriteGetter, replacements, tints);
    }
  }

  /** Baked model for tools with just 1 material. */
  public static class SimpleBakedTool extends BakedSingleMaterial<MaterialIdNBT> {
    private SimpleBakedTool(BakedModel original, BakeContext ctx, Set<String> retexture) {
      super(original, ctx, retexture, ModelProperties.MATERIALS);
    }

    @Override
    public BakedModel getCachedModel(MaterialIdNBT materials) {
      return getCachedModel(materials.getMaterial(0));
    }
  }

  /** Baked model for tool parts. */
  public static class BakedPart extends BakedSingleMaterial<MaterialVariantId> {
    private BakedPart(BakedModel original, BakeContext ctx, Set<String> retexture) {
      super(original, ctx, retexture, ModelProperties.MATERIAL);
    }
  }

  /** Model that supports both block and material textures. */
  public static class BakedAnvil extends BakedSingleMaterial<MaterialVariantId> {
    private final Map<ResourceLocation, BakedModel> blockCache = new ConcurrentHashMap<>();
    private final Function<ResourceLocation, BakedModel> blockBaker = this::bakeWithBlock;
    private BakedAnvil(BakedModel original, BakeContext ctx, Set<String> retexture) {
      super(original, ctx, retexture, ModelProperties.MATERIAL);
    }

    /** Rebakes the model with a specific block texture. See also {@link RetexturedModel} */
    private BakedModel bakeWithBlock(ResourceLocation texture) {
      Material material = new Material(TextureAtlas.LOCATION_BLOCKS, texture);
      Map<String,Material> replacements = new HashMap<>();
      for (String name : retexture) {
        replacements.put(name, material);
      }
      return ctx.model().bakeReplaced(ctx.textures(), replacements, ctx.transform(), ctx.useAmbientOcclusion(), ctx.useBlockLight(), ctx.itemTransforms(), ctx.rootTransform(), ctx.renderType());
    }

    /** Gets the cached model for the given block. */
    public BakedModel getCachedModel(Block block) {
      return this.blockCache.computeIfAbsent(ModelHelper.getParticleTexture(block), blockBaker);
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
      // block takes priority if present
      if (particleRetextured) {
        Block block = data.get(RetexturedHelper.BLOCK_PROPERTY);
        if (block != null) {
          return getCachedModel(block).getParticleIcon(data);
        }
      }
      return super.getParticleIcon(data);
    }

    @Nonnull
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType) {
      Block block = extraData.get(RetexturedHelper.BLOCK_PROPERTY);
      if (block != null) {
        return getCachedModel(block).getQuads(state, side, rand, extraData, renderType);
      }
      return super.getQuads(state, side, rand, extraData, renderType);
    }
  }
}
