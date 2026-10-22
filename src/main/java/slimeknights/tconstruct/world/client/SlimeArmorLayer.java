package slimeknights.tconstruct.world.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.world.level.block.SkullBlock;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders a helmet worn by an armored slime, a reimplementation of parts of {@code HumanoidArmorLayer}.
 *
 * <p>PORT 1.21.4: this is a near-total rework. {@link RenderLayer} is now generic on the entity <i>render state</i>
 * (not the entity), its {@code render} takes a 6-arg {@code (pose, buffers, light, state, limbSwing, limbSwingAmount)}
 * signature, {@link HumanoidModel} must be typed to a {@link HumanoidRenderState} (no longer the entity), and the old
 * armor pipeline it leaned on (per-{@code ArmorMaterial.Layer} textures, {@code ClientHooks.getArmorModel}, the
 * {@code CustomHeadLayer}/{@code ItemInHandRenderer} head path) was removed in favor of {@code EquipmentLayerRenderer}
 * driven by the {@code Equippable} component's {@code EquipmentClientInfo} assets. The layer is kept structurally present
 * (correct generics, skull-model set and armor model baked as the Phase-4 inputs); the actual helmet/skull/item render
 * is deferred: Phase-4 should drive the armor through {@code EntityRendererProvider.Context#getEquipmentRenderer()} and
 * the skull branch through the new {@link SkullBlockRenderer#renderSkull} using the worn-head fields now carried on the
 * {@link SlimeRenderState} ({@code wornHeadType}/{@code wornHeadProfile}/{@code headItem}).
 */
public class SlimeArmorLayer<M extends EntityModel<? super SlimeRenderState>> extends RenderLayer<SlimeRenderState, M> {
  @SuppressWarnings("unused") // PORT 1.21.4: Phase-4 input - armor model for the EquipmentLayerRenderer re-seat
  private final HumanoidModel<HumanoidRenderState> armorModel;
  public final Map<SkullBlock.Type, SkullModelBase> skullModels;
  @SuppressWarnings("unused") // PORT 1.21.4: Phase-4 input - lava slime squish offset for the worn helmet
  private final boolean lavaSlime;

  public SlimeArmorLayer(RenderLayerParent<SlimeRenderState, M> renderer, HumanoidModel<HumanoidRenderState> armorModel, EntityModelSet modelSet, boolean lavaSlime) {
    super(renderer);
    this.armorModel = armorModel;
    this.skullModels = buildSkullModels(modelSet);
    this.lavaSlime = lavaSlime;
  }

  /** PORT 1.21.4: {@code SkullBlockRenderer.createSkullRenderers(modelSet)} was removed; build the per-type map via the new {@link SkullBlockRenderer#createModel}. */
  private static Map<SkullBlock.Type, SkullModelBase> buildSkullModels(EntityModelSet modelSet) {
    Map<SkullBlock.Type, SkullModelBase> map = new HashMap<>();
    for (SkullBlock.Types type : SkullBlock.Types.values()) {
      SkullModelBase model = SkullBlockRenderer.createModel(modelSet, type);
      if (model != null) {
        map.put(type, model);
      }
    }
    return map;
  }

  @Override
  public void render(PoseStack matrices, MultiBufferSource buffer, int packedLight, SlimeRenderState state, float limbSwing, float limbSwingAmount) {
    // PORT 1.21.4: see class breadcrumb - the helmet/skull/item render path was removed and needs re-seating onto
    // EquipmentLayerRenderer + the worn-head render-state fields in Phase-4 (uitest). No-op for now (compiles clean).
  }
}
