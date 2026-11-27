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
import java.util.stream.IntStream;

/** Datagen for villager trades (26.1: trades are datapack registry entries) */
public class VillagerTradeProvider implements RegistrySetBuilder.RegistryBootstrap<VillagerTrade> {
  /**
   * Wandering trader trades selling a random ancient tool, {@code tconstruct:ancient_tool_1} to {@code _6}, all in {@code #minecraft:wandering_trader/uncommon}.
   * Copy {@code k} (index {@code k - 1}) is enabled when the config weight is at least {@code k}: the uncommon set picks 2 of its entries without duplicates
   * and skips a disabled one, so the default weight 6 among the 15 vanilla entries gives the 1.21.11 chance (50 % per trader).
   */
  public static final List<ResourceKey<VillagerTrade>> ANCIENT_TOOL = IntStream.rangeClosed(1, ConfigEnabledCondition.WANDERER_ANCIENT_TOOL_COPIES)
    .mapToObj(copy -> ResourceKey.create(Registries.VILLAGER_TRADE, TConstruct.getResource("ancient_tool_" + copy)))
    .toList();

  private VillagerTradeProvider() {}

  /** Registers this provider with the registry set builder */
  public static void register(RegistrySetBuilder builder) {
    builder.add(Registries.VILLAGER_TRADE, new VillagerTradeProvider());
  }

  @Override
  public void run(BootstrapContext<VillagerTrade> context) {
    // the item modifier replaces the given item with a random tool from the trader tools tag and adds the tier-based cost to
    // the base cost of 0 emeralds; same offer as the 1.21.11 listing (1 use, 15 xp, price multiplier 1)
    for (int i = 0; i < ANCIENT_TOOL.size(); i++) {
      context.register(ANCIENT_TOOL.get(i), new VillagerTrade(
        new TradeCost(Items.EMERALD, 0), new ItemStackTemplate(TinkerTools.warPick.asItem()), 1, 15, 1f,
        Optional.of(ConfigEnabledCondition.WANDERER_ANCIENT_TOOL.get(i)), List.of(AncientToolTradeFunction.builder().build())));
    }
  }
}
