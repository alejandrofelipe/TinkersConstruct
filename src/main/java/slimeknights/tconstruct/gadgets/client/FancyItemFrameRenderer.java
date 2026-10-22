package slimeknights.tconstruct.gadgets.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.event.RenderItemInFrameEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.NeoForge;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.gadgets.client.FancyItemFrameRenderer.FancyItemFrameRenderState;
import slimeknights.tconstruct.gadgets.entity.FancyItemFrameEntity;
import slimeknights.tconstruct.gadgets.entity.FrameType;

import java.util.EnumMap;
import java.util.Map;

public class FancyItemFrameRenderer<T extends FancyItemFrameEntity> extends ItemFrameRenderer<T> {
  public static final Map<FrameType, ModelResourceLocation> LOCATIONS_MODEL = new EnumMap<>(FrameType.class);
  public static final Map<FrameType, ModelResourceLocation> LOCATIONS_MODEL_MAP = new EnumMap<>(FrameType.class);
  static {
    for (FrameType type : FrameType.values()) {
      String name = type == FrameType.REVERSED_GOLD ? FrameType.GOLD.getSerializedName() : type.getSerializedName();
      LOCATIONS_MODEL.put(type, new ModelResourceLocation(TConstruct.getResource("block/frame/" + name), "standalone"));
      LOCATIONS_MODEL_MAP.put(type, new ModelResourceLocation(TConstruct.getResource("block/frame/" + name + "_map"), "standalone"));
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
  public void render(ItemFrameRenderState state, PoseStack matrices, MultiBufferSource bufferIn, int packedLight) {
    FrameType frameType = ((FancyItemFrameRenderState) state).frameType;

    // base entity rendering logic (nameplate); calling super would draw the vanilla item frame we are replacing
    if (state.nameTag != null) {
      RenderNameTagEvent.DoRender renderNameplate = new RenderNameTagEvent.DoRender(state, state.nameTag, this, matrices, bufferIn, packedLight, state.partialTick);
      if (!NeoForge.EVENT_BUS.post(renderNameplate).isCanceled()) {
        this.renderNameTag(state, state.nameTag, matrices, bufferIn, packedLight);
      }
    }

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
      this.blockRenderer.getModelRenderer().renderModel(
        matrices.last(), bufferIn.getBuffer(Sheets.cutoutBlockSheet()), null,
        this.blockRenderer.getBlockModelShaper().getModelManager().getModel(isMap ? LOCATIONS_MODEL_MAP.get(frameType) : LOCATIONS_MODEL.get(frameType)),
        1.0F, 1.0F, 1.0F, packedLight, OverlayTexture.NO_OVERLAY);
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
      if (!NeoForge.EVENT_BUS.post(new RenderItemInFrameEvent(state, this, matrices, bufferIn, packedLight)).isCanceled()) {
        if (isMap) {
          matrices.scale(0.0078125F, 0.0078125F, 0.0078125F);
          matrices.translate(-64.0D, -64.0D, -1.0D);
          int light = frameType == FrameType.MANYULLYN ? 0x00F000F0 : packedLight;
          this.mapRenderer.render(state.mapRenderState, matrices, bufferIn, true, light);
        } else {
          float scale = frameType == FrameType.CLEAR ? 0.75f : 0.5f;
          matrices.scale(scale, scale, scale);
          int light = frameType == FrameType.MANYULLYN ? 0x00F000F0 : packedLight;
          state.item.render(matrices, bufferIn, light, OverlayTexture.NO_OVERLAY);
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
