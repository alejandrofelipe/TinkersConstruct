package slimeknights.tconstruct.plugin.jei.modifiers;

import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.rendering.IIngredientRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import slimeknights.tconstruct.library.client.modifiers.ModifierIconManager;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;

import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Special modifier ingredient renderer used for ingredients in the bookmark menu */
public enum ModifierBookmarkIngredientRenderer implements IIngredientRenderer<ModifierEntry> {
  INSTANCE;

  private static final String WRAPPER_KEY = "jei.tconstruct.modifier_ingredient";

  @Override
  public void render(GuiGraphicsExtractor graphics, @Nullable ModifierEntry entry, int x, int y, IIngredientRenderContext context) {
    if (entry != null) {
      ModifierIconManager.renderIcon(graphics, entry.getModifier(), x, y, 100, 16);
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
    List<Component> list = new ArrayList<>();
    // not using the main method as that applies color
    list.add(Component.translatable(WRAPPER_KEY, Component.translatable(entry.getModifier().getTranslationKey())));
    list.addAll(entry.getModifier().getDescriptionList());
    if (flag.isAdvanced()) {
      list.add((Component.literal(entry.getId().toString())).withStyle(ChatFormatting.DARK_GRAY));
    }
    return list;
  }

  /** JEI 27 still declares this overload abstract, but only calls the one with a tooltip context above */
  @SuppressWarnings("removal")
  @Override
  public List<Component> getTooltip(ModifierEntry entry, TooltipFlag flag) {
    return getTooltip(entry, Item.TooltipContext.EMPTY, null, flag);
  }
}
