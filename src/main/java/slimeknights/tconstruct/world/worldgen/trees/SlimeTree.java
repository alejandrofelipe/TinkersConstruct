package slimeknights.tconstruct.world.worldgen.trees;

import net.minecraft.world.level.block.grower.TreeGrower;
import slimeknights.tconstruct.world.TinkerStructures;
import slimeknights.tconstruct.world.block.FoliageType;

import java.util.Optional;

/**
 * Factory creating {@link TreeGrower} instances for the slime foliage types.
 * {@link TreeGrower} is final in 1.21.1, so this can no longer subclass it.
 */
public final class SlimeTree {
  private SlimeTree() {}

  /** Creates a tree grower for the given foliage type */
  public static TreeGrower create(FoliageType foliageType) {
    return switch (foliageType) {
      case EARTH -> new TreeGrower("tconstruct:earth_slime", Optional.empty(), Optional.of(TinkerStructures.earthSlimeTree), Optional.empty());
      case SKY -> new TreeGrower("tconstruct:sky_slime", Optional.empty(), Optional.of(TinkerStructures.skySlimeTree), Optional.empty());
      // ender picks the tall tree 85% of the time, the short tree otherwise; use the secondary slot for the short tree
      case ENDER -> new TreeGrower("tconstruct:ender_slime", 0.15f,
        Optional.empty(), Optional.empty(),
        Optional.of(TinkerStructures.enderSlimeTreeTall), Optional.of(TinkerStructures.enderSlimeTree),
        Optional.empty(), Optional.empty());
      case BLOOD -> new TreeGrower("tconstruct:blood_slime", Optional.empty(), Optional.of(TinkerStructures.bloodSlimeFungus), Optional.empty());
      case ICHOR -> new TreeGrower("tconstruct:ichor_slime", Optional.empty(), Optional.of(TinkerStructures.ichorSlimeFungus), Optional.empty());
    };
  }
}
