package slimeknights.tconstruct.gadgets;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent.RegisterStandalone;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.ClientEventBase;
import slimeknights.tconstruct.gadgets.client.FancyItemFrameRenderer;
import slimeknights.tconstruct.gadgets.entity.FrameType;
import slimeknights.tconstruct.gadgets.entity.shuriken.ShurikenEntityBase;
import slimeknights.tconstruct.tools.client.material.ThrownShurikenRenderer;

@SuppressWarnings("unused")
@EventBusSubscriber(modid=TConstruct.MOD_ID, value=Dist.CLIENT)
public class GadgetClientEvents extends ClientEventBase {
  @SubscribeEvent
  static void registerModels(RegisterStandalone event) {
    // 1.21.5: RegisterAdditional -> RegisterStandalone with typed keys; frames are block models rendered as a block state model
    for (FrameType type : FrameType.values()) {
      event.register(FancyItemFrameRenderer.LOCATIONS_MODEL.get(type), SimpleUnbakedStandaloneModel.blockStateModel(FancyItemFrameRenderer.modelId(type, false)));
      event.register(FancyItemFrameRenderer.LOCATIONS_MODEL_MAP.get(type), SimpleUnbakedStandaloneModel.blockStateModel(FancyItemFrameRenderer.modelId(type, true)));
    }
  }

  @SubscribeEvent
  static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
    event.registerEntityRenderer(TinkerGadgets.itemFrameEntity.get(), FancyItemFrameRenderer::new);
    EntityRendererProvider<ThrowableItemProjectile> throwable = ThrownItemRenderer::new;
    event.registerEntityRenderer(TinkerGadgets.glowBallEntity.get(), throwable);
    event.registerEntityRenderer(TinkerGadgets.eflnEntity.get(), throwable);
    EntityRendererProvider<ShurikenEntityBase> shuriken = ctx -> new ThrownShurikenRenderer<>(ctx);
    event.registerEntityRenderer(TinkerGadgets.quartzShurikenEntity.get(), shuriken);
    event.registerEntityRenderer(TinkerGadgets.flintShurikenEntity.get(), shuriken);
  }
}
