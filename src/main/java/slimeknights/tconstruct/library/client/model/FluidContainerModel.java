package slimeknights.tconstruct.library.client.model;

import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import slimeknights.mantle.client.model.util.MantleItemLayerModel;
import slimeknights.tconstruct.library.client.model.ItemModelUtil.BakedItem;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Item model for a fluid container (copper can, etc): composites a base sprite with a fluid clipped to a mask sprite,
 * with the fluid either fixed in the model or read from the stack.
 * <p>
 * PORT 1.21.4: ported from the removed {@code IUnbakedGeometry}/{@code DynamicFluidContainerModel}/{@code ItemOverrides}
 * to {@link ItemModel.Unbaked}. The fluid is clipped to the mask via {@link MantleItemLayerModel#getMaskedQuadsForSprite}
 * (the removed {@code UnbakedGeometryHelper} flow); textures are carried in the codec and display transforms come from a
 * referenced {@code transform_model}. Dynamic per-stack fluid resolution now lives in {@link Baked#update}.
 */
public final class FluidContainerModel {
  private FluidContainerModel() {}

  /** Clone of the same field from the former {@code DynamicFluidContainerModel}; referenced by FluidModifierModel */
  public static final Transformation FLUID_TRANSFORM = new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1, 1, 1.002f), new Quaternionf());

  /** Builds a block-atlas material */
  private static Material material(ResourceLocation texture) {
    return new Material(TextureAtlas.LOCATION_BLOCKS, texture);
  }

  /** Unbaked fluid container item model */
  public record Unbaked(ResourceLocation transformModel, Optional<ResourceLocation> base, Optional<ResourceLocation> fluidMask,
                        Optional<ResourceLocation> particle, FluidStack fluid, boolean flipGas) implements ItemModel.Unbaked {
    public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
      ResourceLocation.CODEC.fieldOf("transform_model").forGetter(Unbaked::transformModel),
      ResourceLocation.CODEC.optionalFieldOf("base").forGetter(Unbaked::base),
      ResourceLocation.CODEC.optionalFieldOf("fluid").forGetter(Unbaked::fluidMask),
      ResourceLocation.CODEC.optionalFieldOf("particle").forGetter(Unbaked::particle),
      FluidStack.OPTIONAL_CODEC.optionalFieldOf("contained_fluid", FluidStack.EMPTY).forGetter(Unbaked::fluid),
      Codec.BOOL.optionalFieldOf("flip_gas", true).forGetter(Unbaked::flipGas)
    ).apply(inst, Unbaked::new));

    @Override
    public MapCodec<Unbaked> type() {
      return MAP_CODEC;
    }

    @Override
    public void resolveDependencies(net.minecraft.client.resources.model.ResolvableModel.Resolver resolver) {
      resolver.markDependency(transformModel);
    }

    @Override
    public ItemModel bake(ItemModel.BakingContext context) {
      // 1.21.5: display transforms are read from the resolved transform model
      ResolvedModel transforms = context.blockModelBaker().getModel(transformModel);
      return new Baked(
        transforms.getTopTransforms(),
        base.map(FluidContainerModel::material).orElse(null),
        fluidMask.map(FluidContainerModel::material).orElse(null),
        particle.map(FluidContainerModel::material).orElse(null),
        fluid, flipGas);
    }
  }

  /** Baked fluid container model */
  public static final class Baked implements ItemModel {
    private final ItemTransforms itemTransforms;
    @Nullable private final Material base;
    @Nullable private final Material fluidMask;
    @Nullable private final Material particle;
    private final FluidStack staticFluid;
    private final boolean flipGas;
    private final Map<FluidStack, BakedItem> cache = new ConcurrentHashMap<>();

    Baked(ItemTransforms itemTransforms, @Nullable Material base, @Nullable Material fluidMask, @Nullable Material particle, FluidStack staticFluid, boolean flipGas) {
      this.itemTransforms = itemTransforms;
      this.base = base;
      this.fluidMask = fluidMask;
      this.particle = particle;
      this.staticFluid = staticFluid;
      this.flipGas = flipGas;
    }

    /** Builds the composite model for the given fluid */
    private BakedItem bakeWith(FluidStack fluid) {
      TextureAtlasSprite baseSprite = base != null ? base.sprite() : null;
      TextureAtlasSprite maskSprite = fluidMask != null ? fluidMask.sprite() : null;
      IClientFluidTypeExtensions clientFluid = IClientFluidTypeExtensions.of(fluid.getFluid());
      TextureAtlasSprite fluidSprite = !fluid.isEmpty() ? material(clientFluid.getStillTexture(fluid)).sprite() : null;

      // determine particle
      TextureAtlasSprite particleSprite = particle != null ? particle.sprite() : null;
      if (particleSprite == null) particleSprite = fluidSprite;
      if (particleSprite == null) particleSprite = baseSprite;
      if (particleSprite == null) particleSprite = material(MissingTextureAtlasSprite.getLocation()).sprite();

      // container is rendered flat: block light false
      List<BakedQuad> builder = new ArrayList<>();

      // base layer
      if (baseSprite != null) {
        builder.addAll(MantleItemLayerModel.getQuadsForSprite(-1, -1, baseSprite, Transformation.identity(), 0));
      }

      // fluid layer, clipped to the mask
      if (fluidSprite != null && maskSprite != null) {
        // PORT 1.21.4: gas flip rotates 180 about the center; may need Phase-4 tuning of the flip origin
        Transformation fluidTransform = FLUID_TRANSFORM;
        if (flipGas && fluid.getFluid().getFluidType().isLighterThanAir()) {
          fluidTransform = new Transformation(null, new Quaternionf(0, 0, 1, 0), null, null).applyOrigin(new Vector3f(0.5f, 0.5f, 0.5f)).compose(FLUID_TRANSFORM);
        }
        int light = fluid.getFluid().getFluidType().getLightLevel(fluid);
        int color = clientFluid.getTintColor(fluid);
        List<BakedQuad> quads = MantleItemLayerModel.getMaskedQuadsForSprite(color, -1, maskSprite, fluidSprite, fluidTransform, light, null);
        builder.addAll(quads);
      }
      return new BakedItem(List.copyOf(builder), ItemModelUtil.properties(false, particleSprite, itemTransforms), ItemModelUtil.defaultRenderType());
    }

    /** Gets the cached model for the given fluid */
    private BakedItem getCachedModel(FluidStack fluid) {
      // normalize amount so the cache key is consistent
      FluidStack key = fluid.copy();
      key.setAmount(FluidType.BUCKET_VOLUME);
      return cache.computeIfAbsent(key, this::bakeWith);
    }

    @Override
    public void update(ItemStackRenderState renderState, ItemStack stack, ItemModelResolver resolver, ItemDisplayContext displayContext, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
      FluidStack fluid = staticFluid;
      if (fluid.isEmpty()) {
        fluid = FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
      }
      ItemModelUtil.renderModel(renderState, stack, displayContext, getCachedModel(fluid));
    }
  }
}
