package slimeknights.tconstruct.gametest;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.connection.ConnectionType;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.tools.TinkerToolParts;
import slimeknights.tconstruct.tools.data.material.MaterialIds;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Safety net for every Tinkers block entity: each Tinkers block with a block entity is placed, then its block entity goes
 * through the paths that run outside singleplayer, where a failure drops the block entity data or disconnects the client
 * ("Network Protocol Error"): save then load into a fresh instance (chunk save/load) with an identical re-save, the chunk
 * sync update tag ({@code handleUpdateTag}) and the block entity data packet (network codec, then {@code onDataPacket}).
 * Done empty and again filled with a fluid and an item where the block entity exposes a fluid or item handler.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(TConstruct.MOD_ID)
public class BlockEntitySyncGameTests {
  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void block_entities_save_load_and_sync(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
    Set<BlockEntityType<?>> covered = new HashSet<>();
    List<String> failures = new ArrayList<>();
    int checked = 0;
    for (Block block : BuiltInRegistries.BLOCK) {
      ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
      if (!TConstruct.MOD_ID.equals(id.getNamespace()) || !(block instanceof EntityBlock)) {
        continue;
      }
      try {
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        // structure blocks (seared bricks, glass, ladders) only have a block entity in some states (in_structure)
        BlockState state = blockEntityState(block, pos);
        if (state == null) {
          failures.add(id + ": no block state has a block entity");
          continue;
        }
        level.setBlockAndUpdate(pos, state);
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
          failures.add(id + ": no block entity after placing " + state);
          continue;
        }
        covered.add(be.getType());
        checked++;
        checkRoundTrip(level, id + " (empty)", be, failures);
        if (fill(level, pos)) {
          checkRoundTrip(level, id + " (filled)", be, failures);
        }
      } catch (Throwable t) {
        TConstruct.LOG.error("Block entity of {} failed the save/sync round trip", id, t);
        failures.add(id + ": " + t);
      }
    }
    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
      ResourceLocation id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
      if (id != null && TConstruct.MOD_ID.equals(id.getNamespace()) && !covered.contains(type)) {
        failures.add(id + ": block entity type not placed by any Tinkers block");
      }
    }
    helper.assertTrue(checked > 0, "no Tinkers block entities found");
    helper.assertTrue(failures.isEmpty(), failures.size() + " block entity save/sync failure(s) over " + checked + " block(s):\n" + String.join("\n", failures));
    helper.succeed();
  }

  /** Finds a state of the block that has a block entity, preferring the default state */
  @Nullable
  private static BlockState blockEntityState(Block block, BlockPos pos) {
    EntityBlock entityBlock = (EntityBlock) block;
    if (entityBlock.newBlockEntity(pos, block.defaultBlockState()) != null) {
      return block.defaultBlockState();
    }
    for (BlockState state : block.getStateDefinition().getPossibleStates()) {
      if (entityBlock.newBlockEntity(pos, state) != null) {
        return state;
      }
    }
    return null;
  }

  /** Runs the save/load and client sync paths on the block entity, adding any failure to the list */
  private static void checkRoundTrip(ServerLevel level, String name, BlockEntity be, List<String> failures) {
    RegistryAccess registries = level.registryAccess();
    BlockPos pos = be.getBlockPos();
    Supplier<BlockEntity> fresh = () -> be.getType().create(pos, be.getBlockState());

    // chunk save, then chunk load into a fresh block entity without a level (BlockEntity.loadStatic), which must save the same
    CompoundTag saved = be.saveWithFullMetadata(registries);
    BlockEntity loaded = fresh.get();
    loaded.loadWithComponents(saved, registries);
    CompoundTag resaved = loaded.saveWithFullMetadata(registries);
    if (!saved.equals(resaved)) {
      failures.add(name + ": re-save after load differs\n  saved:   " + saved + "\n  resaved: " + resaved);
    }

    // chunk sync: the update tag is handled by a fresh client block entity
    fresh.get().handleUpdateTag(be.getUpdateTag(registries), registries);

    // block entity data packet: network codec, then handled by the client block entity
    if (be.getUpdatePacket() instanceof ClientboundBlockEntityDataPacket packet) {
      RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries, ConnectionType.NEOFORGE);
      try {
        ClientboundBlockEntityDataPacket.STREAM_CODEC.encode(buffer, packet);
        ClientboundBlockEntityDataPacket decoded = ClientboundBlockEntityDataPacket.STREAM_CODEC.decode(buffer);
        fresh.get().onDataPacket(null, decoded, registries);
      } finally {
        buffer.release();
      }
    }
  }

  /** Fills the fluid and item handlers of the block entity at the position, if any; true if anything was inserted */
  private static boolean fill(ServerLevel level, BlockPos pos) {
    boolean filled = false;
    IFluidHandler fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
    if (fluids == null) {
      fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.UP);
    }
    if (fluids != null) {
      for (Fluid fluid : List.of(Fluids.LAVA, TinkerFluids.moltenIron.get(), Fluids.WATER)) {
        if (fluids.fill(new FluidStack(fluid, 1000), FluidAction.EXECUTE) > 0) {
          filled = true;
          break;
        }
      }
    }
    IItemHandler items = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
    if (items == null) {
      items = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
    }
    if (items != null) {
      List<ItemStack> candidates = List.of(new ItemStack(Items.IRON_INGOT), TinkerToolParts.pickHead.get().withMaterial(MaterialIds.iron), new ItemStack(Items.COBBLESTONE));
      insert:
      for (ItemStack candidate : candidates) {
        for (int slot = 0; slot < items.getSlots(); slot++) {
          if (items.insertItem(slot, candidate.copy(), false).getCount() < candidate.getCount()) {
            filled = true;
            break insert;
          }
        }
      }
    }
    return filled;
  }
}
