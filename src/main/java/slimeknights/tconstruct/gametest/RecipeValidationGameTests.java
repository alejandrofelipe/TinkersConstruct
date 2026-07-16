package slimeknights.tconstruct.gametest;

import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import slimeknights.mantle.recipe.helper.ItemOutput;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.alloying.AlloyRecipe;
import slimeknights.tconstruct.library.recipe.casting.CastingRecipeLookup;
import slimeknights.tconstruct.library.recipe.casting.ICastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.IDisplayableCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.ItemCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialFluidRecipe;
import slimeknights.tconstruct.library.recipe.fuel.MeltingFuelLookup;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipe;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import slimeknights.tconstruct.library.recipe.melting.IMeltingContainer;
import slimeknights.tconstruct.library.recipe.melting.IMeltingRecipe;
import slimeknights.tconstruct.library.recipe.melting.MeltingRecipe;
import slimeknights.tconstruct.library.recipe.melting.MeltingRecipeLookup;
import slimeknights.tconstruct.library.recipe.modifiers.ModifierRecipeLookup;
import slimeknights.tconstruct.library.recipe.modifiers.adding.IDisplayModifierRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationRecipe;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Data-driven: every melting/casting/alloy recipe registered with the recipe manager is well-formed.
 * Purely inspects recipe objects (ingredient/output/temperature/time getters) - no in-world ticking, no smeltery.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(TConstruct.MOD_ID)
public class RecipeValidationGameTests {

