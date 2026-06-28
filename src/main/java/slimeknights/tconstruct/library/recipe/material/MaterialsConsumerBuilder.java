package slimeknights.tconstruct.library.recipe.material;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.common.conditions.ICondition;
import slimeknights.mantle.recipe.data.ConsumerWrapperBuilder;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Special variant of {@link ConsumerWrapperBuilder} for {@link ShapedMaterialsRecipe} and {@link ShapelessMaterialsRecipe}.
 * <p>
 * In 1.20 the shaped parts referenced symbols in the shaped recipe key map; in 1.21 the {@link net.minecraft.world.item.crafting.ShapedRecipePattern}
 * is opaque, so the shaped variant takes the part ingredients directly.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MaterialsConsumerBuilder {
  /** Part ingredients for a shaped recipe, empty for shapeless */
  private final List<Ingredient> parts;
  /** Number of parts to consume from the front of a shapeless recipe, 0 for shaped */
  private final int partCount;
  private final List<MaterialVariantId> materials = new ArrayList<>();

  /** Creates a new shaped recipe with the given ingredients as parts */
  public static MaterialsConsumerBuilder shaped(List<Ingredient> parts) {
    if (parts.isEmpty()) {
      throw new IllegalArgumentException("Parts may not be empty");
    }
    return new MaterialsConsumerBuilder(List.copyOf(parts), 0);
  }

  /** Creates a new shaped recipe with the given ingredients as parts */
  public static MaterialsConsumerBuilder shaped(Ingredient... parts) {
    return shaped(List.of(parts));
  }

  /** Creates a new shapeless recipe with the first ingredients as parts */
  public static MaterialsConsumerBuilder shapeless(int parts) {
    if (parts <= 0) {
      throw new IllegalArgumentException("Parts must be greater than 0");
    }
    return new MaterialsConsumerBuilder(List.of(), parts);
  }

  /** Adds a material to the builder */
  public MaterialsConsumerBuilder material(MaterialVariantId material) {
    materials.add(material);
    return this;
  }

  /** Builds the wrapped recipe output */
  public RecipeOutput build(RecipeOutput consumer) {
    return new Wrapped(consumer, parts, partCount, List.copyOf(materials));
  }

  /** Recipe output that wraps a vanilla crafting recipe into a material variant before forwarding */
  private record Wrapped(RecipeOutput delegate, List<Ingredient> parts, int partCount, List<MaterialVariantId> materials) implements RecipeOutput {
    @Override
    public void accept(ResourceLocation id, Recipe<?> recipe, @Nullable AdvancementHolder advancement, ICondition... conditions) {
      Recipe<?> wrapped;
      if (partCount > 0 && recipe instanceof ShapelessRecipe shapeless) {
        wrapped = new ShapelessMaterialsRecipe(shapeless, partCount, materials);
      } else if (recipe instanceof ShapedRecipe shaped) {
        wrapped = new ShapedMaterialsRecipe(shaped, parts, materials);
      } else {
        wrapped = recipe;
      }
      delegate.accept(id, wrapped, advancement, conditions);
    }

    @Override
    public net.minecraft.advancements.Advancement.Builder advancement() {
      return delegate.advancement();
    }
  }
}
