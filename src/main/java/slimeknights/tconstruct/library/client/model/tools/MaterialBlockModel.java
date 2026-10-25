package slimeknights.tconstruct.library.client.model.tools;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.block.model.SimpleModelWrapper;
import net.minecraft.client.renderer.block.model.SingleVariant;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.block.model.Variant;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.QuadCollection;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.model.data.ModelProperty;
import slimeknights.mantle.client.model.RetexturedModel;
import slimeknights.mantle.client.model.util.ColoredBlockModel;
import slimeknights.mantle.client.model.util.DynamicBakedWrapper;
import slimeknights.mantle.client.model.util.ElementModelData;
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
import slimeknights.tconstruct.library.client.model.ItemModelUtil;
import slimeknights.tconstruct.library.client.model.ItemModelUtil.BakedItem;
import slimeknights.tconstruct.library.client.model.ModelProperties;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.nbt.MaterialIdNBT;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;

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
 * 1.21.5: split like Mantle's dynamic models:
 * <ul>
 *   <li>The model loader ({@code "loader": "tconstruct:material_block"}) holds the material texture names and bakes the
 *   base elements as static geometry.</li>
 *   <li>The block state model type ({@code {"type": "tconstruct:material_block", "model": ...}}, see {@link Unbaked})
 *   reads the material (and for anvils the texture block) from the block entity model data ({@code level.getModelData(pos)}).</li>
 *   <li>The item model type ({@code {"type": "tconstruct:material_block", "model": ...}}, see {@link ItemUnbaked}) reads
 *   them from the stack.</li>
 * </ul>
 * Both share the per-material rebaking in {@link AbstractBaked}, built from the {@link ElementModelData} captured at bake time.
 * @see MaterialModel
 * @see ToolModel
 */
public class MaterialBlockModel extends SimpleBlockModel {
  /** Loadable for the list of material textures */
  private static final Loadable<Set<String>> MATERIAL = StringLoadable.DEFAULT.set(ArrayLoadable.COMPACT);
  /** Loadable for the list of parts, each a list of material textures */
  private static final Loadable<List<Set<String>>> PARTS = StringLoadable.DEFAULT.set(ArrayLoadable.COMPACT_OR_EMPTY).list(1);
  /** Shared loader instance */
  public static final UnbakedModelLoader<MaterialBlockModel> LOADER = MaterialBlockModel::deserialize;

  public enum ModelType { TOOL, PART, ANVIL }

  /** List of parts, each is a set of materials to retexture. Expected each element is a single material or not a material, no multiple or mixed. */
  private final List<Set<String>> parts;
  /** Type of model to create. */
  private final ModelType type;

