package slimeknights.tconstruct.gadgets.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import slimeknights.mantle.transfer.TransferHelper;

import javax.annotation.Nullable;

public class DropperRailBlock extends RailBlock {

  public DropperRailBlock(Properties properties) {
    super(properties);
  }

  @Override
  public void onMinecartPass(BlockState state, Level world, BlockPos pos, AbstractMinecart cart) {
    if (!(cart instanceof Hopper)) {
      return;
    }
    @Nullable ResourceHandler<ItemResource> itemHandlerCart = cart.getCapability(Capabilities.Item.ENTITY_AUTOMATION, Direction.UP);
    if (itemHandlerCart == null) {
      return;
    }
    @Nullable ResourceHandler<ItemResource> itemHandlerTE = TransferHelper.getItemResourceHandler(world, pos.below(), Direction.UP);
    if (itemHandlerTE == null) {
      return;
    }

    // PORT 1.21.10: move one item natively, committing only if both the extraction and the insertion succeed
    for (int i = 0; i < itemHandlerCart.size(); i++) {
      ItemResource resource = itemHandlerCart.getResource(i);
      if (resource.isEmpty()) {
        continue;
      }
      try (Transaction tx = Transaction.openRoot()) {
        if (itemHandlerCart.extract(i, resource, 1, tx) == 1 && itemHandlerTE.insert(resource, 1, tx) == 1) {
          tx.commit();
          break;
        }
      }
    }
  }

}
