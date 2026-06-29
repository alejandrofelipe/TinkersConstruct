package slimeknights.tconstruct.tools.recipe.severing;

import com.mojang.datafixers.util.Function3;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import slimeknights.mantle.recipe.data.AbstractRecipeBuilder;
import slimeknights.tconstruct.library.recipe.modifiers.severing.SeveringRecipe;

import java.util.Objects;
import java.util.function.Supplier;

/** Builder for severing recipes that have only the base chance and looting bonus as fields */
@Setter
@Accessors(chain = true)
@RequiredArgsConstructor(staticName = "serializer")
public class SpecialSeveringRecipeBuilder extends AbstractRecipeBuilder<SpecialSeveringRecipeBuilder> {
  private final Supplier<? extends RecipeSerializer<? extends SeveringRecipe>> serializer;
  private final Function3<ResourceLocation,Float,Float,? extends SeveringRecipe> constructor;
  private float baseChance = 0.05f;
  private float lootingBonus = 0.01f;

  /** Doubles the drop chances for this rare mob */
  public SpecialSeveringRecipeBuilder rareMob() {
    baseChance = 0.1f;
    lootingBonus = 0.02f;
    return this;
  }

  @SuppressWarnings("deprecation")
  @Override
  public void save(RecipeOutput output) {
    save(output, Objects.requireNonNull(BuiltInRegistries.RECIPE_SERIALIZER.getKey(serializer.get())));
  }

  @Override
  public void save(RecipeOutput output, ResourceLocation id) {
    AdvancementHolder advancement = buildOptionalAdvancement(id, "severing");
    output.accept(id, constructor.apply(id, baseChance, lootingBonus), advancement);
  }
}