  public MaterialBlockModel(SimpleBlockModel model, List<Set<String>> parts, ModelType type) {
    super(model);
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

  /**
   * Finds the material block model in a resolved model chain, allowing child models without a loader to inherit the config
   * @param model  Resolved model
   * @return  Material block model, or null if the chain has none
   */
  @Nullable
  public static MaterialBlockModel find(@Nullable ResolvedModel model) {
    for (; model != null; model = model.parent()) {
      if (model.wrapped() instanceof MaterialBlockModel material) {
        return material;
      }
    }
    return null;
  }

  /**
   * Creates the dynamic material logic for the given resolved model, shared by the block state and item models.
   * @param baker     Model baker
   * @param resolved  Resolved model
   * @param state     Model state
   * @return  Dynamic material logic, or null if the model has no material block model in its chain
   */
  @Nullable
  public static AbstractBaked<?,?> bakeDynamic(ModelBaker baker, ResolvedModel resolved, ModelState state) {
    MaterialBlockModel model = find(resolved);
    if (model == null) {
      return null;
    }
    ElementModelData data = ElementModelData.of(baker, resolved, state);
    List<Set<String>> parts = model.parts.stream().map(part -> RetexturedModel.getAllRetextured(resolved, part)).toList();

    // part model - fetches material from NBT field
    if (model.type == ModelType.PART) {
      return new BakedPart(data, parts.get(0));
    }
    // anvil model - fetches material from NBT field, but can also choose block texture
    if (model.type == ModelType.ANVIL) {
      return new BakedAnvil(data, parts.get(0));
    }
    // for tools with just one material, use simpler tool model
    if (parts.size() == 1) {
      return new SimpleBakedTool(data, parts.get(0));
    }
    // standard tool with multiple materials
    boolean particleRetextured = parts.stream().anyMatch(set -> set.contains("particle"));
    return new BakedTool(data, parts, particleRetextured);
  }

  /**
   * Common logic between all variants of baking. Rebakes the captured elements per material, caching the result.
   * 1.21.5: no longer a model itself; the block state model ({@link BlockModel}) and item model ({@link ItemUnbaked}) use it.
   * Methods return null when the base model should be used.
   */
  public static abstract class AbstractBaked<T,P> {
    protected final ElementModelData data;
    protected final boolean particleRetextured;
    protected final ModelProperty<P> property;

    private AbstractBaked(ElementModelData data, boolean particleRetextured, ModelProperty<P> property) {
      this.data = data;
      this.particleRetextured = particleRetextured;
      this.property = property;
    }

    /** Gets the captured element data */
    public ElementModelData getData() {
      return data;
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
        TextureSlots textures = data.textures();
        for (String name : retextured) {
          Material texture = textures.getMaterial(name);
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

    /**
     * Retextures with the given materials list
     * @return  Baked model, or null to use the base model
     */
    @Nullable
    protected SimpleModelWrapper bakeWith(T materials) {
      // map of name to replacement texture
      Map<String,Material> replacements = new HashMap<>();
      // map of name to transformation to apply, will likely be smaller than replacement textures
      Map<String,TintedSprite> tints = new HashMap<>();

      // delegate material fetching
      // PORT 1.21.5: rebaked at runtime through the block atlas (Material#sprite); verify material blocks/anvils in the uitest phase
      Function<Material,TextureAtlasSprite> spriteGetter = Material::sprite;
      fetchMaterials(materials, spriteGetter, replacements, tints);

      // quick exit in case we found nothing, should never happen
      if (replacements.isEmpty()) {
        return null;
      }

      // if no parts need colors, we can just use standard replacement baking
      if (tints.isEmpty()) {
        return data.part(data.bakeReplaced(replacements), data.particle(replacements));
      }

      // need to tint or apply light to some textures: manual per-part bake
      Function<String,TextureAtlasSprite> sprites = data.runtimeSprites(replacements);
      ModelState state = data.state();
      QuadCollection.Builder builder = new QuadCollection.Builder();
      for (BlockElement part : data.elements()) {
        // determine if any of the faces needs a tint
        // for simplicity, assume the whole part is tinted if so. Build your model to separate distinct material faces if needed
        TintedSprite tint = null;
        for (BlockElementFace face : part.faces().values()) {
          TintedSprite faceTint = tints.get(face.texture());
          if (faceTint != null) {
            tint = faceTint;
            break;
          }
        }
        // apply color if we have it
        if (tint != null) {
          IQuadTransformer partTransformer = tint.color() == -1 ? QuadTransformers.empty() : ColoredBlockModel.applyColorQuadTransformer(tint.color());
          ColoredBlockModel.bakePart(builder, part, tint.emissivity(), sprites, state, partTransformer);
        } else {
          SimpleBlockModel.bakePart(builder, part, sprites, state, QuadTransformers.empty());
        }
      }
      return data.part(builder.build(), data.particle(replacements));
    }

    /**
     * Gets the cached model for the given materials.
     * @return  Baked model, or null to use the base model
     */
    @Nullable
    public abstract SimpleModelWrapper getCachedModel(P materials);

    /**
     * Resolves the baked model for the given item stack; drives the item-model system (replaces the old ItemOverrides).
     * @return  Baked model, or null to use the base model
     */
    @Nullable
    public abstract SimpleModelWrapper resolveForStack(ItemStack stack);

    /**
     * Resolves the baked model for the given block entity model data.
     * @return  Baked model, or null to use the base model
     */
    @Nullable
    public SimpleModelWrapper resolveForData(ModelData data) {
      P materials = data.get(property);
      if (materials != null) {
        return getCachedModel(materials);
      }
      return null;
    }

    /**
     * Resolves the particle model for the given block entity model data.
     * @return  Baked model, or null to use the base model
     */
    @Nullable
    public SimpleModelWrapper resolveParticle(ModelData data) {
      if (particleRetextured) {
        P materials = data.get(property);
        if (materials != null) {
          return getCachedModel(materials);
        }
      }
      return null;
    }

    /** Creates the geometry key part for the given model data, everything that determines the geometry */
    @Nullable
    public Object geometryKey(ModelData data) {
      return data.get(property);
    }
  }

  /** Baked model for tools with multiple materials. */
  public static class BakedTool extends AbstractBaked<MaterialIdNBT,MaterialIdNBT> {
    /** Cache of texture name to baked model */
    private final Cache<MaterialIdNBT, SimpleModelWrapper> cache = CacheBuilder
      .newBuilder()
      // ensure we can display every single tool that shows in JEI, plus a couple extra
      .maximumSize(MaterialRenderInfoLoader.INSTANCE.getAllRenderInfos().size() * 3L / 2)
      .build();

    private final List<Set<String>> parts;
    private BakedTool(ElementModelData data, List<Set<String>> parts, boolean particleRetextured) {
      super(data, particleRetextured, ModelProperties.MATERIALS);
      this.parts = parts;
    }

    @Override
    protected void fetchMaterials(MaterialIdNBT materials, Function<Material, TextureAtlasSprite> spriteGetter, Map<String, Material> replacements, Map<String, TintedSprite> tints) {
      for (int i = 0; i < parts.size(); i++) {
        fetchMaterial(materials.getMaterial(i), parts.get(i), spriteGetter, replacements, tints);
      }
    }

    @Nullable
    @Override
    public SimpleModelWrapper getCachedModel(MaterialIdNBT materials) {
      if (!materials.getMaterials().isEmpty()) {
        try {
          // guava caches do not accept null, so cache a placeholder when the base model is used
          return unwrap(cache.get(materials, () -> Optional.ofNullable(bakeWith(materials)).orElse(NO_MODEL)));
        } catch (ExecutionException e) {
          TConstruct.LOG.error("Failed to get tool model from cache", e);
        }
      }
      return null;
    }

    @Nullable
    @Override
    public SimpleModelWrapper resolveForStack(ItemStack stack) {
      if (stack.isEmpty() || stack.isComponentsPatchEmpty()) {
        return null;
      }
      return getCachedModel(MaterialIdNBT.from(stack));
    }
  }

  /** Placeholder cached when a material resolves to the base model, as the caches cannot hold null */
  private static final SimpleModelWrapper NO_MODEL = new SimpleModelWrapper(QuadCollection.EMPTY, false, null, null);

  /** Converts the cache placeholder back to null */
  @Nullable
  private static SimpleModelWrapper unwrap(@Nullable SimpleModelWrapper model) {
    return model == NO_MODEL ? null : model;
  }

  /** Common logic between tool parts and tools with just 1 material. */
  public static abstract class BakedSingleMaterial<P> extends AbstractBaked<MaterialVariantId,P> {
    /** Cache of texture name to baked model */
    private final Map<MaterialVariantId, SimpleModelWrapper> cache = new ConcurrentHashMap<>();

    protected final Set<String> retexture;
    private final Function<MaterialVariantId, SimpleModelWrapper> baker = material -> Optional.ofNullable(bakeWith(material)).orElse(NO_MODEL);

    private BakedSingleMaterial(ElementModelData data, Set<String> retexture, ModelProperty<P> property) {
      super(data, retexture.contains("particle"), property);
      this.retexture = retexture;
    }

    /** Gets the model for the given material, or null to use the base model */
    @Nullable
    public SimpleModelWrapper getCachedModel(MaterialVariantId material) {
      if (IMaterial.UNKNOWN_ID.equals(material)) {
        return null;
      }
      return unwrap(cache.computeIfAbsent(material, baker));
    }

    @Override
    protected void fetchMaterials(MaterialVariantId materials, Function<Material, TextureAtlasSprite> spriteGetter, Map<String, Material> replacements, Map<String, TintedSprite> tints) {
      fetchMaterial(materials, retexture, spriteGetter, replacements, tints);
    }
  }

  /** Baked model for tools with just 1 material. */
  public static class SimpleBakedTool extends BakedSingleMaterial<MaterialIdNBT> {
    private SimpleBakedTool(ElementModelData data, Set<String> retexture) {
      super(data, retexture, ModelProperties.MATERIALS);
    }

    @Nullable
    @Override
    public SimpleModelWrapper getCachedModel(MaterialIdNBT materials) {
      return getCachedModel(materials.getMaterial(0));
    }

    @Nullable
    @Override
    public SimpleModelWrapper resolveForStack(ItemStack stack) {
      if (stack.isEmpty() || stack.isComponentsPatchEmpty()) {
        return null;
      }
      return getCachedModel(MaterialIdNBT.from(stack));
    }
  }

  /** Baked model for tool parts. */
  public static class BakedPart extends BakedSingleMaterial<MaterialVariantId> {
    private BakedPart(ElementModelData data, Set<String> retexture) {
      super(data, retexture, ModelProperties.MATERIAL);
    }

    @Nullable
    @Override
    public SimpleModelWrapper resolveForStack(ItemStack stack) {
      if (stack.isEmpty() || stack.isComponentsPatchEmpty()) {
        return null;
      }
      return getCachedModel(IMaterialItem.getMaterialFromStack(stack));
    }
  }

  /** Model that supports both block and material textures. */
  public static class BakedAnvil extends BakedSingleMaterial<MaterialVariantId> {
    private final Map<ResourceLocation, SimpleModelWrapper> blockCache = new ConcurrentHashMap<>();
    private final Function<ResourceLocation, SimpleModelWrapper> blockBaker = this::bakeWithBlock;
    private BakedAnvil(ElementModelData data, Set<String> retexture) {
      super(data, retexture, ModelProperties.MATERIAL);
    }

    /** Rebakes the model with a specific block texture. See also {@link RetexturedModel} */
    private SimpleModelWrapper bakeWithBlock(ResourceLocation texture) {
      Material material = new Material(TextureAtlas.LOCATION_BLOCKS, texture);
      Map<String,Material> replacements = new HashMap<>();
      for (String name : retexture) {
        replacements.put(name, material);
      }
      return data.part(data.bakeReplaced(replacements), data.particle(replacements));
    }

    /** Gets the cached model for the given block. */
    public SimpleModelWrapper getCachedModel(Block block) {
      return this.blockCache.computeIfAbsent(ModelHelper.getParticleTexture(block), blockBaker);
    }

    @Nullable
    @Override
    public SimpleModelWrapper resolveForStack(ItemStack stack) {
      if (stack.isEmpty() || stack.isComponentsPatchEmpty()) {
        return null;
      }
      // block takes priority over material
      Block block = RetexturedHelper.getTexture(stack);
      if (block != Blocks.AIR) {
        return getCachedModel(block);
      }
      return getCachedModel(IMaterialItem.getMaterialFromStack(stack));
    }

    @Nullable
    @Override
    public SimpleModelWrapper resolveParticle(ModelData data) {
      // block takes priority if present
      if (particleRetextured) {
        Block block = data.get(RetexturedHelper.BLOCK_PROPERTY);
        if (block != null) {
          return getCachedModel(block);
        }
      }
      return super.resolveParticle(data);
    }

    @Nullable
    @Override
    public SimpleModelWrapper resolveForData(ModelData data) {
      Block block = data.get(RetexturedHelper.BLOCK_PROPERTY);
      if (block != null) {
        return getCachedModel(block);
      }
      return super.resolveForData(data);
    }

    @Nullable
    @Override
    public Object geometryKey(ModelData data) {
      Block block = data.get(RetexturedHelper.BLOCK_PROPERTY);
      if (block != null) {
        return ModelHelper.getParticleTexture(block);
      }
      return super.geometryKey(data);
    }
  }

  /**
   * Block state model for material blocks, referenced from the blockstate JSON as
   * {@code {"type": "tconstruct:material_block", "model": "<material block model>", "x": 0, "y": 0}}. Accepts the same fields as a vanilla variant.
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
      AbstractBaked<?,?> dynamic = bakeDynamic(baker, resolved, state);
      if (dynamic == null) {
        TConstruct.LOG.warn("Block state model type tconstruct:material_block used with {}, which is not a material block model", resolved.debugName());
        return new SingleVariant(base);
      }
      return new BlockModel(base, dynamic);
    }
  }

  /** Block state model choosing the material variant from the block entity model data */
  public static class BlockModel extends DynamicBakedWrapper<SingleVariant> {
    private final AbstractBaked<?,?> dynamic;

    protected BlockModel(BlockModelPart base, AbstractBaked<?,?> dynamic) {
      super(new SingleVariant(base));
      this.dynamic = dynamic;
    }

    /** Gets the dynamic material logic */
    public AbstractBaked<?,?> getDynamic() {
      return dynamic;
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockModelPart> parts) {
      SimpleModelWrapper model = dynamic.resolveForData(level.getModelData(pos));
      if (model != null) {
        parts.add(model);
      } else {
        parent.collectParts(random, parts);
      }
    }

    @Override
    public TextureAtlasSprite particleIcon(BlockAndTintGetter level, BlockPos pos, BlockState state) {
      SimpleModelWrapper model = dynamic.resolveParticle(level.getModelData(pos));
      if (model != null) {
        return model.particleIcon();
      }
      return super.particleIcon(level, pos, state);
    }

    @Override
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
      return new GeometryKey(this, dynamic.geometryKey(level.getModelData(pos)));
    }

    /** Geometry key, the material (or texture block) fully determines the geometry */
    private record GeometryKey(BlockModel model, @Nullable Object key) {}
  }

  /**
   * Item model that renders a {@link MaterialBlockModel} block model, resolving the per-stack material/block
   * variant through {@link AbstractBaked}.
   * <p>
   * PORT 1.21.4: replaces the item-side {@code ItemOverrides}. Registered via {@code RegisterItemModelsEvent}; the item
   * JSON is {@code {"model":{"type":"tconstruct:material_block","model":"<block model>"}}}.
   */
  public record ItemUnbaked(ResourceLocation model) implements ItemModel.Unbaked {
    public static final MapCodec<ItemUnbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
      ResourceLocation.CODEC.fieldOf("model").forGetter(ItemUnbaked::model)
    ).apply(inst, ItemUnbaked::new));

    @Override
    public MapCodec<ItemUnbaked> type() {
      return MAP_CODEC;
    }

    @Override
    public void resolveDependencies(net.minecraft.client.resources.model.ResolvableModel.Resolver resolver) {
      resolver.markDependency(model);
    }

    @Override
    public ItemModel bake(ItemModel.BakingContext context) {
      // base model, matching vanilla BlockModelWrapper
      ModelBaker baker = context.blockModelBaker();
      ResolvedModel resolved = baker.getModel(model);
      TextureSlots slots = resolved.getTopTextureSlots();
      List<BakedQuad> quads = resolved.bakeTopGeometry(slots, baker, BlockModelRotation.X0_Y0).getAll();
      ModelRenderProperties properties = ModelRenderProperties.fromResolvedModel(baker, resolved, slots);
      BakedItem base = new BakedItem(quads, properties, ElementModelData.itemRenderType(resolved));
      return new Baked(base, bakeDynamic(baker, resolved, BlockModelRotation.X0_Y0));
    }

    /** Baked item model wrapping the dynamic material logic */
    public static class Baked implements ItemModel {
      private final BakedItem base;
      @Nullable
      private final AbstractBaked<?,?> dynamic;
      private final Map<SimpleModelWrapper,BakedItem> cache = new ConcurrentHashMap<>();

      public Baked(BakedItem base, @Nullable AbstractBaked<?,?> dynamic) {
        this.base = base;
        this.dynamic = dynamic;
      }

      /** Converts a baked block part to an item variant */
      private BakedItem toItem(SimpleModelWrapper part) {
        assert dynamic != null;
        return new BakedItem(part.quads().getAll(), dynamic.getData().itemProperties(part.particleIcon()), base.renderType());
      }

      @Override
      public void update(ItemStackRenderState renderState, ItemStack stack, ItemModelResolver resolver, ItemDisplayContext displayContext, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
        BakedItem resolved = base;
        if (dynamic != null) {
          SimpleModelWrapper part = dynamic.resolveForStack(stack);
          if (part != null) {
            resolved = cache.computeIfAbsent(part, this::toItem);
          }
        }
        ItemModelUtil.renderModel(renderState, stack, displayContext, resolved);
      }
    }
  }
}
