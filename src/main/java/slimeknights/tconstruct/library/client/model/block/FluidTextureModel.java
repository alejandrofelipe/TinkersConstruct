package slimeknights.tconstruct.library.client.model.block;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.block.model.SimpleModelWrapper;
import net.minecraft.client.renderer.block.model.SingleVariant;
import net.minecraft.client.renderer.block.model.Variant;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.QuadCollection;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.model.data.ModelData;
import slimeknights.mantle.client.model.RetexturedModel;
import slimeknights.mantle.client.model.util.ColoredBlockModel;
import slimeknights.mantle.client.model.util.ColoredBlockModel.ColorData;
import slimeknights.mantle.client.model.util.DynamicBakedWrapper;
import slimeknights.mantle.client.model.util.ElementModelData;
import slimeknights.mantle.client.model.util.ModelHelper;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.mantle.util.LogicHelper;
import slimeknights.mantle.util.RetexturedHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.model.ModelProperties;

import javax.annotation.Nullable;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Model that replaces fluid textures with the fluid from model data.
 * <p>
 * 1.21.5: split like Mantle's dynamic models:
 * <ul>
 *   <li>The model loader ({@code "loader": "tconstruct:fluid_texture"}) holds the fluid and retextured names, and bakes the
 *   base elements as static geometry.</li>
 *   <li>The block state model type ({@code {"type": "tconstruct:fluid_texture", "model": ...}}, see {@link Unbaked}) reads
 *   the fluid and texture block from the block entity model data ({@code level.getModelData(pos)}) and rebakes.</li>
 * </ul>
 */
public class FluidTextureModel extends ColoredBlockModel {
  /** Loader instance */
  public static final UnbakedModelLoader<FluidTextureModel> LOADER = FluidTextureModel::deserialize;

  private final Set<String> fluids;
  private final Set<String> retextured;

  public FluidTextureModel(ColoredBlockModel model, Set<String> fluids, Set<String> retextured) {
    super(model, model.getColorData());
    this.fluids = fluids;
    this.retextured = retextured;
  }

  /** Trims the # character off the beginning of a texture name (if present) */
  private static String trimTextureName(String name) {
    if (name.charAt(0) == '#') {
      return name.substring(1);
    }
    return name;
  }

  /**
   * Finds the fluid texture model in a resolved model chain, allowing child models without a loader to inherit the config
   * @param model  Resolved model
   * @return  Fluid texture model, or null if the chain has none
   */
  @Nullable
  public static FluidTextureModel find(@Nullable ResolvedModel model) {
    for (; model != null; model = model.parent()) {
      if (model.wrapped() instanceof FluidTextureModel fluidTexture) {
        return fluidTexture;
      }
    }
    return null;
  }

  /**
   * Block state model for fluid texture models, referenced from the blockstate JSON as
   * {@code {"type": "tconstruct:fluid_texture", "model": "<fluid texture model>", "x": 0, "y": 0}}. Accepts the same fields as a vanilla variant.
   */
  public record Unbaked(Variant variant) implements CustomUnbakedBlockStateModel {
    public static final MapCodec<Unbaked> MAP_CODEC = Variant.MAP_CODEC.xmap(Unbaked::new, Unbaked::variant);

    @Override
    public MapCodec<Unbaked> codec() {
      return MAP_CODEC;
    }

    @Override
    public void resolveDependencies(Resolver resolver) {
      variant.resolveDependencies(resolver);
    }

    @Override
    public BlockStateModel bake(ModelBaker baker) {
      ResolvedModel resolved = baker.getModel(variant.modelLocation());
      ModelState state = variant.modelState().asModelState();
      BlockModelPart base = SimpleModelWrapper.bake(baker, resolved, state);
      FluidTextureModel model = find(resolved);
      if (model == null) {
        TConstruct.LOG.warn("Block state model type tconstruct:fluid_texture used with {}, which is not a fluid texture model", resolved.debugName());
        return new SingleVariant(base);
      }
      ElementModelData data = ElementModelData.of(baker, resolved, state);

      // determine which block parts are fluids
      Set<String> fluidTextures = model.fluids.isEmpty() ? Collections.emptySet() : RetexturedModel.getAllRetextured(resolved, model.fluids);
      List<BlockElement> elements = data.elements();
      int size = elements.size();
      BitSet fluidParts = new BitSet(size);
      if (!fluidTextures.isEmpty()) {
        for (int i = 0; i < size; i++) {
          Map<?,BlockElementFace> faces = elements.get(i).faces();
          long fluidFaces = faces.values().stream()
                                 .filter(face -> fluidTextures.contains(trimTextureName(face.texture())))
                                 .count();
          // for simplicity, each part is either a fluid or not. If for some reason it contains both we mark it as a fluid, meaning it may get colored
          // if this is undesired, just use separate elements
          if (fluidFaces > 0) {
            if (fluidFaces < faces.size()) {
              TConstruct.LOG.warn("Mixed fluid and non-fluid elements in model {}, may cause unexpected results", resolved.debugName());
            }
            fluidParts.set(i);
          }
        }
      }
      Set<String> retextured = model.retextured.isEmpty() ? Collections.emptySet() : RetexturedModel.getAllRetextured(resolved, model.retextured);
      return new Baked(base, data, fluidTextures, fluidParts, retextured);
    }
  }

