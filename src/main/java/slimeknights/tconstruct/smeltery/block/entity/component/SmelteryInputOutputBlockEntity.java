package slimeknights.tconstruct.smeltery.block.entity.component;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.EmptyFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import slimeknights.mantle.block.entity.IRetexturedBlockEntity;
import slimeknights.mantle.inventory.EmptyItemHandler;
import slimeknights.mantle.util.RetexturedHelper;
import slimeknights.tconstruct.common.multiblock.IMasterLogic;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.entity.tank.ISmelteryTankHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

import static slimeknights.mantle.util.RetexturedHelper.TAG_TEXTURE;

/**
 * Shared logic between drains and ducts
 */
public abstract class SmelteryInputOutputBlockEntity<T> extends SmelteryComponentBlockEntity implements IRetexturedBlockEntity {
  /** Capability this TE watches */
  private final BlockCapability<T, Direction> capability;
  /** Empty capability for in case the valid capability becomes invalid without invalidating */
  protected final T emptyInstance;
  /** Cached handler resolved from the master, or null if not yet resolved */
  @Nullable
  private T capabilityHolder = null;
  /** If true, the cached handler has been resolved (may still be null) */
  private boolean capabilityResolved = false;

  /* Retexturing */
  @Nonnull
  @Getter
  private Block texture = Blocks.AIR;

  protected SmelteryInputOutputBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, BlockCapability<T, Direction> capability, T emptyInstance) {
    super(type, pos, state);
    this.capability = capability;
    this.emptyInstance = emptyInstance;
  }

  /** Clears all cached capabilities */
  private void clearHandler() {
    capabilityHolder = null;
    capabilityResolved = false;
    if (level != null) {
      level.invalidateCapabilities(worldPosition);
    }
  }

  @Override
  public void onMasterLoad(IMasterLogic master) {
    clearHandler();
  }

  @Override
  protected void setMaster(@Nullable BlockPos master, @Nullable Block block) {
    assert level != null;

    // if we have a new master, invalidate handlers
    boolean masterChanged = false;
    if (!Objects.equals(getMasterPos(), master)) {
      clearHandler();
      masterChanged = true;
    }
    super.setMaster(master, block);
    // notify neighbors of the change (state change skips the notify flag)
    if (masterChanged) {
      level.blockUpdated(worldPosition, getBlockState().getBlock());
    }
  }

  /**
   * Gets the capability to store in this IO block, resolved from the master block entity.
   * @param parent  Parent (master) block entity
   * @return  Capability from parent, or null if absent
   */
  @Nullable
  protected T getCapability(BlockEntity parent) {
    if (level == null) {
      return null;
    }
    return level.getCapability(capability, parent.getBlockPos(), null);
  }

  /**
   * Fetches the capability handler if missing. Registered centrally on {@code RegisterCapabilitiesEvent}.
   * @return  Resolved handler, or null if absent
   */
  @Nullable
  public T getCachedCapability() {
    if (!capabilityResolved) {
      capabilityResolved = true;
      if (validateMaster()) {
        BlockPos master = getMasterPos();
        if (master != null && this.level != null) {
          BlockEntity te = level.getBlockEntity(master);
          if (te != null) {
            capabilityHolder = getCapability(te);
          }
        }
      }
    }
    return capabilityHolder;
  }


  /* Retexturing */

  @Override
  @Nonnull
  public ModelData getModelData() {
    return RetexturedHelper.getModelData(getTexture());
  }

  @Override
  public String getTextureName() {
    return RetexturedHelper.getTextureName(texture);
  }

  @Override
  public void updateTexture(String name) {
    Block oldTexture = texture;
    texture = RetexturedHelper.getBlock(name);
    if (oldTexture != texture) {
      setChangedFast();
      RetexturedHelper.onTextureUpdated(this);
    }
  }


  /* NBT */

  @Override
  protected boolean shouldSyncOnUpdate() {
    return true;
  }

  @Override
  protected void saveSynced(CompoundTag tags, net.minecraft.core.HolderLookup.Provider registries) {
    super.saveSynced(tags, registries);
    if (texture != Blocks.AIR) {
      tags.putString(TAG_TEXTURE, getTextureName());
    }
  }

  @Override
  public void loadAdditional(CompoundTag tags, net.minecraft.core.HolderLookup.Provider registries) {
    super.loadAdditional(tags, registries);
    if (tags.contains(TAG_TEXTURE, Tag.TAG_STRING)) {
      texture = RetexturedHelper.getBlock(tags.getString(TAG_TEXTURE));
      RetexturedHelper.onTextureUpdated(this);
    }
  }


  /** Fluid implementation of smeltery IO */
  public static abstract class SmelteryFluidIO extends SmelteryInputOutputBlockEntity<IFluidHandler> {
    protected SmelteryFluidIO(BlockEntityType<?> type, BlockPos pos, BlockState state) {
      super(type, pos, state, Capabilities.FluidHandler.BLOCK, EmptyFluidHandler.INSTANCE);
    }

    /** Wraps the given handler; subclasses may override to add extra behavior */
    protected IFluidHandler makeWrapper(IFluidHandler capability) {
      return capability;
    }

    @Nullable
    @Override
    protected IFluidHandler getCapability(BlockEntity parent) {
      // fluid capability is not exposed directly in the smeltery
      if (parent instanceof ISmelteryTankHandler tankHandler) {
        IFluidHandler capability = tankHandler.getFluidCapability();
        if (capability != null) {
          return makeWrapper(capability);
        }
      }
      return null;
    }
  }

  /** Item implementation of smeltery IO */
  public static class ChuteBlockEntity extends SmelteryInputOutputBlockEntity<IItemHandler> {
    public ChuteBlockEntity(BlockPos pos, BlockState state) {
      this(TinkerSmeltery.chute.get(), pos, state);
    }

    protected ChuteBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
      super(type, pos, state, Capabilities.ItemHandler.BLOCK, EmptyItemHandler.INSTANCE);
    }
  }

}
