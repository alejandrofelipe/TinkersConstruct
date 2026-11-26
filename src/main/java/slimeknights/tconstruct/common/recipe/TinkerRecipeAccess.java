package slimeknights.tconstruct.common.recipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;
import java.util.Collection;
import java.util.Optional;

/**
 * Side-agnostic recipe access for 1.21.4+. {@code Level#getRecipeManager()} was removed and the client no longer owns a
 * {@link net.minecraft.world.item.crafting.RecipeManager}; it instead receives a {@link RecipeMap} through
 * {@link net.neoforged.neoforge.client.event.RecipesReceivedEvent}, which only contains the recipe types the server
 * opted into via {@link net.neoforged.neoforge.event.OnDatapackSyncEvent#sendRecipes}. This helper returns the server's
 * live recipe map on the logical server (including singleplayer's integrated server) and the last synced map on a remote
 * client, so GUI block entities, container wrappers and recipe packets resolve recipes identically on both sides.
 *
 * <p>The recipe types synced to the client are requested in {@link RecipeLookupPopulator#onDatapackSync}.
 */
public final class TinkerRecipeAccess {
  private TinkerRecipeAccess() {}

  /** Last recipe map synced to the client; empty before the first sync and on a dedicated server. */
  private static RecipeMap clientRecipeMap = RecipeMap.EMPTY;

  /** Stores the recipe map synced from the server; called on the client from {@link RecipeLookupPopulator#onRecipesUpdated}. */
  public static void setClientRecipeMap(RecipeMap map) {
    clientRecipeMap = map;
  }

  /** Gets the recipe map for the given level: the server manager's map on the logical server, the synced map on a remote client. */
  public static RecipeMap getRecipeMap(@Nullable Level level) {
    if (level != null) {
      MinecraftServer server = level.getServer();
      if (server != null) {
        return server.getRecipeManager().recipeMap();
      }
    }
    return clientRecipeMap;
  }

  /**
   * Finds the first recipe of the given type matching the input. Behaves like the 1.20.1 recipe lookup, with no empty-input
   * short-circuit: vanilla's {@link RecipeMap#getRecipesFor} (and already 1.21.1's {@code RecipeManager#getRecipeFor}) returns
   * nothing when {@link RecipeInput#isEmpty()}, i.e. when every item slot is empty, but castless casting recipes (an empty
   * basin or table, where the fluid is the input) have an empty item input. Each recipe's own {@code matches} decides instead.
   */
  public static <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getRecipeFor(RecipeType<T> type, I input, Level level) {
    return getRecipeMap(level).byType(type).stream().filter(holder -> holder.value().matches(input, level)).findFirst();
  }

  /** Gets all recipes of the given type, mirroring the removed {@code RecipeManager#getAllRecipesFor}. */
  public static <I extends RecipeInput, T extends Recipe<I>> Collection<RecipeHolder<T>> byType(RecipeType<T> type, Level level) {
    return getRecipeMap(level).byType(type);
  }

  /** Looks up a recipe holder by id, mirroring the removed {@code RecipeManager#byKey(Identifier)}. */
  public static Optional<RecipeHolder<?>> byKey(Level level, Identifier id) {
    return Optional.ofNullable(getRecipeMap(level).byKey(ResourceKey.create(Registries.RECIPE, id)));
  }

  /** Looks up a recipe of a specific class by id, mirroring {@code RecipeHelper#getRecipe}. */
  public static <C extends Recipe<?>> Optional<C> getRecipe(Level level, Identifier id, Class<C> clazz) {
    return byKey(level, id).map(RecipeHolder::value).filter(clazz::isInstance).map(clazz::cast);
  }
}
