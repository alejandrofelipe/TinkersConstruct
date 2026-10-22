package slimeknights.tconstruct.plugin.jei.material;

import slimeknights.tconstruct.library.recipe.material.ShapelessMaterialsRecipe;

/**
 * Logic to show {@link ShapelessMaterialsRecipe} in JEI. Uses the base {@link MaterialsCraftingExtension} as-is: the
 * default {@code getWidth}/{@code getHeight} of 0 marks the layout shapeless, and the grid inputs come from the recipe's
 * own ingredient list.
 */
public class ShapelessMaterialsExtension extends MaterialsCraftingExtension<ShapelessMaterialsRecipe> {}
