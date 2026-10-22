package slimeknights.tconstruct.plugin.jei.material;

import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import slimeknights.tconstruct.library.recipe.material.MaterialsCraftingTableRecipe;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.item.IModifiableDisplay;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;

import java.util.List;
import java.util.Optional;

/**
 * Common logic for {@link ShapedMaterialsExtension} and {@link ShapelessMaterialsExtension}.
 *
 * <p>JEI 20 (1.21.4) replaced the JEI 19 {@code setRecipe(RecipeHolder, IRecipeLayoutBuilder, ICraftingGridHelper,
 * IFocusGroup)} model with a {@link SlotDisplay}-based one: {@link #getIngredients(RecipeHolder)} supplies the grid
 * inputs (which still cycle every material variant, since a material ingredient's display lists all of its matching
 * items) and JEI derives the output from the recipe's {@link CraftingRecipe#display()}. The per-material output is
 * restored by overriding the output slot in {@link #onDisplayedIngredientsUpdate}.
 *
 * <p>The one feature that cannot be carried over is the output&harr;material-input focus link: JEI 20 no longer hands
 * extensions the layout builder, so {@code builder.createFocusLink(...)} is gone. The input and output still cycle their
 * own variants, they just no longer cycle in lock-step. See the task report.
 */
public class MaterialsCraftingExtension<T extends CraftingRecipe & MaterialsCraftingTableRecipe> implements ICraftingCategoryExtension<T> {

  @Override
  public List<SlotDisplay> getIngredients(RecipeHolder<T> holder) {
    // shapeless layout: one slot display per ingredient; shaped overrides with the pattern (including empty cells)
    return holder.value().placementInfo().ingredients().stream()
                 .map(ingredient -> Ingredient.optionalIngredientToDisplay(Optional.of(ingredient)))
                 .toList();
  }

  @Override
  public void onDisplayedIngredientsUpdate(RecipeHolder<T> holder, List<IRecipeSlotDrawable> recipeSlots, IFocusGroup focuses) {
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
  private List<ItemStack> displayOutputs(T recipe) {
    ItemStack plainResult = getResult(recipe);
    if (plainResult.isEmpty()) {
      return List.of();
    }
    // if we have just the one part, set the output to match each material variant of its ingredient
    if (recipe.getPartCount() == 1) {
      Ingredient firstPart = recipe.getParts().get(0);
      return firstPart.items().map(variant -> {
        ItemStack stack = plainResult.copy();
        ItemStack variantStack = new ItemStack(variant);
        if (variantStack.getItem() instanceof IMaterialItem materialItem) {
          recipe.setMaterial(stack, materialItem.getMaterial(variantStack));
        } else {
          recipe.setMaterial(stack, MaterialRecipeCache.findRecipe(variantStack).getMaterial().getVariant());
        }
        return stack;
      }).toList();
      // otherwise, use a display material. allow display tool part if it has just 1 material
    } else if (recipe.getExtraMaterials().isEmpty() && plainResult.getItem() instanceof IMaterialItem materialItem) {
      return List.of(materialItem.setMaterialForced(plainResult, ToolBuildHandler.getRenderMaterial(0)));
    } else {
      // display tool
      return List.of(IModifiableDisplay.getDisplayStack(plainResult));
    }
  }

  /** Reads the plain result from the recipe display, as {@link CraftingRecipe} no longer exposes it directly in 1.21.4 */
  protected static ItemStack getResult(CraftingRecipe recipe) {
    for (RecipeDisplay display : recipe.display()) {
      if (display.result() instanceof SlotDisplay.ItemStackSlotDisplay stackDisplay) {
        return stackDisplay.stack();
      }
    }
    return ItemStack.EMPTY;
  }

  @Override
  public boolean isHandled(RecipeHolder<T> holder) {
    // recipes with unmatchable parts cannot be displayed
    for (Ingredient ingredient : holder.value().getParts()) {
      if (ingredient.items().findAny().isEmpty()) {
        return false;
      }
    }
    return true;
  }
}
