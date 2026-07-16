package slimeknights.tconstruct.library.recipe.fuel;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;
import slimeknights.tconstruct.TConstruct;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Class handling a recipe cache for fuel recipes, since any given entity type has one recipe
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MeltingFuelLookup {
  /** Dummy fuel instance sine caches don't support caching null */
  private static final MeltingFuel EMPTY = new MeltingFuel(Objects.requireNonNull(ResourceLocation.tryBuild("missingno", "missingno")), FluidIngredient.EMPTY, 0, 0, 0);
  /** Temperature for solid fuels in the heater */
  private static MeltingFuel SOLID = EMPTY;
  /** List of all recipes */
  private static final List<MeltingFuel> RECIPES = new ArrayList<>();
  /** Mapping from fluid to fuel */
  private static final Map<Fluid,MeltingFuel> CACHE = new HashMap<>();
  /** Logic to fill the cache */
  private static final Function<Fluid,MeltingFuel> LOOKUP = fluid -> {
    for (MeltingFuel recipe : RECIPES) {
      if (recipe.matches(fluid)) {
        return recipe;
      }
    }
    return EMPTY;
  };

  /** Clears the lookup; called by RecipeLookupPopulator before repopulating from the RecipeManager. */
  public static void clear() {
    SOLID = EMPTY;
    RECIPES.clear();
    CACHE.clear();
  }

  /**
   * Adds a melting fuel to the lookup. Called by {@link slimeknights.tconstruct.common.recipe.RecipeLookupPopulator}
   * once per FUEL recipe on each reload, after {@link #clear()} has emptied the lookup.
   * @param fuel   Fuel
   */
  public static void addFuel(MeltingFuel fuel) {
    // skip empty fuel
    if (fuel.getRate() == 0) {
      return;
    }
    if (fuel.getInput() != FluidIngredient.EMPTY) {
      RECIPES.add(fuel);
    } else if (SOLID == EMPTY) {
      SOLID = fuel;
    } else if (SOLID.getTemperature() != fuel.getTemperature() || SOLID.getRate() != fuel.getRate()) {
      // Population is a single clear-then-populate pass (RecipeLookupPopulator clears before adding), so a second
      // solid fuel with different values here is a genuine datapack conflict (two distinct solid-fuel recipes), not
      // the historical integrated-server double-construction false positive.
      TConstruct.LOG.warn("Multiple fuel recipes for solid fuel. This usually indicates a datapack error and may cause desyncs. Original temperature {} rate {}, latest temperature {} rate {}", SOLID.getTemperature(), SOLID.getRate(), fuel.getTemperature(), fuel.getRate());
    }
  }

  /** Checks if the given fluid is a fuel */
  public static boolean isFuel(Fluid fluid) {
    return CACHE.computeIfAbsent(fluid, LOOKUP) != EMPTY;
  }

  /** Gets the properties for solid fuel */
  public static MeltingFuel getSolid() {
    return SOLID;
  }

  /**
   * Gets the recipe for the given fluid
   * @param fluid   Fluid found
   * @return  Recipe, or null if no recipe for this type
   */
  @Nullable
  public static MeltingFuel findFuel(Fluid fluid) {
    MeltingFuel recipe = CACHE.computeIfAbsent(fluid, LOOKUP);
    if (recipe == EMPTY) {
      return null;
    }
    return recipe;
  }
}