  /** Recipe ids confirmed intentional-latent, each with a one-line justification comment. */
  private static final Set<String> KNOWN_INTENTIONAL = Set.of(
    // Water/milk-output melting recipes: temperature is computed as fluid.getFluidType().getTemperature() - 300
    // (see IMeltingRecipe#getTemperature(Fluid)); water and milk both use the default room-temperature FluidType
    // value of 300, so this is always exactly 0 by design (these melt passively, no smeltery heat tier required).
    "tconstruct:smeltery/melting/water/ice",
    "tconstruct:smeltery/melting/water/packed_ice",
    "tconstruct:smeltery/melting/water/blue_ice",
    "tconstruct:smeltery/melting/water/snowball",
    "tconstruct:smeltery/melting/water/snow_block",
    "tconstruct:smeltery/melting/water/snow_layer",
    "tconstruct:smeltery/entity_melting/heads/skeleton", // skulls -> milk, same 0-temperature formula outcome
    // SmelteryRecipeBuilder#rawOre() unconditionally emits a "raw_materials/<metal>" + "storage_blocks/raw_<metal>"
    // tag-based melting recipe for every metal (see SmelteryRecipeProvider line ~2251, .rawOre(Byproduct.IRON) on
    // moltenSteel). Steel has no vanilla/Tinkers raw ore item, so both c: tags are legitimately empty here; this is
    // the same forward-compat-with-other-mods placeholder pattern used for bronze/brass/electrum/pewter/etc.
    "tconstruct:smeltery/melting/metal/steel/raw",
    "tconstruct:smeltery/melting/metal/steel/raw_block",
    // TippingCastingRecipe/TipClearingCastingRecipe (tipped arrows/fishing rods) mutate the input tool's persistent
    // NBT in place via assemble(); their PotionCastingRecipe result field is Items.AIR by construction since
    // getResultItem() is not how they produce output - see TippingCastingRecipe/TipClearingCastingRecipe ctors.
    "tconstruct:tools/modifiers/slotless/ammo_tipping",
    "tconstruct:tools/modifiers/slotless/ammo_tip_clearing",
    "tconstruct:tools/modifiers/slotless/fishing_rod_tipping",
    "tconstruct:tools/modifiers/slotless/fishing_rod_tip_clearing"
  );

  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void all_melting_recipes_valid(GameTestHelper helper) {
    RecipeManager recipeManager = helper.getLevel().getRecipeManager();

    // MaterialMeltingRecipe ("melt any tool part of material X") has no item Ingredient of its own - getIngredients()
    // is empty - and its getOutput(container)/getTime(container) scale the declared output by
    // MaterialRecipeCache.getItemCost(container item), which is 0 (so output/time read as 0) for any stack that
    // is not a registered tool-part item. Use any one real, registered part item as the fallback container stack so
    // those recipes report their true declared output/time instead of an artifact of an empty dummy stack.
    ItemStack fallbackStack = MaterialRecipeCache.getAllItemCosts().stream()
      .filter(entry -> entry.getIntValue() > 0)
      .findFirst()
      .map(entry -> new ItemStack(entry.getKey()))
      .orElse(ItemStack.EMPTY);

    Map<String, String> failures = new LinkedHashMap<>();
    for (RecipeHolder<IMeltingRecipe> holder : recipeManager.getAllRecipesFor(TinkerRecipeTypes.MELTING.get())) {
      IMeltingRecipe recipe = holder.value();
      String id = holder.id().toString();
      try {
        List<Ingredient> ingredients = recipe.getIngredients();
        for (Ingredient ingredient : ingredients) {
          // isEmpty() = deliberately no ingredient declared (fine); hasNoItems() catches a tag/item that is
          // declared but resolves to nothing (NeoForge's Ingredient#getItems() returns a synthetic "Empty Tag: .."
          // barrier stack for a dead tag rather than a zero-length array, so getItems().length == 0 never fires).
          if (!ingredient.isEmpty() && ingredient.hasNoItems()) {
            failures.merge(id, "input ingredient resolves to no items", (a, b) -> a + "; " + b);
          }
        }
        ItemStack dummyStack = fallbackStack;
        if (!ingredients.isEmpty() && !ingredients.get(0).isEmpty() && !ingredients.get(0).hasNoItems()) {
          dummyStack = ingredients.get(0).getItems()[0];
        }
        IMeltingContainer container = new DummyMeltingContainer(dummyStack);
        FluidStack output = recipe.getOutput(container);
        if (output.isEmpty()) {
          failures.merge(id, "output fluid is empty", (a, b) -> a + "; " + b);
        }
        if (recipe.getTemperature(container) <= 0) {
          failures.merge(id, "non-positive temperature", (a, b) -> a + "; " + b);
        }
        if (recipe.getTime(container) <= 0) {
          failures.merge(id, "non-positive time", (a, b) -> a + "; " + b);
        }
      } catch (Exception e) {
        failures.merge(id, "threw " + e, (a, b) -> a + "; " + b);
      }
    }
    reportResult(helper, "melting", failures);
  }

  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void all_casting_recipes_valid(GameTestHelper helper) {
    RecipeManager recipeManager = helper.getLevel().getRecipeManager();
    HolderLookup.Provider registryAccess = helper.getLevel().registryAccess();

    Map<String, String> failures = new LinkedHashMap<>();
    for (RecipeType<ICastingRecipe> type : List.of(TinkerRecipeTypes.CASTING_TABLE.get(), TinkerRecipeTypes.CASTING_BASIN.get())) {
      for (RecipeHolder<ICastingRecipe> holder : recipeManager.getAllRecipesFor(type)) {
        ICastingRecipe recipe = holder.value();
        String id = holder.id().toString();
        try {
          for (Ingredient ingredient : recipe.getIngredients()) {
            if (!ingredient.isEmpty() && ingredient.hasNoItems()) {
              failures.merge(id, "cast ingredient resolves to no items", (a, b) -> a + "; " + b);
            }
          }
          if (recipe.getResultItem(registryAccess).isEmpty()) {
            failures.merge(id, "output item is empty", (a, b) -> a + "; " + b);
          }
          // A fixed, enumerable fluid ingredient only exists on the "simple" cast+fluid+item family
          // (ItemCastingRecipe & co, marked by IDisplayableCastingRecipe). The material-casting family
          // (MaterialCastingRecipe/ToolCastingRecipe/PartSwapCastingRecipe/...) accepts whatever fluid the global
          // MaterialRecipeCache maps to an allowed material, and PotionCastingRecipe's fluid field isn't exposed
          // by the common interface - neither has a per-recipe fluid ingredient this generic loop can validate.
          if (recipe instanceof IDisplayableCastingRecipe displayable && displayable.getFluids().isEmpty()) {
            failures.merge(id, "fluid ingredient resolves to no fluids", (a, b) -> a + "; " + b);
          }
        } catch (Exception e) {
          failures.merge(id, "threw " + e, (a, b) -> a + "; " + b);
        }
      }
    }
    reportResult(helper, "casting", failures);
  }

  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void all_alloy_recipes_valid(GameTestHelper helper) {
    RecipeManager recipeManager = helper.getLevel().getRecipeManager();

    Map<String, String> failures = new LinkedHashMap<>();
    for (RecipeHolder<AlloyRecipe> holder : recipeManager.getAllRecipesFor(TinkerRecipeTypes.ALLOYING.get())) {
      AlloyRecipe recipe = holder.value();
      String id = holder.id().toString();
      try {
        for (AlloyRecipe.AlloyIngredient ingredient : recipe.getInputs()) {
          if (ingredient.fluid().getFluids().isEmpty()) {
            failures.merge(id, "input fluid resolves to no fluids", (a, b) -> a + "; " + b);
          }
        }
        if (recipe.getOutput().isEmpty()) {
          failures.merge(id, "output fluid is empty", (a, b) -> a + "; " + b);
        }
        if (recipe.getTemperature() <= 0) {
          failures.merge(id, "non-positive temperature", (a, b) -> a + "; " + b);
        }
      } catch (Exception e) {
        failures.merge(id, "threw " + e, (a, b) -> a + "; " + b);
      }
    }
    reportResult(helper, "alloy", failures);
  }

  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void all_material_fluid_recipes_valid(GameTestHelper helper) {
    // The material-casting family (MaterialCastingRecipe/ToolCastingRecipe/PartSwapCastingRecipe) has no per-recipe
    // fluid ingredient - its fluids come from the global MaterialRecipeCache (MaterialFluidRecipe, the data type),
    // so all_casting_recipes_valid can't reach them (see its comment). Validate them at the source instead: every
    // registered casting/composite fluid must resolve to real fluids and a known output material.
    Map<String, String> failures = new LinkedHashMap<>();
    Stream.concat(MaterialRecipeCache.getAllCastingFluids().stream(), MaterialRecipeCache.getAllCompositeFluids().stream())
      .forEach(recipe -> {
        // MaterialFluidRecipe carries no id in 1.21 (it lives on the RecipeHolder, unreachable from the lookup
        // collection - getId() is null here), so describe the recipe by its input->output material for reporting.
        String id = "material_fluid(" + (recipe.getInput() != null ? recipe.getInput().getVariant() + "->" : "") + recipe.getOutput().getVariant() + ")";
        if (recipe.getFluids().isEmpty()) {
          failures.merge(id, "fluid ingredient resolves to no fluids", (a, b) -> a + "; " + b);
        }
        if (recipe.getOutput().isUnknown()) {
          failures.merge(id, "output material is unknown", (a, b) -> a + "; " + b);
        }
      });
    reportResult(helper, "material fluid", failures);
  }

  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void melting_fuel_lookup_populated(GameTestHelper helper) {
    // MeltingFuelLookup must be populated by the reload populator (RecipeLookupPopulator), not constructor side-effects.
    // Lava is a registered fuel and there is a registered solid fuel; assert both are present after load.
    if (!MeltingFuelLookup.isFuel(Fluids.LAVA)) {
      helper.fail("MeltingFuelLookup not populated: lava should be a registered fuel");
    } else if (MeltingFuelLookup.findFuel(Fluids.LAVA) == null) {
      helper.fail("findFuel(lava) returned null despite isFuel(lava)=true");
    } else if (MeltingFuelLookup.getSolid().getRate() <= 0) {
      helper.fail("no solid fuel registered (getSolid() returned the EMPTY sentinel)");
    } else {
      helper.succeed();
    }
  }

  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void melting_recipe_lookup_populated(GameTestHelper helper) {
    // MeltingRecipeLookup must be populated by the reload populator (RecipeLookupPopulator), not constructor side-effects.
    // Data-driven so it can't go stale: pull a real static melting recipe (a MeltingRecipe with a concrete item
    // ingredient) from the manager, then assert the lookup resolves that same input. If population never ran, the
    // lookup is empty and canMelt returns false. MaterialMeltingRecipe is skipped as it has no item ingredient of its
    // own and never feeds the lookup.
    RecipeManager recipeManager = helper.getLevel().getRecipeManager();
    ItemStack sample = ItemStack.EMPTY;
    for (RecipeHolder<IMeltingRecipe> holder : recipeManager.getAllRecipesFor(TinkerRecipeTypes.MELTING.get())) {
      if (holder.value() instanceof MeltingRecipe recipe) {
        Ingredient input = recipe.getInput();
        if (!input.isEmpty() && !input.hasNoItems()) {
          sample = input.getItems()[0];
          break;
        }
      }
    }
    if (sample.isEmpty()) {
      helper.fail("no static MeltingRecipe with a concrete item ingredient found; cannot verify MeltingRecipeLookup population");
    } else if (!MeltingRecipeLookup.canMelt(sample.getItem())) {
      helper.fail("MeltingRecipeLookup not populated: " + sample.getItem() + " has a MeltingRecipe but the lookup does not resolve it");
    } else {
      helper.succeed();
    }
  }

  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void casting_recipe_lookup_populated(GameTestHelper helper) {
    // CastingRecipeLookup must be populated by the reload populator (RecipeLookupPopulator), not constructor side-effects.
    // Data-driven so it can't go stale: pull a real static casting recipe (an ItemCastingRecipe with a concrete result
    // output) from either casting type, then assert the lookup marks that same item castable. If population never ran,
    // the lookup is empty and isCastable returns false. We read getResult() (the ItemOutput actually registered as
    // castable) rather than getResultItem(): CastDuplicationRecipe extends ItemCastingRecipe but registers ItemOutput.EMPTY
    // while overriding getResultItem() to return the cast, so it is skipped here via the isEmpty() check.
    RecipeManager recipeManager = helper.getLevel().getRecipeManager();
    ItemStack sample = ItemStack.EMPTY;
    for (RecipeType<ICastingRecipe> type : List.of(TinkerRecipeTypes.CASTING_TABLE.get(), TinkerRecipeTypes.CASTING_BASIN.get())) {
      for (RecipeHolder<ICastingRecipe> holder : recipeManager.getAllRecipesFor(type)) {
        if (holder.value() instanceof ItemCastingRecipe recipe) {
          ItemOutput result = recipe.getResult();
          if (!result.isEmpty()) {
            sample = result.get();
            break;
          }
        }
      }
      if (!sample.isEmpty()) {
        break;
      }
    }
    if (sample.isEmpty()) {
      helper.fail("no static ItemCastingRecipe with a concrete result output found; cannot verify CastingRecipeLookup population");
    } else if (!CastingRecipeLookup.isCastable(sample.getItem())) {
      helper.fail("CastingRecipeLookup not populated: " + sample.getItem() + " has a casting recipe but the lookup does not mark it castable");
    } else {
      helper.succeed();
    }
  }

  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void modifier_recipe_lookup_populated(GameTestHelper helper) {
    // ModifierRecipeLookup must be populated by the reload populator (RecipeLookupPopulator), not constructor side-effects.
    // Data-driven so it can't go stale: pull a real modifier-adding recipe (any IDisplayModifierRecipe under the
    // tinker_station type - ModifierRecipe, OverslimeModifierRecipe, etc.) from the manager, read the modifier it adds
    // via getDisplayResult().getId() (which does not resolve the lazy modifier), then assert the lookup marks that same
    // modifier as a recipe modifier. If the TINKER_STATION populate path never ran, the lookup is empty and
    // isRecipeModifier returns false.
    RecipeManager recipeManager = helper.getLevel().getRecipeManager();
    ModifierId sample = null;
    for (RecipeHolder<ITinkerStationRecipe> holder : recipeManager.getAllRecipesFor(TinkerRecipeTypes.TINKER_STATION.get())) {
      if (holder.value() instanceof IDisplayModifierRecipe recipe) {
        sample = recipe.getDisplayResult().getId();
        break;
      }
    }
    if (sample == null) {
      helper.fail("no IDisplayModifierRecipe found under the tinker_station type; cannot verify ModifierRecipeLookup population");
    } else if (!ModifierRecipeLookup.isRecipeModifier(sample)) {
      helper.fail("ModifierRecipeLookup not populated: " + sample + " is added by a modifier recipe but the lookup does not resolve it");
    } else {
      helper.succeed();
    }
  }

  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void material_casting_lookup_populated(GameTestHelper helper) {
    // MaterialRecipeCache must be populated by the reload populator, not constructor side-effects. Assert both write
    // paths ran: registerItemCost (getAllItemCosts non-empty) and registerFluid (a registered casting fluid exists).
    // registerFluid also drives the coupling into MaterialRecipeCache, so spot-check a real casting fluid: its output
    // variant must have been recorded as a known variant via registerFluid -> addKnownVariant.
    if (MaterialRecipeCache.getAllItemCosts().isEmpty()) {
      helper.fail("MaterialRecipeCache item costs not populated by the reload populator");
      return;
    }
    MaterialFluidRecipe sample = null;
    for (MaterialFluidRecipe recipe : MaterialRecipeCache.getAllCastingFluids()) {
      if (!recipe.getOutput().isUnknown()) {
        sample = recipe;
        break;
      }
    }
    if (sample == null) {
      helper.fail("no casting fluids registered; MaterialRecipeCache.registerFluid path did not populate");
    } else {
      MaterialVariantId variant = sample.getOutput().getVariant();
      if (!MaterialRecipeCache.getVariants(variant.getId()).contains(variant)) {
        helper.fail("coupling broken: casting fluid output " + variant + " was not recorded as a known variant in MaterialRecipeCache");
      } else {
        helper.succeed();
      }
    }
  }

  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void material_recipe_cache_populated(GameTestHelper helper) {
    // MaterialRecipeCache must be populated by the reload populator, not constructor side-effects. Sample a real material
    // recipe (registerRecipe path, MATERIAL type) and assert its variant was recorded as a known variant (addKnownVariant
    // path). Data-driven so it can't go stale on recipe-id changes.
    MaterialRecipe sample = null;
    for (MaterialRecipe recipe : MaterialRecipeCache.getAllRecipes()) {
      if (!recipe.getMaterial().isUnknown()) {
        sample = recipe;
        break;
      }
    }
    if (sample == null) {
      helper.fail("MaterialRecipeCache has no known material recipes; the registerRecipe/MATERIAL populate path did not run");
    } else {
      MaterialVariantId variant = sample.getMaterial().getVariant();
      if (!MaterialRecipeCache.getVariants(variant.getId()).contains(variant)) {
        helper.fail("MaterialRecipeCache did not record variant " + variant + " from a loaded material recipe");
      } else {
        helper.succeed();
      }
    }
  }

  /** Fails the test listing every non-triaged offender (id + reason), else succeeds. */
  private static void reportResult(GameTestHelper helper, String kind, Map<String, String> failures) {
    Set<String> bad = new LinkedHashSet<>(failures.keySet());
    bad.removeAll(KNOWN_INTENTIONAL);
    if (!bad.isEmpty()) {
      List<String> details = bad.stream().map(id -> id + " (" + failures.get(id) + ")").toList();
      helper.fail(bad.size() + " malformed " + kind + " recipe(s): " + details);
    } else {
      helper.succeed();
    }
  }

  /** Minimal {@link IMeltingContainer} for probing container-dependent getters without a real smeltery. */
  private record DummyMeltingContainer(ItemStack stack) implements IMeltingContainer {
    @Override
    public ItemStack getStack() {
      return stack;
    }

    @Override
    public IOreRate getOreRate() {
      return (rate, amount) -> amount;
    }
  }
}
