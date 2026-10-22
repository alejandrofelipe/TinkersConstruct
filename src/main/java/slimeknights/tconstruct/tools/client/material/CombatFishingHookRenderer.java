package slimeknights.tconstruct.tools.client.material;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.armor.texture.ArmorTextureSupplier;
import slimeknights.tconstruct.library.client.armor.texture.TintedArmorTexture;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfo;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfoLoader;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.utils.SimpleCache;
import slimeknights.tconstruct.tools.client.material.CombatFishingHookRenderer.CombatFishingHookRenderState;
import slimeknights.tconstruct.tools.entity.CombatFishingHook;

import javax.annotation.Nullable;
import java.util.Optional;

/** Renderer for {@link CombatFishingHook}. Mostly a recreation of {@link net.minecraft.client.renderer.entity.FishingHookRenderer} */
public class CombatFishingHookRenderer extends EntityRenderer<CombatFishingHook, CombatFishingHookRenderState> {
  /** Texture under the local folder */
  private static final ResourceLocation LOCAL = TConstruct.getResource("fishing_hook/material");
  /** Easier to append material variants without root */
  private static final ResourceLocation BASE = ArmorTextureSupplier.getTexturePath(LOCAL);

  /** Checks if the given texture is valid */
  @Nullable
  private static ResourceLocation tryTexture(String material) {
    ResourceLocation texture = LOCAL.withSuffix(material);
    if (ArmorTextureSupplier.TEXTURE_VALIDATOR.test(texture)) {
      return ArmorTextureSupplier.getTexturePath(texture);
    }
    return null;
  }

  /** Cache of texture and color for each material. */
  private static final SimpleCache<MaterialVariantId,MaterialTexture> TEXTURE_CACHE = new SimpleCache<>(material -> {
    if (!IMaterial.UNKNOWN_ID.equals(material)) {
      Optional<MaterialRenderInfo> infoOptional = MaterialRenderInfoLoader.INSTANCE.getRenderInfo(material);
      int color = -1;
      int luminosity = 0;
      if (infoOptional.isPresent()) {
        MaterialRenderInfo info = infoOptional.get();
        // first try untinted
        ResourceLocation untinted = info.texture();
        luminosity = info.luminosity();
        if (untinted != null) {
          ResourceLocation texture = tryTexture('_' + untinted.getNamespace() + '_' + untinted.getPath());
          if (texture != null) {
            return new MaterialTexture(texture, -1, luminosity);
          }
        }
        // fallback to tinted
        color = info.vertexColor();
        for (String fallback : info.fallbacks()) {
          ResourceLocation texture = tryTexture('_' + fallback);
          if (texture != null) {
            return new MaterialTexture(texture, color, luminosity);
          }
        }
      }
      // tint base texture
      return new MaterialTexture(ArmorTextureSupplier.getTexturePath(LOCAL), color, luminosity);
    }
    return MaterialTexture.EMPTY;
  });

  public CombatFishingHookRenderer(Context context) {
    super(context);
  }

  /** Clears any cache in the renderer. */
  public static void clearCache() {
    TEXTURE_CACHE.clear();
  }

  @Override
  public boolean shouldRender(CombatFishingHook hook, Frustum frustum, double camX, double camY, double camZ) {
    return super.shouldRender(hook, frustum, camX, camY, camZ) && hook.getPlayerOwner() != null;
  }

  /** Computes the player hand position the fishing line originates from, matching vanilla {@link FishingHookRenderer} */
  private Vec3 getPlayerHandPos(Player player, float handAngle, float partialTick) {
    int side = FishingHookRenderer.getHoldingArm(player) == HumanoidArm.RIGHT ? 1 : -1;
    if (this.entityRenderDispatcher.options.getCameraType().isFirstPerson() && player == Minecraft.getInstance().player) {
      double fov = 960.0 / this.entityRenderDispatcher.options.fov().get().intValue();
      Vec3 point = this.entityRenderDispatcher
        .camera
        .getNearPlane()
        .getPointOnPlane((float) side * 0.525F, -0.1F)
        .scale(fov)
        .yRot(handAngle * 0.5F)
        .xRot(-handAngle * 0.7F);
      return player.getEyePosition(partialTick).add(point);
    } else {
      float rotation = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * (float) (Math.PI / 180.0);
      double rotationSin = Mth.sin(rotation);
      double rotationCos = Mth.cos(rotation);
      float scale = player.getScale();
      double scaledSide = (double) side * 0.35 * (double) scale;
      double forward = 0.8 * (double) scale;
      float crouchOffset = player.isCrouching() ? -0.1875F : 0.0F;
      return player.getEyePosition(partialTick).add(-rotationCos * scaledSide - rotationSin * forward, (double) crouchOffset - 0.45 * (double) scale, -rotationSin * scaledSide + rotationCos * forward);
    }
  }

