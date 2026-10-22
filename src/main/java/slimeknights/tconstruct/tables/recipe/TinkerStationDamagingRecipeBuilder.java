package slimeknights.tconstruct.tables.recipe;

import lombok.RequiredArgsConstructor;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import slimeknights.mantle.recipe.data.AbstractRecipeBuilder;

/** Builder for tinker station damaging recipes */
@RequiredArgsConstructor(staticName = "damage")
public class TinkerStationDamagingRecipeBuilder extends AbstractRecipeBuilder<TinkerStationDamagingRecipeBuilder> {

  private final Ingredient ingredient;
  private final int damageAmount;

  @Override
  public void save(RecipeOutput output) {
    save(output, BuiltInRegistries.ITEM.getKey(ingredient.items().findFirst().orElseThrow(() -> new IllegalStateException("Empty ingredient not allowed")).value()));
  }

  public void save(RecipeOutput output, ResourceLocation id) {
    if (ingredient == null) {
      throw new IllegalStateException("Empty ingredient not allowed");
    }
    AdvancementHolder advancement = buildOptionalAdvancement(id, "tinker_station");
    output.accept(ResourceKey.create(Registries.RECIPE, id), new TinkerStationDamagingRecipe(ingredient, damageAmount), advancement);
  }

  @Override
  public void save(RecipeOutput output, ResourceKey<Recipe<?>> id) {
    save(output, id.location());
  }
}
