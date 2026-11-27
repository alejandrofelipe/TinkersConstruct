package slimeknights.tconstruct.library.recipe.material;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.recipe.ingredient.MaterialValueIngredient;
import slimeknights.tconstruct.tables.TinkerTables;

import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Shaped recipe with a number of {@link slimeknights.tconstruct.library.recipe.ingredient.MaterialValueIngredient} to set the material of the result.
 * @deprecated use {@link ShapedMaterialsRecipe}, which requires specifying the ingredients for each part.
 */
@Deprecated
public class ShapedMaterialRecipe extends ShapedRecipe {
  private MaterialValueIngredient material;
  private final List<MaterialVariantId> extraMaterials;
  public ShapedMaterialRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, ShapedRecipePattern pattern, ItemStackTemplate result, List<MaterialVariantId> extraMaterials) {
    super(commonInfo, bookInfo, pattern, result);
    this.extraMaterials = extraMaterials;
  }

  public ShapedMaterialRecipe(ShapedRecipe recipe, List<MaterialVariantId> extraMaterials) {
    // 26.1: the shaped recipe result is a public template again
    this(new Recipe.CommonInfo(recipe.showNotification()), new CraftingRecipe.CraftingBookInfo(recipe.category(), recipe.group()), recipe.pattern, recipe.result, extraMaterials);
  }

  /** @deprecated use {@link #ShapedMaterialRecipe(ShapedRecipe,List)} */
  @Deprecated(forRemoval = true)
  public ShapedMaterialRecipe(ShapedRecipe recipe) {
    this(recipe, List.of());
  }

  /** Gets the material to match */
  @Nullable
  public MaterialValueIngredient getMaterial() {
    if (material == null) {
      // assume all material ingredients match the same stat type
      for (Optional<Ingredient> optional : getIngredients()) {
        // collect all ingredients that match
        if (optional.isPresent() && optional.get().getCustomIngredient() instanceof MaterialValueIngredient materialValue) {
          if (material == null) {
            material = materialValue;
          } else {
            // ensure the stat type matches, and expand the range
            material = material.merge(materialValue);
          }
        }
      }
      // if we found no materials, that is also an issue
      if (material == null) {
        TConstruct.LOG.error("No material ingredient found for material shaped recipe, this indicates a broken recipe");
      }
    }
    return material;
  }

  @Nullable
  private MaterialVariantId findMaterial(CraftingInput inventory) {
    MaterialValueIngredient material = getMaterial();
    if (material == null) {
      return null;
    }
    // ensure same material in all slots
    MaterialVariantId firstMaterial = null;
    for (int i = 0; i < inventory.size(); i++) {
      ItemStack stack = inventory.getItem(i);
      if (!stack.isEmpty()) {
        // ignore anything that does not meet our requirements
        MaterialVariantId matchedMaterial = material.getMaterial(stack);
        if (matchedMaterial != null) {
          // first match is set
          if (firstMaterial == null) {
            firstMaterial = matchedMaterial;
          } else if (!firstMaterial.matchesVariant(matchedMaterial)) {
            // if same material but different variants, just discard the variant
            if (firstMaterial.getId().equals(matchedMaterial.getId())) {
              firstMaterial = firstMaterial.getId();
            } else {
              // if different materials, no match
              return null;
            }
          }
        }
      }
    }
    return firstMaterial;
  }

  @Override
  public boolean matches(CraftingInput inventory, Level level) {
    if (!super.matches(inventory, level)) {
      return false;
    }

    // must have a material to match, no mixing
    return findMaterial(inventory) != null;
  }

  /** Sets the material for the given stack */
  public void setMaterial(ItemStack stack, MaterialVariantId material) {
    ShapedMaterialsRecipe.setMaterial(stack, material, extraMaterials);
  }

  @Override
  public ItemStack assemble(CraftingInput inventory) {
    ItemStack stack = super.assemble(inventory);
    MaterialVariantId material = findMaterial(inventory);
    if (material != null) {
      setMaterial(stack, material);
    }
    return stack;
  }

  @SuppressWarnings("unchecked")
  @Override
  public RecipeSerializer<ShapedRecipe> getSerializer() {
    // 26.1: ShapedRecipe#getSerializer returns the invariant RecipeSerializer<ShapedRecipe>
    return (RecipeSerializer<ShapedRecipe>) (RecipeSerializer<?>) TinkerTables.shapedMaterialRecipeSerializer.get();
  }

  /** 26.1: {@link RecipeSerializer} is a record of the two codecs, built by {@link #SERIALIZER} */
  public static final class Serializer {
    static final Loadable<List<MaterialVariantId>> EXTRA_MATERIALS = ShapedMaterialsRecipe.Serializer.EXTRA_MATERIALS;

    private static final MapCodec<ShapedMaterialRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
      ShapedRecipe.MAP_CODEC.forGetter(r -> (ShapedRecipe) r),
      EXTRA_MATERIALS.codec().optionalFieldOf("extra_materials", List.of()).forGetter(r -> r.extraMaterials)
    ).apply(inst, ShapedMaterialRecipe::new));

    private static final StreamCodec<RegistryFriendlyByteBuf,ShapedMaterialRecipe> STREAM_CODEC = StreamCodec.composite(
      ShapedRecipe.STREAM_CODEC, r -> r,
      EXTRA_MATERIALS.streamCodec(), r -> r.extraMaterials,
      ShapedMaterialRecipe::new);

    private Serializer() {}

    /** Creates the serializer to register */
    public static RecipeSerializer<ShapedMaterialRecipe> create() {
      return new RecipeSerializer<>(CODEC, STREAM_CODEC);
    }
  }
}
