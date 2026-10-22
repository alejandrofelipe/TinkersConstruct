package slimeknights.tconstruct.library.recipe.worktable;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.tconstruct.library.json.predicate.modifier.ModifierPredicate;
import slimeknights.tconstruct.library.modifiers.ModifierId;

/** Builder for recipes to add or remove a modifier from a set in persistent data */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ModifierSetWorktableRecipeBuilder extends AbstractSizedIngredientRecipeBuilder<ModifierSetWorktableRecipeBuilder> {
  private final ResourceLocation dataKey;
  @Setter @Accessors(fluent = true)
  private IJsonPredicate<ModifierId> modifierPredicate = ModifierPredicate.ANY;
  private final boolean addToSet;
  private Ingredient tools = AbstractWorktableRecipe.DEFAULT_TOOLS;
  private boolean allowTraits = false;

  /** Creates a new recipe for adding to a set */
  public static ModifierSetWorktableRecipeBuilder setAdding(ResourceLocation dataKey) {
    return new ModifierSetWorktableRecipeBuilder(dataKey, true);
  }

  /** Creates a new recipe for removing from a set */
  public static ModifierSetWorktableRecipeBuilder setRemoving(ResourceLocation dataKey) {
    return new ModifierSetWorktableRecipeBuilder(dataKey, false);
  }

  /** Sets the tool requirement for this recipe */
  public ModifierSetWorktableRecipeBuilder setTools(Ingredient ingredient) {
    this.tools = ingredient;
    return this;
  }

  /** Sets the tool requirement for this recipe */
  public ModifierSetWorktableRecipeBuilder setTools(TagKey<Item> tag) {
    return this.setTools(Ingredient.of(BuiltInRegistries.ITEM.getOrThrow(tag)));
  }

  /** Sets the recipe to allow traits */
  public ModifierSetWorktableRecipeBuilder allowTraits() {
    allowTraits = true;
    return this;
  }

  @Override
  public void save(RecipeOutput consumer) {
    save(consumer, dataKey);
  }

  public void save(RecipeOutput consumer, ResourceLocation id) {
    if (inputs.isEmpty()) {
      throw new IllegalStateException("Must have at least one ingredient");
    }
    if (tools == null) {
      throw new IllegalStateException("Tools cannot be empty");
    }
    AdvancementHolder advancement = buildOptionalAdvancement(id, "modifiers");
    consumer.accept(ResourceKey.create(Registries.RECIPE, id), new ModifierSetWorktableRecipe(id, dataKey, inputs, tools, modifierPredicate, addToSet, allowTraits), advancement);
  }

  @Override
  public void save(RecipeOutput output, ResourceKey<Recipe<?>> id) {
    save(output, id.location());
  }
}
