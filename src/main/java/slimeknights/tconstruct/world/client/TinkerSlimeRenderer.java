package slimeknights.tconstruct.world.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Slime;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.world.entity.ArmoredSlimeEntity;

public class TinkerSlimeRenderer extends SlimeRenderer {
  public static final Factory SKY_SLIME_FACTORY = new Factory(TConstruct.getResource("textures/entity/sky_slime.png"), TConstruct.getResource("textures/entity/steel_slime.png"));
  public static final Factory ENDER_SLIME_FACTORY = new Factory(TConstruct.getResource("textures/entity/ender_slime.png"), TConstruct.getResource("textures/entity/knightmetal_slime.png"));

  private final ResourceLocation slime, metal;
  public TinkerSlimeRenderer(EntityRendererProvider.Context context, ResourceLocation slime, ResourceLocation metal) {
    super(context);
    this.slime = slime;
    this.metal = metal;
    addLayer(new SlimeArmorLayer<>(this, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), context.getModelSet(), false));
  }

  // PORT 1.21.4: getTextureLocation now receives the extracted render state, not the entity. The metal flag is captured
  // into ArmoredSlimeRenderState during extractRenderState (the entity is unavailable at texture-selection time).
  @Override
  public SlimeRenderState createRenderState() {
    return new ArmoredSlimeRenderState();
  }

  @Override
  public void extractRenderState(Slime entity, SlimeRenderState state, float partialTick) {
    super.extractRenderState(entity, state, partialTick);
    if (state instanceof ArmoredSlimeRenderState armored && entity instanceof ArmoredSlimeEntity armoredEntity) {
      armored.isMetal = armoredEntity.isMetal();
    }
  }

  @Override
  public ResourceLocation getTextureLocation(SlimeRenderState state) {
    if (slime != metal && state instanceof ArmoredSlimeRenderState armored && armored.isMetal) {
      return metal;
    }
    return slime;
  }

  private record Factory(ResourceLocation slime, ResourceLocation metal) implements EntityRendererProvider<Slime> {
    public Factory(ResourceLocation texture) {
      this(texture, texture);
    }

    @Override
    public EntityRenderer<Slime, ?> create(Context context) {
      return new TinkerSlimeRenderer(context, slime, metal);
    }
  }
}
