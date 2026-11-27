package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.VillagerTradeTags;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.TradeSets;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.world.data.VillagerTradeProvider;

import java.util.Optional;

/**
 * Gametests for the wandering trader ancient tool trade. 26.1 made trades datapack entries: {@code tconstruct:ancient_tool} is a
 * {@code villager_trade} added to {@code #minecraft:wandering_trader/uncommon}, whose item modifier {@code tconstruct:ancient_tool_trade}
 * picks the tool and its cost (replaces the 1.21.11 {@code WandererTradesEvent} listing).
 */
public class TradeGameTests {
  /** The trade entry loads, is in the uncommon wandering trader tag and that tag feeds the uncommon trade set */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 20)
  public static void ancient_tool_trade_loaded(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    Registry<VillagerTrade> trades = level.registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE);
    Optional<Holder.Reference<VillagerTrade>> trade = trades.get(VillagerTradeProvider.ANCIENT_TOOL);
    helper.assertTrue(trade.isPresent(), Component.literal("villager trade " + VillagerTradeProvider.ANCIENT_TOOL.identifier() + " did not load"));
    helper.assertTrue(trade.get().is(VillagerTradeTags.WANDERING_TRADER_UNCOMMON), Component.literal("ancient tool trade is not in #" + VillagerTradeTags.WANDERING_TRADER_UNCOMMON.location()));
    TradeSet uncommon = level.registryAccess().lookupOrThrow(Registries.TRADE_SET).getValueOrThrow(TradeSets.WANDERING_TRADER_UNCOMMON);
    HolderSet<VillagerTrade> setTrades = uncommon.getTrades();
    helper.assertTrue(setTrades.contains(trade.get()), Component.literal("uncommon wandering trader trade set does not offer the ancient tool trade"));
    helper.succeed();
  }

  /**
   * The trade produces an offer like the 1.21.11 listing: a tool from the trader tools tag with materials, a cost of 6 to 32
   * emeralds (the tier formula), 1 use and 15 xp; the cost component is consumed by the trade (not left on the tool).
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 20)
  public static void ancient_tool_trade_offer(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    VillagerTrade trade = level.registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE).getValueOrThrow(VillagerTradeProvider.ANCIENT_TOOL);
    WanderingTrader trader = helper.spawnWithNoFreeWill(EntityType.WANDERING_TRADER, new BlockPos(2, 1, 2));
    // same context as AbstractVillager#addOffersFromTradeSet
    LootContext context = new LootContext.Builder(
      new LootParams.Builder(level)
        .withParameter(LootContextParams.ORIGIN, trader.position())
        .withParameter(LootContextParams.THIS_ENTITY, trader)
        .withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, Unit.INSTANCE)
        .create(LootContextParamSets.VILLAGER_TRADE)
    ).create(Optional.empty());
    for (int i = 0; i < 20; i++) {
      MerchantOffer offer = trade.getOffer(context);
      helper.assertTrue(offer != null, Component.literal("ancient tool trade gave no offer (config enabled by default)"));
      ItemStack result = offer.getResult();
      helper.assertTrue(result.is(TinkerTags.Items.TRADER_TOOLS), Component.literal("offered item is not a trader tool: " + result));
      helper.assertTrue(!result.has(DataComponents.ADDITIONAL_TRADE_COST), Component.literal("additional trade cost left on the tool"));
      helper.assertTrue(!ToolStack.from(result).getMaterials().isEmpty(), Component.literal("offered tool has no materials: " + result));
      ItemStack cost = offer.getBaseCostA();
      helper.assertTrue(cost.is(Items.EMERALD), Component.literal("cost is not emeralds: " + cost));
      helper.assertTrue(cost.getCount() >= 6 && cost.getCount() <= 32, Component.literal("cost out of the 6-32 range: " + cost.getCount()));
      helper.assertTrue(offer.getMaxUses() == 1 && offer.getXp() == 15, Component.literal("expected 1 use and 15 xp, got " + offer.getMaxUses() + " / " + offer.getXp()));
    }
    helper.succeed();
  }
}
