package slimeknights.tconstruct.library.tools.nbt;

import com.google.common.collect.MapMaker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent.UpdateCause;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.VisibleForTesting;
import slimeknights.tconstruct.TConstruct;

import org.jspecify.annotations.Nullable;
import java.util.Map;

/**
 * Lazy replacement for {@code Item#verifyComponentsAfterLoad}, which vanilla removed in 1.21.9 without a replacement. Tool stacks used to be
 * verified (material redirects resolved, stats rebuilt, derived components synced) whenever they were decoded or copied; now they are verified on
 * their first server read after each data load, see {@link ToolStack#from(net.minecraft.world.item.ItemStack)}.
 * <p>
 * The datapack "generation" is a map from each {@link CustomData} instance verified since the last server data load to its verified result: the
 * instance itself when verification changed nothing, else the new instance given to the stack that was read. It is replaced on every load, so
 * every tool is verified again once after a {@code /reload}, and a fresh server starts empty, so stacks loaded from disk are verified once per
 * session. Other holders of an instance (copies or splits made before the verification, temporary stacks of an item resource, cached command
 * inputs) take the result on their next server read instead of verifying again, so they end up with equal data and keep stacking. Tool code that
 * stores its data through {@link ToolStack} marks the new instance as verified, as it was built from the current data.
 * <p>
 * The map is identity based and weak (keys and values), so it neither changes the stacks (no stamp in the tool data, which would keep stackable
 * tools from stacking with older copies and resync every stack once per session) nor keeps them alive.
 * <p>
 * Verification only runs on the logical server thread: it changes the stack, which must never happen on the client or render thread. The client
 * resolves material redirects in memory when reading materials; stats of a stack the server never read (a chest it never opened, an item frame)
 * may show stale on the client until the server reads that stack.
 */
@EventBusSubscriber(modid = TConstruct.MOD_ID)
public final class ToolDataVerifier {
  /** Set once the server loaded its data for the first time, before that there is nothing to verify against */
  private static volatile boolean dataLoaded = false;
  /** Custom data instances verified against the current data, mapped to their verified result */
  private static volatile Map<CustomData, CustomData> verified = newGeneration();
  /** Set while a verification runs on the server thread, so reads made by the verification itself do not start another one */
  private static boolean verifying = false;

  private ToolDataVerifier() {}

  /** Creates an empty weak identity map of verified data */
  private static Map<CustomData, CustomData> newGeneration() {
    return new MapMaker().weakKeys().weakValues().makeMap();
  }

  /** Starts a new generation once the server finished loading its data, after the material and tool definition loaders */
  @SubscribeEvent
  static void onTagsUpdated(TagsUpdatedEvent event) {
    if (event.getUpdateCause() == UpdateCause.SERVER_DATA_LOAD) {
      startGeneration();
    }
  }

  /** Starts a new generation, so every stack is verified again on its next server read. Called on server data load; exposed for tests. */
  @VisibleForTesting
  public static void startGeneration() {
    verified = newGeneration();
    dataLoaded = true;
  }

  /** Checks if we are on the logical server thread */
  private static boolean isServerThread() {
    MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
    return server != null && server.isSameThread();
  }

  /**
   * Checks if stacks may be verified on this thread right now: the data is loaded, we are on the logical server thread, and no verification is
   * running already. Used directly by verifications that need no generation, such as the material redirects of parts.
   */
  public static boolean canVerify() {
    return dataLoaded && !verifying && isServerThread();
  }

  /**
   * Gets the verified result of the given data in the current generation: the same instance if it is verified and unchanged, the instance to use
   * instead if its verification changed it, or null if it is not verified yet. Only meaningful when {@link #canVerify()}.
   */
  @Nullable
  public static CustomData getVerified(CustomData data) {
    return verified.get(data);
  }

  /** Marks the given data as verified against the current data, if on the logical server thread. Client built stacks are verified on the server. */
  public static void markVerified(CustomData data) {
    if (dataLoaded && isServerThread()) {
      verified.put(data, data);
    }
  }

  /** Marks a copy of the given data as verified if the original is verified and unchanged */
  public static void inheritVerification(CustomData original, CustomData copy) {
    if (dataLoaded && isServerThread() && verified.get(original) == original) {
      verified.put(copy, copy);
    }
  }

  /**
   * Runs a verification of the given data. The data is marked first so a failing verification is not retried on every read, and nested reads
   * see a verification in progress.
   * @param data          Data to verify
   * @param verification  Verification, returns the verified data to use instead, or {@code data} if unchanged
   */
  public static void verify(CustomData data, java.util.function.Supplier<CustomData> verification) {
    Map<CustomData, CustomData> generation = verified;
    generation.put(data, data);
    verifying = true;
    try {
      CustomData result = verification.get();
      if (result != data) {
        generation.put(result, result);
        generation.put(data, result);
      }
    } finally {
      verifying = false;
    }
  }
}
