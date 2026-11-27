package slimeknights.tconstruct.fluids;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterItemModelsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.ClientEventBase;
import slimeknights.tconstruct.library.client.model.FluidContainerModel;

@EventBusSubscriber(modid = TConstruct.MOD_ID, value = Dist.CLIENT)
public class FluidClientEvents extends ClientEventBase {
  // 26.1: ItemBlockRenderTypes is gone, the fluid render layer comes from the fluid model (Mantle registers one per fluid texture), which picks it
  // from the texture alpha: the translucent fluids (honey, slimes, diamond, emerald, glass, soul, amethyst) have translucent textures; molten
  // soulsteel is fully opaque, so it renders solid instead of the identical-looking translucent layer.

  // PORT 1.21.4: RegisterColorHandlersEvent.Item was removed; the potion fluid item's tint should move to a registered
  // ItemTintSource referenced from its item JSON (deferred to the item-tint batch).

  // PORT 1.21.4: FluidContainerModel is an item model now (ItemModel.Unbaked MapCodec on RegisterItemModelsEvent)
  @SubscribeEvent
  static void registerItemModels(RegisterItemModelsEvent event) {
    event.register(TConstruct.getResource("fluid_container"), FluidContainerModel.Unbaked.MAP_CODEC);
  }
}
