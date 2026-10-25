package slimeknights.tconstruct.library.client.model.block;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.block.model.BlockElement;
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
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.model.data.ModelData;
import slimeknights.mantle.client.model.util.ColoredBlockModel;
import slimeknights.mantle.client.model.util.DynamicBakedWrapper;
import slimeknights.mantle.client.model.util.ElementModelData;
import slimeknights.mantle.client.model.util.SimpleBlockModel;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.library.client.model.ModelProperties;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/**
 * This model contains a single scalable fluid that can either be statically rendered or rendered in the TESR. It also supports rendering fluids in the item model.
 * <p>
 * 1.21.5: split like Mantle's dynamic models:
 * <ul>
 *   <li>The model loader ({@code "loader": "tconstruct:tank"}) holds the fluid cuboid and GUI variant, and bakes the base
 *   elements as static geometry (used by items and when the fluid is rendered by the block entity renderer).</li>
 *   <li>The block state model type ({@code {"type": "tconstruct:tank", "model": ...}}, see {@link Unbaked}) reads the fluid
 *   from the block entity model data ({@code level.getModelData(pos)}) and rebakes with the fluid element.</li>
 * </ul>
 */
public class TankModel extends SimpleBlockModel {
  /** Shared loader instance */
  public static final UnbakedModelLoader<TankModel> LOADER = TankModel::deserialize;

  @Nullable
  protected final SimpleBlockModel gui;
  protected final IncrementalFluidCuboid fluid;
  protected final boolean forceModelFluid;

  public TankModel(SimpleBlockModel model, @Nullable SimpleBlockModel gui, IncrementalFluidCuboid fluid, boolean forceModelFluid) {
    super(model);
    this.gui = gui;
    this.fluid = fluid;
    this.forceModelFluid = forceModelFluid;
  }

  /** Gets the GUI variant of this model, or null if it has none */
  @Nullable
  public SimpleBlockModel getGui() {
    return gui;
  }

  /**
   * Finds the tank model in a resolved model chain, allowing child models without a loader to inherit the config
   * @param model  Resolved model
   * @return  Tank model, or null if the chain has none
   */
  @Nullable
  public static TankModel find(@Nullable ResolvedModel model) {
    for (; model != null; model = model.parent()) {
      if (model.wrapped() instanceof TankModel tank) {
        return tank;
      }
    }
    return null;
  }

  /**
   * Block state model for tanks, referenced from the blockstate JSON as
   * {@code {"type": "tconstruct:tank", "model": "<tank model>", "x": 0, "y": 0}}. Accepts the same fields as a vanilla variant.
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
      TankModel tank = find(resolved);
      if (tank == null) {
        TConstruct.LOG.warn("Block state model type tconstruct:tank used with {}, which is not a tank model", resolved.debugName());
        return new SingleVariant(base);
      }
      return new Baked(base, tank, ElementModelData.of(baker, resolved, state));
    }
  }

  /** Cache key since fluids don't do equality over amount */
  private record CacheKey(FluidStack fluid, int increments) {
    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      return other instanceof CacheKey key && increments == key.increments && FluidStack.isSameFluidSameComponents(fluid, key.fluid);
    }

