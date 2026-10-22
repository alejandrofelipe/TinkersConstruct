package slimeknights.tconstruct.library.client.model.block;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
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
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.model.AbstractUnbakedModel;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.NeoForgeModelProperties;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.UnbakedElementsHelper;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.fluids.FluidStack;
import com.mojang.math.Transformation;
import slimeknights.mantle.client.model.RetexturedModel;
import slimeknights.mantle.client.model.util.ColoredBlockModel;
import slimeknights.mantle.client.model.util.ColoredBlockModel.ColorData;
import slimeknights.mantle.client.model.util.DynamicBakedWrapper;
import slimeknights.mantle.client.model.util.ModelHelper;
import slimeknights.mantle.client.model.util.SimpleBlockModel;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.mantle.util.LogicHelper;
import slimeknights.mantle.util.RetexturedHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.model.ModelProperties;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Model that replaces fluid textures with the fluid from model data.
 * <p>
 * PORT 1.21.4: ported to {@link AbstractUnbakedModel}; dynamic retexturing uses the
 * {@link ColoredBlockModel#bakePart(SimpleBakedModel.Builder, TextureSlots, Map, BlockElement, int, java.util.function.Function, Transformation, IQuadTransformer, boolean)}
 * replacement overload (replacing the removed RetexturedContext). The item-side override that selected the texture from
 * the stack component now lives in the item-model system (see the retextured item {@code ItemModel}).
 */
public class FluidTextureModel extends AbstractUnbakedModel {
  /** Loader instance */
  public static final UnbakedModelLoader<FluidTextureModel> LOADER = FluidTextureModel::deserialize;

  private final ColoredBlockModel model;
  private final Set<String> fluids;
  private final Set<String> retextured;

  public FluidTextureModel(ColoredBlockModel model, Set<String> fluids, Set<String> retextured) {
    super(model.getParameters());
    this.model = model;
    this.fluids = fluids;
    this.retextured = retextured;
  }

  @Override
  public void resolveDependencies(Resolver resolver) {
    super.resolveDependencies(resolver);
    model.resolveDependencies(resolver);
  }

  /** Trims the # character off the beginning of a texture name (if present) */
  private static String trimTextureName(String name) {
    if (name.charAt(0) == '#') {
      return name.substring(1);
    }
    return name;
  }

  /** Location used for logging since the bake method no longer receives the model location */
  private static final ResourceLocation BAKE_LOCATION = TConstruct.getResource("fluid_texture");

  @Override
  public BakedModel bake(TextureSlots textures, ModelBaker baker, ModelState transform, boolean useAmbientOcclusion, boolean useBlockLight, ItemTransforms itemTransforms, ContextMap additionalProperties) {
    // start by baking the base model
    BakedModel baked = model.bake(textures, baker, transform, useAmbientOcclusion, useBlockLight, itemTransforms, additionalProperties);
    Transformation rootTransform = additionalProperties.getOrDefault(NeoForgeModelProperties.TRANSFORM, Transformation.identity());
    RenderTypeGroup renderType = additionalProperties.getOrDefault(NeoForgeModelProperties.RENDER_TYPE, RenderTypeGroup.EMPTY);

    // determine which block parts are fluids
    Set<String> fluidTextures = this.fluids.isEmpty() ? Collections.emptySet() : RetexturedModel.getAllRetextured(model, this.fluids);
    List<BlockElement> elements = model.getElements();
    int size = elements.size();
    BitSet fluidParts = new BitSet(size);
    if (!fluidTextures.isEmpty()) {
      for (int i = 0; i < size; i++) {
        BlockElement part = elements.get(i);
        long fluidFaces = part.faces.values().stream()
                                    .filter(face -> fluidTextures.contains(trimTextureName(face.texture())))
                                    .count();
        // for simplicity, each part is either a fluid or not. If for some reason it contains both we mark it as a fluid, meaning it may get colored
        // if this is undesired, just use separate elements
        if (fluidFaces > 0) {
          if (fluidFaces < part.faces.size()) {
            TConstruct.LOG.warn("Mixed fluid and non-fluid elements in model {}, may cause unexpected results", BAKE_LOCATION);
          }
          fluidParts.set(i);
        }
      }
    }
    Set<String> retextured = this.retextured.isEmpty() ? Collections.emptySet() : RetexturedModel.getAllRetextured(this.model, this.retextured);
    return new Baked(baked, elements, model.getColorData(), textures, transform, useAmbientOcclusion, useBlockLight, itemTransforms, rootTransform, renderType, fluidTextures, fluidParts, retextured);
  }

  private record BakedCacheKey(FluidStack fluid, @Nullable ResourceLocation texture) {}

  /** Baked wrapper class */
  public static class Baked extends DynamicBakedWrapper<BakedModel> {
    private final Map<BakedCacheKey,BakedModel> cache = new ConcurrentHashMap<>();
    private final List<BlockElement> elements;
    private final List<ColorData> colorData;
    private final TextureSlots textures;
    private final ModelState transform;
    private final boolean useAmbientOcclusion;
    private final boolean useBlockLight;
    private final ItemTransforms itemTransforms;
    private final Transformation rootTransform;
    private final RenderTypeGroup renderType;
    private final Set<String> fluids;
    private final BitSet fluidParts;
    private final Set<String> retextured;

    protected Baked(BakedModel original, List<BlockElement> elements, List<ColorData> colorData, TextureSlots textures, ModelState transform, boolean useAmbientOcclusion, boolean useBlockLight, ItemTransforms itemTransforms, Transformation rootTransform, RenderTypeGroup renderType, Set<String> fluids, BitSet fluidParts, Set<String> retextured) {
      super(original);
      this.elements = elements;
      this.colorData = colorData;
      this.textures = textures;
      this.transform = transform;
      this.useAmbientOcclusion = useAmbientOcclusion;
      this.useBlockLight = useBlockLight;
      this.itemTransforms = itemTransforms;
      this.rootTransform = rootTransform;
      this.renderType = renderType;
      this.fluids = fluids;
      this.fluidParts = fluidParts;
      this.retextured = retextured;
    }

    /** Retextures a model for the given fluid */
    private BakedModel getRetexturedModel(BakedCacheKey key) {
      // build the replacement texture map: retextured block textures + fluid still textures
      Map<String,Material> replacements = new HashMap<>();
      if (key.texture != null) {
        Material blockTexture = new Material(TextureAtlas.LOCATION_BLOCKS, key.texture);
        for (String name : retextured) {
          replacements.put(name, blockTexture);
        }
      }
      int luminosity = 0;
      int fluidColor = -1;
      if (!key.fluid.isEmpty()) {
        IClientFluidTypeExtensions attributes = IClientFluidTypeExtensions.of(key.fluid.getFluid());
        fluidColor = attributes.getTintColor(key.fluid);
        luminosity = key.fluid.getFluid().getFluidType().getLightLevel(key.fluid);
        Material stillTexture = new Material(TextureAtlas.LOCATION_BLOCKS, attributes.getStillTexture(key.fluid));
        for (String name : fluids) {
          replacements.put(name, stillTexture);
        }
      }

      // compose the root transform into the model state so quads are baked in the right orientation
      ModelState state = rootTransform.isIdentity() ? transform : UnbakedElementsHelper.composeRootTransformIntoModelState(transform, rootTransform);
      Transformation rotation = state.getRotation();
      boolean defaultUvLock = state.isUvLocked();

      // start baking
      TextureAtlasSprite particle = SimpleBlockModel.findSprite(Material::sprite, textures, replacements, "particle");
      SimpleBakedModel.Builder builder = SimpleBlockModel.bakedBuilder(useAmbientOcclusion, useBlockLight, itemTransforms).particle(particle);
      int size = elements.size();
      for (int i = 0; i < size; i++) {
        BlockElement element = elements.get(i);
        ColorData colors = LogicHelper.getOrDefault(colorData, i, ColorData.DEFAULT);
        if (fluidParts.get(i)) {
          IQuadTransformer fluidTransformer = fluidColor == -1 ? QuadTransformers.empty() : ColoredBlockModel.applyColorQuadTransformer(fluidColor);
          ColoredBlockModel.bakePart(builder, textures, replacements, element, luminosity, Material::sprite, rotation, fluidTransformer, colors.isUvLock(defaultUvLock));
        } else {
          int partColor = colors.color();
          IQuadTransformer partTransformer = partColor == -1 ? QuadTransformers.empty() : ColoredBlockModel.applyColorQuadTransformer(partColor);
          ColoredBlockModel.bakePart(builder, textures, replacements, element, colors.luminosity(), Material::sprite, rotation, partTransformer, colors.isUvLock(defaultUvLock));
        }
      }
      return builder.build(renderType);
    }

    /** Gets a retextured model for the given fluid, using the cached model if possible */
    public BakedModel getCachedModel(BakedCacheKey key) {
      return this.cache.computeIfAbsent(key, this::getRetexturedModel);
    }

    /** Gets the cached model for the given fluid and contained block (used by both block and item rendering) */
    public BakedModel getCachedModel(FluidStack fluid, @Nullable Block block) {
      return getCachedModel(new BakedCacheKey(fluid, block != null ? ModelHelper.getParticleTexture(block) : null));
    }

    @Nonnull
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random, ModelData data, @Nullable RenderType renderType) {
      FluidStack fluid = fluids.isEmpty() ? FluidStack.EMPTY : data.get(ModelProperties.FLUID_STACK);
      if (fluid == null) {
        fluid = FluidStack.EMPTY;
      }
      Block block = retextured.isEmpty() ? null : data.get(RetexturedHelper.BLOCK_PROPERTY);
      if (!fluid.isEmpty() || block != null) {
        BakedCacheKey key = new BakedCacheKey(fluid, block != null ? ModelHelper.getParticleTexture(block) : null);
        return getCachedModel(key).getQuads(state, direction, random, data, renderType);
      }
      return parent.getQuads(state, direction, random, data, renderType);
    }
  }

  /** Deserializes this model from JSON */
  public static FluidTextureModel deserialize(JsonObject json, JsonDeserializationContext context) {
    ColoredBlockModel model = ColoredBlockModel.deserialize(json, context);
    Set<String> fluids = Collections.emptySet();
    if (json.has("fluids")) {
      fluids = ImmutableSet.copyOf(JsonHelper.parseList(json, "fluids", GsonHelper::convertToString));
    }
    Set<String> retextured = Collections.emptySet();
    if (json.has("retextured")) {
      retextured = ImmutableSet.copyOf(JsonHelper.parseList(json, "retextured", GsonHelper::convertToString));
    }
    return new FluidTextureModel(model, fluids, retextured);
  }
}
