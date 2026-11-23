package slimeknights.tconstruct.library.tools.capability;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Capability provider for tool stacks. Resolves the proper capability instance for a tool item.
 * <p>
 * Under NeoForge, item capabilities are registered centrally on {@code RegisterCapabilitiesEvent} (M3).
 * Each modifiable tool item registers its item capabilities (energy, fluid handler, item handler, block item provider)
 * to delegate to {@link #getCapability(ItemStack, ItemCapability)}, which walks the registered providers.
 */
public class ToolCapabilityProvider {
  private static final List<BiFunction<ItemStack,Supplier<? extends IToolStackView>,IToolCapabilityProvider>> PROVIDER_CONSTRUCTORS = new ArrayList<>();

  private final ItemStack stack;
  private final ToolStack tool;
  private final List<IToolCapabilityProvider> providers;

  public ToolCapabilityProvider(ItemStack stack) {
    this.stack = stack;
    this.tool = ToolStack.from(stack);
    this.providers = PROVIDER_CONSTRUCTORS.stream().map(con -> con.apply(stack, () -> this.tool)).filter(Objects::nonNull).collect(Collectors.toList());
  }

  /**
   * Resolves the given capability for this tool stack.
   * @param cap  Capability to resolve
   * @return  Capability value, or null if not present
   */
  @Nullable
  public <T> T getCapability(ItemCapability<T, ?> cap) {
    return getCapability(cap, null);
  }

  /**
   * Resolves the given capability for this tool stack.
   * @param cap     Capability to resolve
   * @param access  Access to the tool item, required by the transfer capabilities ({@code Capabilities.Fluid/Item/Energy.ITEM}),
   *                which change the tool by exchanging it through the access. Null for capabilities without an item access context.
   * @return  Capability value, or null if not present
   */
  @Nullable
  public <T> T getCapability(ItemCapability<T, ?> cap, @Nullable ItemAccess access) {
    // clear the tool cache, as it may have changed since the last time a cap was fetched
    ToolStack toolStack = tool;
    toolStack.refreshTag(stack);
    // return the first successful provider
    for (IToolCapabilityProvider provider : providers) {
      provider.clearCache();
      T result = provider.getCapability(toolStack, cap, access);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  /**
   * Resolves the given capability for the given tool stack, building a fresh provider list.
   * Convenience for the central {@code RegisterCapabilitiesEvent} registration.
   */
  @Nullable
  public static <T> T getCapability(ItemStack stack, ItemCapability<T, ?> cap) {
    return new ToolCapabilityProvider(stack).getCapability(cap);
  }

  /**
   * Resolves a transfer capability for the tool in the given access, for a central {@code event.registerItem(Capabilities.Fluid.ITEM,
   * (stack, access) -> ToolCapabilityProvider.getCapability(access, Capabilities.Fluid.ITEM), ...)} registration.
   * PORT 1.21.10: note no tool item registers these capabilities yet, as in 1.21.8.
   */
  @Nullable
  public static <T> T getCapability(ItemAccess access, ItemCapability<T, ItemAccess> cap) {
    return new ToolCapabilityProvider(access.getResource().toStack(access.getAmount())).getCapability(cap, access);
  }

  /** Registers a tool capability provider constructor. Every new tool will call this constructor to create your provider.
   * Is it valid for this constructor to return null, just note that it will not be called a second time if the tools state changes. Thus you should avoid conditioning on anything other than item type */
  public static void register(BiFunction<ItemStack,Supplier<? extends IToolStackView>,IToolCapabilityProvider> constructor) {
    PROVIDER_CONSTRUCTORS.add(constructor);
  }

  /** Interface to get a capability on a tool */
  @FunctionalInterface
  public interface IToolCapabilityProvider {
    /** Gets a capability on the given tool, or null if this provider does not handle the requested capability */
    @Nullable
    <T> T getCapability(IToolStackView tool, ItemCapability<T, ?> cap);

    /**
     * Gets a capability on the given tool with access to the tool item, or null if this provider does not handle the requested capability.
     * Transfer capabilities ({@code Capabilities.Fluid/Item/Energy.ITEM}) need the access, as their handlers change the tool by exchanging it.
     * @param access  Access to the tool item, null if the capability has no item access context
     */
    @Nullable
    default <T> T getCapability(IToolStackView tool, ItemCapability<T, ?> cap, @Nullable ItemAccess access) {
      return getCapability(tool, cap);
    }

    /** Called to clear the cache of the provider */
    default void clearCache() {}
  }
}