    @Override
    public int hashCode() {
      return 31 * FluidStack.hashFluidAndComponents(fluid) + increments;
    }
  }

  /**
   * Baked variant handling the dynamic fluid.
   */
  public static class Baked extends DynamicBakedWrapper<SingleVariant> {
    @SuppressWarnings("WeakerAccess")
    protected final TankModel original;
    private final ElementModelData data;
    private final Cache<CacheKey,SimpleModelWrapper> cache = CacheBuilder
      .newBuilder()
      .maximumSize(64)
      .build();

    @SuppressWarnings("WeakerAccess")
    protected Baked(BlockModelPart base, TankModel original, ElementModelData data) {
      super(new SingleVariant(base));
      this.original = original;
      this.data = data;
    }

    /**
     * Bakes the model with the given fluid element
     * @param replacements Fluid texture replacements
     * @param fluid        Fluid element for baking
     * @param color        Color for the fluid part
     * @param luminosity   Luminosity for the fluid part
     * @return  Baked model
     */
    private SimpleModelWrapper bakeWithFluid(Map<String,Material> replacements, BlockElement fluid, int color, int luminosity) {
      // PORT 1.21.5: rebaked at runtime through the block atlas (Material#sprite); verify tank fluids in the uitest phase
      Function<String,TextureAtlasSprite> sprites = data.runtimeSprites(replacements);
      QuadCollection.Builder builder = new QuadCollection.Builder();
      // first, add all regular elements
      data.bake(builder, sprites);
      // next, add in the fluid
      IQuadTransformer fluidTransformer = color == -1 ? QuadTransformers.empty() : ColoredBlockModel.applyColorQuadTransformer(color);
      ColoredBlockModel.bakePart(builder, fluid, luminosity, sprites, data.state(), fluidTransformer);
      return data.part(builder.build(), data.particle(replacements));
    }

    /**
     * Gets the model with the fluid part added
     * @param key  Cache key containing fluid and increments
     * @return  Model with the fluid part
     */
    private SimpleModelWrapper getModel(CacheKey key) {
      // fetch fluid data
      FluidStack stack = key.fluid();
      IClientFluidTypeExtensions attributes = IClientFluidTypeExtensions.of(stack.getFluid());
      FluidType type = stack.getFluid().getFluidType();
      int color = attributes.getTintColor(stack);
      int luminosity = type.getLightLevel(stack);
      Map<String,Material> replacements = new HashMap<>();
      replacements.put("fluid", new Material(TextureAtlas.LOCATION_BLOCKS, attributes.getStillTexture(stack)));
      replacements.put("flowing_fluid", new Material(TextureAtlas.LOCATION_BLOCKS, attributes.getFlowingTexture(stack)));

      // add fluid part
      BlockElement fluid = original.fluid.getPart(key.increments, type.isLighterThanAir());
      // bake the model
      // 1.21.5: the GUI variant only matters for items, which render the static geometry, so it is not baked here
      return bakeWithFluid(replacements, fluid, color, luminosity);
    }

    /**
     * Gets a cached model with the fluid part added
     * @param fluid  Scaled contained fluid
     * @return  Cached model, or null if it failed to bake
     */
    @Nullable
    private SimpleModelWrapper getCachedModel(CacheKey fluid) {
      try {
        return cache.get(fluid, () -> getModel(fluid));
      }
      catch(ExecutionException e) {
        TConstruct.LOG.error(e);
        return null;
      }
    }

    /** Creates a cache key for the given fluid and capacity */
    private CacheKey getKey(FluidStack fluid, int capacity) {
      int increments = original.fluid.getIncrements();
      return new CacheKey(fluid.copy(), Mth.clamp(fluid.getAmount() * increments / capacity, 1, increments));
    }

    /**
     * Gets a cached model with the fluid part added
     * @param fluid     Fluid contained
     * @param capacity  Tank capacity
     * @return  Cached model, or null if it failed to bake
     */
    @Nullable
    public BlockModelPart getCachedModel(FluidStack fluid, int capacity) {
      return getCachedModel(getKey(fluid, capacity));
    }

    /** Gets the cache key for the fluid in the model data, or null if the fluid is not rendered in the model */
    @Nullable
    private CacheKey getKey(BlockAndTintGetter level, BlockPos pos) {
      if (original.forceModelFluid || Config.CLIENT.tankFluidModel.get()) {
        ModelData data = level.getModelData(pos);
        FluidStack fluid = data.get(ModelProperties.FLUID_STACK);
        if (fluid != null && !fluid.isEmpty()) {
          int capacity = Objects.requireNonNullElse(data.get(ModelProperties.TANK_CAPACITY), fluid.getAmount());
          return getKey(fluid, capacity);
        }
      }
      return null;
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockModelPart> parts) {
      CacheKey key = getKey(level, pos);
      if (key != null) {
        SimpleModelWrapper model = getCachedModel(key);
        if (model != null) {
          parts.add(model);
          return;
        }
      }
      parent.collectParts(random, parts);
    }

    @Override
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
      return new GeometryKey(this, getKey(level, pos));
    }

    /** Geometry key, the fluid and its level fully determine the geometry */
    private record GeometryKey(Baked model, @Nullable CacheKey fluid) {}
  }


  /** Loader for this model */
  public static TankModel deserialize(JsonObject json, JsonDeserializationContext context) {
    SimpleBlockModel model = SimpleBlockModel.deserialize(json, context);
    SimpleBlockModel gui = null;
    if (json.has("gui")) {
      gui = SimpleBlockModel.deserialize(GsonHelper.getAsJsonObject(json, "gui"), context);
    }
    IncrementalFluidCuboid fluid = IncrementalFluidCuboid.fromJson(GsonHelper.getAsJsonObject(json, "fluid"));
    boolean forceModelFluid = GsonHelper.getAsBoolean(json, "render_fluid_in_model", false);
    return new TankModel(model, gui, fluid, forceModelFluid);
  }
}
