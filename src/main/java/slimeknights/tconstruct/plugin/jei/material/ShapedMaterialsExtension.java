package slimeknights.tconstruct.plugin.jei.material;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import slimeknights.tconstruct.library.recipe.material.ShapedMaterialsRecipe;

import java.util.List;

/** Logic to show {@link ShapedMaterialsRecipe} in JEI */
public class ShapedMaterialsExtension extends MaterialsCraftingExtension<ShapedMaterialsRecipe> {
  @Override
  public List<SlotDisplay> getIngredients(RecipeHolder<ShapedMaterialsRecipe> holder) {
    // shaped layout: one slot display per pattern cell, empty cells become SlotDisplay.Empty
    return holder.value().getIngredients().stream().map(Ingredient::optionalIngredientToDisplay).toList();
  }

  @Override
  public int getWidth(RecipeHolder<ShapedMaterialsRecipe> holder) {
    return holder.value().getWidth();
  }

  @Override
  public int getHeight(RecipeHolder<ShapedMaterialsRecipe> holder) {
    return holder.value().getHeight();
  }
}
