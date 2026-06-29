package slimeknights.tconstruct.library.recipe.material;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.tables.TinkerTables;

import java.util.List;

/**
 * Shapeless recipe with a number of {@link slimeknights.tconstruct.library.recipe.ingredient.MaterialIngredient} and
 * {@link slimeknights.tconstruct.library.recipe.ingredient.MaterialValueIngredient} to set the materials of the result.
 */
public class ShapelessMaterialsRecipe extends ShapelessRecipe implements MaterialsCraftingTableRecipe {
  /** Number of parts to match */
  @Getter
  private final int partCount;
  /** List of additional materials to add beyond the parts */
  @Getter
  private final List<MaterialVariantId> extraMaterials;

  public ShapelessMaterialsRecipe(String group, net.minecraft.world.item.crafting.CraftingBookCategory category, ItemStack result, net.minecraft.core.NonNullList<Ingredient> ingredients, int partCount, List<MaterialVariantId> extraMaterials) {
    super(group, category, result, ingredients);
    this.partCount = partCount;
    this.extraMaterials = extraMaterials;
  }

  public ShapelessMaterialsRecipe(ShapelessRecipe recipe, int partCount, List<MaterialVariantId> extraMaterials) {
    this(recipe.getGroup(), recipe.category(), recipe.getResultItem(null), recipe.getIngredients(), partCount, extraMaterials);
  }

  @Override
  public List<Ingredient> getParts() {
    return getIngredients();
  }

  /** Sets the material for the given stack */
  @Override
  public void setMaterial(ItemStack stack, MaterialVariantId material) {
    ShapedMaterialsRecipe.setMaterial(stack, material, extraMaterials);
  }

  @Override
  public ItemStack assemble(CraftingInput inventory, HolderLookup.Provider registryAccess) {
    return ShapedMaterialsRecipe.assemble(super.assemble(inventory, registryAccess), inventory, getIngredients(), partCount, false, extraMaterials);
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return TinkerTables.shapelessMaterialsRecipeSerializer.get();
  }

  public static class Serializer implements RecipeSerializer<ShapelessMaterialsRecipe> {
    static final Loadable<List<MaterialVariantId>> EXTRA_MATERIALS = ShapedMaterialsRecipe.Serializer.EXTRA_MATERIALS;

    private static final MapCodec<ShapelessMaterialsRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
      RecipeSerializer.SHAPELESS_RECIPE.codec().forGetter(r -> (ShapelessRecipe) r),
      Codec.INT.fieldOf("parts").forGetter(r -> r.partCount),
      EXTRA_MATERIALS.codec().optionalFieldOf("extra_materials", List.of()).forGetter(r -> r.extraMaterials)
    ).apply(inst, ShapelessMaterialsRecipe::new));

    private static final StreamCodec<RegistryFriendlyByteBuf,ShapelessMaterialsRecipe> STREAM_CODEC = StreamCodec.composite(
      ShapelessRecipe.Serializer.STREAM_CODEC, r -> r,
      ByteBufCodecs.VAR_INT, r -> r.partCount,
      EXTRA_MATERIALS.streamCodec(), r -> r.extraMaterials,
      ShapelessMaterialsRecipe::new);

    @Override
    public MapCodec<ShapelessMaterialsRecipe> codec() {
      return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf,ShapelessMaterialsRecipe> streamCodec() {
      return STREAM_CODEC;
    }
  }
}
