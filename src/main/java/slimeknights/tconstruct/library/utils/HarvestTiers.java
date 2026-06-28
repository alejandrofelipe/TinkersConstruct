package slimeknights.tconstruct.library.utils;

import com.google.common.collect.Maps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
// PORT M3: TierSortingRegistry was removed in 1.21 (no NeoForge replacement; Mantle's HarvestTiersCommand
// also defers this). A central tier-ordering replacement is needed (see crossPackageAssumptions). Until then,
// the sorted-tier helpers below fall back to vanilla Tiers ordering so this class compiles.
import slimeknights.mantle.client.ResourceColorManager;
import slimeknights.mantle.data.listener.ISafeManagerReloadListener;
import slimeknights.tconstruct.TConstruct;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * Harvest level display names
 */
public class HarvestTiers {
  private HarvestTiers() {}

  // PORT M3: temporary stand-in for the removed TierSortingRegistry. Replace with the central tier-ordering
  // system once it exists. Falls back to vanilla Tiers enum ordering, which preserves the common case.
  private static final List<Tier> VANILLA_SORTED = List.of(Tiers.values());

  /** Stand-in for {@code getSortedTiers()} */
  private static List<Tier> getSortedTiers() {
    return VANILLA_SORTED;
  }

  /** Stand-in for {@code TierSortingRegistry.getName(Tier)} */
  private static ResourceLocation getTierName(Tier tier) {
    if (tier instanceof Tiers vanilla) {
      return ResourceLocation.fromNamespaceAndPath("minecraft", vanilla.name().toLowerCase(java.util.Locale.ROOT));
    }
    return ResourceLocation.fromNamespaceAndPath("minecraft", "unknown");
  }

  /** Cache of name for each tier */
  private static final Map<Tier, Component> harvestLevelNames = Maps.newHashMap();
  /** Listener to clear name cache so we get new colors */
  public static final ISafeManagerReloadListener RELOAD_LISTENER = manager -> harvestLevelNames.clear();

  /** Makes a translation key for the given name */
  private static MutableComponent makeLevelKey(Tier tier) {
    String key = Util.makeTranslationKey("harvest_tier", getTierName(tier));
    TextColor color = ResourceColorManager.getTextColor(key);
    return TConstruct.makeTranslation("stat", key).withStyle(style -> style.withColor(color));
  }

  /**
   * Gets the harvest level name for the given level number
   * @param tier  Tier
   * @return  Level name
   */
  public static Component getName(Tier tier) {
    return harvestLevelNames.computeIfAbsent(tier, n ->  makeLevelKey(tier));
  }

  /** Gets the larger of two tiers */
  public static Tier max(Tier a, Tier b) {
    List<Tier> sorted = getSortedTiers();
    // note indexOf returns -1 if the tier is missing, so the larger of an unsorted tier and a sorted one is the sorted one
    if (sorted.indexOf(b) > sorted.indexOf(a)) {
      return b;
    }
    return a;
  }

  /** Gets the smaller of two tiers */
  public static Tier min(Tier a, Tier b) {
    List<Tier> sorted = getSortedTiers();
    // note indexOf returns -1 if the tier is missing, so the smaller of an unsorted tier and a sorted one is the unsorted one
    if (sorted.indexOf(b) < sorted.indexOf(a)) {
      return b;
    }
    return a;
  }

  /** Gets the smallest tier in the sorting registry */
  public static Tier minTier() {
    List<Tier> sortedTiers = getSortedTiers();
    if (sortedTiers.isEmpty()) {
      TConstruct.LOG.error("No sorted tiers exist, this should not happen");
      return Tiers.WOOD;
    }
    return sortedTiers.get(0);
  }
}
