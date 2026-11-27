package slimeknights.tconstruct.world.data;

import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.TradeCost;
import net.minecraft.world.item.trading.VillagerTrade;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.json.ConfigEnabledCondition;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.world.logic.AncientToolTradeFunction;

import java.util.List;
import java.util.Optional;

/** Datagen for villager trades (26.1: trades are datapack registry entries) */
public class VillagerTradeProvider implements RegistrySetBuilder.RegistryBootstrap<VillagerTrade> {
  /** Wandering trader trade selling a random ancient tool, added to {@code #minecraft:wandering_trader/uncommon} */
  public static final ResourceKey<VillagerTrade> ANCIENT_TOOL = ResourceKey.create(Registries.VILLAGER_TRADE, TConstruct.getResource("ancient_tool"));

  private VillagerTradeProvider() {}

  /** Registers this provider with the registry set builder */
  public static void register(RegistrySetBuilder builder) {
    builder.add(Registries.VILLAGER_TRADE, new VillagerTradeProvider());
  }

  @Override
  public void run(BootstrapContext<VillagerTrade> context) {
    // the item modifier replaces the given item with a random tool from the trader tools tag and adds the tier-based cost to
    // the base cost of 0 emeralds; same offer as the 1.21.11 listing (1 use, 15 xp, price multiplier 1)
    context.register(ANCIENT_TOOL, new VillagerTrade(
      new TradeCost(Items.EMERALD, 0), new ItemStackTemplate(TinkerTools.warPick.asItem()), 1, 15, 1f,
      Optional.of(ConfigEnabledCondition.WANDERER_ANCIENT_TOOL), List.of(AncientToolTradeFunction.builder().build())));
  }
}
