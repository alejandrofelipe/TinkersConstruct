package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.VillagerTradeTags;
import net.minecraft.util.RandomSource;
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
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.world.data.VillagerTradeProvider;
import slimeknights.tconstruct.world.logic.AncientToolTradeFunction;

import java.util.Optional;

/**
 * Gametests for the wandering trader ancient tool trade. 26.1 made trades datapack entries: {@code tconstruct:ancient_tool_1} to {@code _6} are
 * {@code villager_trade}s added to {@code #minecraft:wandering_trader/uncommon}, copy {@code k} gated by the config weight being at least {@code k};
 * their item modifier {@code tconstruct:ancient_tool_trade} picks the tool and its cost (replaces the 1.21.11 {@code WandererTradesEvent} listing).
 */
public class TradeGameTests {
  /** Every trade copy loads, is in the uncommon wandering trader tag and that tag feeds the uncommon trade set */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 20)
  public static void ancient_tool_trade_loaded(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    Registry<VillagerTrade> trades = level.registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE);
    TradeSet uncommon = level.registryAccess().lookupOrThrow(Registries.TRADE_SET).getValueOrThrow(TradeSets.WANDERING_TRADER_UNCOMMON);
    HolderSet<VillagerTrade> setTrades = uncommon.getTrades();
    helper.assertTrue(VillagerTradeProvider.ANCIENT_TOOL.size() == 6, Component.literal("expected 6 ancient tool trade copies, got " + VillagerTradeProvider.ANCIENT_TOOL.size()));
    for (ResourceKey<VillagerTrade> key : VillagerTradeProvider.ANCIENT_TOOL) {
      Optional<Holder.Reference<VillagerTrade>> trade = trades.get(key);
      helper.assertTrue(trade.isPresent(), Component.literal("villager trade " + key.identifier() + " did not load"));
      helper.assertTrue(trade.get().is(VillagerTradeTags.WANDERING_TRADER_UNCOMMON), Component.literal(key.identifier() + " is not in #" + VillagerTradeTags.WANDERING_TRADER_UNCOMMON.location()));
      helper.assertTrue(setTrades.contains(trade.get()), Component.literal("uncommon wandering trader trade set does not offer " + key.identifier()));
    }
    helper.succeed();
  }

  /** Copy {@code k} gives an offer only when the weight is at least {@code k}: weight 6 enables all, 2 the first two, 0 none, 100 acts like 6 */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 20)
  public static void ancient_tool_trade_copies_follow_weight(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    Registry<VillagerTrade> trades = level.registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE);
    LootContext context = tradeContext(helper);
    int weight = Config.COMMON.wandererAncientToolWeight.get();
    try {
      for (int setting : new int[] {6, 2, 0, 100}) {
        Config.COMMON.wandererAncientToolWeight.set(setting);
        for (int i = 0; i < VillagerTradeProvider.ANCIENT_TOOL.size(); i++) {
          int copy = i + 1;
          boolean offered = trades.getValueOrThrow(VillagerTradeProvider.ANCIENT_TOOL.get(i)).getOffer(context) != null;
          helper.assertTrue(offered == (copy <= setting), Component.literal("weight " + setting + ": copy " + copy + (offered ? " gave" : " gave no") + " offer"));
        }
      }
    } finally {
      Config.COMMON.wandererAncientToolWeight.set(weight);
    }
    helper.succeed();
  }

  /**
   * Each copy produces an offer like the 1.21.11 listing: a tool from the trader tools tag with materials, a cost from the
   * tier formula (6 to 8 emeralds per average material tier), 1 use and 15 xp; the cost component is consumed by the trade (not left on the tool).
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 20)
  public static void ancient_tool_trade_offer(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    Registry<VillagerTrade> trades = level.registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE);
    LootContext context = tradeContext(helper);
    for (ResourceKey<VillagerTrade> key : VillagerTradeProvider.ANCIENT_TOOL) {
      VillagerTrade trade = trades.getValueOrThrow(key);
      for (int i = 0; i < 5; i++) {
        MerchantOffer offer = trade.getOffer(context);
        helper.assertTrue(offer != null, Component.literal(key.identifier() + " gave no offer (all copies enabled by default)"));
        ItemStack result = offer.getResult();
        helper.assertTrue(result.is(TinkerTags.Items.TRADER_TOOLS), Component.literal("offered item is not a trader tool: " + result));
        helper.assertTrue(!result.has(DataComponents.ADDITIONAL_TRADE_COST), Component.literal("additional trade cost left on the tool"));
        helper.assertTrue(!ToolStack.from(result).getMaterials().isEmpty(), Component.literal("offered tool has no materials: " + result));
        ItemStack cost = offer.getBaseCostA();
        helper.assertTrue(cost.is(Items.EMERALD), Component.literal("cost is not emeralds: " + cost));
        // the 1.21.11 tier formula: round(6 * average tier) plus 0 to round(2 * average tier); ancient materials go down to tier 0 (5 emeralds or less)
        float tier = 0;
        MaterialNBT materials = ToolStack.from(result).getMaterials();
        for (MaterialVariant material : materials) {
          tier += material.get().getTier();
        }
        tier /= materials.size();
        // at least 1: an all tier 0 tool (0 emeralds in 1.21.11) would otherwise give no offer
        int min = Math.max(1, Math.round(tier * 6)), max = Math.max(1, Math.round(tier * 6) + Math.round(2 * tier));
        helper.assertTrue(cost.getCount() >= min && cost.getCount() <= max, Component.literal("cost " + cost.getCount() + " out of the " + min + "-" + max + " range for average tier " + tier));
        helper.assertTrue(offer.getMaxUses() == 1 && offer.getXp() == 15, Component.literal("expected 1 use and 15 xp, got " + offer.getMaxUses() + " / " + offer.getXp()));
      }
    }
    helper.succeed();
  }

  /**
   * The cost formula for the rare all tier 0 tool, deterministically: ancient materials include tier 0 ones (wood, string, leather...), which give
   * 0 emeralds by the 1.21.11 formula, and a 26.1 trade with no cost gives no offer. The cost must be 1, for every random roll; tier 2 stays 12-16.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 20)
  public static void ancient_tool_trade_cost_minimum(GameTestHelper helper) {
    IMaterial tierZero = MaterialRegistry.getInstance().getAllMaterials().stream().filter(material -> material.getTier() == 0).findFirst().orElse(null);
    helper.assertTrue(tierZero != null, Component.literal("no tier 0 material loaded"));
    IMaterial tierTwo = MaterialRegistry.getInstance().getAllMaterials().stream().filter(material -> material.getTier() == 2).findFirst().orElse(null);
    helper.assertTrue(tierTwo != null, Component.literal("no tier 2 material loaded"));
    RandomSource random = RandomSource.create(42);
    for (int i = 0; i < 50; i++) {
      int zero = AncientToolTradeFunction.cost(MaterialNBT.of(tierZero, tierZero, tierZero), random);
      helper.assertTrue(zero == 1, Component.literal("all tier 0 tool costs " + zero + ", expected 1"));
      int two = AncientToolTradeFunction.cost(MaterialNBT.of(tierTwo, tierTwo), random);
      helper.assertTrue(two >= 12 && two <= 16, Component.literal("tier 2 tool costs " + two + ", expected 12 to 16"));
    }
    helper.succeed();
  }

  /** Same context as {@code AbstractVillager#addOffersFromTradeSet}, for a wandering trader in the test */
  private static LootContext tradeContext(GameTestHelper helper) {
    WanderingTrader trader = helper.spawnWithNoFreeWill(EntityType.WANDERING_TRADER, new BlockPos(2, 1, 2));
    return new LootContext.Builder(
      new LootParams.Builder(helper.getLevel())
        .withParameter(LootContextParams.ORIGIN, trader.position())
        .withParameter(LootContextParams.THIS_ENTITY, trader)
        .withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, Unit.INSTANCE)
        .create(LootContextParamSets.VILLAGER_TRADE)
    ).create(Optional.empty());
  }
}
