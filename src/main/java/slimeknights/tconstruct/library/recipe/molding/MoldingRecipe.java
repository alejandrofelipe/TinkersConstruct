package slimeknights.tconstruct.library.recipe.molding;

import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import slimeknights.mantle.data.loadable.common.IngredientLoadable;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.primitive.BooleanLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.ICommonRecipe;
import slimeknights.mantle.recipe.helper.ItemOutput;
import slimeknights.mantle.recipe.helper.LoadableRecipeSerializer;
import slimeknights.mantle.recipe.helper.TypeAwareRecipeSerializer;

import javax.annotation.Nullable;
import java.util.List;

/** Recipe to combine two items on the top of a casting table, changing the first */
public class MoldingRecipe implements ICommonRecipe<IMoldingContainer> {
  public static final RecordLoadable<MoldingRecipe> LOADER = RecordLoadable.create(
    LoadableRecipeSerializer.TYPED_SERIALIZER.requiredField(),
    ContextKey.ID.nullableField(),
    IngredientLoadable.DISALLOW_EMPTY.requiredField("material", MoldingRecipe::getMaterial),
    IngredientLoadable.ALLOW_EMPTY.nullableField("pattern", MoldingRecipe::getPattern),
    BooleanLoadable.INSTANCE.defaultField("pattern_consumed", false, false, MoldingRecipe::isPatternConsumed),
    ItemOutput.Loadable.REQUIRED_ITEM.requiredField("result", (MoldingRecipe r) -> r.recipeOutput),
    MoldingRecipe::new);

  @Getter
  private final RecipeType<? extends Recipe<IMoldingContainer>> type;
  @Getter
  private final RecipeSerializer<? extends Recipe<IMoldingContainer>> serializer;
  @Getter
  private final ResourceLocation id;
  @Getter
  private final Ingredient material;
  /** Pattern ingredient; null if this recipe has no pattern (1.21.4 removed the empty ingredient) */
  @Getter @Nullable
  private final Ingredient pattern;
  @Getter
  private final boolean patternConsumed;
  private final ItemOutput recipeOutput;

  @SuppressWarnings("unchecked")
  public MoldingRecipe(TypeAwareRecipeSerializer<?> serializer, ResourceLocation id, Ingredient material, @Nullable Ingredient pattern, boolean patternConsumed, ItemOutput recipeOutput) {
    this.type = (RecipeType<? extends Recipe<IMoldingContainer>>) serializer.getType();
    this.serializer = (RecipeSerializer<? extends Recipe<IMoldingContainer>>) serializer;
    this.id = id;
    this.material = material;
    this.pattern = pattern;
    this.patternConsumed = pattern != null && patternConsumed;
    this.recipeOutput = recipeOutput;
  }

  @Override
  public boolean matches(IMoldingContainer inv, Level worldIn) {
    return material.test(inv.getMaterial()) && (pattern == null ? inv.getPattern().isEmpty() : pattern.test(inv.getPattern()));
  }

  /** Gets the ingredients for display; {@code Recipe#getIngredients} was removed in 1.21.4 */
  public List<Ingredient> getIngredients() {
    return pattern == null ? List.of(material) : List.of(material, pattern);
  }

  @Override
  public ItemStack getResultItem(HolderLookup.Provider access) {
    return recipeOutput.get();
  }
}
