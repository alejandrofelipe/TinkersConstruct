package slimeknights.tconstruct.common.recipe;

import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import slimeknights.mantle.recipe.helper.RecipeHelper;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.fuel.MeltingFuel;
import slimeknights.tconstruct.library.recipe.fuel.MeltingFuelLookup;

/**
 * Populates static recipe lookups from the {@link RecipeManager} at reload, replacing recipe-constructor
 * self-registration. Clear-then-populate is idempotent: under an integrated server the client
 * ({@link RecipesUpdatedEvent}) and server ({@link ServerStartedEvent}/{@link OnDatapackSyncEvent}) both populate the
 * shared static lookups, but each clears first, so the historical double-construction no longer produces duplicates.
 */
public final class RecipeLookupPopulator {
  private RecipeLookupPopulator() {}

  /** Clears and repopulates every migrated lookup from the given manager. */
  public static void populate(RecipeManager manager) {
    MeltingFuelLookup.clear();
    for (MeltingFuel fuel : RecipeHelper.getRecipes(manager, TinkerRecipeTypes.FUEL.get(), MeltingFuel.class)) {
      MeltingFuelLookup.addFuel(fuel);
    }
    // future lookups migrate here
  }

  /** Client: recipes synced from the server. */
  public static void onRecipesUpdated(RecipesUpdatedEvent event) {
    populate(event.getRecipeManager());
  }

  /** Server: initial load (covers dedicated servers before any player joins, and the gametest server). */
  public static void onServerStarted(ServerStartedEvent event) {
    populate(event.getServer().getRecipeManager());
  }

  /** Server: /reload and player join. */
  public static void onDatapackSync(OnDatapackSyncEvent event) {
    populate(event.getPlayerList().getServer().getRecipeManager());
  }
}
