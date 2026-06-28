package slimeknights.tconstruct.library.recipe.ingredient;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import slimeknights.tconstruct.TConstruct;

/**
 * Registration for Tinkers' custom {@link net.neoforged.neoforge.common.crafting.ICustomIngredient} types.
 * Replaces the old Forge {@code CraftingHelper.register(id, serializer)} calls that lived in
 * {@code TinkerCommons}, {@code TinkerMaterials} and {@code TinkerTools}.
 */
@SuppressWarnings("unused")
public class TinkerIngredients {
  private static final DeferredRegister<IngredientType<?>> INGREDIENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, TConstruct.MOD_ID);

  private TinkerIngredients() {}

  /** Registers this to the mod event bus */
  public static void init(IEventBus bus) {
    INGREDIENT_TYPES.register(bus);
  }

  public static final DeferredHolder<IngredientType<?>,IngredientType<MaterialIngredient>> MATERIAL = INGREDIENT_TYPES.register("material", () -> MaterialIngredient.SERIALIZER.ingredientType());
  public static final DeferredHolder<IngredientType<?>,IngredientType<MaterialValueIngredient>> MATERIAL_VALUE = INGREDIENT_TYPES.register("material_value", () -> MaterialValueIngredient.SERIALIZER.ingredientType());
  public static final DeferredHolder<IngredientType<?>,IngredientType<NoContainerIngredient>> NO_CONTAINER = INGREDIENT_TYPES.register("no_container", () -> NoContainerIngredient.SERIALIZER.ingredientType());
  public static final DeferredHolder<IngredientType<?>,IngredientType<BlockTagIngredient>> BLOCK_TAG = INGREDIENT_TYPES.register("block_tag", () -> BlockTagIngredient.SERIALIZER.ingredientType());
  public static final DeferredHolder<IngredientType<?>,IngredientType<ToolHookIngredient>> TOOL_HOOK = INGREDIENT_TYPES.register("tool_hook", () -> ToolHookIngredient.SERIALIZER.ingredientType());
}
