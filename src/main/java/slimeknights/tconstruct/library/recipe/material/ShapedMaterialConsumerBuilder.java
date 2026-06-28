package slimeknights.tconstruct.library.recipe.material;

import lombok.NoArgsConstructor;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.common.conditions.ICondition;
import slimeknights.mantle.recipe.data.ConsumerWrapperBuilder;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Special variant of {@link ConsumerWrapperBuilder} for {@link ShapedMaterialRecipe} */
@Deprecated
@NoArgsConstructor(staticName = "wrap")
public class ShapedMaterialConsumerBuilder {
  private final List<MaterialVariantId> materials = new ArrayList<>();

  /** Adds a material to the builder */
  public ShapedMaterialConsumerBuilder material(MaterialVariantId material) {
    materials.add(material);
    return this;
  }

  /** Builds the wrapped recipe output */
  public RecipeOutput build(RecipeOutput consumer) {
    return new Wrapped(consumer, List.copyOf(materials));
  }

  /** Recipe output that wraps a vanilla shaped recipe into a {@link ShapedMaterialRecipe} before forwarding */
  private record Wrapped(RecipeOutput delegate, List<MaterialVariantId> materials) implements RecipeOutput {
    @Override
    public void accept(ResourceLocation id, Recipe<?> recipe, @Nullable AdvancementHolder advancement, ICondition... conditions) {
      Recipe<?> wrapped = recipe instanceof ShapedRecipe shaped ? new ShapedMaterialRecipe(shaped, materials) : recipe;
      delegate.accept(id, wrapped, advancement, conditions);
    }

    @Override
    public net.minecraft.advancements.Advancement.Builder advancement() {
      return delegate.advancement();
    }
  }
}
