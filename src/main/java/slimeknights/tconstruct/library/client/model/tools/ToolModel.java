package slimeknights.tconstruct.library.client.model.tools;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.model.UnbakedCompositeModel;
import org.joml.Vector3f;
import slimeknights.mantle.client.model.util.MantleItemLayerModel;
import slimeknights.mantle.util.ItemLayerPixels;
import slimeknights.mantle.util.ReversedListBuilder;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfo.TintedSprite;
import slimeknights.tconstruct.library.client.model.ItemModelUtil;
import slimeknights.tconstruct.library.client.modifiers.IBakedModifierModel;
import slimeknights.tconstruct.library.client.modifiers.ModifierModelMap;
import slimeknights.tconstruct.library.client.modifiers.ModifierModelMapManager;
import slimeknights.tconstruct.library.client.modifiers.model.ModifierModel;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.recipe.worktable.ModifierSetWorktableRecipe;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.MaterialIdNBT;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Model handling all tools, both multipart and non. Composites per-material part textures and per-modifier overlays
 * dynamically per item stack.
 * <p>
 * PORT 1.21.4: ported from the removed {@code IUnbakedGeometry}/{@code ItemOverrides} system to
 * {@link ItemModel.Unbaked}. The per-stack bake + cache (formerly {@code MaterialOverrideHandler.resolve}) now lives in
 * {@link Baked#update} (decision #1), which also selects the gui/small/left/right variant from {@link BakedToolModel} by
 * display context (since {@link BakedModel#applyTransform} can no longer swap models). Material and modifier colors are
 * baked into the quads, retiring the old {@code ItemColors} handler (decision #2). Part textures are carried in the
 * model codec and display transforms come from a referenced {@code transform_model}. Ammo overlay rendering is stubbed
 * (see breadcrumb in {@link #bakeInternal}) pending a Phase-4 {@code ItemStackRenderState}-based extraction.
 */
public class ToolModel {
  /** Set of transform types that make tools render small */
  private static final BitSet SMALL_TOOL_TYPES = new BitSet();

  /** Registers a new small tool transform type */
  public static synchronized void registerSmallTool(ItemDisplayContext type) {
    SMALL_TOOL_TYPES.set(type.ordinal());
  }

  /** Builds a Material on the block atlas for the given texture location */
  private static Material material(ResourceLocation texture) {
    return new Material(TextureAtlas.LOCATION_BLOCKS, texture);
  }

  /**
   * adds quads for relevant modifiers
   */
  private static void addModifierQuads(Function<Material, TextureAtlasSprite> spriteGetter, ModifierModelMap modifierModels, List<FirstModifier> firstModifiers, boolean showTraits, IToolStackView tool, Consumer<Collection<BakedQuad>> quadConsumer, @Nullable ItemLayerPixels pixels, Transformation transforms, boolean isLarge) {
    if (!modifierModels.isEmpty()) {
      int modelIndex = 0;
      List<ModifierEntry> modifiers = (showTraits ? tool.getModifiers() : tool.getUpgrades()).getModifiers();
      ModifierEntry[] firsts = new ModifierEntry[firstModifiers.size()];
      if (!modifiers.isEmpty()) {
        Set<ModifierId> hidden = ModifierSetWorktableRecipe.getModifierSet(tool.getPersistentData(), TConstruct.getResource("invisible_modifiers"));
        for (int i = modifiers.size() - 1; i >= 0; i--) {
          ModifierEntry entry = modifiers.get(i);
          ModifierId modifier = entry.getModifier().getId();
          int index = FirstModifier.indexOf(firstModifiers, modifier);
          if (index != -1) {
            firsts[index] = entry;
          } else if (!hidden.contains(modifier)) {
            IBakedModifierModel model = modifierModels.get(modifier);
            if (model != null) {
              model.addQuads(tool, entry, spriteGetter, transforms, isLarge, modelIndex, quadConsumer, pixels);
              modelIndex += model.getTintIndexes();
            }
          }
        }
      }
      for (int i = firsts.length - 1; i >= 0; i--) {
        ModifierEntry entry = firsts[i];
        FirstModifier first = firstModifiers.get(i);
        if (entry != null || first.forced) {
          IBakedModifierModel model = modifierModels.get(first.id);
          if (model != null) {
            if (entry == null) {
              entry = new ModifierEntry(first.id, 0);
            }
            model.addQuads(tool, entry, spriteGetter, transforms, isLarge, modelIndex, quadConsumer, pixels);
            modelIndex += model.getTintIndexes();
          }
        }
      }
      for (ModifierModel model : modifierModels.constant().values()) {
        model.addQuads(tool, ModifierEntry.EMPTY, spriteGetter, transforms, isLarge, modelIndex, quadConsumer, pixels);
        modelIndex += model.getTintIndexes();
      }
    }
  }

  /** Modifier that may be forced */
  record FirstModifier(ModifierId id, boolean forced) {
    public static final Codec<FirstModifier> CODEC = RecordCodecBuilder.create(inst -> inst.group(
      ResourceLocation.CODEC.xmap(ModifierId::new, ModifierId::getLocation).fieldOf("name").forGetter(FirstModifier::id),
      Codec.BOOL.optionalFieldOf("forced", false).forGetter(FirstModifier::forced)
    ).apply(inst, FirstModifier::new));

    /** Gets the index of a modifier in the list */
    public static int indexOf(List<FirstModifier> list, ModifierId id) {
      for (int i = 0; i < list.size(); i++) {
        if (list.get(i).id.equals(id)) {
          return i;
        }
      }
      return -1;
    }
  }

  /**
   * Builds the four display variants (right/left/small/gui) for a given material + modifier state.
   * @param materialGetter  Resolves a texture name to a block-atlas material
   * @param largeTransforms Transform for the large parts, or null to only build the small model
   * @param itemTransforms  Display transforms from the referenced transform model
   */
  private static BakedToolModel bakeInternal(Function<String,Material> materialGetter, @Nullable Transformation largeTransforms,
                                             List<ToolPart> parts, ModifierModelMap modifierModels, List<FirstModifier> firstModifiers, boolean showTraits,
                                             List<MaterialVariantId> materials, @Nullable IToolStackView tool,
                                             boolean useAmbientOcclusion, boolean useBlockLight, net.minecraft.client.renderer.block.model.ItemTransforms itemTransforms) {
    Function<Material,TextureAtlasSprite> spriteGetter = Material::sprite;
    RenderTypeGroup renderType = MantleItemLayerModel.getDefaultRenderType(RenderTypeGroup.EMPTY);
    Transformation smallTransforms = Transformation.identity();

    ReversedListBuilder<Collection<BakedQuad>> smallQuads = new ReversedListBuilder<>();
    ItemLayerPixels smallPixels = new ItemLayerPixels();
    ReversedListBuilder<Collection<BakedQuad>> largeQuads = largeTransforms != null ? new ReversedListBuilder<>() : smallQuads;
    ItemLayerPixels largePixels = largeTransforms != null ? new ItemLayerPixels() : smallPixels;

    // modifiers first (for the item layer pixels)
    if (tool != null && !modifierModels.isEmpty()) {
      addModifierQuads(spriteGetter, modifierModels, firstModifiers, showTraits, tool, smallQuads::add, smallPixels, smallTransforms, false);
      if (largeTransforms != null) {
        addModifierQuads(spriteGetter, modifierModels, firstModifiers, showTraits, tool, largeQuads::add, largePixels, largeTransforms, true);
      }
    }

    // all parts
    TextureAtlasSprite particle = null;
    for (int i = parts.size() - 1; i >= 0; i--) {
      ToolPart part = parts.get(i);
      if (part.hasMaterials()) {
        int index = part.index();
        MaterialVariantId material = index < materials.size() ? materials.get(index) : IMaterial.UNKNOWN_ID;
        TintedSprite materialSprite = MaterialModel.getMaterialSprite(spriteGetter, materialGetter.apply(part.getName(false)), material);
        particle = materialSprite.sprite();
        smallQuads.add(MantleItemLayerModel.getQuadsForSprite(materialSprite.color(), -1, materialSprite.sprite(), smallTransforms, materialSprite.emissivity(), smallPixels));
        if (largeTransforms != null) {
          largeQuads.add(MaterialModel.getQuadsForMaterial(spriteGetter, materialGetter.apply(part.getName(true)), material, -1, largeTransforms, largePixels));
        }
      } else {
        particle = spriteGetter.apply(materialGetter.apply(part.getName(false)));
        smallQuads.add(MantleItemLayerModel.getQuadsForSprite(-1, -1, particle, smallTransforms, 0, smallPixels));
        if (largeTransforms != null) {
          largeQuads.add(MantleItemLayerModel.getQuadsForSprite(-1, -1, spriteGetter.apply(materialGetter.apply(part.getName(true))), largeTransforms, 0, largePixels));
        }
      }
    }
    if (particle == null) {
      particle = spriteGetter.apply(new Material(TextureAtlas.LOCATION_BLOCKS, MissingTextureAtlasSprite.getLocation()));
      TConstruct.LOG.error("Created tool model without a particle sprite, this means it somehow has no parts. This should not be possible");
    }

    // PORT 1.21.4: ammo overlay quads are not added here. The old path pulled quads from the ammo item's BakedModel via
    // ItemRenderer.getModel, which is gone; a Phase-4 ItemStackRenderState-based extraction can reintroduce them.

    // collect quads then build each variant via the composite baked builder
    List<BakedQuad> smallAll = new ArrayList<>();
    smallQuads.build(coll -> smallAll.addAll(coll));
    List<BakedQuad> guiAll = new ArrayList<>();
    for (BakedQuad quad : smallAll) {
      if (quad.getDirection() == Direction.SOUTH) {
        guiAll.add(quad);
      }
    }
    BakedModel small = UnbakedCompositeModel.Baked.builder(useAmbientOcclusion, false, useBlockLight, particle, itemTransforms).addQuads(renderType, smallAll).build();
    BakedModel gui = UnbakedCompositeModel.Baked.builder(useAmbientOcclusion, false, useBlockLight, particle, itemTransforms).addQuads(renderType, guiAll).build();

    BakedModel right;
    if (largeTransforms != null) {
      List<BakedQuad> largeAll = new ArrayList<>();
      largeQuads.build(coll -> largeAll.addAll(coll));
      right = UnbakedCompositeModel.Baked.builder(useAmbientOcclusion, false, useBlockLight, particle, itemTransforms).addQuads(renderType, largeAll).build();
    } else {
      right = small;
    }
    // left hand matches right (ammo-flip for the left hand is part of the stubbed ammo feature)
    return new BakedToolModel(right, right, small, gui);
  }

  /** Holds the four display variants; the item model selects between them by display context */
  public static final class BakedToolModel {
    private final BakedModel right;
    private final BakedModel left;
    private final BakedModel small;
    private final BakedModel gui;

    public BakedToolModel(BakedModel right, BakedModel left, BakedModel small, BakedModel gui) {
      this.right = right;
      this.left = left;
      this.small = small;
      this.gui = gui;
    }

    /** Selects the variant for the given display context */
    public BakedModel forContext(ItemDisplayContext type) {
      if (type == ItemDisplayContext.GUI) {
        return gui;
      }
      if (type == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || type == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
        return left;
      }
      if (right != small && SMALL_TOOL_TYPES.get(type.ordinal())) {
        return small;
      }
      return right;
    }
  }

  /** Data class for a single tool part */
  record ToolPart(String name, int index) {
    public static final ToolPart DEFAULT = new ToolPart("tool", -1);
    public static final List<ToolPart> DEFAULT_PARTS = List.of(DEFAULT);
    public static final Codec<ToolPart> CODEC = RecordCodecBuilder.create(inst -> inst.group(
      Codec.STRING.fieldOf("name").forGetter(ToolPart::name),
      Codec.INT.optionalFieldOf("index", -1).forGetter(ToolPart::index)
    ).apply(inst, ToolPart::new));

    /** If true, this part has material variants */
    public boolean hasMaterials() {
      return index >= 0;
    }

    /** Gets the texture name for this part */
    public String getName(boolean isLarge) {
      return isLarge ? "large_" + name : name;
    }
  }

  /** Unbaked tool item model */
  public record Unbaked(ResourceLocation transformModel, Map<String,ResourceLocation> textures, List<ToolPart> parts,
                        boolean large, net.minecraft.world.phys.Vec2 offset, List<ResourceLocation> modifierModels,
                        List<FirstModifier> firstModifiers, boolean showTraits) implements ItemModel.Unbaked {
    public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
      ResourceLocation.CODEC.fieldOf("transform_model").forGetter(Unbaked::transformModel),
      Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC).optionalFieldOf("textures", Map.of()).forGetter(Unbaked::textures),
      ToolPart.CODEC.listOf().optionalFieldOf("parts", List.of()).forGetter(Unbaked::parts),
      Codec.BOOL.optionalFieldOf("large", false).forGetter(Unbaked::large),
      MaterialModel.OFFSET_CODEC.optionalFieldOf("large_offset", net.minecraft.world.phys.Vec2.ZERO).forGetter(Unbaked::offset),
      ResourceLocation.CODEC.listOf().optionalFieldOf("modifier_maps", List.of()).forGetter(Unbaked::modifierModels),
      FirstModifier.CODEC.listOf().optionalFieldOf("first_modifiers", List.of()).forGetter(Unbaked::firstModifiers),
      Codec.BOOL.optionalFieldOf("show_traits", false).forGetter(Unbaked::showTraits)
    ).apply(inst, Unbaked::new));

    @Override
    public MapCodec<Unbaked> type() {
      return MAP_CODEC;
    }

    @Override
    public void resolveDependencies(net.minecraft.client.resources.model.ResolvableModel.Resolver resolver) {
      resolver.resolve(transformModel);
    }

    @Override
    public ItemModel bake(ItemModel.BakingContext context) {
      ModelBaker baker = context.blockModelBaker();
      BakedModel transforms = context.bake(transformModel);
      // load modifier models using the bake-time sprite getter
      ModifierModelMap modifierMap = ModifierModelMapManager.INSTANCE.getModelsForTool(baker.sprites()::get, modifierModels, List.of(), List.of(), BAKE_LOCATION);
      // large tools are stretched in X and Y by 200%, with a special offset
      Transformation largeTransforms = large ? new Transformation(new Vector3f((offset.x - 8) / 32, (-offset.y - 8) / 32, 0), null, new Vector3f(2, 2, 1), null) : null;
      List<ToolPart> toolParts = parts.isEmpty() ? ToolPart.DEFAULT_PARTS : parts;
      Function<String,Material> materialGetter = name -> material(textures.getOrDefault(name, MissingTextureAtlasSprite.getLocation()));
      return new Baked(materialGetter, toolParts, largeTransforms, modifierMap, firstModifiers, showTraits,
                       transforms.useAmbientOcclusion(), transforms.usesBlockLight(), transforms.getTransforms());
    }
  }

  /** Location used for modifier model lookup, since bake no longer receives the model location */
  private static final ResourceLocation BAKE_LOCATION = TConstruct.getResource("dynamic_tool");

  /** Baked tool item model: resolves and caches the composited variants per stack */
  public static final class Baked implements ItemModel {
    private final Function<String,Material> materialGetter;
    private final List<ToolPart> toolParts;
    @Nullable
    private final Transformation largeTransforms;
    private final ModifierModelMap modifierModels;
    private final List<FirstModifier> firstModifiers;
    private final boolean showTraits;
    private final boolean useAmbientOcclusion;
    private final boolean useBlockLight;
    private final net.minecraft.client.renderer.block.model.ItemTransforms itemTransforms;
    /** Base (no material/modifier) variants */
    private final BakedToolModel base;
    private final Cache<ToolCacheKey, BakedToolModel> cache = CacheBuilder.newBuilder()
      .maximumSize(Math.max(64, slimeknights.tconstruct.library.client.materials.MaterialRenderInfoLoader.INSTANCE.getAllRenderInfos().size() * 3L / 2))
      .build();

    Baked(Function<String,Material> materialGetter, List<ToolPart> toolParts, @Nullable Transformation largeTransforms, ModifierModelMap modifierModels,
          List<FirstModifier> firstModifiers, boolean showTraits, boolean useAmbientOcclusion, boolean useBlockLight,
          net.minecraft.client.renderer.block.model.ItemTransforms itemTransforms) {
      this.materialGetter = materialGetter;
      this.toolParts = toolParts;
      this.largeTransforms = largeTransforms;
      this.modifierModels = modifierModels;
      this.firstModifiers = firstModifiers;
      this.showTraits = showTraits;
      this.useAmbientOcclusion = useAmbientOcclusion;
      this.useBlockLight = useBlockLight;
      this.itemTransforms = itemTransforms;
      this.base = bake(List.of(), null);
    }

    private BakedToolModel bake(List<MaterialVariantId> materials, @Nullable IToolStackView tool) {
      return bakeInternal(materialGetter, largeTransforms, toolParts, modifierModels, firstModifiers, showTraits, materials, tool, useAmbientOcclusion, useBlockLight, itemTransforms);
    }

    /** Resolves the composited variants for the given stack, using the cache */
    private BakedToolModel resolve(ItemStack stack) {
      List<MaterialVariantId> materialIds = MaterialIdNBT.from(stack).getMaterials();
      IToolStackView tool = ToolStack.from(stack);
      ModifierNBT modifiers = showTraits ? tool.getModifiers() : tool.getUpgrades();
      // nothing unique: use the base model
      boolean anyForced = false;
      for (FirstModifier modifier : firstModifiers) {
        if (modifier.forced) { anyForced = true; break; }
      }
      if (materialIds.isEmpty() && modifiers.isEmpty() && !anyForced) {
        return base;
      }
      // build the cache key from the modifier-requested keys
      ImmutableList.Builder<Object> builder = ImmutableList.builder();
      Set<ModifierId> hidden = ModifierSetWorktableRecipe.getModifierSet(tool.getPersistentData(), TConstruct.getResource("invisible_modifiers"));
      ModifierEntry[] firstEntries = new ModifierEntry[firstModifiers.size()];
      for (ModifierEntry entry : modifiers) {
        ModifierId id = entry.getId();
        int index = FirstModifier.indexOf(firstModifiers, id);
        if (index != -1) {
          firstEntries[index] = entry;
        } else if (!hidden.contains(id)) {
          IBakedModifierModel model = modifierModels.get(id);
          if (model != null) {
            Object cacheKey = model.getCacheKey(tool, entry);
            if (cacheKey != null) {
              builder.add(cacheKey);
            }
          }
        }
      }
      for (int i = 0; i < firstModifiers.size(); i++) {
        FirstModifier modifier = firstModifiers.get(i);
        ModifierEntry entry = firstEntries[i];
        if (entry != null || modifier.forced) {
          IBakedModifierModel model = modifierModels.get(modifier.id);
          if (model != null) {
            if (entry == null) {
              entry = new ModifierEntry(modifier.id, 0);
            }
            Object cacheKey = model.getCacheKey(tool, entry);
            if (cacheKey != null) {
              builder.add(cacheKey);
            }
          }
        }
      }
      for (ModifierModel model : modifierModels.constant().values()) {
        Object cacheKey = model.getCacheKey(tool, ModifierEntry.EMPTY);
        if (cacheKey != null) {
          builder.add(cacheKey);
        }
      }
      try {
        return cache.get(new ToolCacheKey(materialIds, builder.build()), () -> bake(materialIds, tool));
      } catch (ExecutionException e) {
        TConstruct.LOG.error("Failed to get tool model from cache", e);
        return base;
      }
    }

    @Override
    public void update(ItemStackRenderState renderState, ItemStack stack, ItemModelResolver resolver, ItemDisplayContext displayContext, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
      BakedToolModel model = resolve(stack);
      ItemModelUtil.renderModel(renderState, stack, model.forContext(displayContext));
    }
  }

  /** Simple data class to cache built tool variants, contains everything unique in the textures */
  private record ToolCacheKey(List<MaterialVariantId> materials, List<Object> modifierData) {}
}