  /** Cache key, compares fluids by fluid and components as fluid stacks have no equality */
  private record BakedCacheKey(FluidStack fluid, @Nullable ResourceLocation texture) {
    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      return other instanceof BakedCacheKey key && Objects.equals(texture, key.texture) && FluidStack.isSameFluidSameComponents(fluid, key.fluid);
    }

    @Override
    public int hashCode() {
      return 31 * FluidStack.hashFluidAndComponents(fluid) + Objects.hashCode(texture);
    }
  }

  /** Baked wrapper class */
  public static class Baked extends DynamicBakedWrapper<SingleVariant> {
    private final Map<BakedCacheKey,SimpleModelWrapper> cache = new ConcurrentHashMap<>();
    private final ElementModelData data;
    private final Set<String> fluids;
    private final BitSet fluidParts;
    private final Set<String> retextured;

    protected Baked(BlockModelPart original, ElementModelData data, Set<String> fluids, BitSet fluidParts, Set<String> retextured) {
      super(new SingleVariant(original));
      this.data = data;
      this.fluids = fluids;
      this.fluidParts = fluidParts;
      this.retextured = retextured;
    }

    /** Retextures a model for the given fluid */
    private SimpleModelWrapper getRetexturedModel(BakedCacheKey key) {
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

      // start baking
      // PORT 1.21.5: rebaked at runtime through the block atlas (Material#sprite); verify fluid textures in the uitest phase
      Function<String,TextureAtlasSprite> sprites = data.runtimeSprites(replacements);
      ModelState state = data.state();
      List<BlockElement> elements = data.elements();
      List<ColorData> colorData = data.colorData();
      QuadCollection.Builder builder = new QuadCollection.Builder();
      int size = elements.size();
      for (int i = 0; i < size; i++) {
        BlockElement element = elements.get(i);
        ColorData colors = LogicHelper.getOrDefault(colorData, i, ColorData.DEFAULT);
        ModelState partState = colors.uvlock() == null ? state : ModelHelper.withUvLock(state, colors.uvlock());
        if (fluidParts.get(i)) {
          IQuadTransformer fluidTransformer = fluidColor == -1 ? QuadTransformers.empty() : ColoredBlockModel.applyColorQuadTransformer(fluidColor);
          ColoredBlockModel.bakePart(builder, element, luminosity, sprites, partState, fluidTransformer);
        } else {
          int partColor = colors.color();
          IQuadTransformer partTransformer = partColor == -1 ? QuadTransformers.empty() : ColoredBlockModel.applyColorQuadTransformer(partColor);
          ColoredBlockModel.bakePart(builder, element, colors.luminosity(), sprites, partState, partTransformer);
        }
      }
      return data.part(builder.build(), data.particle(replacements));
    }

    /** Gets a retextured model for the given fluid, using the cached model if possible */
    private SimpleModelWrapper getCachedModel(BakedCacheKey key) {
      return this.cache.computeIfAbsent(key, this::getRetexturedModel);
    }

    /** Gets the cached model for the given fluid and contained block (used by both block and item rendering) */
    public BlockModelPart getCachedModel(FluidStack fluid, @Nullable Block block) {
      return getCachedModel(new BakedCacheKey(fluid.copy(), block != null ? ModelHelper.getParticleTexture(block) : null));
    }

    /** Gets the cache key for the model data, or null if the model data has no fluid or texture */
    @Nullable
    private BakedCacheKey getKey(BlockAndTintGetter level, BlockPos pos) {
      ModelData data = level.getModelData(pos);
      FluidStack fluid = fluids.isEmpty() ? null : data.get(ModelProperties.FLUID_STACK);
      if (fluid == null) {
        fluid = FluidStack.EMPTY;
      }
      Block block = retextured.isEmpty() ? null : data.get(RetexturedHelper.BLOCK_PROPERTY);
      // 1.21.5: an unset texture is air, treat it as no texture like Mantle's retextured models
      if (block == Blocks.AIR) {
        block = null;
      }
      if (!fluid.isEmpty() || block != null) {
        // copy as the key is stored in caches, while the model data stack may change
        return new BakedCacheKey(fluid.copy(), block != null ? ModelHelper.getParticleTexture(block) : null);
      }
      return null;
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockModelPart> parts) {
      BakedCacheKey key = getKey(level, pos);
      if (key != null) {
        parts.add(getCachedModel(key));
      } else {
        parent.collectParts(random, parts);
      }
    }

    @Override
    public TextureAtlasSprite particleIcon(BlockAndTintGetter level, BlockPos pos, BlockState state) {
      BakedCacheKey key = getKey(level, pos);
      if (key != null) {
        return getCachedModel(key).particleIcon();
      }
      return super.particleIcon(level, pos, state);
    }

    @Override
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
      return new GeometryKey(this, getKey(level, pos));
    }

    /** Geometry key, the fluid and texture fully determine the geometry */
    private record GeometryKey(Baked model, @Nullable BakedCacheKey key) {}
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
