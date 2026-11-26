package slimeknights.tconstruct.library.tools.capability;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.modifiers.modules.ModifierModule;
import slimeknights.tconstruct.library.modifiers.modules.build.ModifierTraitModule;
import slimeknights.tconstruct.library.tools.capability.ToolCapabilityProvider.IToolCapabilityProvider;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.CapacityStat;
import slimeknights.tconstruct.library.tools.stat.ToolStatId;
import slimeknights.tconstruct.tools.TinkerModifiers;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Standard implementation of energy capability on a tool. Not currently used in the mod directly, but should help addons have more unity.
 * <p>
 * PORT 1.21.10 (transfer phase 6): no longer a legacy {@code IEnergyStorage} over a tool supplier (nothing constructed it); the energy
 * capability is the native {@link ItemHandler}, the static helpers read and write the tool's energy.
 */
public final class ToolEnergyCapability {
  private ToolEnergyCapability() {}

  /** Format string to display energy amounts, used internally by the stat */
  public static final String ENERGY_FORMAT = TConstruct.makeDescriptionId("tool_stat", "energy");
  /** Stat marking the max capacity */
  public static final CapacityStat MAX_STAT = new CapacityStat(new ToolStatId(TConstruct.MOD_ID, "max_energy"), 0xa00000, ENERGY_FORMAT);
  /** Persistent data key for fetching the current energy */
  public static final Identifier ENERGY_KEY = TConstruct.getResource("energy");
  /** Include this module in a modifier adding energy capacity or functionality to ensure capacity changes are properly cleaned up */
  public static final ModifierModule ENERGY_HANDLER = new ModifierTraitModule(TinkerModifiers.energyHandler.getId(), 1, true);

  /** Gets the energy capacity for the given tool */
  public static int getMaxEnergy(IToolStackView tool) {
    return tool.getStats().getInt(MAX_STAT);
  }

  /** Gets the current energy on a tool */
  public static int getEnergy(IToolStackView tool) {
    return tool.getPersistentData().getInt(ENERGY_KEY);
  }

  /** Internal method to set energy, skips a capacity check */
  private static void setEnergyRaw(IToolStackView tool, int energy) {
    if (energy == 0) {
      tool.getPersistentData().remove(ENERGY_KEY);
    } else {
      tool.getPersistentData().putInt(ENERGY_KEY, energy);
    }
  }

  /** Sets the energy on the tool */
  public static void setEnergy(IToolStackView tool, int energy) {
    setEnergyRaw(tool, Mth.clamp(energy, 0, getMaxEnergy(tool)));
  }

  /** Adds the given amount of energy to the tool. Can use negative to subtract energy. */
  public static void addEnergy(IToolStackView tool, int energy) {
    if (energy != 0) {
      setEnergy(tool, getEnergy(tool) + energy);
    }
  }

  /** Ensures the tool's energy is within the cap. Generally not necessary to call this directly as it's called by {@link #ENERGY_HANDLER} on tool change. */
  public static void checkEnergy(IToolStackView tool) {
    int energy = ToolEnergyCapability.getEnergy(tool);
    if (energy < 0) {
      ToolEnergyCapability.setEnergyRaw(tool, 0);
    } else {
      int capacity = ToolEnergyCapability.getMaxEnergy(tool);
      if (energy > capacity) {
        ToolEnergyCapability.setEnergyRaw(tool, capacity);
      }
    }
  }

  /**
   * Native energy handler of the tool in an item access, the form of {@code Capabilities.Energy.ITEM}.
   * PORT 1.21.10 (transfer step 1): every change copies the tool, updates its energy and exchanges the item through the access, so it is
   * transactional and a simulation leaves the tool untouched. Amounts are totals over the accessed items.
   */
  public record ItemHandler(ItemAccess access) implements EnergyHandler {
    /** Gets a detached copy of the accessed tool */
    private ToolStack tool() {
      return ToolStack.copyFrom(access.getResource().toStack());
    }

    @Override
    public long getAmountAsLong() {
      return access.getResource().isEmpty() ? 0 : (long) access.getAmount() * getEnergy(tool());
    }

    @Override
    public long getCapacityAsLong() {
      return access.getResource().isEmpty() ? 0 : (long) access.getAmount() * getMaxEnergy(tool());
    }

    /** Changes the energy of every accessed tool by the given amount, returns the number of tools changed */
    private int exchange(ItemStack copy, ToolStack tool, int energy, TransactionContext transaction) {
      setEnergyRaw(tool, energy);
      return access.exchange(ItemResource.of(tool.updateStack(copy)), access.getAmount(), transaction);
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
      TransferPreconditions.checkNonNegative(amount);
      int count = access.getAmount();
      if (count == 0 || access.getResource().isEmpty()) {
        return 0;
      }
      ItemStack copy = access.getResource().toStack();
      ToolStack tool = ToolStack.copyFrom(copy);
      int current = getEnergy(tool);
      int filled = Math.min(getMaxEnergy(tool) - current, amount / count);
      if (filled <= 0) {
        return 0;
      }
      return filled * exchange(copy, tool, current + filled, transaction);
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
      TransferPreconditions.checkNonNegative(amount);
      int count = access.getAmount();
      if (count == 0 || access.getResource().isEmpty()) {
        return 0;
      }
      ItemStack copy = access.getResource().toStack();
      ToolStack tool = ToolStack.copyFrom(copy);
      int current = getEnergy(tool);
      int drained = Math.min(current, amount / count);
      if (drained <= 0) {
        return 0;
      }
      return drained * exchange(copy, tool, current - drained, transaction);
    }
  }

  /** Provider instance for an energy cap */
  public static class Provider implements IToolCapabilityProvider {
    @SuppressWarnings("unused")
    public Provider(Supplier<? extends IToolStackView> toolStack) {}

    @Nullable
    @Override
    public <T> T getCapability(IToolStackView tool, ItemCapability<T, ?> cap) {
      // the transfer capability needs the item access
      return null;
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(IToolStackView tool, ItemCapability<T, ?> cap, @Nullable ItemAccess access) {
      if (cap == Capabilities.Energy.ITEM && access != null && tool.getStats().getInt(MAX_STAT) > 0) {
        return (T) new ItemHandler(access);
      }
      return null;
    }
  }
}
