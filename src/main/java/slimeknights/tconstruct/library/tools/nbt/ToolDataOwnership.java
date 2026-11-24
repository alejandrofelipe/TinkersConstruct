package slimeknights.tconstruct.library.tools.nbt;

import com.google.common.collect.MapMaker;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Tracks which stack may change the tag of a tool's {@link CustomData} in place.
 * <p>
 * {@link ToolStack} reads and writes the live tag of the custom data component for speed and so tools bound to the same stack see each other's
 * changes. Up to 1.21.8 every stack had its own tag, as {@code Item#verifyComponentsAfterLoad} gave a fresh component to each stack built by
 * {@code ItemStack#copy()} or decoding. Since 1.21.9 copies share the component instance, so changing the tag in place would also change every
 * copy, such as the equipment snapshot {@code LivingEntity} compares against to fire equipment changes and update attributes.
 * <p>
 * A stack owns a custom data instance once {@link ToolStack} gave it a fresh one. Building a stack around the components of a stack that owns its
 * data (copy, split, item resource) gives the new stack a fresh copy that it owns (see {@code ItemStackMixin}), like 1.21.8 did, so the original
 * stays the owner and tools bound to it for a long time (menus, thrown tools) keep writing to the stack. Unowned data (decoded, never bound) stays
 * shared until a tool binds one of its holders, which then gets its own copy. Keys and values are weak and compared by identity.
 * <p>
 * Stacks only get their own data on the logical server thread or the client main thread: on other threads (background tooltip search trees,
 * workers) a bound tool reads a detached copy instead, see {@link #canOwnOnThisThread()}.
 * <p>
 * Not covered: component patches taken without copying the stack ({@code ItemStack#getComponentsPatch}, {@code applyComponents}) still share the
 * instance with its owner.
 */
public final class ToolDataOwnership {
  private static final Map<CustomData, ItemStack> OWNERS = new MapMaker().weakKeys().weakValues().makeMap();
  /** Client main thread, set during client setup; null on a dedicated server */
  @Nullable
  private static volatile Thread clientThread;

  private ToolDataOwnership() {}

  /** Sets the client main thread, called from client setup */
  public static void setClientThread(Thread thread) {
    clientThread = thread;
  }

  /**
   * Checks if stacks may get their own data on this thread: the logical server thread or the client main thread. Before either exists (startup,
   * unit tests) there is a single game thread, so any thread may.
   */
  public static boolean canOwnOnThisThread() {
    Thread client = clientThread;
    MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
    if (client == null && server == null) {
      return true;
    }
    return Thread.currentThread() == client || (server != null && server.isSameThread());
  }

  /** Checks if the stack owns the given data, so a tool bound to it may change the tag in place */
  public static boolean owns(ItemStack stack, CustomData data) {
    return OWNERS.get(data) == stack;
  }

  /** Makes the stack the owner of the given data, which must be a fresh instance only the stack holds */
  public static void claim(ItemStack stack, CustomData data) {
    OWNERS.put(data, stack);
  }

  /**
   * Called by the ItemStack mixin when a stack of a modifiable item is built around a component map (copy, split, item resource, decoding):
   * if another stack owns the custom data, the new stack gets its own copy.
   */
  public static void onStackBuilt(ItemStack stack, PatchedDataComponentMap components) {
    CustomData data = components.get(DataComponents.CUSTOM_DATA);
    if (data != null && OWNERS.get(data) != null) {
      CustomData fresh = CustomData.of(data.tag);
      components.set(DataComponents.CUSTOM_DATA, fresh);
      claim(stack, fresh);
      ToolDataVerifier.inheritVerification(data, fresh);
    }
  }
}
