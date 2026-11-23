package slimeknights.tconstruct.gametest;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.recipe.molding.MoldingRecipe;
import slimeknights.tconstruct.plugin.jei.MoldingRecipeCategory;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Gametests for the JEI recipe categories that need real recipes. JEI itself does not run on the gametest server, so its
 * helpers are stubbed with dynamic proxies. Only {@link #STUBBED} interfaces are proxied (returning other interfaces as null):
 * creating a proxy resolves every method of the interface, and {@code IDrawable} and friends take the client-only
 * {@code GuiGraphics}, which the dedicated gametest server cannot load.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(TConstruct.MOD_ID)
public class JeiCategoryGameTests {
  /** Interfaces safe to proxy on the dedicated server, enough for {@code IRecipeCategory#setRecipe} */
  private static final Set<Class<?>> STUBBED = Set.of(IGuiHelper.class, IRecipeLayoutBuilder.class, IRecipeSlotBuilder.class, IFocusGroup.class);

  /**
   * Since 1.21.4 a molding recipe without a pattern has a null pattern (the empty ingredient was removed), and the molding
   * category threw on it in {@code setRecipe} (and {@code draw}), so JEI dropped those recipes as broken. Only {@code setRecipe}
   * is exercised: {@code draw} takes a client-only {@code GuiGraphics}, which the dedicated gametest server cannot load.
   */
  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void jei_molding_category_patternless(GameTestHelper helper) {
    List<RecipeHolder<?>> patternless = new ArrayList<>();
    for (RecipeHolder<?> holder : helper.getLevel().getServer().getRecipeManager().getRecipes()) {
      if (holder.value() instanceof MoldingRecipe molding && molding.getPattern() == null) {
        patternless.add(holder);
      }
    }
    helper.assertTrue(!patternless.isEmpty(), "no molding recipe without a pattern found");
    MoldingRecipeCategory category = new MoldingRecipeCategory(stub(IGuiHelper.class));
    for (RecipeHolder<?> holder : patternless) {
      MoldingRecipe recipe = (MoldingRecipe) holder.value();
      try {
        category.setRecipe(stub(IRecipeLayoutBuilder.class), recipe, stub(IFocusGroup.class));
      } catch (RuntimeException e) {
        TConstruct.LOG.error("Molding category failed on {}", holder.id().location(), e);
        helper.fail("molding category failed on patternless recipe " + holder.id().location() + ": " + e);
        return;
      }
    }
    helper.succeed();
  }

  /** Creates a do-nothing implementation of the interface */
  @SuppressWarnings("unchecked")
  private static <T> T stub(Class<T> type) {
    return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
      if (method.getDeclaringClass() == Object.class) {
        return switch (method.getName()) {
          case "equals" -> proxy == args[0];
          case "hashCode" -> System.identityHashCode(proxy);
          default -> "stub " + type.getSimpleName();
        };
      }
      Class<?> returnType = method.getReturnType();
      if (STUBBED.contains(returnType)) {
        return stub(returnType);
      }
      if (returnType == boolean.class) return false;
      if (returnType == int.class) return 0;
      if (returnType == long.class) return 0L;
      if (returnType == float.class) return 0f;
      if (returnType == double.class) return 0d;
      if (returnType == short.class) return (short) 0;
      if (returnType == byte.class) return (byte) 0;
      if (returnType == char.class) return (char) 0;
      return null;
    });
  }
}
