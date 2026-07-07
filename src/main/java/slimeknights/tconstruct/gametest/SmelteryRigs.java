package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.IItemHandler;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.library.recipe.FluidValues;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.FaucetBlock;
import slimeknights.tconstruct.smeltery.block.component.SearedTankBlock.TankType;
import slimeknights.tconstruct.smeltery.block.controller.ControllerBlock;
import slimeknights.tconstruct.smeltery.block.entity.component.TankBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.SmelteryBlockEntity;

/**
 * Builds known-good smeltery/casting rigs in a level, entirely in code.
 * Positions are relative to an origin corner; callers pass absolute origins.
 */
public final class SmelteryRigs {
  private SmelteryRigs() {}

  /** Smallest smeltery: 3x3 outer footprint (1x1 inner), 3 tall, controller on south wall, fuel tank in wall. */
  public static BlockPos buildSmeltery(ServerLevel level, BlockPos origin) {
    BlockState bricks = TinkerSmeltery.searedBricks.get().defaultBlockState();
    // floor 3x3 at y=0
    for (int x = 0; x < 3; x++) {
      for (int z = 0; z < 3; z++) {
        level.setBlockAndUpdate(origin.offset(x, 0, z), bricks);
      }
    }
    // wall ring at y=1 and y=2 (center 1,*,1 stays air)
    for (int y = 1; y <= 2; y++) {
      for (int x = 0; x < 3; x++) {
        for (int z = 0; z < 3; z++) {
          if (x == 1 && z == 1) {
            level.setBlockAndUpdate(origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
          } else {
            level.setBlockAndUpdate(origin.offset(x, y, z), bricks);
          }
        }
      }
    }
    // fuel tank replaces one wall block at y=1
    BlockPos tankPos = origin.offset(0, 1, 1);
    level.setBlockAndUpdate(tankPos, TinkerSmeltery.searedTank.get(TankType.FUEL_TANK).defaultBlockState());
    fillTank(level, tankPos, new FluidStack(Fluids.LAVA, 4000));
    // controller replaces the south-middle wall block at y=1, facing outward (south)
    BlockPos controller = origin.offset(1, 1, 2);
    level.setBlockAndUpdate(controller,
      TinkerSmeltery.smelteryController.get().defaultBlockState()
        .setValue(ControllerBlock.FACING, Direction.SOUTH));
    return controller;
  }

  /**
   * Inserts an item into a formed smeltery's melting inventory (call after formation settles).
   * @throws IllegalStateException if there is no smeltery controller at the position, or if the melting
   *                               inventory rejects the stack (e.g. the structure has not formed yet)
   */
  public static void insertMeltable(ServerLevel level, BlockPos controller, ItemStack stack) {
    if (level.getBlockEntity(controller) instanceof SmelteryBlockEntity smeltery) {
      IItemHandler inventory = smeltery.getItemCapability();
      ItemStack remainder = inventory.insertItem(0, stack, false);
      if (!remainder.isEmpty()) {
        throw new IllegalStateException("Melting inventory rejected " + stack + " at " + controller
                                        + " (slots=" + inventory.getSlots() + " - structure not formed yet?)");
      }
    } else {
      throw new IllegalStateException("Expected smeltery controller at " + controller + ", found " + level.getBlockEntity(controller));
    }
  }

  /** Casting rig: seared tank (pre-filled molten iron) + faucet on its side + casting table below the faucet. */
  public static CastingRig buildCastingRig(ServerLevel level, BlockPos origin) {
    BlockPos tank = origin.above(1);
    level.setBlockAndUpdate(tank, TinkerSmeltery.searedTank.get(TankType.INGOT_TANK).defaultBlockState());
    fillTank(level, tank, new FluidStack(TinkerFluids.moltenIron.get(), FluidValues.INGOT * 4));
    BlockPos faucet = tank.relative(Direction.EAST);
    level.setBlockAndUpdate(faucet,
      TinkerSmeltery.searedFaucet.get().defaultBlockState()
        .setValue(FaucetBlock.FACING, Direction.EAST));
    BlockPos table = faucet.below();
    level.setBlockAndUpdate(table, TinkerSmeltery.searedTable.get().defaultBlockState());
    return new CastingRig(tank, faucet, table);
  }

  public record CastingRig(BlockPos tank, BlockPos faucet, BlockPos table) {}

  /**
   * Fills any TankBlockEntity-style block at pos via its fluid handler.
   * @throws IllegalStateException if there is no tank block entity at the position, or if the tank
   *                               does not accept the full fluid amount
   */
  public static void fillTank(ServerLevel level, BlockPos pos, FluidStack fluid) {
    if (level.getBlockEntity(pos) instanceof TankBlockEntity tank) {
      int filled = tank.getFluidHandler(null).fill(fluid, FluidAction.EXECUTE);
      if (filled != fluid.getAmount()) {
        throw new IllegalStateException("Tank at " + pos + " accepted only " + filled + "/" + fluid.getAmount() + "mb of " + BuiltInRegistries.FLUID.getKey(fluid.getFluid()));
      }
    } else {
      throw new IllegalStateException("Expected tank block entity at " + pos + ", found " + level.getBlockEntity(pos));
    }
  }
}
