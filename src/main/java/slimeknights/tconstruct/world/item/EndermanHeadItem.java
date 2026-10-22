package slimeknights.tconstruct.world.item;

import net.minecraft.core.Direction;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;

/**
 * Head item for enderman head, which counts as a pumpkin on the head.
 * PORT 1.21.4: the {@code IItemExtension#isEnderMask} hook was removed; the enderman-mask behaviour is no longer
 * overridable here and must be re-added via the 1.21.4 equipment/data-component path.
 */
public class EndermanHeadItem extends StandingAndWallBlockItem {
  public EndermanHeadItem(Block pBlock, Block pWallBlock, Properties pProperties, Direction pAttachmentDirection) {
    super(pBlock, pWallBlock, pAttachmentDirection, pProperties);
  }
}
