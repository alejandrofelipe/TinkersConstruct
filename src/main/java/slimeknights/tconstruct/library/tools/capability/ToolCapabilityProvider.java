package slimeknights.tconstruct.library.tools.capability;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.ItemCapability;
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
    // clear the tool cache, as it may have changed since the last time a cap was fetched
    ToolStack toolStack = tool;
    toolStack.refreshTag(stack);
    // return the first successful provider
    for (IToolCapabilityProvider provider : providers) {
      provider.clearCache();
      T result = provider.getCapability(toolStack, cap);
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

    /** Called to clear the cache of the provider */
    default void clearCache() {}
  }
}
