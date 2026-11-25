package slimeknights.tconstruct.plugin.jei.util;

import com.mojang.datafixers.util.Either;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotRichTooltipCallback;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.ArrayList;
import java.util.List;

/** @deprecated use {@link FluidTooltipCallback} for better handling of advanced tooltip information */
@Deprecated(forRemoval = true)
@FunctionalInterface
public interface IRecipeTooltipReplacement extends IRecipeSlotRichTooltipCallback {
  /** Tooltip replacement that keeps just the name and mod ID */
  IRecipeTooltipReplacement EMPTY = (slot, tooltip) -> {};

  /** PORT 1.21.10 (JEI 26): works on the mutable line list instead of the deprecated toLegacyToComponents()/removeAll(List), see {@link FluidTooltipCallback} */
  @Override
  default void onRichTooltip(IRecipeSlotView recipeSlotView, ITooltipBuilder tooltip) {
    List<Either<FormattedText, TooltipComponent>> lines = tooltip.getLines();
    List<Component> current = FluidTooltipCallback.components(lines);
    if (!current.isEmpty()) {
      for (Component component : current.subList(1, current.size())) {
        lines.remove(Either.<FormattedText, TooltipComponent>left(component));
      }
      List<Component> middle = new ArrayList<>();
      addMiddleLines(recipeSlotView, middle);
      tooltip.addAll(middle);
    }
  }

  /** Adds the lines between the name and mod ID */
  void addMiddleLines(IRecipeSlotView recipeSlotView, List<Component> tooltip);
}
