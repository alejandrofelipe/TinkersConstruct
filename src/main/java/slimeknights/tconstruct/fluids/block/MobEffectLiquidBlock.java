package slimeknights.tconstruct.fluids.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.MapColor;
import slimeknights.mantle.registration.deferred.FluidDeferredRegister;

import java.util.function.Function;
import java.util.function.Supplier;

/** Liquid block setting the entity on fire */
public class MobEffectLiquidBlock extends LiquidBlock {
  private final Supplier<MobEffectInstance> effect;
  public MobEffectLiquidBlock(Supplier<? extends FlowingFluid> supplier, Properties properties, Supplier<MobEffectInstance> effect) {
    super(supplier.get(), properties);
    this.effect = effect;
  }

  @Override
  public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
    if (entity.getFluidTypeHeight(this.fluid.getFluidType()) > 0 && entity instanceof LivingEntity living) {
      MobEffectInstance effect = this.effect.get();
      // PORT 1.21.4: the curative-items API (MobEffectInstance#getCures) was removed; effects can no longer be marked uncurable here
      living.addEffect(effect);
    }
  }

  /** Creates a new block supplier, setting the block id (1.21.4 requirement) */
  public static java.util.function.BiFunction<net.minecraft.resources.ResourceKey<net.minecraft.world.level.block.Block>, Supplier<? extends FlowingFluid>, LiquidBlock> createEffect(MapColor color, int lightLevel, Supplier<MobEffectInstance> effect) {
    return (key, fluid) -> new MobEffectLiquidBlock(fluid, FluidDeferredRegister.createProperties(color, lightLevel).setId(key), effect);
  }
}
