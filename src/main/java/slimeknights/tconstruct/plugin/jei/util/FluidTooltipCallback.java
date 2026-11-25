package slimeknights.tconstruct.plugin.jei.util;

import com.mojang.datafixers.util.Either;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotRichTooltipCallback;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
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

  /*
   * PORT 1.21.10 (JEI 26): the deprecated toLegacyToComponents()/removeAll(List) are replaced by the tooltip's mutable line list, doing what
   * JEI implemented them as: the components are the text lines that are Components, and removing one removes the first line equal to
   * Either.left(component).
   */
  @Override
  default void onRichTooltip(IRecipeSlotView recipeSlotView, ITooltipBuilder tooltip) {
    List<Either<FormattedText, TooltipComponent>> lines = tooltip.getLines();
    for (Component component : components(lines)) {
      if (component.getContents() instanceof TranslatableContents translatable && AMOUNT_KEY.equals(translatable.getKey())) {
        lines.remove(Either.<FormattedText, TooltipComponent>left(component));
        FluidStack fluid = recipeSlotView.getDisplayedIngredient(NeoForgeTypes.FLUID_STACK).orElse(FluidStack.EMPTY);
        List<Component> newTooltip = new ArrayList<>();
        onFluidTooltip(fluid, recipeSlotView, newTooltip);
        tooltip.addAll(newTooltip);
        return;
      }
    }
    // failed to find the tooltip to replace, so just append our stuff at the end
    FluidStack fluid = recipeSlotView.getDisplayedIngredient(NeoForgeTypes.FLUID_STACK).orElse(FluidStack.EMPTY);
    List<Component> newTooltip = new ArrayList<>();
    onFluidTooltip(fluid, recipeSlotView, newTooltip);
    tooltip.addAll(newTooltip);
  }

  /** Adds information about the fluid to the tooltip */
  void onFluidTooltip(FluidStack fluid, IRecipeSlotView recipeSlotView, List<Component> tooltip);

  /** Gets a copy of the text lines of the tooltip that are components, in order */
  static List<Component> components(List<Either<FormattedText, TooltipComponent>> lines) {
    List<Component> components = new ArrayList<>();
    for (Either<FormattedText, TooltipComponent> line : lines) {
      line.left().ifPresent(text -> {
        if (text instanceof Component component) {
          components.add(component);
        }
      });
    }
    return components;
  }
}
