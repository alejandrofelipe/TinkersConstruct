package slimeknights.tconstruct.library.client.model.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import org.joml.Vector3f;
import slimeknights.mantle.client.model.util.ElementModelData;
import slimeknights.mantle.client.model.util.MantleItemLayerModel;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfo;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfo.TintedSprite;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfoLoader;
import slimeknights.tconstruct.library.client.model.ItemModelUtil;
import slimeknights.tconstruct.library.client.model.ItemModelUtil.BakedItem;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Model for an item with material texture variants, such as tool parts. Used only for single material items,
 * {@link ToolModel} is used for multi-material items.
 * <p>
 * PORT 1.21.4: converted from the removed {@code IUnbakedGeometry}/{@code ItemOverrides} system to
 * {@link ItemModel.Unbaked}. Item models no longer carry their own {@code ItemTransforms}, so the Unbaked references a
 * base model purely to source the display transforms (and the greyscale base texture, read from its particle). The
 * per-material sprite is composited into item-layer quads in {@code update()} (decision #2: material colors are baked
 * into the quads; the old dynamic {@code ItemColors} tint index is retired, so a tint index of -1 is used).
 */
public class MaterialModel {
  /** Codec for a material variant id (string form) */
  public static final Codec<MaterialVariantId> MATERIAL_CODEC = Codec.STRING.comapFlatMap(
    s -> {
      MaterialVariantId id = MaterialVariantId.tryParse(s);
      return id != null ? DataResult.success(id) : DataResult.error(() -> "Invalid material variant id: " + s);
    },
    MaterialVariantId::toString);
  /** Codec for a 2-float offset */
  public static final Codec<Vec2> OFFSET_CODEC = Codec.FLOAT.listOf().comapFlatMap(
    l -> l.size() == 2 ? DataResult.success(new Vec2(l.get(0), l.get(1))) : DataResult.error(() -> "Expected 2 offset values, got " + l.size()),
    v -> List.of(v.x, v.y));

  private MaterialModel() {}

  /**
   * Checks that all unique material textures for the given part exist, logs any that are missing via the sprite getter function.
   */
  public static void validateMaterialTextures(Function<Material, TextureAtlasSprite> spriteGetter, Material texture, @Nullable MaterialVariantId material) {
    // if no specific material is set, load all materials as dependencies. If just one material, use just that one
    if (material == null) {
      MaterialRenderInfoLoader.INSTANCE.getAllRenderInfos().forEach(info -> info.getSprite(texture, spriteGetter));
    } else {
      MaterialRenderInfoLoader.INSTANCE.getRenderInfo(material).ifPresent(info -> info.getSprite(texture, spriteGetter));
    }
  }

  /**
   * Gets the tinted sprite info for the given material
   * @param spriteGetter  Sprite getter instance
   * @param texture       Base texture
   * @param material      Material variant
   * @return  Tinted sprite or fallback
   */
  public static TintedSprite getMaterialSprite(Function<Material, TextureAtlasSprite> spriteGetter, Material texture, MaterialVariantId material) {
    // if the base material is non-null, try to find the sprite for that material
    Optional<MaterialRenderInfo> optional = MaterialRenderInfoLoader.INSTANCE.getRenderInfo(material);
    if (optional.isPresent()) {
      return optional.get().getSprite(texture, spriteGetter);
    }
    return new TintedSprite(spriteGetter.apply(texture), -1, 0);
  }

  /**
   * Gets quads for the given material variant of the texture
   */
  public static List<BakedQuad> getQuadsForMaterial(Function<Material, TextureAtlasSprite> spriteGetter, Material texture, MaterialVariantId material, int tintIndex, Transformation transformation, @Nullable slimeknights.mantle.util.ItemLayerPixels pixels) {
    TintedSprite sprite = getMaterialSprite(spriteGetter, texture, material);
    return MantleItemLayerModel.getQuadsForSprite(sprite.color(), tintIndex, sprite.sprite(), transformation, sprite.emissivity(), pixels);
  }

  /** Builds the transform for the given pixel offset */
  public static Transformation offsetTransform(Vec2 offset) {
    if (Vec2.ZERO.equals(offset)) {
      return Transformation.identity();
    }
    // divide by 16 to convert from pixels to base values; negate Y as positive is up for transforms but down for pixels
    return new Transformation(new Vector3f(offset.x / 16, -offset.y / 16, 0), null, null, null);
  }

