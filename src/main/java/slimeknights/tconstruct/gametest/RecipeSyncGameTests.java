package slimeknights.tconstruct.gametest;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.FireworkStarRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.connection.ConnectionType;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.recipe.RecipeLookupPopulator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
      String id = holder.id().identifier().toString();
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
        String unordered = UNORDERED_STREAM_CODECS.get(holder.value().getSerializer());
        if (unordered != null) {
          // only the order of the map entries may change: same bytes, possibly permuted
          if (!Arrays.equals(sortedCopy(first), sortedCopy(second))) {
            TConstruct.LOG.error("Synced recipe {} re-encodes with different content:\n  first:  {}\n  second: {}", id, ByteBufUtil.hexDump(first), ByteBufUtil.hexDump(second));
            failures.put(id, "re-encoding has different content (" + first.length + " vs " + second.length + " bytes; entry order is exempt: " + unordered + ")");
          }
        } else if (!Arrays.equals(first, second)) {
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

  /**
   * Recipe types registered by Tinkers that are deliberately not sent to clients, each with the reason. Empty: every Tinkers type is read
   * client side (JEI categories, book pages, GUI block entities, recipe packets). A new recipe type fails
   * {@link #client_synced_recipe_types_complete} until it is either added to {@link RecipeLookupPopulator}'s client synced types or listed here.
   */
  private static final Map<Identifier, String> SERVER_ONLY = Map.of();
  /**
   * Recipe types of other namespaces that Tinkers client code reads from the synced recipes, with the reader. Vanilla only syncs recipe
   * displays since 1.21.2, so these must be requested like the Tinkers types.
   */
  private static final Map<RecipeType<?>, String> CLIENT_READ_FOREIGN = Map.of(
    RecipeType.CRAFTING, "book crafting pages (Mantle ContentCrafting, Tinkers ContentTool) and the crafting station");
  /** Client synced Tinkers recipe types allowed to have no recipe in the default datapacks, each with the reason. */
  private static final Map<Identifier, String> NO_RECIPES_EXPECTED = Map.of(
    TConstruct.getResource("molding_basin"), "no shipped recipe molds in a basin (datagen only builds table molding for sand casts); the type serves datapacks and addons, and JEI lists it with table molding");

  /** Recipe types the server actually requests for clients: fires the datapack sync event (with no players) and reads what the listeners asked for */
  private static Set<RecipeType<?>> sentRecipeTypes(MinecraftServer server) {
    OnDatapackSyncEvent event = new OnDatapackSyncEvent(server.getPlayerList(), null);
    NeoForge.EVENT_BUS.post(event);
    return Set.copyOf(event.getRecipeTypesToSend());
  }

  private static String typeName(RecipeType<?> type) {
    Identifier id = BuiltInRegistries.RECIPE_TYPE.getKey(type);
    return id == null ? type.toString() : id.toString();
  }

  /**
   * Every Tinkers recipe type must reach the client (the alloying / entity melting / severing recipes were missing from JEI on servers from
   * 1.21.4 until a hotfix, while {@link #synced_recipes_stream_codec_round_trip} only checks the bytes of what is synced). Fires the real
   * {@link OnDatapackSyncEvent} and requires: each {@code tconstruct} recipe type is sent unless listed in {@link #SERVER_ONLY}; each
   * foreign type Tinkers reads on the client ({@link #CLIENT_READ_FOREIGN}) is sent; those sent types are exactly
   * {@link RecipeLookupPopulator#clientSyncedTypes()}, which the codec round trip covers; and each sent Tinkers type has recipes in the
   * default datapacks (so the client actually receives some), unless listed in {@link #NO_RECIPES_EXPECTED}.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void client_synced_recipe_types_complete(GameTestHelper helper) {
    MinecraftServer server = helper.getLevel().getServer();
    Set<RecipeType<?>> sent = sentRecipeTypes(server);
    List<RecipeType<?>> declared = RecipeLookupPopulator.clientSyncedTypes();
    List<String> failures = new ArrayList<>();
    int tinkerTypes = 0;
    for (Identifier id : new TreeSet<>(BuiltInRegistries.RECIPE_TYPE.keySet())) {
      if (!TConstruct.MOD_ID.equals(id.getNamespace())) {
        continue;
      }
      tinkerTypes++;
      RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.getValue(id);
      boolean isSent = sent.contains(type);
      if (SERVER_ONLY.containsKey(id)) {
        if (isSent) {
          failures.add(id + " is listed as SERVER_ONLY but is sent to clients; remove it from SERVER_ONLY");
        }
        continue;
      }
      if (!isSent) {
        failures.add(id + " is not sent to clients: add it to RecipeLookupPopulator's CLIENT_SYNCED_TYPES, or list it in SERVER_ONLY with the reason");
        continue;
      }
      long recipes = server.getRecipeManager().getRecipes().stream().filter(holder -> holder.value().getType() == type).count();
      if (recipes == 0 && !NO_RECIPES_EXPECTED.containsKey(id)) {
        failures.add(id + " is sent but has no recipes in the default datapacks (datagen or loading lost them?); list it in NO_RECIPES_EXPECTED if intended");
      } else if (recipes > 0 && NO_RECIPES_EXPECTED.containsKey(id)) {
        failures.add(id + " has " + recipes + " recipe(s) but is listed in NO_RECIPES_EXPECTED; remove it from there");
      }
    }
    CLIENT_READ_FOREIGN.forEach((type, reader) -> {
      if (!sent.contains(type)) {
        failures.add(typeName(type) + " is not sent to clients, but is read there by " + reader);
      }
    });
    // the Tinkers types sent and the foreign types Tinkers reads must be what the stream codec round trip covers; other vanilla types
    // (smelting, stonecutting, ...) are requested by other listeners and are not ours to cover
    for (RecipeType<?> type : sent) {
      if ((TConstruct.MOD_ID.equals(typeName(type).split(":")[0]) || CLIENT_READ_FOREIGN.containsKey(type)) && !declared.contains(type)) {
        failures.add(typeName(type) + " is sent to clients but missing from RecipeLookupPopulator.clientSyncedTypes(), so synced_recipes_stream_codec_round_trip does not cover it");
      }
    }
    for (RecipeType<?> type : declared) {
      if (!sent.contains(type)) {
        failures.add(typeName(type) + " is declared in RecipeLookupPopulator.clientSyncedTypes() but the datapack sync does not send it");
      }
    }
    if (tinkerTypes == 0) {
      failures.add("no tconstruct recipe types registered");
    }
    TConstruct.LOG.info("Client synced recipe types: {} tconstruct type(s), sent: {}", tinkerTypes, sent.stream().map(RecipeSyncGameTests::typeName).sorted().toList());
    helper.assertTrue(failures.isEmpty(), Component.literal(failures.size() + " recipe sync problem(s):\n" + String.join("\n", failures)));
    helper.succeed();
  }

  /**
   * Serializers whose stream codec decodes a map into a hash map, so a decoded recipe re-encodes the same entries in another order. Their
   * round trip only requires the same bytes in any order (a lost or changed entry still fails). Vanilla only; each with the reason.
   */
  private static final Map<RecipeSerializer<?>, String> UNORDERED_STREAM_CODECS = Map.of(
    FireworkStarRecipe.SERIALIZER, "26.1 vanilla FireworkStarRecipe.STREAM_CODEC decodes the shapes into a HashMap keyed by an enum (identity hash order), "
      + "while the JSON codec keeps the file order; the map's content and the recipe's behaviour are the same");

  private static byte[] sortedCopy(byte[] bytes) {
    byte[] sorted = bytes.clone();
    Arrays.sort(sorted);
    return sorted;
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
