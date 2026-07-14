package slimeknights.tconstruct.tables.client.inventory.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import slimeknights.mantle.client.screen.ElementScreen;
import slimeknights.tconstruct.library.client.Icons;

/**
 * Edge tab shown only in the {@link slimeknights.tconstruct.tables.client.inventory.ResponsiveLayout.Tier#COLLAPSED}
 * tier: clicking it toggles the matching {@link slimeknights.tconstruct.tables.client.inventory.ToolTableScreen.OverlayPanel}.
 * Art reuses the selector button trio ({@link Icons#BUTTON}) shifted for the wood/metal style, exactly like
 * {@code TinkerStationButtonsWidget.addInfoButton} — no new textures.
 */
public class PanelTabButton extends Button {
  public static final int WIDTH = 18, HEIGHT = 18;

  /** Draws the tab's icon at the given top-left corner (mirrors {@link SlotButtonItem}'s icon draw). */
  @FunctionalInterface
  public interface Icon {
    void render(GuiGraphics graphics, int x, int y);
  }

  private final ElementScreen background;
  private final ElementScreen backgroundHover;
  private final Icon icon;

  public PanelTabButton(int x, int y, int style, Icon icon, OnPress onPress) {
    super(x, y, WIDTH, HEIGHT, Component.empty(), onPress, DEFAULT_NARRATION);
    this.background = Icons.BUTTON.shift(0, -18 * style);
    this.backgroundHover = Icons.BUTTON_HOVERED.shift(0, -18 * style);
    this.icon = icon;
  }

  @Override
  public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
    (this.isHovered ? this.backgroundHover : this.background).draw(graphics, this.getX(), this.getY());
    this.icon.render(graphics, this.getX() + 1, this.getY() + 1);
  }
}
