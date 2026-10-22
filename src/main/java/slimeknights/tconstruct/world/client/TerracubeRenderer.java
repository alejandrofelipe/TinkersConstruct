package slimeknights.tconstruct.world.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.LavaSlimeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Slime;
import slimeknights.tconstruct.TConstruct;

public class TerracubeRenderer extends MobRenderer<Slime, SlimeRenderState, LavaSlimeModel> {
  private static final ResourceLocation TEXTURE = TConstruct.getResource("textures/entity/terracube.png");
  public TerracubeRenderer(EntityRendererProvider.Context context) {
    // PORT 1.21.4: LavaSlimeModel is no longer generic (typed to SlimeRenderState internally); MobRenderer gained the
    // render-state type parameter.
    super(context, new LavaSlimeModel(context.bakeLayer(ModelLayers.MAGMA_CUBE)), 0.25F);
    addLayer(new SlimeArmorLayer<>(this, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), context.getModelSet(), true));
  }

  @Override
  public SlimeRenderState createRenderState() {
    return new SlimeRenderState();
  }

  @Override
  public void extractRenderState(Slime entity, SlimeRenderState state, float partialTick) {
    super.extractRenderState(entity, state, partialTick);
    state.squish = Mth.lerp(partialTick, entity.oSquish, entity.squish);
    state.size = entity.getSize();
  }

  @Override
  public ResourceLocation getTextureLocation(SlimeRenderState state) {
    return TEXTURE;
  }

  // PORT 1.21.4: scale now receives the render state (not the entity + partialTick); squish/size come off the state.
  @Override
  protected void scale(SlimeRenderState state, PoseStack matrices) {
    int size = state.size;
    float squishFactor = state.squish / ((float)size * 0.5F + 1.0F);
    float invertedSquish = 1.0F / (squishFactor + 1.0F);
    matrices.scale(invertedSquish * (float)size, 1.0F / invertedSquish * (float)size, invertedSquish * (float)size);
  }
}
