package slimeknights.tconstruct.library.recipe.modifiers.adding;

import slimeknights.tconstruct.library.recipe.ingredient.LazyTagIngredient;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import slimeknights.mantle.recipe.data.AbstractRecipeBuilder;
import slimeknights.tconstruct.common.TinkerTags;

/**
 * Builder for overslime recipes
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class OverslimeModifierRecipeBuilder extends AbstractRecipeBuilder<OverslimeModifierRecipeBuilder> {
  @Setter @Accessors(chain = true)
  private Ingredient tools = LazyTagIngredient.of(TinkerTags.Items.DURABILITY);
  private final Ingredient ingredient;
  private final int restoreAmount;

  /** Creates a new builder for the given ingredient */
  public static OverslimeModifierRecipeBuilder modifier(Ingredient ingredient, int restoreAmount) {
    return new OverslimeModifierRecipeBuilder(ingredient, restoreAmount);
  }

  /** Creates a new builder for the given item */
  public static OverslimeModifierRecipeBuilder modifier(ItemLike item, int restoreAmount) {
    return modifier(Ingredient.of(item), restoreAmount);
  }

  @Override
  public void save(RecipeOutput consumer) {
    Item first = ingredient.items().map(Holder::value).findFirst()
                           .orElseThrow(() -> new IllegalStateException("Empty ingredient not allowed"));
    save(consumer, BuiltInRegistries.ITEM.getKey(first));
  }
  public void save(RecipeOutput consumer, ResourceLocation id) {
    if (ingredient.items().findAny().isEmpty()) {
      throw new IllegalStateException("Empty ingredient not allowed");
    }
    AdvancementHolder advancement = buildOptionalAdvancement(id, "modifiers");
    consumer.accept(ResourceKey.create(Registries.RECIPE, id), new OverslimeModifierRecipe(id, tools, ingredient, restoreAmount), advancement);
  }

  @Override
  public void save(RecipeOutput consumer, ResourceKey<Recipe<?>> id) {
    save(consumer, id.location());
  }

  /** Creates a crafting table overslime repair recipe */
  public OverslimeModifierRecipeBuilder saveCrafting(RecipeOutput consumer, ResourceLocation id) {
    if (ingredient.items().findAny().isEmpty()) {
      throw new IllegalStateException("Empty ingredient not allowed");
    }
    AdvancementHolder advancement = buildOptionalAdvancement(id, "modifiers");
    consumer.accept(ResourceKey.create(Registries.RECIPE, id), new OverslimeCraftingTableRecipe(id, tools, ingredient, restoreAmount), advancement);
    return this;
  }
}
