package slimeknights.tconstruct.plugin.jei.modifiers;

import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.rendering.IIngredientRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;

import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

public record ModifierIngredientRenderer(int width, int height) implements IIngredientRenderer<ModifierEntry> {
  @Override
  public int getWidth() {
    return width;
  }

  @Override
  public int getHeight() {
    return height;
  }

  @Override
  public void render(GuiGraphicsExtractor graphics, @Nullable ModifierEntry entry, int x, int y, IIngredientRenderContext context) {
    if (entry != null) {
      Component name = entry.getDisplayName();
      Font fontRenderer = getFontRenderer(Minecraft.getInstance(), entry);
      graphics.text(fontRenderer, name, x + (width - fontRenderer.width(name)) / 2, y + 1, -1, true);
    }
  }

  /** JEI 27 still declares this overload abstract, but only calls the positioned one above */
  @SuppressWarnings("removal")
  @Override
  public void render(GuiGraphicsExtractor graphics, @Nullable ModifierEntry entry) {
    render(graphics, entry, 0, 0, IIngredientRenderContext.INGREDIENT);
  }

  @Override
  public List<Component> getTooltip(ModifierEntry entry, Item.TooltipContext context, @Nullable Player player, TooltipFlag flag) {
    List<Component> tooltip = entry.getModifier().getDescriptionList(entry.getLevel());
    if (flag.isAdvanced()) {
      tooltip = new ArrayList<>(tooltip);
      tooltip.add((Component.literal(entry.getId().toString())).withStyle(ChatFormatting.DARK_GRAY));
    }
    return tooltip;
  }

  /** JEI 27 still declares this overload abstract, but only calls the one with a tooltip context above */
  @SuppressWarnings("removal")
  @Override
  public List<Component> getTooltip(ModifierEntry entry, TooltipFlag flag) {
    return getTooltip(entry, Item.TooltipContext.EMPTY, null, flag);
  }
}
