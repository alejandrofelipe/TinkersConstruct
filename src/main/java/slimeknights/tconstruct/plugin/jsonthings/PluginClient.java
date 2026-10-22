package slimeknights.tconstruct.plugin.jsonthings;

import net.minecraft.world.item.Item;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.model.TinkerItemProperties;

/** Handles anything that requires clientside class loading */
public class PluginClient {
  public static void init() {
    // PORT 1.21.4: tool item colors are baked into the quads now (decision #2); no ItemColorHandler needed
    TConstruct.modBus.addListener(PluginClient::clientSetup);
  }

  private static void clientSetup(FMLClientSetupEvent event) {
    event.enqueueWork(() -> {
      for (Item item : FlexItemTypes.TOOL_ITEMS) {
        TinkerItemProperties.registerToolProperties(item);
      }
      for (Item item : FlexItemTypes.CROSSBOW_ITEMS) {
        TinkerItemProperties.registerCrossbowProperties(item);
      }
      for (Item item : FlexItemTypes.ARMOR_ITEMS) {
        TinkerItemProperties.registerBrokenProperty(item);
      }
    });
  }
}
