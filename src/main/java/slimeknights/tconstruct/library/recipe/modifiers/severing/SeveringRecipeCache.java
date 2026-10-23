package slimeknights.tconstruct.library.recipe.modifiers.severing;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import slimeknights.tconstruct.common.recipe.RecipeCacheInvalidator;
import slimeknights.tconstruct.common.recipe.TinkerRecipeAccess;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class handling a recipe cache for entity melting recipes, since any given entity type has one recipe
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SeveringRecipeCache {
  private static final Map<EntityType<?>,List<SeveringRecipe>> CACHE = new HashMap<>();

  static {
    RecipeCacheInvalidator.addReloadListener(client -> CACHE.clear());
  }

  /**
   * Gets the recipe for the given type
   * @param level  Level, used for side-agnostic recipe access
   * @param type   Entity type
   * @return  Recipe, or null if no recipe for this type
   */
  public static List<SeveringRecipe> findRecipe(Level level, EntityType<?> type) {
    if (CACHE.containsKey(type)) {
      return CACHE.get(type);
    }

    // find all severing recipes for the entity
    List<SeveringRecipe> list = new ArrayList<>();
    for (RecipeHolder<SeveringRecipe> holder : TinkerRecipeAccess.byType(TinkerRecipeTypes.SEVERING.get(), level)) {
      SeveringRecipe recipe = holder.value();
      if (recipe.matches(type)) {
        list.add(recipe);
      }
    }
    if (list.isEmpty()) {
      list = Collections.emptyList();
    }

    // cache nothing was found
    CACHE.put(type, list);
    return list;
  }
}