  /**
   * Converts a JSON float array to a {@link Vec2}
   * @throws JsonParseException  If there is no array or the length is wrong
   */
  public static Vec2 getVec2(JsonObject json, String name) {
    JsonArray array = GsonHelper.getAsJsonArray(json, name);
    if (array.size() != 2) {
      throw new JsonParseException("Expected " + 2 + " " + name + " values, found: " + array.size());
    }
    float[] vec = new float[2];
    for (int i = 0; i < 2; ++i) {
      vec[i] = GsonHelper.convertToFloat(array.get(i), name + "[" + i + "]");
    }
    return new Vec2(vec[0], vec[1]);
  }

  /** Unbaked item model */
  public record Unbaked(ResourceLocation model, int index, Optional<MaterialVariantId> material, Vec2 offset) implements ItemModel.Unbaked {
    public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
      ResourceLocation.CODEC.fieldOf("model").forGetter(Unbaked::model),
      Codec.INT.optionalFieldOf("index", 0).forGetter(Unbaked::index),
      MATERIAL_CODEC.optionalFieldOf("material").forGetter(Unbaked::material),
      OFFSET_CODEC.optionalFieldOf("offset", Vec2.ZERO).forGetter(Unbaked::offset)
    ).apply(inst, Unbaked::new));

    @Override
    public MapCodec<Unbaked> type() {
      return MAP_CODEC;
    }

    @Override
    public void resolveDependencies(net.minecraft.client.resources.model.ResolvableModel.Resolver resolver) {
      resolver.markDependency(model);
    }

    @Override
    public ItemModel bake(ItemModel.BakingContext context) {
      // 1.21.5: bake the base model like vanilla BlockModelWrapper, BakedModel is gone
      ModelBaker baker = context.blockModelBaker();
      ResolvedModel resolved = baker.getModel(model);
      TextureSlots slots = resolved.getTopTextureSlots();
      List<BakedQuad> quads = resolved.bakeTopGeometry(slots, baker, BlockModelRotation.X0_Y0).getAll();
      BakedItem base = new BakedItem(quads, ModelRenderProperties.fromResolvedModel(baker, resolved, slots), ElementModelData.itemRenderType(resolved));
      return new Baked(base, index, material.orElse(null), offsetTransform(offset));
    }
  }

  /** Baked item model, composites the per-material sprite at render time */
  public static class Baked implements ItemModel {
    private final BakedItem base;
    private final int index;
    @Nullable
    private final MaterialVariantId material;
    private final Transformation transform;
    private final Map<MaterialVariantId, BakedItem> cache = new ConcurrentHashMap<>();

    public Baked(BakedItem base, int index, @Nullable MaterialVariantId material, Transformation transform) {
      this.base = base;
      this.index = index;
      this.material = material;
      this.transform = transform;
    }

    /** Rebakes the item-layer quads for the given material */
    private BakedItem bakeDynamic(MaterialVariantId material) {
      // the base model's particle is the greyscale layer texture we recolor per material
      TextureAtlasSprite baseSprite = base.particle();
      Material texture = new Material(baseSprite.atlasLocation(), baseSprite.contents().name());
      TintedSprite sprite = getMaterialSprite(Material::sprite, texture, material);
      // PORT 1.21.4: tint index -1 - material color is baked into the quads rather than resolved via ItemColors
      List<BakedQuad> quads = MantleItemLayerModel.getQuadsForSprite(sprite.color(), -1, sprite.sprite(), transform, sprite.emissivity());
      // 1.21.4 built a composite model without block light, keep that
      return new BakedItem(quads, ItemModelUtil.properties(false, sprite.sprite(), base.properties().transforms()), ItemModelUtil.defaultRenderType());
    }

    /** Gets the cached baked model for the given material */
    private BakedItem getCachedModel(MaterialVariantId material) {
      if (IMaterial.UNKNOWN_ID.equals(material)) {
        return base;
      }
      return cache.computeIfAbsent(material, this::bakeDynamic);
    }

    @Override
    public void update(ItemStackRenderState renderState, ItemStack stack, ItemModelResolver resolver, ItemDisplayContext displayContext, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
      MaterialVariantId variant = material != null ? material : IMaterialItem.getMaterialFromStack(stack);
      ItemModelUtil.renderModel(renderState, stack, displayContext, getCachedModel(variant));
    }
  }
}
