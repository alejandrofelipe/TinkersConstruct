package slimeknights.tconstruct.plugin.jei.util;

import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotRichTooltipCallback;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.fluid.tooltip.FluidTooltipHandler;

import java.util.ArrayList;
import java.util.List;

/** Helper for working with fluid tooltips */
@FunctionalInterface
public interface FluidTooltipCallback extends IRecipeSlotRichTooltipCallback {
  String AMOUNT_KEY = "jei.tooltip.liquid.amount";

  /** Default instance, simply replaces mb units with our unit handler. */
  FluidTooltipCallback UNITS = (fluid, recipeSlotView, tooltip) -> FluidTooltipHandler.appendMaterial(fluid, tooltip);

  /** Default instance, simply replaces mb units with our unit handler. */
  FluidTooltipCallback NO_AMOUNT = (fluid, recipeSlotView, tooltip) -> {};

  @Override
  @SuppressWarnings("removal")
  default void onRichTooltip(IRecipeSlotView recipeSlotView, ITooltipBuilder tooltip) {
    List<Component> current = new ArrayList<>(tooltip.toLegacyToComponents());
    for (Component component : current) {
      if (component.getContents() instanceof TranslatableContents translatable && AMOUNT_KEY.equals(translatable.getKey())) {
        tooltip.removeAll(List.of(component));
        FluidStack fluid = recipeSlotView.getDisplayedIngredient(ForgeTypes.FLUID_STACK).orElse(FluidStack.EMPTY);
        List<Component> newTooltip = new ArrayList<>();
        onFluidTooltip(fluid, recipeSlotView, newTooltip);
        tooltip.addAll(newTooltip);
        return;
      }
    }
    // failed to find the tooltip to replace, so just append our stuff at the end
    FluidStack fluid = recipeSlotView.getDisplayedIngredient(ForgeTypes.FLUID_STACK).orElse(FluidStack.EMPTY);
    List<Component> newTooltip = new ArrayList<>();
    onFluidTooltip(fluid, recipeSlotView, newTooltip);
    tooltip.addAll(newTooltip);
  }

  /** Adds information about the fluid to the tooltip */
  void onFluidTooltip(FluidStack fluid, IRecipeSlotView recipeSlotView, List<Component> tooltip);
}
