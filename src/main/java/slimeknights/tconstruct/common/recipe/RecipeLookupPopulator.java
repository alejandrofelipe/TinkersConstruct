package slimeknights.tconstruct.common.recipe;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.casting.CastingRecipeLookup;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialCastingLookup;
import slimeknights.tconstruct.library.recipe.fuel.MeltingFuelLookup;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import slimeknights.tconstruct.library.recipe.melting.MeltingRecipeLookup;
import slimeknights.tconstruct.library.recipe.modifiers.ModifierRecipeLookup;

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
    MeltingRecipeLookup.clear();
    CastingRecipeLookup.clear();
    ModifierRecipeLookup.clear();
    MaterialCastingLookup.clear();
    MaterialRecipeCache.clear();
    registerFrom(manager, TinkerRecipeTypes.FUEL.get());
    registerFrom(manager, TinkerRecipeTypes.MELTING.get());
    registerFrom(manager, TinkerRecipeTypes.CASTING_TABLE.get());
    registerFrom(manager, TinkerRecipeTypes.CASTING_BASIN.get());
    registerFrom(manager, TinkerRecipeTypes.TINKER_STATION.get());
    registerFrom(manager, TinkerRecipeTypes.DATA.get());
    registerFrom(manager, TinkerRecipeTypes.MATERIAL.get());
    // future lookups migrate here
  }

  /**
   * Calls {@link ILookupRegistrar#registerLookups()} on every recipe of the given type that implements the interface.
   * Takes a {@code RecipeType<?>} and casts to a concrete parameterization so it accepts both narrowly-typed recipe
   * types and the generic {@code DATA} type ({@code RecipeType<Recipe<?>>}, whose nested wildcard can't satisfy
   * {@link RecipeManager#getAllRecipesFor}'s {@code T extends Recipe<C>} bound). Only the interface is invoked on
   * matching recipes, so the erased element type is irrelevant.
   */
  @SuppressWarnings("unchecked")
  private static void registerFrom(RecipeManager manager, RecipeType<?> type) {
    for (RecipeHolder<Recipe<RecipeInput>> holder : manager.getAllRecipesFor((RecipeType<Recipe<RecipeInput>>) type)) {
      if (holder.value() instanceof ILookupRegistrar registrar) {
        registrar.registerLookups();
      }
    }
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
