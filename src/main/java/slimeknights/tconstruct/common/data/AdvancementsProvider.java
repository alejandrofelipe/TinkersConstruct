package slimeknights.tconstruct.common.data;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import net.minecraft.data.PackOutput.Target;
import slimeknights.mantle.data.GenericDataProvider;

import java.util.concurrent.CompletableFuture;

// PORT M3: Full advancement generation is disabled pending the 1.21.1 advancement-API rewrite.
//   The 1.20.1 implementation used net.minecraft.advancements.FrameType, RequirementsStrategy,
//   Advancement#deconstruct(), and net.neoforged.neoforge.common.crafting.ConditionalAdvancement,
//   all of which were removed/replaced (FrameType -> AdvancementType, RequirementsStrategy ->
//   AdvancementRequirements.Strategy, Advancement -> AdvancementHolder, ConditionalAdvancement
//   removed from NeoForge, AdvancementRewards.Builder.loot now takes ResourceKey<LootTable>,
//   ContextAwarePredicate.ANY / EnchantmentPredicate.NONE removed, LocationPredicate.inStructure
//   signature changed). It also depended on TConstruct library predicate APIs that changed in the
//   library bucket (ToolStackItemPredicate.ofTool, StatInSetPredicate, etc.). This provider should
//   be reimplemented on top of net.minecraft.data.advancements.AdvancementProvider /
//   AdvancementSubProvider once those land. Until then it generates nothing so the build is green.
public class AdvancementsProvider extends GenericDataProvider {
  public AdvancementsProvider(PackOutput output) {
    super(output, Target.DATA_PACK, "advancements");
  }

  @Override
  public String getName() {
    return "Tinkers' Construct Advancements";
  }

  @Override
  public CompletableFuture<?> run(CachedOutput cache) {
    // PORT M3: no advancements generated yet (see class javadoc).
    return CompletableFuture.completedFuture(null);
  }
}
