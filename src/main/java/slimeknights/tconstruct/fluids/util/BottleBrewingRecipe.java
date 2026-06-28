package slimeknights.tconstruct.fluids.util;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.brewing.BrewingRecipe;

/**
 * Recipe for transforming a bottle, reusing the reagent of a vanilla container brewing recipe to get the ingredient.
 * <p>
 * In 1.21 the vanilla container mixes are no longer publicly accessible (they live on a private list inside
 * {@code PotionBrewing}), so the reagent for each supported {@code from -> to} conversion is resolved statically here.
 */
public class BottleBrewingRecipe extends BrewingRecipe {
  public BottleBrewingRecipe(Ingredient input, Item from, Item to, ItemStack output) {
    super(input, reagentFor(from, to), output);
  }

  /** Resolves the vanilla reagent that converts the {@code from} container into the {@code to} container */
  private static Ingredient reagentFor(Item from, Item to) {
    // mirrors PotionBrewing.addVanillaMixes container recipes
    if (from == Items.POTION && to == Items.SPLASH_POTION) {
      return Ingredient.of(Items.GUNPOWDER);
    }
    if (from == Items.SPLASH_POTION && to == Items.LINGERING_POTION) {
      return Ingredient.of(Items.DRAGON_BREATH);
    }
    return Ingredient.EMPTY;
  }
}
