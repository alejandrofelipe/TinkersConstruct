package slimeknights.tconstruct.plugin.jsonthings.block;

import dev.gigaherz.jsonthings.things.blocks.FlexLiquidBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.InsideBlockEffectType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FlowingFluid;

import java.util.Map;
import java.util.function.Supplier;

/** Json Things version of {@link slimeknights.tconstruct.fluids.block.BurningLiquidBlock} */
public class FlexBurningLiquidBlock extends FlexLiquidBlock {
  private final int burnTime;
  private final float damage;
  public FlexBurningLiquidBlock(Properties properties, Map<Property<?>,Comparable<?>> propertyDefaultValues, Supplier<FlowingFluid> fluidSupplier, int burnTime, float damage) {
    super(properties, propertyDefaultValues, fluidSupplier.get());
    this.burnTime = burnTime;
    this.damage = damage;
  }

  @Override
  public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean intersects) {
    if (!entity.fireImmune() && entity.getFluidTypeHeight(this.fluid.getFluidType()) > 0) {
      // same as BurningLiquidBlock: queued on the applier so it runs in vanilla order (before any extinguish), like lava
      effectApplier.runAfter(InsideBlockEffectType.LAVA_IGNITE, target -> {
        target.igniteForSeconds(burnTime);
        if (target.hurtOrSimulate(target.damageSources().lava(), damage)) {
          target.playSound(SoundEvents.GENERIC_BURN, 0.4F, 2.0F + level.getRandom().nextFloat() * 0.4F);
        }
      });
    }
  }
}
