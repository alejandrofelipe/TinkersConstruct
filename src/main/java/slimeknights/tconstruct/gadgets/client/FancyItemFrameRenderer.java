package slimeknights.tconstruct.gadgets.client;

import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.event.ModelEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderItemInFrameEvent;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import net.neoforged.neoforge.common.NeoForge;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.gadgets.client.FancyItemFrameRenderer.FancyItemFrameRenderState;
import slimeknights.tconstruct.gadgets.entity.FancyItemFrameEntity;
import slimeknights.tconstruct.gadgets.entity.FrameType;

import java.util.EnumMap;
import java.util.Map;

public class FancyItemFrameRenderer<T extends FancyItemFrameEntity> extends ItemFrameRenderer<T> {
  // 1.21.5+: ModelResourceLocation and ModelManager#getModel are gone; standalone models use typed keys. In 1.21.8 a key only
  // carries a ModelDebugName, so the model id is passed separately to SimpleUnbakedStandaloneModel in registerModels
  public static final Map<FrameType, StandaloneModelKey<BlockStateModel>> LOCATIONS_MODEL = new EnumMap<>(FrameType.class);
  public static final Map<FrameType, StandaloneModelKey<BlockStateModel>> LOCATIONS_MODEL_MAP = new EnumMap<>(FrameType.class);
  static {
    for (FrameType type : FrameType.values()) {
      LOCATIONS_MODEL.put(type, key(modelId(type, false)));
      LOCATIONS_MODEL_MAP.put(type, key(modelId(type, true)));
    }
  }

  /** Gets the block model location for the given frame type */
  private static ResourceLocation modelId(FrameType type, boolean map) {
    String name = type == FrameType.REVERSED_GOLD ? FrameType.GOLD.getSerializedName() : type.getSerializedName();
    return TConstruct.getResource("block/frame/" + name + (map ? "_map" : ""));
  }

  private static StandaloneModelKey<BlockStateModel> key(ResourceLocation id) {
    return new StandaloneModelKey<>(id::toString);
  }

  /** Registers all frame models as block state models, frames are block models */
  public static void registerModels(ModelEvent.RegisterStandalone event) {
    for (FrameType type : FrameType.values()) {
      event.register(LOCATIONS_MODEL.get(type), SimpleUnbakedStandaloneModel.blockStateModel(modelId(type, false)));
      event.register(LOCATIONS_MODEL_MAP.get(type), SimpleUnbakedStandaloneModel.blockStateModel(modelId(type, true)));
    }
  }

  private final MapRenderer mapRenderer;
  public FancyItemFrameRenderer(EntityRendererProvider.Context context) {
    super(context);
    this.mapRenderer = context.getMapRenderer();
  }

  @Override
  protected int getBlockLightLevel(T frame, BlockPos pPos) {
    int baseLight = super.getBlockLightLevel(frame, pPos);
    return frame.getFrameType() == FrameType.MANYULLYN ? Math.max(7, baseLight) : baseLight;
  }

  @Override
  public FancyItemFrameRenderState createRenderState() {
    return new FancyItemFrameRenderState();
  }

  @Override
  public void extractRenderState(T frame, ItemFrameRenderState state, float partialTicks) {
    // populates direction, item (FIXED), rotation, isGlowFrame, mapId, mapRenderState + base
    super.extractRenderState(frame, state, partialTicks);
    ((FancyItemFrameRenderState) state).frameType = frame.getFrameType();
  }

  @SuppressWarnings({"UnstableApiUsage", "deprecation"})  // matching vanilla behavior, so calling the vanilla hooks
  @Override
  public void submit(ItemFrameRenderState state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera) {
    FrameType frameType = ((FancyItemFrameRenderState) state).frameType;

    // base entity rendering logic (nameplate, fires RenderNameTagEvent); calling super would draw the vanilla item frame we are replacing
    this.submitNameTag(state, matrices, collector, camera);
    int packedLight = state.lightCoords;

    // orient the renderer
    matrices.pushPose();
    Direction facing = state.direction;
    net.minecraft.world.phys.Vec3 offset = this.getRenderOffset(state);
    matrices.translate(facing.getStepX() * 0.46875D - offset.x(), facing.getStepY() * 0.46875D - offset.y(), facing.getStepZ() * 0.46875D - offset.z());
    float xRot, yRot;
    if (facing.getAxis().isHorizontal()) {
      xRot = 0.0F;
      yRot = 180.0F - facing.toYRot();
    } else {
      xRot = (float) (-90 * facing.getAxisDirection().getStep());
      yRot = 180.0F;
    }
    matrices.mulPose(Axis.XP.rotationDegrees(xRot));
    matrices.mulPose(Axis.YP.rotationDegrees(yRot));

    // render the frame
    boolean isMap = state.mapId != null;
    // clear does not render the frame if filled
    boolean frameVisible = !state.isInvisible && (frameType != FrameType.CLEAR || state.item.isEmpty());
    if (frameVisible) {
      matrices.pushPose();
      matrices.translate(-0.5D, -0.5D, -0.5D);
      BlockStateModel model = Minecraft.getInstance().getModelManager().getStandaloneModel(isMap ? LOCATIONS_MODEL_MAP.get(frameType) : LOCATIONS_MODEL.get(frameType));
      if (model != null) {
        collector.submitBlockModel(matrices, Sheets.cutoutBlockSheet(), model, 1.0F, 1.0F, 1.0F, packedLight, OverlayTexture.NO_OVERLAY, state.outlineColor);
      }
      matrices.popPose();
    }

    // render the item
    if (!state.item.isEmpty()) {
      // if no frame, offset the item farther back
      matrices.translate(0.0D, 0.0D, 0.4375D);

      int frameRotation = state.rotation;
      // for diamond, render the timer as a partial rotation
      if (frameType.hasMoreRotations()) {
        int rotation = isMap ? (frameRotation + 2) % 4 * 4 : frameRotation;
        matrices.mulPose(Axis.ZP.rotationDegrees(rotation * 360f / 16f));
      } else {
        int rotation = isMap ? (frameRotation + 2) % 4 * 2 : frameRotation;
        matrices.mulPose(Axis.ZP.rotationDegrees(rotation * 360f / 8f));
      }
      if (!NeoForge.EVENT_BUS.post(new RenderItemInFrameEvent(state, this, matrices, collector)).isCanceled()) {
        if (isMap) {
          matrices.scale(0.0078125F, 0.0078125F, 0.0078125F);
          matrices.translate(-64.0D, -64.0D, -1.0D);
          int light = frameType == FrameType.MANYULLYN ? 0x00F000F0 : packedLight;
          this.mapRenderer.render(state.mapRenderState, matrices, collector, true, light);
        } else {
          float scale = frameType == FrameType.CLEAR ? 0.75f : 0.5f;
          matrices.scale(scale, scale, scale);
          int light = frameType == FrameType.MANYULLYN ? 0x00F000F0 : packedLight;
          state.item.submit(matrices, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        }
      }
    }

    matrices.popPose();
  }

  /** Render state carrying the fancy frame variant */
  public static class FancyItemFrameRenderState extends ItemFrameRenderState {
    public FrameType frameType = FrameType.GOLD;
  }
}
