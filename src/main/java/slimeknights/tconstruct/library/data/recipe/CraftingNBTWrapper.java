package slimeknights.tconstruct.library.data.recipe;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
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
  public void accept(ResourceLocation id, Recipe<?> recipe, @Nullable AdvancementHolder advancement, ICondition... conditions) {
    original.accept(id, applyPatch(recipe), advancement, conditions);
  }

  /** Applies the stored component patch to the recipe's result stack, where the recipe type exposes one */
  private Recipe<?> applyPatch(Recipe<?> recipe) {
    // vanilla shaped/shapeless expose a mutable result stack; apply the patch in place
    if (recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe) {
      // PORT M3: result is fetched without a registry context during datagen; acceptable as components datagen does not need lookup
      ItemStack result = recipe.getResultItem(null);
      if (result != null && !result.isEmpty()) {
        result.applyComponents(patch);
      }
    }
    return recipe;
  }

  /** Creates a wrapped recipe output, applying the given component patch to each result */
  public static RecipeOutput wrap(RecipeOutput base, DataComponentPatch patch) {
    return new CraftingNBTWrapper(base, patch);
  }
}
