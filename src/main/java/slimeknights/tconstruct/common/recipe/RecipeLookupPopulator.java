package slimeknights.tconstruct.common.recipe;

import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.casting.CastingRecipeLookup;
import slimeknights.tconstruct.library.recipe.fuel.MeltingFuelLookup;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import slimeknights.tconstruct.library.recipe.melting.MeltingRecipeLookup;
import slimeknights.tconstruct.library.recipe.modifiers.ModifierRecipeLookup;

import java.util.Collection;

/**
 * Populates static recipe lookups from the loaded recipes at reload, replacing recipe-constructor
 * self-registration. Clear-then-populate is idempotent: under an integrated server the client
 * ({@link RecipesReceivedEvent}) and server ({@link ServerStartedEvent}/{@link OnDatapackSyncEvent}) both populate the
 * shared static lookups, but each clears first, so the historical double-construction no longer produces duplicates.
 *
 * <p>1.21.4: {@code RecipeManager#getAllRecipesFor} was removed, so recipes are iterated directly and filtered by type.
 * The client path now reads the synced {@link net.minecraft.world.item.crafting.RecipeMap} from {@link RecipesReceivedEvent};
 * note that the client only receives recipe types requested via {@link OnDatapackSyncEvent#sendRecipes}.
 */
public final class RecipeLookupPopulator {
  private RecipeLookupPopulator() {}

  /** Clears and repopulates every migrated lookup from the given recipe collection. */
  public static void populate(Collection<RecipeHolder<?>> recipes) {
    MeltingFuelLookup.clear();
    MeltingRecipeLookup.clear();
    CastingRecipeLookup.clear();
    ModifierRecipeLookup.clear();
    MaterialRecipeCache.clear();
    registerFrom(recipes, TinkerRecipeTypes.FUEL.get());
    registerFrom(recipes, TinkerRecipeTypes.MELTING.get());
    registerFrom(recipes, TinkerRecipeTypes.CASTING_TABLE.get());
    registerFrom(recipes, TinkerRecipeTypes.CASTING_BASIN.get());
    registerFrom(recipes, TinkerRecipeTypes.TINKER_STATION.get());
    registerFrom(recipes, TinkerRecipeTypes.DATA.get());
    registerFrom(recipes, TinkerRecipeTypes.MATERIAL.get());
    // future lookups migrate here
  }

  /**
   * Calls {@link ILookupRegistrar#registerLookups()} on every recipe of the given type that implements the interface.
   * {@code RecipeManager#getAllRecipesFor} was removed in 1.21.4, so the full recipe list is filtered by type instead.
   */
  private static void registerFrom(Collection<RecipeHolder<?>> recipes, RecipeType<?> type) {
    for (RecipeHolder<?> holder : recipes) {
      if (holder.value().getType() == type && holder.value() instanceof ILookupRegistrar registrar) {
        registrar.registerLookups();
      }
    }
  }

  /** Client: recipes synced from the server. */
  public static void onRecipesUpdated(RecipesReceivedEvent event) {
    populate(event.getRecipeMap().values());
  }

  /** Server: initial load (covers dedicated servers before any player joins, and the gametest server). */
  public static void onServerStarted(ServerStartedEvent event) {
    populate(event.getServer().getRecipeManager().getRecipes());
  }

  /** Server: /reload and player join. */
  public static void onDatapackSync(OnDatapackSyncEvent event) {
    RecipeManager manager = event.getPlayerList().getServer().getRecipeManager();
    populate(manager.getRecipes());
  }
}
