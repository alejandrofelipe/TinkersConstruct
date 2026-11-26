package slimeknights.tconstruct.tools.client;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.object.skull.PiglinHeadModel;
import net.minecraft.client.model.object.skull.SkullModel;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.item.ItemStack;
import slimeknights.mantle.data.listener.ISafeManagerReloadListener;
import slimeknights.tconstruct.library.client.armor.ArmorModelManager.ArmorModel;
import slimeknights.tconstruct.library.client.armor.MultilayerArmorModel;
import slimeknights.tconstruct.library.client.armor.TinkerArmorLayer.Context;
import slimeknights.tconstruct.library.client.armor.texture.ArmorTextureSupplier.TextureType;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfo;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfoLoader;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.nbt.MaterialIdNBT;
import slimeknights.tconstruct.library.utils.SimpleCache;
import slimeknights.tconstruct.world.client.BlockModelSkullRenderer;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

/** Model to render a slimeskull helmet with both the helmet and skull */
public class SlimeskullArmorModel extends MultilayerArmorModel {
  /** Singleton model instance, all data is passed in via setters */
  public static final SlimeskullArmorModel INSTANCE = new SlimeskullArmorModel();
  /** Cache of colors for materials */
  private static final SimpleCache<MaterialVariantId,Integer> MATERIAL_COLOR_CACHE = new SimpleCache<>(mat ->
    MaterialRenderInfoLoader.INSTANCE.getRenderInfo(mat)
            .map(MaterialRenderInfo::vertexColor)
            .orElse(-1));
  /** Listener to clear caches */
  public static final ISafeManagerReloadListener RELOAD_LISTENER = manager -> {
    HEAD_MODELS = null;
    MATERIAL_COLOR_CACHE.clear();
  };

  private SlimeskullArmorModel() {}

  /** Composites the slimeskull armor layers plus the embedded skull model of the first material. */
  @Override
  public <S extends HumanoidRenderState> void submit(ArmorModel model, ItemStack stack, EquipmentSlot slot, HumanoidModel<S> armorModel, Context<S> context) {
    S state = context.state();
    PoseStack matrices = context.matrices();
    int order = FIRST_ORDER;
    if (model != ArmorModel.EMPTY) {
      matrices.pushPose();
      // TODO: this offset messes with the rotation of the skull slightly, though it is barely noticable
      matrices.translate(0.0D, state.isBaby ? -0.015D : -0.02D, 0.0D);
      matrices.scale(1.01f, 1.1f, 1.01f);
      order = submitLayers(model, stack, TextureType.fromSlot(slot), armorModel, context, order);
      matrices.popPose();
    }
    MaterialId materialId = MaterialIdNBT.from(stack).getMaterial(0).getId();
    if (!materialId.equals(IMaterial.UNKNOWN_ID)) {
      SkullModelBase headModel = getHeadModel(materialId);
      Identifier headTexture = HEAD_TEXTURES.get(materialId);
      if (headModel != null && headTexture != null) {
        // determine the color to tint the head, fallback to no tint if missing
        MaterialVariantId material = MaterialIdNBT.from(stack).getMaterial(1);
        int headColor = IMaterial.UNKNOWN_ID.equals(material) ? -1 : MATERIAL_COLOR_CACHE.apply(material);
        matrices.pushPose();
        // 1.21.4: follow the posed head of the entity like vanilla's custom head layer, which covers crouching and baby models
        HumanoidModel<S> parent = context.parent();
        parent.root().translateAndRotate(matrices);
        parent.head.translateAndRotate(matrices);
        matrices.scale(1.115f, 1.115f, 1.115f);
        SkullModelBase.State skullState = new SkullModelBase.State();
        // walk animation, of the vehicle when riding
        skullState.animationPos = state.wornHeadAnimationPos;
        SubmitNodeCollector collector = context.collector();
        int packedLight = context.packedLight();
        collector.order(order++).submitModel(headModel, skullState, matrices, RenderTypes.entityCutoutNoCullZOffset(headTexture), packedLight, OverlayTexture.NO_OVERLAY, headColor, null, state.outlineColor, null);
        if (stack.hasFoil()) {
          collector.order(order).submitModel(headModel, skullState, matrices, RenderTypes.armorEntityGlint(), packedLight, OverlayTexture.NO_OVERLAY, headColor, null, state.outlineColor, null);
        }
        matrices.popPose();
      }
    }
  }


  /* Head models */

  /** Map of all skull factories */
  private static final Map<MaterialId,Function<EntityModelSet,? extends SkullModelBase>> HEAD_MODEL_FACTORIES = new HashMap<>();
  /** Map of texture for the skull textures */
  private static final Map<MaterialId,Identifier> HEAD_TEXTURES = new HashMap<>();

  /** Registers a head model and texture, using the default skull model */
  public static void registerHeadModel(MaterialId materialId, ModelLayerLocation headModel, Identifier texture) {
    registerHeadModel(materialId, modelSet -> new SkullModel(modelSet.bakeLayer(headModel)), texture);
  }

  /** Registers a head model and texture, using the piglin skull model */
  public static void registerPiglinHeadModel(MaterialId materialId, ModelLayerLocation headModel, Identifier texture) {
    registerHeadModel(materialId, modelSet -> new PiglinHeadModel(modelSet.bakeLayer(headModel)), texture);
  }

  /** Registers a skull model using an item as the model */
  public static void registerBlockModel(MaterialId materialId, ItemStack stack) {
    registerHeadModel(materialId, modelSet -> new BlockModelSkullRenderer(Minecraft.getInstance().getItemRenderer(), stack), TextureAtlas.LOCATION_BLOCKS);
  }

  /** Registers a head model and texture, using a custom skull model */
  public static void registerHeadModel(MaterialId materialId, Function<EntityModelSet,? extends SkullModelBase> headFunction, Identifier texture) {
    if (HEAD_MODEL_FACTORIES.containsKey(materialId)) {
      throw new IllegalArgumentException("Duplicate head model " + materialId);
    }
    HEAD_MODEL_FACTORIES.put(materialId, headFunction);
    HEAD_TEXTURES.put(materialId, texture);
  }

  /** Map of baked head models, if null it is not currently computed */
  private static Map<MaterialId, SkullModelBase> HEAD_MODELS;

  /** Gets the head model for the given material */
  @Nullable
  private static SkullModelBase getHeadModel(MaterialId materialId) {
    if (HEAD_MODELS == null) {
      // vanilla rebakes these a lot, so figure we should at least do it every resource reload
      EntityModelSet modelSet = Minecraft.getInstance().getEntityModels();
      ImmutableMap.Builder<MaterialId,SkullModelBase> models = ImmutableMap.builder();
      for (Entry<MaterialId,Function<EntityModelSet,? extends SkullModelBase>> entry : HEAD_MODEL_FACTORIES.entrySet()) {
        models.put(entry.getKey(), entry.getValue().apply(modelSet));
      }
      HEAD_MODELS = models.build();
    }
    return HEAD_MODELS.get(materialId);
  }
}
