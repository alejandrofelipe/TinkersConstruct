package slimeknights.tconstruct.common.recipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
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

  /** Finds the first recipe of the given type matching the input, mirroring the removed {@code RecipeManager#getRecipeFor}. */
  public static <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getRecipeFor(RecipeType<T> type, I input, Level level) {
    return getRecipeMap(level).getRecipesFor(type, input, level).findFirst();
  }

  /** Gets all recipes of the given type, mirroring the removed {@code RecipeManager#getAllRecipesFor}. */
  public static <I extends RecipeInput, T extends Recipe<I>> Collection<RecipeHolder<T>> byType(RecipeType<T> type, Level level) {
    return getRecipeMap(level).byType(type);
  }

  /** Looks up a recipe holder by id, mirroring the removed {@code RecipeManager#byKey(ResourceLocation)}. */
  public static Optional<RecipeHolder<?>> byKey(Level level, ResourceLocation id) {
    return Optional.ofNullable(getRecipeMap(level).byKey(ResourceKey.create(Registries.RECIPE, id)));
  }

  /** Looks up a recipe of a specific class by id, mirroring {@code RecipeHelper#getRecipe}. */
  public static <C extends Recipe<?>> Optional<C> getRecipe(Level level, ResourceLocation id, Class<C> clazz) {
    return byKey(level, id).map(RecipeHolder::value).filter(clazz::isInstance).map(clazz::cast);
  }
}
