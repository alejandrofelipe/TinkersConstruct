package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.tconstruct.library.fluid.FluidAction;
import slimeknights.tconstruct.library.modifiers.fluid.FluidEffectContext;
import slimeknights.tconstruct.library.modifiers.fluid.FluidEffectManager;
import slimeknights.tconstruct.library.modifiers.fluid.FluidEffects;

/**
 * Gametests for the fluid effect API's simulate/execute flag (M5 phase 6b(iv): Tinkers' {@link FluidAction} replaced NeoForge's
 * {@code IFluidHandler.FluidAction}). Runs the datapack's lava effects (fire damage + ignite on entities, place fire on blocks): simulating
 * reports the same consumption as executing but changes nothing.
 */
public class FluidEffectGameTests {
  /** Lava effects from the datapack, one level's worth (1/20 of a bucket) */
  private static final FluidStack LAVA = new FluidStack(Fluids.LAVA, 50);

  private static FluidEffects lavaEffects(GameTestHelper helper) {
    FluidEffects effects = FluidEffectManager.INSTANCE.find(Fluids.LAVA);
    helper.assertTrue(effects.hasBlockEffects() && effects.hasEntityEffects(), Component.literal("lava has no block and entity effects loaded"));
    return effects;
  }

  /** Lava's {@code place_block} fire effect: simulate places nothing, execute places fire on the stone. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void fluid_effect_block_simulate_changes_nothing(GameTestHelper helper) {
    FluidEffects effects = lavaEffects(helper);
    BlockPos stone = new BlockPos(2, 1, 2);
    helper.setBlock(stone, Blocks.STONE);
    BlockPos absolute = helper.absolutePos(stone);
    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute).add(0, 0.5, 0), Direction.UP, absolute, false);

    int simulated = effects.applyToBlock(LAVA, 1, FluidEffectContext.builder(helper.getLevel()).block(hit), FluidAction.SIMULATE);
    helper.assertTrue(simulated == LAVA.getAmount(), Component.literal("simulating should consume " + LAVA.getAmount() + " but reported " + simulated));
    helper.assertBlockPresent(Blocks.AIR, stone.above());

    int executed = effects.applyToBlock(LAVA, 1, FluidEffectContext.builder(helper.getLevel()).block(hit), FluidAction.EXECUTE);
    helper.assertTrue(executed == simulated, Component.literal("executing consumed " + executed + " but simulating reported " + simulated));
    helper.assertBlockPresent(Blocks.FIRE, stone.above());
    helper.succeed();
  }

  /** Lava's fire damage and ignite effects: simulate leaves the pig untouched, execute damages and ignites it. */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void fluid_effect_entity_simulate_changes_nothing(GameTestHelper helper) {
    FluidEffects effects = lavaEffects(helper);
    helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);
    Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 1, 2));
    float health = pig.getHealth();
    int fireTicks = pig.getRemainingFireTicks();

    int simulated = effects.applyToEntity(LAVA, 1, FluidEffectContext.builder(helper.getLevel()).target(pig), FluidAction.SIMULATE);
    helper.assertTrue(simulated == LAVA.getAmount(), Component.literal("simulating should consume " + LAVA.getAmount() + " but reported " + simulated));
    helper.assertTrue(pig.getHealth() == health, Component.literal("simulating damaged the pig: " + health + " -> " + pig.getHealth()));
    helper.assertTrue(pig.getRemainingFireTicks() == fireTicks, Component.literal("simulating ignited the pig: " + fireTicks + " -> " + pig.getRemainingFireTicks()));

    int executed = effects.applyToEntity(LAVA, 1, FluidEffectContext.builder(helper.getLevel()).target(pig), FluidAction.EXECUTE);
    helper.assertTrue(executed == simulated, Component.literal("executing consumed " + executed + " but simulating reported " + simulated));
    helper.assertTrue(pig.getHealth() < health, Component.literal("executing did not damage the pig, health " + pig.getHealth()));
    helper.assertTrue(pig.getRemainingFireTicks() > 0, Component.literal("executing did not ignite the pig, fire ticks " + pig.getRemainingFireTicks()));
    helper.succeed();
  }
}
