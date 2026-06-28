package slimeknights.tconstruct.world.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.TriState;

public class SlimeDirtBlock extends Block {

  public SlimeDirtBlock(Properties properties) {
    super(properties);
  }

  @Override
  public TriState canSustainPlant(BlockState state, BlockGetter world, BlockPos pos, Direction facing, BlockState plant) {
    // can sustain both slime plants and normal (plains-type) plants
    Block plantBlock = plant.getBlock();
    if (plantBlock instanceof SlimePlant || plantBlock instanceof BushBlock) {
      return TriState.TRUE;
    }
    return TriState.DEFAULT;
  }
}
