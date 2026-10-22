package slimeknights.tconstruct.library.utils;

import com.google.common.collect.Maps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ToolMaterial;
// PORT M3: TierSortingRegistry was removed in 1.21 (no NeoForge replacement; Mantle's HarvestTiersCommand
// also defers this). A central tier-ordering replacement is needed (see crossPackageAssumptions). Until then,
// the sorted-tier helpers below fall back to vanilla tool-material ordering so this class compiles.
// PORT 1.21.4: vanilla Tier/Tiers were removed; harvest tiers are now modeled with ToolMaterial. The vanilla
// tool materials keep their 1.20 registry names (minecraft:wood..netherite) so serialized data is unchanged.
import slimeknights.mantle.client.ResourceColorManager;
import slimeknights.mantle.data.listener.ISafeManagerReloadListener;
import slimeknights.tconstruct.TConstruct;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Harvest level display names
 */
public class HarvestTiers {
  private HarvestTiers() {}

  // PORT M3: temporary stand-in for the removed TierSortingRegistry. Replace with the central tier-ordering
  // system once it exists. Falls back to vanilla tool-material ordering, which preserves the common case.
  private static final List<ToolMaterial> VANILLA_SORTED = List.of(ToolMaterial.WOOD, ToolMaterial.STONE, ToolMaterial.IRON, ToolMaterial.DIAMOND, ToolMaterial.GOLD, ToolMaterial.NETHERITE);

  // PORT 1.21.4: ToolMaterial is an unnamed record, so we keep the 1.20 Tiers registry names here to preserve
  // serialized data. Only vanilla materials resolve; modded tiers need the central tier-ordering replacement.
  private static final Map<ToolMaterial, ResourceLocation> TIER_NAMES = Map.of(
    ToolMaterial.WOOD, ResourceLocation.withDefaultNamespace("wood"),
    ToolMaterial.STONE, ResourceLocation.withDefaultNamespace("stone"),
    ToolMaterial.IRON, ResourceLocation.withDefaultNamespace("iron"),
    ToolMaterial.DIAMOND, ResourceLocation.withDefaultNamespace("diamond"),
    ToolMaterial.GOLD, ResourceLocation.withDefaultNamespace("gold"),
    ToolMaterial.NETHERITE, ResourceLocation.withDefaultNamespace("netherite"));

  /** Stand-in for {@code getSortedTiers()} */
  private static List<ToolMaterial> getSortedTiers() {
    return VANILLA_SORTED;
  }

  /** Stand-in for {@code TierSortingRegistry.getName(Tier)} */
  private static ResourceLocation getTierName(ToolMaterial tier) {
    return TIER_NAMES.getOrDefault(tier, ResourceLocation.withDefaultNamespace("unknown"));
  }

  /**
   * Serializes a tier to its registry name.
   * PORT M3: stand-in for the removed {@code TierSortingRegistry.getName(Tier)}. Currently only resolves
   * vanilla tiers; modded tiers need the central tier-ordering replacement.
   * @return  Tier name, or null if the tier is not registered
   */
  @Nullable
  public static ResourceLocation tierName(ToolMaterial tier) {
    return TIER_NAMES.get(tier);
  }

  /**
   * Deserializes a tier from its registry name.
   * PORT M3: stand-in for the removed {@code TierSortingRegistry.byName(ResourceLocation)}. Currently only
   * resolves vanilla tiers; modded tiers need the central tier-ordering replacement.
   * @return  Tier, or null if no tier matches the name
   */
  @Nullable
  public static ToolMaterial byName(ResourceLocation id) {
    for (Map.Entry<ToolMaterial, ResourceLocation> entry : TIER_NAMES.entrySet()) {
      if (entry.getValue().equals(id)) {
        return entry.getKey();
      }
    }
    return null;
  }

  /** Cache of name for each tier */
  private static final Map<ToolMaterial, Component> harvestLevelNames = Maps.newHashMap();
  /** Listener to clear name cache so we get new colors */
  public static final ISafeManagerReloadListener RELOAD_LISTENER = manager -> harvestLevelNames.clear();

  /** Makes a translation key for the given name */
  private static MutableComponent makeLevelKey(ToolMaterial tier) {
    String key = Util.makeTranslationKey("harvest_tier", getTierName(tier));
    TextColor color = ResourceColorManager.getTextColor(key);
    return TConstruct.makeTranslation("stat", key).withStyle(style -> style.withColor(color));
  }

  /**
   * Gets the harvest level name for the given level number
   * @param tier  Tier
   * @return  Level name
   */
  public static Component getName(ToolMaterial tier) {
    return harvestLevelNames.computeIfAbsent(tier, n ->  makeLevelKey(tier));
  }

  /** Gets the larger of two tiers */
  public static ToolMaterial max(ToolMaterial a, ToolMaterial b) {
    List<ToolMaterial> sorted = getSortedTiers();
    // note indexOf returns -1 if the tier is missing, so the larger of an unsorted tier and a sorted one is the sorted one
    if (sorted.indexOf(b) > sorted.indexOf(a)) {
      return b;
    }
    return a;
  }

  /** Gets the smaller of two tiers */
  public static ToolMaterial min(ToolMaterial a, ToolMaterial b) {
    List<ToolMaterial> sorted = getSortedTiers();
    // note indexOf returns -1 if the tier is missing, so the smaller of an unsorted tier and a sorted one is the unsorted one
    if (sorted.indexOf(b) < sorted.indexOf(a)) {
      return b;
    }
    return a;
  }

  /** Gets the smallest tier in the sorting registry */
  public static ToolMaterial minTier() {
    List<ToolMaterial> sortedTiers = getSortedTiers();
    if (sortedTiers.isEmpty()) {
      TConstruct.LOG.error("No sorted tiers exist, this should not happen");
      return ToolMaterial.WOOD;
    }
    return sortedTiers.get(0);
  }
}
