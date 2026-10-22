package slimeknights.tconstruct.plugin.jei;

import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import slimeknights.tconstruct.library.recipe.ingredient.MaterialValueIngredient;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import slimeknights.tconstruct.library.recipe.material.ShapedMaterialRecipe;
import slimeknights.tconstruct.plugin.jei.material.ShapedMaterialsExtension;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Logic to show {@link ShapedMaterialRecipe} in JEI. Ported to the JEI 20 {@link SlotDisplay}-based crafting extension
 * model (see {@link slimeknights.tconstruct.plugin.jei.material.MaterialsCraftingExtension}).
 * @deprecated use {@link ShapedMaterialsExtension}
 */
@Deprecated
public class ShapedMaterialExtension implements ICraftingCategoryExtension<ShapedMaterialRecipe> {
  @Override
  public int getWidth(RecipeHolder<ShapedMaterialRecipe> holder) {
    return holder.value().getWidth();
  }

  @Override
  public int getHeight(RecipeHolder<ShapedMaterialRecipe> holder) {
    return holder.value().getHeight();
  }

  @Override
  public List<SlotDisplay> getIngredients(RecipeHolder<ShapedMaterialRecipe> holder) {
    // one slot display per pattern cell, empty cells become SlotDisplay.Empty
    return holder.value().getIngredients().stream().map(Ingredient::optionalIngredientToDisplay).toList();
  }

  @Override
  public void onDisplayedIngredientsUpdate(RecipeHolder<ShapedMaterialRecipe> holder, List<IRecipeSlotDrawable> recipeSlots, IFocusGroup focuses) {
    List<ItemStack> outputs = displayOutputs(holder.value());
    if (outputs.size() > 1) {
      for (IRecipeSlotDrawable slot : recipeSlots) {
        if (slot.getRole() == RecipeIngredientRole.OUTPUT) {
          slot.clearDisplayOverrides();
          slot.createDisplayOverrides().addItemStacks(outputs);
          break;
        }
      }
    }
  }

  /** Computes the per-material output variants for the recipe */
  private static List<ItemStack> displayOutputs(ShapedMaterialRecipe recipe) {
    ItemStack plainResult = getResult(recipe);
    if (plainResult.isEmpty()) {
      return List.of();
    }
    MaterialValueIngredient materials = recipe.getMaterial();
    if (materials != null) {
      return MaterialRecipeCache.getAllRecipes().stream().filter(materials::test).flatMap(mat -> {
        ItemStack stack = plainResult.copy();
        recipe.setMaterial(stack, mat.getMaterial().getVariant());
        // add one copy of the stack per item in the nested ingredient, so the lengths match up
        return IntStream.range(0, (int) mat.getIngredient().items().count()).mapToObj(i -> stack);
      }).toList();
    }
    return List.of(plainResult);
  }

  /** Reads the plain result from the recipe display, as the recipe no longer exposes it directly in 1.21.4 */
  private static ItemStack getResult(ShapedMaterialRecipe recipe) {
    for (RecipeDisplay display : recipe.display()) {
      if (display.result() instanceof SlotDisplay.ItemStackSlotDisplay stackDisplay) {
        return stackDisplay.stack();
      }
    }
    return ItemStack.EMPTY;
  }
}
