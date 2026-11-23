package slimeknights.tconstruct.gametest;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.network.connection.ConnectionType;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.recipe.RecipeLookupPopulator;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Network round trip of every recipe the server syncs to clients ({@link RecipeLookupPopulator#clientSyncedTypes()}).
 * Singleplayer hands the recipe payload to the client in memory, so the stream codecs only run on a dedicated server;
 * this encodes each recipe with {@link RecipeHolder#STREAM_CODEC} (what NeoForge's recipe content payload uses), decodes
 * it, and re-encodes the decoded recipe, which must be byte-identical (recipes don't implement equals).
 */
public class RecipeSyncGameTests {
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void synced_recipes_stream_codec_round_trip(GameTestHelper helper) {
    RegistryAccess access = helper.getLevel().registryAccess();
    List<RecipeType<?>> types = RecipeLookupPopulator.clientSyncedTypes();
    Map<String, String> failures = new LinkedHashMap<>();
    int checked = 0;
    for (RecipeHolder<?> holder : helper.getLevel().getServer().getRecipeManager().getRecipes()) {
      if (!types.contains(holder.value().getType())) {
        continue;
      }
      checked++;
      String id = holder.id().location().toString();
      try {
        byte[] first = encode(access, holder);
        RegistryFriendlyByteBuf buffer = buffer(access, Unpooled.wrappedBuffer(first));
        RecipeHolder<?> decoded = RecipeHolder.STREAM_CODEC.decode(buffer);
        if (buffer.isReadable()) {
          failures.put(id, buffer.readableBytes() + " of " + first.length + " byte(s) left unread");
          continue;
        }
        if (decoded.value().getType() != holder.value().getType()) {
          failures.put(id, "decoded as type " + decoded.value().getType());
          continue;
        }
        byte[] second = encode(access, decoded);
        if (!Arrays.equals(first, second)) {
          TConstruct.LOG.error("Synced recipe {} re-encodes differently:\n  first:  {}\n  second: {}", id, ByteBufUtil.hexDump(first), ByteBufUtil.hexDump(second));
          failures.put(id,"re-encoding differs (" + first.length + " vs " + second.length + " bytes, first difference at byte " + Arrays.mismatch(first, second) + ")");
        }
      } catch (Throwable t) {
        TConstruct.LOG.error("Synced recipe {} fails the stream codec round trip", id, t);
        failures.put(id, t.toString());
      }
    }
    if (checked == 0) {
      helper.fail(Component.literal("no synced recipes found"));
      return;
    }
    if (!failures.isEmpty()) {
      helper.fail(Component.literal(failures.size() + " of " + checked + " synced recipe(s) fail the stream codec round trip: "
        + failures.entrySet().stream().map(entry -> entry.getKey() + " -> " + entry.getValue()).collect(Collectors.joining("; "))));
      return;
    }
    helper.succeed();
  }

  private static RegistryFriendlyByteBuf buffer(RegistryAccess access, ByteBuf buf) {
    // NEOFORGE: the connection type the integrated and dedicated server use with a NeoForge client
    return new RegistryFriendlyByteBuf(buf, access, ConnectionType.NEOFORGE);
  }

  private static byte[] encode(RegistryAccess access, RecipeHolder<?> holder) {
    RegistryFriendlyByteBuf buffer = buffer(access, Unpooled.buffer());
    try {
      RecipeHolder.STREAM_CODEC.encode(buffer, holder);
      return ByteBufUtil.getBytes(buffer);
    } finally {
      buffer.release();
    }
  }
}
