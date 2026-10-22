package slimeknights.tconstruct.library.recipe.casting;

import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import slimeknights.mantle.data.loadable.common.IngredientLoadable;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.primitive.BooleanLoadable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/** Shared logic between item and material casting */
public abstract class AbstractCastingRecipe implements ICastingRecipe {
  /* Common fields */
  protected static final LoadableField<Ingredient,AbstractCastingRecipe> CAST_FIELD = IngredientLoadable.ALLOW_EMPTY.nullableField("cast", AbstractCastingRecipe::getCast);
  protected static final LoadableField<Boolean,AbstractCastingRecipe> CAST_CONSUMED_FIELD = BooleanLoadable.INSTANCE.defaultField("cast_consumed", false, false, AbstractCastingRecipe::isConsumed);
  protected static final LoadableField<Boolean,AbstractCastingRecipe> SWITCH_SLOTS_FIELD = BooleanLoadable.INSTANCE.defaultField("switch_slots", false, false, AbstractCastingRecipe::switchSlots);

  @Getter @Nonnull
  private final RecipeType<? extends Recipe<ICastingContainer>> type;
  @Getter
  private final ResourceLocation id;
  @Getter
  private final String group;
  /** 'cast' item for recipe (doesn't have to be an actual 'cast'); null if this recipe has no cast */
  @Getter @Nullable
  private final Ingredient cast;
  @Getter
  private final boolean consumed;
  private final boolean switchSlots;

  @Override
  public boolean switchSlots() {
    return this.switchSlots;
  }

  @SuppressWarnings("unchecked")
  protected AbstractCastingRecipe(RecipeType<?> type, ResourceLocation id, String group, @Nullable Ingredient cast, boolean consumed, boolean switchSlots) {
    this.type = (RecipeType<? extends Recipe<ICastingContainer>>) type;
    this.id = id;
    this.group = group;
    this.cast = cast;
    this.consumed = consumed;
    this.switchSlots = switchSlots;
  }

  /** Gets the ingredients for display; {@code Recipe#getIngredients} was removed in 1.21.4 so this is Mantle/Tinkers-only now */
  public List<Ingredient> getIngredients() {
    return cast == null ? List.of() : List.of(cast);
  }

  /** Tests the cast slot, treating a null cast as requiring an empty slot (matches the old {@code Ingredient.EMPTY} behavior) */
  protected boolean testCast(ItemStack stack) {
    return cast == null ? stack.isEmpty() : cast.test(stack);
  }
}
