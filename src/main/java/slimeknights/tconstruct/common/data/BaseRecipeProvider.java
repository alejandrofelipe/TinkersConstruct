package slimeknights.tconstruct.common.data;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.world.item.Item;
import slimeknights.mantle.recipe.data.IRecipeHelper;
import slimeknights.tconstruct.TConstruct;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * Shared logic for each module's recipe provider.
 *
 * <p>Ported to the 1.21.4 datagen API: {@link RecipeProvider} is no longer a {@code DataProvider}; the data provider is
 * the nested {@link RecipeProvider.Runner}. The provider now receives a resolved {@link HolderLookup.Provider} and the
 * {@link RecipeOutput} directly, and {@link #buildRecipes()} takes no argument. To keep the module recipe bodies
 * unchanged, this base bridges the no-arg {@link #buildRecipes()} to {@code buildRecipes(this.output)} and exposes the
 * item lookup now required by the various recipe helper interfaces.
 */
public abstract class BaseRecipeProvider extends RecipeProvider implements IRecipeHelper {
  public BaseRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
    super(registries, output);
    TConstruct.sealTinkersClass(this, "BaseRecipeProvider", "BaseRecipeProvider is trivial to recreate and directly extending can lead to addon recipes polluting our namespace.");
  }

  @Override
  protected final void buildRecipes() {
    buildRecipes(this.output);
  }

  /** Builds all recipes for this module, mirroring the pre-1.21.4 {@code buildRecipes(RecipeOutput)} signature. */
  protected abstract void buildRecipes(RecipeOutput consumer);

  /** Display name for this provider, surfaced through the {@link Runner}. */
  public abstract String getName();

  /** Item registry lookup, required by recipe and criterion builders in 1.21.4. */
  public HolderGetter<Item> itemLookup() {
    return this.registries.lookupOrThrow(Registries.ITEM);
  }

  /** Wraps a recipe id in the {@code ResourceKey<Recipe<?>>} now required by {@code RecipeBuilder#save} and {@code RecipeOutput#accept}. */
  public net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> recipeKey(net.minecraft.resources.ResourceLocation id) {
    return net.minecraft.resources.ResourceKey.create(Registries.RECIPE, id);
  }

  @Override
  public String getModId() {
    return TConstruct.MOD_ID;
  }

  /**
   * Generic {@link RecipeProvider.Runner} for Tinkers recipe providers. Mirrors the vanilla pattern where the runner
   * is the registered {@code DataProvider} and constructs the actual provider once the registries resolve.
   */
  public static final class Runner extends RecipeProvider.Runner {
    private final String name;
    private final BiFunction<HolderLookup.Provider,RecipeOutput,? extends BaseRecipeProvider> factory;
    public Runner(PackOutput output, CompletableFuture<HolderLookup.Provider> registries, String name, BiFunction<HolderLookup.Provider,RecipeOutput,? extends BaseRecipeProvider> factory) {
      super(output, registries);
      this.name = name;
      this.factory = factory;
    }

    @Override
    protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
      return factory.apply(registries, output);
    }

    @Override
    public String getName() {
      return name;
    }
  }
}
