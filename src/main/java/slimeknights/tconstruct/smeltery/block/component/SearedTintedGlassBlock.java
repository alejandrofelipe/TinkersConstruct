package slimeknights.tconstruct.smeltery.block.component;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LightEngine;

public class SearedTintedGlassBlock extends SearedGlassBlock {
  public SearedTintedGlassBlock(Properties properties) {
    super(properties);
  }

  @Override
  public boolean propagatesSkylightDown(BlockState state) {
    return false;
  }

  @Override
  public int getLightBlock(BlockState state) {
    return LightEngine.MAX_LEVEL;
  }
}
