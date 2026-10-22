package slimeknights.tconstruct.library.data.recipe;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.common.conditions.ICondition;

import javax.annotation.Nullable;

/**
 * Helper to add data components to a vanilla recipe result during datagen.
 *
 * <p>Ported from the 1.20 {@code FinishedRecipe}-based JSON wrapper: in 1.21 recipes are typed objects
 * whose result {@link ItemStack} carries data components directly, so this wraps a {@link RecipeOutput}
 * and applies a {@link DataComponentPatch} to the result of each recipe it forwards.
 */
public record CraftingNBTWrapper(RecipeOutput original, DataComponentPatch patch) implements RecipeOutput {
  @Override
  public Advancement.Builder advancement() {
    return original.advancement();
  }

  @Override
  public void accept(ResourceKey<Recipe<?>> id, Recipe<?> recipe, @Nullable AdvancementHolder advancement, ICondition... conditions) {
    original.accept(id, applyPatch(recipe), advancement, conditions);
  }

  @Override
  public void includeRootAdvancement() {
    original.includeRootAdvancement();
  }

  /** Applies the stored component patch to the recipe's result stack, where the recipe type exposes one */
  private Recipe<?> applyPatch(Recipe<?> recipe) {
    // vanilla shaped/shapeless expose a mutable result stack; apply the patch in place
    // 1.21.4: Recipe#getResultItem was removed; shaped/shapeless expose the result field directly
    ItemStack result = null;
    if (recipe instanceof ShapedRecipe shaped) {
      result = shaped.result;
    } else if (recipe instanceof ShapelessRecipe shapeless) {
      result = shapeless.result;
    }
    if (result != null && !result.isEmpty()) {
      result.applyComponents(patch);
    }
    return recipe;
  }

  /** Creates a wrapped recipe output, applying the given component patch to each result */
  public static RecipeOutput wrap(RecipeOutput base, DataComponentPatch patch) {
    return new CraftingNBTWrapper(base, patch);
  }
}