  @Override
  public CombatFishingHookRenderState createRenderState() {
    return new CombatFishingHookRenderState();
  }

  @Override
  public void extractRenderState(CombatFishingHook hook, CombatFishingHookRenderState state, float partialTicks) {
    super.extractRenderState(hook, state, partialTicks);
    state.texture = TEXTURE_CACHE.apply(hook.getMaterial());
    Player player = hook.getPlayerOwner();
    if (player == null) {
      state.lineOriginOffset = Vec3.ZERO;
    } else {
      float attack = player.getAttackAnim(partialTicks);
      float attackSin = Mth.sin(Mth.sqrt(attack) * (float) Math.PI);
      Vec3 handPos = this.getPlayerHandPos(player, attackSin, partialTicks);
      Vec3 hookPos = hook.getPosition(partialTicks).add(0.0, 0.25, 0.0);
      state.lineOriginOffset = handPos.subtract(hookPos);
    }
  }

  @Override
  public void render(CombatFishingHookRenderState state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
    // setup rendering
    poseStack.pushPose();
    poseStack.pushPose();
    poseStack.scale(0.5F, 0.5F, 0.5F);
    poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
    poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));

    // render bobber using the material texture
    MaterialTexture texture = state.texture;
    VertexConsumer consumer = buffer.getBuffer(texture.texture);
    int bobberLight = texture.applyLuminosity(packedLight);
    PoseStack.Pose lastPose = poseStack.last();
    texture.vertex(consumer, lastPose, bobberLight, 0f, 0, 0, 1);
    texture.vertex(consumer, lastPose, bobberLight, 1f, 0, 1, 1);
    texture.vertex(consumer, lastPose, bobberLight, 1f, 1, 1, 0);
    texture.vertex(consumer, lastPose, bobberLight, 0f, 1, 0, 0);
    poseStack.popPose();

    // render the string from the stored hand-relative origin
    float f = (float) state.lineOriginOffset.x;
    float f1 = (float) state.lineOriginOffset.y;
    float f2 = (float) state.lineOriginOffset.z;
    consumer = buffer.getBuffer(RenderType.lineStrip());
    lastPose = poseStack.last();
    for (int i = 0; i <= 16; i++) {
      FishingHookRenderer.stringVertex(f, f1, f2, consumer, lastPose, i / 16f, (i + 1) / 16f);
    }

    poseStack.popPose();
    super.render(state, poseStack, buffer, packedLight);
  }

  /** Render state carrying the fishing line origin and bobber texture */
  public static class CombatFishingHookRenderState extends EntityRenderState {
    public Vec3 lineOriginOffset = Vec3.ZERO;
    public MaterialTexture texture = MaterialTexture.EMPTY;
  }

  private record MaterialTexture(RenderType texture, int luminosity, int alpha, int red, int green, int blue) {
    public static final MaterialTexture EMPTY = new MaterialTexture(BASE, -1, 0);

    public MaterialTexture(ResourceLocation texture, int color, int luminosity) {
      this(RenderType.entityCutout(texture), luminosity,
        color >> 24 & 255,
        color >> 16 & 255,
        color >> 8 & 255,
        color & 255
      );
    }

    /** Applies luminosity to the given lightmap color */
    public int applyLuminosity(int packedLight) {
      if (luminosity > 0) {
        return TintedArmorTexture.applyLuminosity(packedLight, luminosity);
      }
      return packedLight;
    }

    /** Draws a vertex using this texture. */
    public void vertex(VertexConsumer consumer, PoseStack.Pose pose, int lightmap, float pX, int pY, int pU, int pV) {
      consumer.addVertex(pose, pX - 0.5f, pY - 0.5f, 0f)
        .setColor(red, green, blue, alpha)
        .setUv(pU, pV)
        .setOverlay(OverlayTexture.NO_OVERLAY)
        .setLight(lightmap)
        .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
  }
}
