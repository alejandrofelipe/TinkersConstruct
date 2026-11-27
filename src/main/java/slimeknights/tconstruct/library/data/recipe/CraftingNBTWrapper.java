package slimeknights.tconstruct.library.data.recipe;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.common.conditions.ICondition;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

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

  /** Applies the stored component patch to the recipe's result, where the recipe type exposes one */
  private Recipe<?> applyPatch(Recipe<?> recipe) {
    // 26.1: results are immutable templates (datagen has no bound components, so no stacks): replace the template with one
    // whose patch has the stored patch merged over it, the stored patch wins like ItemStack#applyComponents did. The field
    // is replaced in place (access transformer public-f) so subclasses such as Mantle's ShapedRetexturedRecipe keep their type.
    if (recipe instanceof ShapedRecipe shaped) {
      shaped.result = withPatch(shaped.result);
    } else if (recipe instanceof ShapelessRecipe shapeless) {
      shapeless.result = withPatch(shapeless.result);
    }
    return recipe;
  }

  /** Creates a copy of the template with the stored patch applied over its components */
  private ItemStackTemplate withPatch(ItemStackTemplate result) {
    DataComponentPatch.Builder builder = DataComponentPatch.builder();
    addAll(builder, result.components());
    addAll(builder, patch);
    return new ItemStackTemplate(result.item(), result.count(), builder.build());
  }

  /** Copies every set or removed component of the patch into the builder */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void addAll(DataComponentPatch.Builder builder, DataComponentPatch patch) {
    for (Map.Entry<DataComponentType<?>,Optional<?>> entry : patch.entrySet()) {
      if (entry.getValue().isPresent()) {
        builder.set((DataComponentType) entry.getKey(), entry.getValue().get());
      } else {
        builder.remove(entry.getKey());
      }
    }
  }

  /** Creates a wrapped recipe output, applying the given component patch to each result */
  public static RecipeOutput wrap(RecipeOutput base, DataComponentPatch patch) {
    return new CraftingNBTWrapper(base, patch);
  }
}
