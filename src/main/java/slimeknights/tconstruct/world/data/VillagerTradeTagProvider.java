package slimeknights.tconstruct.world.data;

import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.KeyTagProvider;
import net.minecraft.tags.VillagerTradeTags;
import net.minecraft.world.item.trading.VillagerTrade;
import slimeknights.tconstruct.TConstruct;

import java.util.concurrent.CompletableFuture;

/** Datagen for villager trade tags */
public class VillagerTradeTagProvider extends KeyTagProvider<VillagerTrade> {
  public VillagerTradeTagProvider(PackOutput output, CompletableFuture<Provider> lookupProvider) {
    super(output, Registries.VILLAGER_TRADE, lookupProvider, TConstruct.MOD_ID);
  }

  @Override
  protected void addTags(Provider provider) {
    tag(VillagerTradeTags.WANDERING_TRADER_UNCOMMON).add(VillagerTradeProvider.ANCIENT_TOOL);
  }
}
