package slimeknights.tconstruct.plugin.jei.util;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotRichTooltipCallback;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** @deprecated use {@link FluidTooltipCallback} for better handling of advanced tooltip information */
@Deprecated(forRemoval = true)
@FunctionalInterface
public interface IRecipeTooltipReplacement extends IRecipeSlotRichTooltipCallback {
  /** Tooltip replacement that keeps just the name and mod ID */
  IRecipeTooltipReplacement EMPTY = (slot, tooltip) -> {};

  @Override
  @SuppressWarnings("removal")
  default void onRichTooltip(IRecipeSlotView recipeSlotView, ITooltipBuilder tooltip) {
    List<Component> current = new ArrayList<>(tooltip.toLegacyToComponents());
    if (!current.isEmpty()) {
      tooltip.removeAll(current.subList(1, current.size()));
      List<Component> middle = new ArrayList<>();
      addMiddleLines(recipeSlotView, middle);
      tooltip.addAll(middle);
    }
  }

  /** Adds the lines between the name and mod ID */
  void addMiddleLines(IRecipeSlotView recipeSlotView, List<Component> tooltip);
}
