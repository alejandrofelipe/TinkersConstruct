package slimeknights.tconstruct.library.tools.capability.fluid;

import lombok.RequiredArgsConstructor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.fluid.FluidAction;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.module.ModuleHook;
import slimeknights.tconstruct.library.tools.capability.ToolCapabilityProvider.IToolCapabilityProvider;
import slimeknights.tconstruct.library.tools.nbt.IModDataView;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Logic to make a tool a fluid handler: a view of a tool's tanks through its fluid modifier hooks, exposed to automation as the native
 * {@link ItemHandler}.
 * <p>
 * PORT 1.21.10 (transfer phase 6): no longer a legacy {@code IFluidHandlerItem}; changes made through this view apply to the viewed tool
 * right away, so the native handler runs it on a detached copy and exchanges the result through the item access.
 */
@RequiredArgsConstructor
public class ToolFluidCapability extends FluidModifierHookIterator<ModifierEntry> {
  /** Boolean key to set in volatile mod data to enable the fluid capability */
  public static final ResourceLocation TOTAL_TANKS = TConstruct.getResource("total_tanks");

  /** Modifier hook instance to make an inventory modifier */
  public static final ModuleHook<FluidModifierHook> HOOK = ModifierHooks.register(TConstruct.getResource("fluid"), FluidModifierHook.class, FluidModifierHookMerger::new, new FluidModifierHook() {
    @Override
    public int getTanks(IModDataView volatileData, ModifierEntry modifier) {
      return 0;
    }

    @Override
    public boolean isFluidValid(IToolStackView tool, ModifierEntry modifier, int tank, FluidStack fluid) {
      return false;
    }

    @Override
    public int fill(IToolStackView tool, ModifierEntry modifier, FluidStack resource, FluidAction action) {
      return 0;
    }

    @Override
    public FluidStack drain(IToolStackView tool, ModifierEntry modifier, FluidStack resource, FluidAction action) {
      return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(IToolStackView tool, ModifierEntry modifier, int maxDrain, FluidAction action) {
      return FluidStack.EMPTY;
    }
  });

  /** Viewed tool */
  private final IToolStackView tool;

  /* Basic inventory */

  /** Gets the number of tanks on the tool */
  public int getTanks() {
    return tool.getVolatileData().getInt(TOTAL_TANKS);
  }

  @Override
  protected Iterator<ModifierEntry> getIterator(IToolStackView tool) {
    return tool.getModifierList().iterator();
  }

  @Override
  protected FluidModifierHook getHook(ModifierEntry entry) {
    indexEntry = entry;
    return entry.getHook(HOOK);
  }

  /** Gets the fluid in the given tank, empty if the tank does not exist */
  public FluidStack getFluidInTank(int tank) {
    FluidModifierHook hook = findHook(tool, tank);
    if (hook != null) {
      return hook.getFluidInTank(tool, indexEntry, tank - startIndex);
    }
    return FluidStack.EMPTY;
  }

  /** Gets the capacity of the given tank, 0 if the tank does not exist */
  public int getTankCapacity(int tank) {
    FluidModifierHook hook = findHook(tool, tank);
    if (hook != null) {
      return hook.getTankCapacity(tool, indexEntry, tank - startIndex);
    }
    return 0;
  }

  /** Checks if the fluid is valid for the given tank */
  public boolean isFluidValid(int tank, FluidStack stack) {
    FluidModifierHook hook = findHook(tool, tank);
    if (hook != null) {
      return hook.isFluidValid(tool, indexEntry, tank - startIndex, stack);
    }
    return false;
  }

  /** Fills the first tanks accepting the fluid, changing the viewed tool when executing. Returns the amount filled. */
  public int fill(FluidStack resource, FluidAction action) {
    return fill(tool, resource, action);
  }

  /** Drains the fluid from the tanks holding it, changing the viewed tool when executing. Returns the fluid drained. */
  public FluidStack drain(FluidStack resource, FluidAction action) {
    return drain(tool, resource, action);
  }

  /** Adds the tanks from the fluid modifier to the tool */
  public static void addTanks(ModifierEntry modifier, ModDataNBT volatileData, FluidModifierHook hook) {
    volatileData.putInt(TOTAL_TANKS, hook.getTanks(volatileData, modifier) + volatileData.getInt(TOTAL_TANKS));
  }

  /**
   * Interface for modifiers with fluid capabilities to return.
   * @deprecated We are considering removing this interface in favor of a much simpler tool tank implementation.
   * For most use-cases {@link ToolTankHelper} is sufficient. If it does not cover your usecase, please leave a comment on <a href="https://github.com/SlimeKnights/TinkersConstruct/issues/5353">GitHub #5353</a>.
   */
  @SuppressWarnings("unused")
  @Deprecated
  public interface FluidModifierHook {
    /**
     * Determines how many fluid tanks are used by this modifier
     * @param volatileData  Tool data to check
     * @param modifier      Modifier to consider
     * @return  Number of tanks used
     */
    default int getTanks(IModDataView volatileData, ModifierEntry modifier) {
      return 1;
    }

    /**
     * Gets the fluid in the given tank
     * @param tool      Tool instance
     * @param modifier  Entry instance
     * @param tank      Tank index
     * @return  Fluid in the given tank
     */
    default FluidStack getFluidInTank(IToolStackView tool, ModifierEntry modifier, int tank) {
      return FluidStack.EMPTY;
    }

    /**
     * Gets the max capacity for the given tank
     * @param tool      Tool instance
     * @param modifier  Entry instance
     * @param tank      Tank index
     * @return  Fluid in the given tank
     */
    default int getTankCapacity(IToolStackView tool, ModifierEntry modifier, int tank) {
      return 0;
    }

    /**
     * Checks if the fluid is valid for the given tank
     * @param tool      Tool instance
     * @param modifier  Entry instance
     * @param tank      Tank index
     * @param fluid  Fluid to insert
     * @return  True if the fluid is valid
     */
    default boolean isFluidValid(IToolStackView tool, ModifierEntry modifier, int tank, FluidStack fluid) {
      return true;
    }

    /**
     * Fills fluid into tanks
     * @param tool      Tool instance
     * @param modifier  Entry instance
     * @param resource  FluidStack representing the Fluid and maximum amount of fluid to be filled. If you want to store this stack, make a copy
     * @param action   If SIMULATE, fill will only be simulated.
     * @return Amount of resource that was (or would have been, if simulated) filled.
     */
    int fill(IToolStackView tool, ModifierEntry modifier, FluidStack resource, FluidAction action);

    /**
     * Drains fluid out of tanks, distribution is left entirely to the IFluidHandler.
     * @param tool      Tool instance
     * @param modifier  Entry instance
     * @param resource  FluidStack representing the Fluid and maximum amount of fluid to be drained.
     * @param action    If SIMULATE, drain will only be simulated.
     * @return FluidStack representing the Fluid and amount that was (or would have been, if
     * simulated) drained.
     */
    FluidStack drain(IToolStackView tool, ModifierEntry modifier, FluidStack resource, FluidAction action);

    /**
     * Drains fluid out of internal tanks, distribution is left entirely to the IFluidHandler.
     * @param tool      Tool instance
     * @param modifier  Entry instance
     * @param maxDrain  Maximum amount of fluid to drain.
     * @param action    If SIMULATE, drain will only be simulated.
     * @return FluidStack representing the Fluid and amount that was (or would have been, if
     * simulated) drained.
     */
    FluidStack drain(IToolStackView tool, ModifierEntry modifier, int maxDrain, FluidAction action);
  }

  /** Logic to merge multiple fluid hooks */
  @RequiredArgsConstructor
  private static class FluidModifierHookMerger extends FluidModifierHookIterator<FluidModifierHook> implements FluidModifierHook {
    private final Collection<FluidModifierHook> modules;

    @Override
    protected Iterator<FluidModifierHook> getIterator(IToolStackView tool) {
      return modules.iterator();
    }

    @Override
    protected FluidModifierHook getHook(FluidModifierHook entry) {
      return entry;
    }

    /** Gets the given hook */
    @Nullable
    private FluidModifierHook findHook(IToolStackView tool, ModifierEntry modifier, int tank) {
      indexEntry = modifier;
      return this.findHook(tool, tank);
    }

    @Override
    public int getTanks(IModDataView volatileData, ModifierEntry modifier) {
      int sum = 0;
      for (FluidModifierHook module : modules) {
        sum += module.getTanks(volatileData, modifier);
      }
      return sum;
    }

    @Override
    public FluidStack getFluidInTank(IToolStackView tool, ModifierEntry modifier, int tank) {
      FluidModifierHook hook = findHook(tool, modifier, tank);
      if (hook != null) {
        return hook.getFluidInTank(tool, modifier, tank - startIndex);
      }
      return FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(IToolStackView tool, ModifierEntry modifier, int tank) {
      FluidModifierHook hook = findHook(tool, modifier, tank);
      if (hook != null) {
        return hook.getTankCapacity(tool, modifier, tank - startIndex);
      }
      return 0;
    }

    @Override
    public boolean isFluidValid(IToolStackView tool, ModifierEntry modifier, int tank, FluidStack fluid) {
      FluidModifierHook hook = findHook(tool, modifier, tank);
      if (hook != null) {
        return hook.isFluidValid(tool, modifier, tank - startIndex, fluid);
      }
      return false;
    }

    @Override
    public int fill(IToolStackView tool, ModifierEntry modifier, FluidStack resource, FluidAction action) {
      indexEntry = modifier;
      return fill(tool, resource, action);
    }

    @Override
    public FluidStack drain(IToolStackView tool, ModifierEntry modifier, FluidStack resource, FluidAction action) {
      indexEntry = modifier;
      return drain(tool, resource, action);
    }

    @Override
    public FluidStack drain(IToolStackView tool, ModifierEntry modifier, int maxDrain, FluidAction action) {
      indexEntry = modifier;
      return drain(tool, maxDrain, action);
    }
  }

  /**
   * Native fluid handler of the tool in an item access, the form of {@code Capabilities.Fluid.ITEM}.
   * <p>
   * PORT 1.21.10 (transfer step 1): reads go through the tool's fluid modifier hooks on a detached copy of the tool; every change runs the
   * hooks on a detached one item copy, then exchanges the accessed items for the updated tool through the access, so it is transactional
   * and a simulation leaves the tool untouched. Amounts are totals over the accessed items, which change together. Like the legacy
   * {@code fill}/{@code drain}, inserts and extracts go to the first tanks accepting or holding the fluid whatever the index, the index only
   * matters to read.
   */
  public record ItemHandler(ItemAccess access) implements ResourceHandler<FluidResource> {
    /** Creates a view of a detached copy of the accessed tool */
    private ToolFluidCapability view() {
      return new ToolFluidCapability(ToolStack.copyFrom(access.getResource().toStack()));
    }

    @Override
    public int size() {
      return access.getResource().isEmpty() ? 0 : view().getTanks();
    }

    @Override
    public FluidResource getResource(int index) {
      Objects.checkIndex(index, size());
      return FluidResource.of(view().getFluidInTank(index));
    }

    @Override
    public long getAmountAsLong(int index) {
      Objects.checkIndex(index, size());
      return (long) access.getAmount() * view().getFluidInTank(index).getAmount();
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
      Objects.checkIndex(index, size());
      ToolFluidCapability view = view();
      if (!resource.isEmpty() && !view.isFluidValid(index, resource.toStack(1))) {
        return 0;
      }
      return (long) access.getAmount() * view.getTankCapacity(index);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
      Objects.checkIndex(index, size());
      return view().isFluidValid(index, resource.toStack(1));
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
      Objects.checkIndex(index, size());
      TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
      int count = access.getAmount();
      int perItem = count == 0 ? 0 : amount / count;
      if (perItem <= 0) {
        return 0;
      }
      ItemStack copy = access.getResource().toStack();
      ToolStack tool = ToolStack.copyFrom(copy);
      int filled = new ToolFluidCapability(tool).fill(resource.toStack(perItem), FluidAction.EXECUTE);
      if (filled <= 0) {
        return 0;
      }
      return filled * access.exchange(ItemResource.of(tool.updateStack(copy)), count, transaction);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
      Objects.checkIndex(index, size());
      TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
      int count = access.getAmount();
      int perItem = count == 0 ? 0 : amount / count;
      if (perItem <= 0) {
        return 0;
      }
      ItemStack copy = access.getResource().toStack();
      ToolStack tool = ToolStack.copyFrom(copy);
      int drained = new ToolFluidCapability(tool).drain(resource.toStack(perItem), FluidAction.EXECUTE).getAmount();
      if (drained <= 0) {
        return 0;
      }
      return drained * access.exchange(ItemResource.of(tool.updateStack(copy)), count, transaction);
    }
  }

  public static class Provider implements IToolCapabilityProvider {
    @SuppressWarnings("unused")
    public Provider(ItemStack stack, Supplier<? extends IToolStackView> toolStack) {}

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
      if (cap == Capabilities.Fluid.ITEM && access != null && tool.getVolatileData().getInt(TOTAL_TANKS) > 0) {
        return (T) new ItemHandler(access);
      }
      return null;
    }
  }
}
