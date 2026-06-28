package slimeknights.tconstruct.library.json.predicate;

import net.minecraft.world.item.Tier;
import net.minecraft.world.level.block.state.BlockState;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.mantle.data.predicate.block.BlockPredicate;
import slimeknights.tconstruct.library.json.TinkerLoadables;

/** Block predicate matching anything minable by the given tier */
public record HarvestTierPredicate(Tier tier) implements BlockPredicate {
  public static final RecordLoadable<HarvestTierPredicate> LOADER = RecordLoadable.create(TinkerLoadables.TIER.requiredField("tier", HarvestTierPredicate::tier), HarvestTierPredicate::new);

  @Override
  public boolean matches(BlockState state) {
    // TierSortingRegistry.isCorrectTierForDrops was removed in 1.20.5+; 1.21 expresses harvestability via the
    // tier's incorrect-blocks-for-drops tag. A block is harvestable by this tier when it is not in that tag.
    return !state.is(tier.getIncorrectBlocksForDrops());
  }

  @Override
  public RecordLoadable<? extends IJsonPredicate<BlockState>> getLoader() {
    return LOADER;
  }
}
