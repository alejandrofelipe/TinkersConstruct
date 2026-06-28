package slimeknights.tconstruct.library.recipe.ingredient;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import slimeknights.mantle.data.loadable.common.IngredientLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.helper.LoadableIngredientSerializer;

import javax.annotation.Nullable;

/** Ingredient matching an item with no container item, used to ensure NBT fluid items are empty */
public class NoContainerIngredient extends NestedIngredient {
  /** Loadable serializer instance, registered as an {@link IngredientType} in {@link TinkerIngredients} */
  public static final LoadableIngredientSerializer<NoContainerIngredient> SERIALIZER = new LoadableIngredientSerializer<>(RecordLoadable.create(
    IngredientLoadable.DISALLOW_EMPTY.requiredField("match", i -> i.nested),
    NoContainerIngredient::new));

  protected NoContainerIngredient(Ingredient nested) {
    super(nested);
  }

  @Override
  public boolean test(@Nullable ItemStack stack) {
    return stack != null && super.test(stack) && !stack.hasCraftingRemainingItem();
  }

  @Override
  public boolean isSimple() {
    return false;
  }

  @Override
  public IngredientType<?> getType() {
    return TinkerIngredients.NO_CONTAINER.get();
  }


  /* Static constructors */

  /** Creates an instance from the given nested ingredient */
  public static Ingredient of(Ingredient ingredient) {
    return new NoContainerIngredient(ingredient).toVanilla();
  }

  /** Creates an instance from the given items */
  public static Ingredient of(ItemLike... items) {
    return of(Ingredient.of(items));
  }

  /** Creates an instance from the given stacks */
  public static Ingredient of(ItemStack... stacks) {
    return of(Ingredient.of(stacks));
  }

  /** Creates an instance from the given tag */
  public static Ingredient of(TagKey<Item> tag) {
    return of(Ingredient.of(tag));
  }
}
