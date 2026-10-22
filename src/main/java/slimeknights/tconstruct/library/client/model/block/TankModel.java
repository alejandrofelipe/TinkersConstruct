package slimeknights.tconstruct.library.client.model.block;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.math.Transformation;
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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextMap;
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
import net.neoforged.neoforge.fluids.FluidType;
import slimeknights.mantle.client.model.util.ColoredBlockModel;
import slimeknights.mantle.client.model.util.SimpleBlockModel;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.library.client.model.ModelProperties;
import slimeknights.tconstruct.library.client.model.UniqueGuiModel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * This model contains a single scalable fluid that can either be statically rendered or rendered in the TESR. It also supports rendering fluids in the item model.
 * <p>
 * PORT 1.21.4: ported to {@link AbstractUnbakedModel}; the fluid part is rebaked through the Mantle replacement-map
 * primitive. The item-side override (fluid from the stack's tank) now lives in the item-model system, which wraps this
 * {@link Baked} and calls {@link Baked#getCachedModel(FluidStack, int)} from {@code ItemModel.update}.
 */
public class TankModel extends AbstractUnbakedModel {
  protected static final ResourceLocation BAKE_LOCATION = TConstruct.getResource("dynamic_model_baking");

  /** Shared loader instance */
  public static final UnbakedModelLoader<TankModel> LOADER = TankModel::deserialize;

  protected final SimpleBlockModel model;
  @Nullable
  protected final SimpleBlockModel gui;
  protected final IncrementalFluidCuboid fluid;
  protected final boolean forceModelFluid;

  public TankModel(SimpleBlockModel model, @Nullable SimpleBlockModel gui, IncrementalFluidCuboid fluid, boolean forceModelFluid) {
    super(model.getParameters());
    this.model = model;
    this.gui = gui;
    this.fluid = fluid;
    this.forceModelFluid = forceModelFluid;
  }

  @Override
  public void resolveDependencies(Resolver resolver) {
    super.resolveDependencies(resolver);
    model.resolveDependencies(resolver);
    if (gui != null) {
      gui.resolveDependencies(resolver);
    }
  }

  @Override
  public BakedModel bake(TextureSlots textures, ModelBaker baker, ModelState transform, boolean useAmbientOcclusion, boolean useBlockLight, ItemTransforms itemTransforms, ContextMap additionalProperties) {
    BakedModel baked = model.bake(textures, baker, transform, useAmbientOcclusion, useBlockLight, itemTransforms, additionalProperties);
    BakedModel bakedGui = baked;
    if (gui != null) {
      bakedGui = gui.bake(textures, baker, transform, useAmbientOcclusion, useBlockLight, itemTransforms, additionalProperties);
    }
    Transformation rootTransform = additionalProperties.getOrDefault(NeoForgeModelProperties.TRANSFORM, Transformation.identity());
    RenderTypeGroup renderType = additionalProperties.getOrDefault(NeoForgeModelProperties.RENDER_TYPE, RenderTypeGroup.EMPTY);
    return new Baked(baked, bakedGui, this, textures, transform, useAmbientOcclusion, useBlockLight, itemTransforms, rootTransform, renderType);
  }

  /**
   * Baked variant handling the dynamic fluid.
   */
  public static class Baked extends UniqueGuiModel.Baked {
    @SuppressWarnings("WeakerAccess")
    protected final TankModel original;
    private final TextureSlots textures;
    private final ModelState transform;
    private final boolean useAmbientOcclusion;
    private final boolean useBlockLight;
    private final ItemTransforms itemTransforms;
    private final Transformation rootTransform;
    private final RenderTypeGroup renderType;
    private final Cache<CacheKey,BakedModel> cache = CacheBuilder
      .newBuilder()
      .maximumSize(64)
      .build();

    /** Cache key since fluids don't do equality over amount */
    private record CacheKey(FluidStack fluid, int increments) {}

    @SuppressWarnings("WeakerAccess")
    protected Baked(BakedModel baked, BakedModel gui, TankModel original, TextureSlots textures, ModelState transform, boolean useAmbientOcclusion, boolean useBlockLight, ItemTransforms itemTransforms, Transformation rootTransform, RenderTypeGroup renderType) {
      super(baked, gui);
      this.original = original;
      this.textures = textures;
      this.transform = transform;
      this.useAmbientOcclusion = useAmbientOcclusion;
      this.useBlockLight = useBlockLight;
      this.itemTransforms = itemTransforms;
      this.rootTransform = rootTransform;
      this.renderType = renderType;
    }

    /**
     * Bakes the model with the given fluid element
     * @param baseModel    Base model for original elements
     * @param replacements Fluid texture replacements
     * @param fluid        Fluid element for baking
     * @param color        Color for the fluid part
     * @param luminosity   Luminosity for the fluid part
     * @return  Baked model
     */
    private BakedModel bakeWithFluid(SimpleBlockModel baseModel, Map<String,Material> replacements, BlockElement fluid, int color, int luminosity) {
      ModelState state = rootTransform.isIdentity() ? transform : UnbakedElementsHelper.composeRootTransformIntoModelState(transform, rootTransform);
      Transformation rotation = state.getRotation();
      boolean uvlock = state.isUvLocked();
      TextureAtlasSprite particle = SimpleBlockModel.findSprite(Material::sprite, textures, replacements, "particle");
      SimpleBakedModel.Builder builder = SimpleBlockModel.bakedBuilder(useAmbientOcclusion, useBlockLight, itemTransforms).particle(particle);
      // first, add all regular elements
      for (BlockElement element : baseModel.getElements()) {
        SimpleBlockModel.bakePart(builder, textures, replacements, element, Material::sprite, state, QuadTransformers.empty());
      }
      // next, add in the fluid
      IQuadTransformer fluidTransformer = color == -1 ? QuadTransformers.empty() : ColoredBlockModel.applyColorQuadTransformer(color);
      ColoredBlockModel.bakePart(builder, textures, replacements, fluid, luminosity, Material::sprite, rotation, fluidTransformer, uvlock);
      return builder.build(renderType);
    }

    /**
     * Gets the model with the fluid part added
     * @param key  Cache key containing fluid and increments
     * @return  Model with the fluid part
     */
    private BakedModel getModel(CacheKey key) {
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
      BakedModel baked = bakeWithFluid(original.model, replacements, fluid, color, luminosity);

      // if we have GUI, bake a GUI variant
      if (original.gui != null) {
        baked = new UniqueGuiModel.Baked(baked, bakeWithFluid(original.gui, replacements, fluid, color, 0));
      }

      // return what we ended up with
      return baked;
    }

    /**
     * Gets a cached model with the fluid part added
     * @param fluid  Scaled contained fluid
     * @return  Cached model
     */
    private BakedModel getCachedModel(CacheKey fluid) {
      try {
        return cache.get(fluid, () -> getModel(fluid));
      }
      catch(ExecutionException e) {
        TConstruct.LOG.error(e);
        return this;
      }
    }

    /**
     * Gets a cached model with the fluid part added
     * @param fluid     Fluid contained
     * @param capacity  Tank capacity
     * @return  Cached model
     */
    public BakedModel getCachedModel(FluidStack fluid, int capacity) {
      int increments = original.fluid.getIncrements();
      return getCachedModel(new CacheKey(fluid.copy(), Mth.clamp(fluid.getAmount() * increments / capacity, 1, increments)));
    }

    @Nonnull
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data, @Nullable RenderType renderType) {
      if ((original.forceModelFluid || Config.CLIENT.tankFluidModel.get()) && data.has(ModelProperties.FLUID_STACK)) {
        FluidStack fluid = data.get(ModelProperties.FLUID_STACK);
        if (fluid != null && !fluid.isEmpty()) {
          int capacity = Objects.requireNonNullElse(data.get(ModelProperties.TANK_CAPACITY), fluid.getAmount());
          return getCachedModel(fluid, capacity).getQuads(state, side, rand, ModelData.EMPTY, renderType);
        }
      }
      return parent.getQuads(state, side, rand, data, renderType);
    }
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
