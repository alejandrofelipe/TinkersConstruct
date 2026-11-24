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

import java.util.Collections;
import java.util.Set;

/**
 * Lazy replacement for {@code Item#verifyComponentsAfterLoad}, which vanilla removed in 1.21.9 without a replacement. Tool stacks used to be
 * verified (material redirects resolved, stats rebuilt, derived components synced) whenever they were decoded; now they are verified on their
 * first read after each datapack load, see {@link ToolStack#from(net.minecraft.world.item.ItemStack)}.
 * <p>
 * The datapack "generation" is the set of {@link CustomData} instances verified since the last server data load: it is replaced on every load,
 * so every tool is verified again once after a {@code /reload}, and a fresh server starts empty, so stacks loaded from disk are verified once per
 * session like the old hook did. The set is identity based and weak, so it neither changes the stacks (no stamp in the tool data, which would
 * keep stackable tools from stacking with older copies and resync every stack once per session) nor keeps them alive. Stack copies share the
 * component instance, so they share the verification; a stack decoded from disk or the network gets a new instance and is verified on its first
 * read. Tool code that stores its data through {@link ToolStack} marks the new instance as verified, as it was built from the current data.
 * <p>
 * Verification only runs on the logical server thread: it changes the stack, which must never happen on the client or render thread.
 */
@EventBusSubscriber(modid = TConstruct.MOD_ID)
public final class ToolDataVerifier {
  /** Set once the server loaded its data for the first time, before that there is nothing to verify against */
  private static volatile boolean dataLoaded = false;
  /** Custom data instances verified against the current data */
  private static volatile Set<CustomData> verified = newGeneration();
  /** Set while a verification runs on the server thread, so reads made by the verification itself do not start another one */
  private static boolean verifying = false;

  private ToolDataVerifier() {}

  /** Creates an empty weak identity set of verified data */
  private static Set<CustomData> newGeneration() {
    return Collections.newSetFromMap(new MapMaker().weakKeys().makeMap());
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

  /** Checks if the given data needs to be verified on this thread right now */
  public static boolean needsVerification(CustomData data) {
    return canVerify() && !verified.contains(data);
  }

  /** Marks the given data as verified against the current data, if on the logical server thread. Client built stacks are verified on the server. */
  public static void markVerified(CustomData data) {
    if (dataLoaded && isServerThread()) {
      verified.add(data);
    }
  }

  /**
   * Runs a verification of the given data. The data is marked first so a failing verification is not retried on every read, and nested reads
   * see a verification in progress.
   */
  public static void verify(CustomData data, Runnable verification) {
    verified.add(data);
    verifying = true;
    try {
      verification.run();
    } finally {
      verifying = false;
    }
  }
}
