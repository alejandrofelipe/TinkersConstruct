package slimeknights.tconstruct.library.recipe.material;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.materials.IMaterialRegistry;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialFluidRecipe;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import slimeknights.tconstruct.library.utils.SimpleCache;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Cache of details related to materials, populated from the {@link net.minecraft.world.item.crafting.RecipeManager} at
 * reload by {@link slimeknights.tconstruct.common.recipe.RecipeLookupPopulator}. Covers three related lookups: material
 * recipes (item -> material), known material variants, and material casting (part item costs plus the fluids that cast
 * or composite into each material). The last two share {@link #KNOWN_VARIANTS}, which is why they live in one class.
 */
public class MaterialRecipeCache {
  private MaterialRecipeCache() {}

  /* Material recipes */

  /** Full list of recipes in the cache */
  private static final List<MaterialRecipe> RECIPES = new ArrayList<>();
  /** Lookup from item ID to recipe */
  private static final Map<Item, MaterialRecipe> RECIPE_BY_ITEM = new ConcurrentHashMap<>();
  /** Lookup from material variant ID to recipe */
  private static final Multimap<MaterialVariantId, MaterialRecipe> RECIPES_BY_MATERIAL = HashMultimap.create();
  /** Map from material variant ID to item stack list for display */
  private static final Map<MaterialVariantId, List<ItemStack>> ITEMS_BY_MATERIAL = new ConcurrentHashMap<>();

  /** Mapping from material ID to all variants for the material */
  private static final Multimap<MaterialId, MaterialVariantId> KNOWN_VARIANTS = HashMultimap.create();
  /** List of all material variants in sorted order. See also {@link IMaterialRegistry#getVisibleMaterials()} */
  @Nullable
  private static List<MaterialVariantId> SORTED_VARIANTS = null;

  /* Material casting */

  /** Map containing a lookup from a material item to the cost in mb */
  private static final Object2IntMap<IMaterialItem> ITEM_COST_LOOKUP = new Object2IntOpenHashMap<>(50);

  /** Fluids that cast into materials */
  private static final List<MaterialFluidRecipe> CASTING_FLUIDS = new ArrayList<>();
  /** Fluids that composite into materials */
  private static final List<MaterialFluidRecipe> COMPOSITE_FLUIDS = new ArrayList<>();

  /** Cache for casting recipe for a given fluid */
  private static final SimpleCache<Fluid,MaterialFluidRecipe> CASTING_CACHE = new SimpleCache<>(fluid -> {
    for (MaterialFluidRecipe recipe : CASTING_FLUIDS) {
      if (recipe.matches(fluid)) {
        return recipe;
      }
    }
    return MaterialFluidRecipe.EMPTY;
  });

  private record CompositeCacheKey(Fluid fluid, MaterialVariantId input) {}

  /** Cache for a composite recipe for a given material */
  private static final SimpleCache<CompositeCacheKey,MaterialFluidRecipe> COMPOSITE_CACHE = new SimpleCache<>(key -> {
    for (MaterialFluidRecipe recipe : COMPOSITE_FLUIDS) {
      if (recipe.matches(key.fluid, key.input)) {
        return recipe;
      }
    }
    return MaterialFluidRecipe.EMPTY;
  });

  /** Cache for all casting recipes for a given material output */
  private static final SimpleCache<MaterialVariantId,List<MaterialFluidRecipe>> MATERIAL_CASTABLE = new SimpleCache<>(material ->
    CASTING_FLUIDS.stream()
      .filter(recipe -> material.matchesVariant(recipe.getOutput()))
      .collect(Collectors.toList()));
  /** Cache for all composite recipes for a given material output */
  private static final SimpleCache<MaterialVariantId,List<MaterialFluidRecipe>> MATERIAL_COMPOSITE = new SimpleCache<>(material ->
    COMPOSITE_FLUIDS.stream()
      .filter(recipe -> material.matchesVariant(recipe.getOutput()))
      .collect(Collectors.toList()));

  /** Clears the cache; called by RecipeLookupPopulator before repopulating from the RecipeManager. */
  public static void clear() {
    RECIPES.clear();
    RECIPE_BY_ITEM.clear();
    RECIPES_BY_MATERIAL.clear();
    ITEMS_BY_MATERIAL.clear();
    KNOWN_VARIANTS.clear();
    SORTED_VARIANTS = null;
    ITEM_COST_LOOKUP.clear();
    CASTING_FLUIDS.clear();
    CASTING_CACHE.clear();
    MATERIAL_CASTABLE.clear();
    MATERIAL_COMPOSITE.clear();
    COMPOSITE_FLUIDS.clear();
    COMPOSITE_CACHE.clear();
  }

  /* Material recipes */

  /** Registers a recipe with the cache */
  public static void registerRecipe(MaterialRecipe recipe) {
    if (recipe.getValue() > 0) {
      // add recipe for item lookup; too early to resolve ingredient
      RECIPES.add(recipe);
      // mark the variant as known
      MaterialVariantId variant = recipe.getMaterial().getVariant();
      addKnownVariant(variant);
      // add lookup for the variant
      RECIPES_BY_MATERIAL.put(variant, recipe);
    }
  }

  /**
   * Locates a recipe by stack
   * @param stack  Stack to check
   * @return Recipe, or {@link MaterialRecipe#EMPTY} if no match.
   */
  public static MaterialRecipe findRecipe(ItemStack stack) {
    if (stack.isEmpty()) {
      return MaterialRecipe.EMPTY;
    }
    return RECIPE_BY_ITEM.computeIfAbsent(stack.getItem(), item -> {
      for (MaterialRecipe recipe : RECIPES) {
        if (recipe.getIngredient().test(stack)) {
          return recipe;
        }
      }
      return MaterialRecipe.EMPTY;
    });
  }

  /** Gets a list of all material recipes */
  public static Collection<MaterialRecipe> getAllRecipes() {
    return RECIPES;
  }

  /** Gets all recipes for the given material variant */
  public static Collection<MaterialRecipe> getRecipes(MaterialVariantId variant) {
    return RECIPES_BY_MATERIAL.get(variant);
  }

  /** Cache lookup function for items by materials */
  private static final Function<MaterialVariantId,List<ItemStack>> GET_ITEMS_BY_MATERIAL = variant ->
    getRecipes(variant).stream().flatMap(r -> {
      Stream<ItemStack> stacks = Arrays.stream(r.getIngredient().getItems());
      // if we need multiple, increase the stack size of the display stacks
      if (r.needed > r.value) {
        int size = (r.needed + r.value - 1) / r.value;
        stacks = stacks.map(stack -> stack.copyWithCount(size));
      }
      return stacks;
    }).toList();

  /** Gets all recipes for the given material variant */
  public static List<ItemStack> getItems(MaterialVariantId variant) {
    return ITEMS_BY_MATERIAL.computeIfAbsent(variant, GET_ITEMS_BY_MATERIAL);
  }


  /* Material variants */

  /** Registers a material variant for the lookups. */
  public static void addKnownVariant(MaterialVariantId variant) {
    KNOWN_VARIANTS.put(variant.getId(), variant);
    // null cache of sorted variants as its outdated now
    SORTED_VARIANTS = null;
  }

  /** Gets a list of known material variants for the given material ID */
  public static Collection<MaterialVariantId> getVariants(MaterialId materialId) {
    Collection<MaterialVariantId> variants = KNOWN_VARIANTS.get(materialId);
    if (variants.isEmpty()) {
      return List.of(materialId);
    }
    return Collections.unmodifiableCollection(variants);
  }

  /** Gets a sorted list of all known material variants */
  public static List<MaterialVariantId> getAllVariants() {
    if (SORTED_VARIANTS == null) {
      Comparator<MaterialVariantId> variantSorter = Comparator.comparing(MaterialVariantId::getVariant);
      SORTED_VARIANTS = MaterialRegistry.getInstance().getVisibleMaterials().stream()
        .flatMap(material -> {
          // if no variants are registered, just list the material itself; useful for uncraftable materials
          MaterialId id = material.getIdentifier();
          Collection<MaterialVariantId> variants = KNOWN_VARIANTS.get(id);
          if (variants.isEmpty()) {
            return Stream.of(id);
          }
          return variants.stream().sorted(variantSorter);
        }).toList();
    }
    return SORTED_VARIANTS;
  }


  /* Material casting: part item costs */

  /** Shared logic to register parts */
  public static void registerItemCost(IMaterialItem item, int cost) {
    // if it already exists
    if (ITEM_COST_LOOKUP.containsKey(item)) {
      int original = ITEM_COST_LOOKUP.getInt(item);
      if (cost != original) {
        TConstruct.LOG.error("Inconsistent cost for item {}", BuiltInRegistries.ITEM.getKey(item.asItem()));
        ITEM_COST_LOOKUP.put(item, Math.min(cost, original));
      }
    } else {
      ITEM_COST_LOOKUP.put(item, cost);
    }
  }

  /**
   * Gets the cost for the given material item in a table
   * @param item  Item
   * @return  Item cost
   */
  public static int getItemCost(IMaterialItem item) {
    return ITEM_COST_LOOKUP.getOrDefault(item, 0);
  }

  /**
   * Gets the cost for the given material item in a table
   * @param item  Item
   * @return  Item cost
   */
  public static int getItemCost(Item item) {
    return ITEM_COST_LOOKUP.getOrDefault(item, 0);
  }

  /**
   * Gets a collection of all registered table parts
   * @return Collection of parts
   */
  public static Collection<Entry<IMaterialItem>> getAllItemCosts() {
    return ITEM_COST_LOOKUP.object2IntEntrySet();
  }


  /* Material casting: fluids */

  /**
   * Registers a fluid recipe to be detected
   * @param recipe  Recipe to add
   */
  public static void registerFluid(MaterialFluidRecipe recipe) {
    if (recipe.getInput() == null) {
      CASTING_FLUIDS.add(recipe);
    } else {
      COMPOSITE_FLUIDS.add(recipe);
    }
    addKnownVariant(recipe.getOutput().getVariant());
  }

  /**
   * Gets the material the given fluid casts into
   * @param fluid  Fluid
   * @return  Recipe, or {@link MaterialFluidRecipe#EMPTY} if not found.
   */
  public static MaterialFluidRecipe getCastingFluid(Fluid fluid) {
    return CASTING_CACHE.apply(fluid);
  }

  /**
   * Gets the material the given fluid casts into
   * @param fluid  Fluid
   * @param filter Material filter, will skip recipes that don't match
   * @return  Recipe, or {@link MaterialFluidRecipe#EMPTY} if not found.
   */
  public static MaterialFluidRecipe getCastingFluid(Fluid fluid, IJsonPredicate<MaterialVariantId> filter) {
    MaterialFluidRecipe recipe = getCastingFluid(fluid);
    if (recipe != MaterialFluidRecipe.EMPTY && filter.matches(recipe.getOutput().getVariant())) {
      return recipe;
    }
    return MaterialFluidRecipe.EMPTY;
  }

  /**
   * Gets the composite fluid recipe for the given inventory
   * @param fluid     Fluid
   * @param material  Material input
   * @return  Composite fluid recipe, or {@link MaterialFluidRecipe#EMPTY} if not found.
   */
  public static MaterialFluidRecipe getCompositeFluid(Fluid fluid, MaterialVariantId material) {
    return COMPOSITE_CACHE.apply(new CompositeCacheKey(fluid, material));
  }

  /**
   * Gets the composite fluid recipe for the given inventory
   * @param fluid     Fluid
   * @param material  Material input
   * @param filter    Material filter, will skip recipes that don't match
   * @return  Composite fluid recipe, or {@link MaterialFluidRecipe#EMPTY} if not found.
   */
  public static MaterialFluidRecipe getCompositeFluid(Fluid fluid, MaterialVariantId material, IJsonPredicate<MaterialVariantId> filter) {
    MaterialFluidRecipe recipe = getCompositeFluid(fluid, material);
    if (recipe != MaterialFluidRecipe.EMPTY && filter.matches(recipe.getOutput().getVariant())) {
      return recipe;
    }
    return MaterialFluidRecipe.EMPTY;
  }

  /**
   * Gets all recipes for the given material
   * @param material  Fluid
   * @return  Recipe
   */
  public static List<MaterialFluidRecipe> getCastingFluids(MaterialVariantId material) {
    return MATERIAL_CASTABLE.apply(material);
  }

  /**
   * Gets all recipes for the given material
   * @param material  Fluid
   * @return  Recipe
   */
  public static List<MaterialFluidRecipe> getCompositeFluids(MaterialVariantId material) {
    return MATERIAL_COMPOSITE.apply(material);
  }

  /**
   * Gets all casting fluid recipes
   * @return  Collection of all recipes
   */
  public static Collection<MaterialFluidRecipe> getAllCastingFluids() {
    return CASTING_FLUIDS;
  }

  /**
   * Gets all composite fluid recipes
   * @return  Collection of all recipes
   */
  public static Collection<MaterialFluidRecipe> getAllCompositeFluids() {
    return COMPOSITE_FLUIDS;
  }
}
