package slimeknights.tconstruct.plugin.jei.melting;

import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.recipe.fuel.MeltingFuel;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@link MeltingFuelHandler#setMeltngFuels}. It sorted the given list in place, so an immutable list (such as
 * {@code Stream#toList()}, which the 1.21.4+ JEI plugin passes) threw {@link UnsupportedOperationException} and aborted
 * the rest of {@code JEIPlugin#registerRecipes}. The 1.21.1 plugin passes a mutable list; the copy is defensive here.
 */
class MeltingFuelHandlerTest extends BaseMcTest {
  private static MeltingFuel fuel(String name, int temperature) {
    return new MeltingFuel(TConstruct.getResource(name), FluidIngredient.of(new FluidStack(Fluids.LAVA, 50)), 100, temperature, 10);
  }

  @Test
  void setMeltngFuels_acceptsImmutableList() {
    MeltingFuelHandler.setMeltngFuels(List.of(fuel("hot", 1000), fuel("cold", 500)));

    // a recipe needing 600 can only use the 1000 fuel, one needing 400 can use both
    assertThat(MeltingFuelHandler.getUsableFuels(600)).hasSize(1);
    assertThat(MeltingFuelHandler.getUsableFuels(400)).hasSize(2);
    assertThat(MeltingFuelHandler.getUsableFuels(2000)).isEmpty();
  }
}
