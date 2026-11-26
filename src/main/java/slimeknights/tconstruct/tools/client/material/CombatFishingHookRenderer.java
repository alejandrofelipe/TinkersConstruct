package slimeknights.tconstruct.tools.client.material;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
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
  private static final Identifier LOCAL = TConstruct.getResource("fishing_hook/material");
  /** Easier to append material variants without root */
  private static final Identifier BASE = ArmorTextureSupplier.getTexturePath(LOCAL);

  /** Checks if the given texture is valid */
  @Nullable
  private static Identifier tryTexture(String material) {
    Identifier texture = LOCAL.withSuffix(material);
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
        Identifier untinted = info.texture();
        luminosity = info.luminosity();
        if (untinted != null) {
          Identifier texture = tryTexture('_' + untinted.getNamespace() + '_' + untinted.getPath());
          if (texture != null) {
            return new MaterialTexture(texture, -1, luminosity);
          }
        }
        // fallback to tinted
        color = info.vertexColor();
        for (String fallback : info.fallbacks()) {
          Identifier texture = tryTexture('_' + fallback);
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
  public void submit(CombatFishingHookRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
    // setup rendering
    poseStack.pushPose();
    poseStack.pushPose();
    poseStack.scale(0.5F, 0.5F, 0.5F);
    poseStack.mulPose(camera.orientation);
    poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));

    // render bobber using the material texture
    MaterialTexture texture = state.texture;
    int bobberLight = texture.applyLuminosity(state.lightCoords);
    collector.submitCustomGeometry(poseStack, texture.texture, (pose, consumer) -> {
      texture.vertex(consumer, pose, bobberLight, 0f, 0, 0, 1);
      texture.vertex(consumer, pose, bobberLight, 1f, 0, 1, 1);
      texture.vertex(consumer, pose, bobberLight, 1f, 1, 1, 0);
      texture.vertex(consumer, pose, bobberLight, 0f, 1, 0, 0);
    });
    poseStack.popPose();

    // render the string from the stored hand-relative origin, like vanilla 1.21.9 as line segments
    float f = (float) state.lineOriginOffset.x;
    float f1 = (float) state.lineOriginOffset.y;
    float f2 = (float) state.lineOriginOffset.z;
    // 1.21.11: line width moved from the render type to the vertices, same width as vanilla
    float lineWidth = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
    collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, consumer) -> {
      for (int i = 0; i < 16; i++) {
        float start = i / 16f;
        float end = (i + 1) / 16f;
        stringVertex(f, f1, f2, consumer, pose, start, end, lineWidth);
        stringVertex(f, f1, f2, consumer, pose, end, start, lineWidth);
      }
    });

    poseStack.popPose();
    super.submit(state, poseStack, collector, camera);
  }

  /** Copy of the private {@link FishingHookRenderer} string vertex */
  private static void stringVertex(float x, float y, float z, VertexConsumer consumer, PoseStack.Pose pose, float stringFraction, float nextStringFraction, float lineWidth) {
    float startX = x * stringFraction;
    float startY = y * (stringFraction * stringFraction + stringFraction) * 0.5F + 0.25F;
    float startZ = z * stringFraction;
    float normalX = x * nextStringFraction - startX;
    float normalY = y * (nextStringFraction * nextStringFraction + nextStringFraction) * 0.5F + 0.25F - startY;
    float normalZ = z * nextStringFraction - startZ;
    float length = Mth.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
    normalX /= length;
    normalY /= length;
    normalZ /= length;
    consumer.addVertex(pose, startX, startY, startZ).setColor(0xFF000000).setNormal(pose, normalX, normalY, normalZ).setLineWidth(lineWidth);
  }

  /** Render state carrying the fishing line origin and bobber texture */
  public static class CombatFishingHookRenderState extends EntityRenderState {
    public Vec3 lineOriginOffset = Vec3.ZERO;
    public MaterialTexture texture = MaterialTexture.EMPTY;
  }

  private record MaterialTexture(RenderType texture, int luminosity, int alpha, int red, int green, int blue) {
    public static final MaterialTexture EMPTY = new MaterialTexture(BASE, -1, 0);

    public MaterialTexture(Identifier texture, int color, int luminosity) {
      this(RenderTypes.entityCutout(texture), luminosity,
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
