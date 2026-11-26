package slimeknights.tconstruct.plugin.jei.partbuilder;

import lombok.NoArgsConstructor;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.rendering.IIngredientRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import slimeknights.tconstruct.library.client.GuiUtil;
import slimeknights.tconstruct.library.recipe.partbuilder.Pattern;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@NoArgsConstructor
public class PatternIngredientRenderer implements IIngredientRenderer<Pattern> {
  public static final PatternIngredientRenderer INSTANCE = new PatternIngredientRenderer();

  @Override
  public void render(GuiGraphics graphics, @Nullable Pattern pattern, int x, int y, IIngredientRenderContext context) {
    if (pattern != null) {
      GuiUtil.renderPattern(graphics, pattern, x, y);
    }
  }

  /** JEI 27 still declares this overload abstract, but only calls the positioned one above */
  @SuppressWarnings("removal")
  @Override
  public void render(GuiGraphics graphics, @Nullable Pattern pattern) {
    render(graphics, pattern, 0, 0, IIngredientRenderContext.INGREDIENT);
  }

  @Override
  public List<Component> getTooltip(Pattern pattern, Item.TooltipContext context, @Nullable Player player, TooltipFlag flag) {
    if (flag.isAdvanced()) {
      return Arrays.asList(pattern.getDisplayName(), Component.literal(pattern.toString()).withStyle(ChatFormatting.DARK_GRAY));
    } else {
      return Collections.singletonList(pattern.getDisplayName());
    }
  }

  /** JEI 27 still declares this overload abstract, but only calls the one with a tooltip context above */
  @SuppressWarnings("removal")
  @Override
  public List<Component> getTooltip(Pattern pattern, TooltipFlag flag) {
    return getTooltip(pattern, Item.TooltipContext.EMPTY, null, flag);
  }
}
