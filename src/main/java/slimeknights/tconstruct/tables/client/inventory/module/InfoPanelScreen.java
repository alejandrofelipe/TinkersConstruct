package slimeknights.tconstruct.tables.client.inventory.module;

import com.google.common.collect.Lists;
import lombok.Setter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.joml.Matrix3x2fStack;
import slimeknights.mantle.client.screen.ElementScreen;
import slimeknights.mantle.client.screen.ModuleScreen;
import slimeknights.mantle.client.screen.MultiModuleScreen;
import slimeknights.mantle.client.screen.ScalableElementScreen;
import slimeknights.mantle.client.screen.SliderWidget;
import slimeknights.tconstruct.library.client.GuiUtil;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.tables.client.inventory.ResponsiveLayout;
import slimeknights.tconstruct.tables.client.inventory.widget.BorderWidget;

import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;

public class InfoPanelScreen<P extends MultiModuleScreen<?>, C extends AbstractContainerMenu> extends ModuleScreen<P,C> {
  private static final int resW = 118;
  private static final int resH = 75;

  /** Default caption displayed until one is set */
  private static final Component DEFAULT_CAPTION = TConstruct.makeTranslation("gui", "caption").withStyle(ChatFormatting.UNDERLINE);
  /** Help marker drawn in the top right corner when the panel has tooltips */
  protected static final FormattedCharSequence HELP_MARKER = Component.literal("?").getVisualOrderText();

  protected static Identifier BACKGROUND_IMAGE = TConstruct.getResource("textures/gui/panel.png");

  protected static final ElementScreen TOP_LEFT = new ElementScreen(BACKGROUND_IMAGE, 0, 0, 4, 4, 256, 256);
  protected static final ElementScreen TOP_RIGHT = TOP_LEFT.move(resW + 4, 0, 4, 4);
  protected static final ElementScreen BOTTOM_LEFT = TOP_LEFT.move(0, resH + 4, 4, 4);
  protected static final ElementScreen BOTTOM_RIGHT = TOP_LEFT.move(resW + 4, resH + 4, 4, 4);

  protected static final ScalableElementScreen TOP = new ScalableElementScreen(BACKGROUND_IMAGE, 4, 0, resW, 4, 256, 256);
  protected static final ScalableElementScreen BOTTOM = TOP.move(4, 4 + resH, resW, 4);
  protected static final ScalableElementScreen LEFT = TOP.move(0, 4, 4, resH);
  protected static final ScalableElementScreen RIGHT = TOP.move(4 + resW, 4, 4, resH);

  protected static final ScalableElementScreen BACKGROUND = TOP.move(4, 4, resW, resH);

  protected static final ElementScreen SLIDER_NORMAL = TOP_LEFT.move(0, 83, 3, 5);
  protected static final ElementScreen SLIDER_HOVER = SLIDER_NORMAL.shift(SLIDER_NORMAL.w, 0);

  protected static final ScalableElementScreen SLIDER_BAR = TOP.move(0, 88, 3, 8);
  protected static final ElementScreen SLIDER_TOP = TOP_LEFT.move(3, 88, 3, 4);
  protected static final ElementScreen SLIDER_BOTTOM = TOP_LEFT.move(3, 92, 3, 4);

  protected BorderWidget border = new BorderWidget();

  protected SliderWidget slider = new SliderWidget(SLIDER_NORMAL, SLIDER_HOVER, SLIDER_HOVER, SLIDER_TOP, SLIDER_BOTTOM, SLIDER_BAR);

  protected Component caption;
  protected List<Component> text;
  protected List<Component> tooltips;

  protected List<Integer> tooltipLines = Lists.newLinkedList();

  @Setter
  protected float textScale = 1.0f;

  /** COLLAPSED tier: when set, the panel centers over the parent window instead of docking to a side (the on-demand overlay). */
  @Setter
  private boolean overlayMode = false;
  /** COLLAPSED tier: when set, the panel is a docked-but-invisible tab (not drawn, no JEI area, no mouse). Mutually exclusive with {@link #overlayMode}. */
  @Setter
  private boolean hidden = false;

  public InfoPanelScreen(P parent, C container, Inventory playerInventory, Component title) {
    super(parent, container, playerInventory, title, true, false);

    this.border.borderTop = TOP;
    this.border.borderBottom = BOTTOM;
    this.border.borderLeft = LEFT;
    this.border.borderRight = RIGHT;

    this.border.cornerTopLeft = TOP_LEFT;
    this.border.cornerTopRight = TOP_RIGHT;
    this.border.cornerBottomLeft = BOTTOM_LEFT;
    this.border.cornerBottomRight = BOTTOM_RIGHT;

    this.imageWidth = resW + 8;
    this.imageHeight = resH + 8;

    this.caption = DEFAULT_CAPTION;
    this.text = Lists.newLinkedList();
  }

  /** Sets the panel width; text wrapping and the slider re-derive from imageWidth (getTotalLines/updateSliderParameters). */
  public void setPanelWidth(int width) {
    this.imageWidth = Mth.clamp(width, ResponsiveLayout.INFO_MIN, resW + 8);
    if (this.hasInitialized()) {
      this.updateSliderParameters();
    }
  }

  /** Gets the height to render fonts scaled by the text scale */
  public int getScaledFontHeight() {
    return (int)Math.ceil(this.font.lineHeight * textScale);
  }

  @Override
  public void updatePosition(int parentX, int parentY, int parentSizeX, int parentSizeY) {
    if (this.overlayMode) {
      // center over the parent window, capped to fit within it; stays inside [parentX, parentX+parentSizeX] so
      // MultiModuleScreen.updateSubmodule finds nothing to expand
      this.imageWidth = Math.min(resW + 8, parentSizeX - 16);
      this.leftPos = parentX + (parentSizeX - this.imageWidth) / 2;
      this.topPos = parentY + 18;                                    // below the title/beam, over the inventory area
      this.border.setPosition(this.leftPos, this.topPos);
      this.border.setSize(this.imageWidth, this.imageHeight);
      this.slider.setPosition(this.guiRight() - this.border.w - 2, this.topPos + this.border.h + 12);
      this.slider.setSize(this.imageHeight - this.border.h * 2 - 2 - 12);
      this.updateSliderParameters();
      return;
    }

    super.updatePosition(parentX, parentY, parentSizeX, parentSizeY);

    this.border.setPosition(this.leftPos, this.topPos);
    this.border.setSize(this.imageWidth, this.imageHeight);
    this.slider.setPosition(this.guiRight() - this.border.w - 2, this.topPos + this.border.h + 12);
    this.slider.setSize(this.imageHeight - this.border.h * 2 - 2 - 12);
    this.updateSliderParameters();
  }

  /** JEI module area: collapsed tabs report nothing; the overlay reports its centered rect (leftPos/imageWidth already moved by {@link #updatePosition}). */
  @Override
  public Rect2i getArea() {
    if (this.hidden) {
      return new Rect2i(0, 0, 0, 0);
    }
    return super.getArea();
  }

  /** Draws the panel unconditionally at its current position; the collapsed-tier overlay pass calls this above the slots (the normal module pass is gated off by {@link #hidden}/{@link #overlayMode}). */
  public void drawOverlay(GuiGraphicsExtractor graphics, float partialTicks, int mouseX, int mouseY) {
    beginOverlayStratum(graphics);
    this.renderBg(graphics, partialTicks, mouseX, mouseY);
  }

  /**
   * Starts a new GUI stratum for the collapsed-tier overlays (this info panel and the station's selector overlay), which draw
   * manually after the parent's whole render(). Everything submitted afterwards paints over what was drawn before, so the
   * centered overlay covers the station's faded tool icon and the slots (replaces clearing the depth buffer before 1.21.6).
   */
  public static void beginOverlayStratum(GuiGraphicsExtractor graphics) {
    graphics.nextStratum();
  }

  /**
   * Sets the panel's hover tooltips (help "?" and per-line entries) unconditionally; the overlay pass calls this since the
   * normal tooltip pass is gated off by {@link #overlayMode}. Tooltips are deferred to the end of the frame, so they always
   * draw above the panel; see {@link GuiUtil} for the first-wins/focused rule they follow.
   */
  public void drawOverlayTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    this.renderTooltip(graphics, mouseX, mouseY);
  }

  public void setCaption(Component caption) {
    this.caption = caption.copy().withStyle(ChatFormatting.UNDERLINE);
    this.updateSliderParameters();
  }

  public void setText(Component... text) {
    List<Component> textComponents = new ArrayList<>(Arrays.asList(text));

    this.setText(textComponents, null);
  }

  public void setText(List<Component> text) {
    this.setText(text, null);
  }

  public void setText(List<Component> text, @Nullable List<Component> tooltips) {
    this.text = text;
    this.updateSliderParameters();

    this.setTooltips(tooltips);
  }

  protected void setTooltips(@Nullable List<Component> tooltips) {
    this.tooltips = tooltips;
  }

  public boolean hasCaption() {
    return this.caption != null && !this.caption.getString().isEmpty();
  }

  public boolean hasTooltips() {
    return this.tooltips != null && !this.tooltips.isEmpty();
  }

  @SuppressWarnings("ConstantConditions")  // well yes, if the screen is initialized it is
  public boolean hasInitialized() {
    return this.font != null;
  }

  public int calcNeededHeight() {
    int neededHeight = 0;

    if (!this.hasInitialized()) {
      return height;
    }

    int scaledFontHeight = this.getScaledFontHeight();
    if (this.hasCaption()) {
      neededHeight += scaledFontHeight;
      neededHeight += 3;
    }

    neededHeight += (scaledFontHeight + 0.5f) * this.getTotalLines().size();

    return neededHeight;
  }

  protected void updateSliderParameters() {
    // we assume slider not shown
    this.slider.hide();

    int h = imageHeight - 2 * 5; // we use 5 as border thickness

    // check if we can display all lines
    if (this.calcNeededHeight() <= h)
    // can display all, stay hidden
    {
      return;
    }

    // we need the slider
    this.slider.show();
    // check how many lines we can show
    int scaledFontHeight = this.getScaledFontHeight();
    int neededHeight = this.calcNeededHeight(); // recalc because width changed due to slider
    int hiddenRows = (neededHeight - h) / scaledFontHeight;

    if ((neededHeight - h) % scaledFontHeight > 0) {
      hiddenRows++;
    }

    this.slider.setSliderParameters(0, hiddenRows, 1);
  }

  protected List<FormattedCharSequence> getTotalLines() {
    int w = this.imageWidth - this.border.w * 2 + 2;

    if (!this.slider.isHidden()) {
      w -= this.slider.width + 3;
    }

    w = (int) ((float) w / this.textScale);

    List<FormattedCharSequence> lines = Lists.newLinkedList();
    this.tooltipLines.clear();

    for (Component textComponent : this.text) {
      this.tooltipLines.add(lines.size());

      if (textComponent.getString().isEmpty()) {
        lines.add(Component.empty().getVisualOrderText());
        continue;
      }

      lines.addAll(this.font.split(textComponent, w));
    }

    return lines;
  }

  public InfoPanelScreen<P,C> wood() {
    this.shift(resW + 8, 0);
    this.shiftSlider(6, 0);
    return this;
  }

  public InfoPanelScreen<P,C> metal() {
    this.shift(resW + 8, resH + 8);
    this.shiftSlider(12, 0);
    return this;
  }

  private void shift(int xd, int yd) {
    this.border.borderTop = TOP.shift(xd, yd);
    this.border.borderBottom = BOTTOM.shift(xd, yd);
    this.border.borderLeft = LEFT.shift(xd, yd);
    this.border.borderRight = RIGHT.shift(xd, yd);

    this.border.cornerTopLeft = TOP_LEFT.shift(xd, yd);
    this.border.cornerTopRight = TOP_RIGHT.shift(xd, yd);
    this.border.cornerBottomLeft = BOTTOM_LEFT.shift(xd, yd);
    this.border.cornerBottomRight = BOTTOM_RIGHT.shift(xd, yd);
  }

  private void shiftSlider(int xd, int yd) {
    this.slider = new SliderWidget(SLIDER_NORMAL.shift(xd, yd), SLIDER_HOVER.shift(xd, yd), SLIDER_HOVER.shift(xd, yd), SLIDER_TOP.shift(xd, yd), SLIDER_BOTTOM.shift(xd, yd), SLIDER_BAR.shift(xd, yd));
  }

  @Override
  protected void renderLabels(GuiGraphicsExtractor graphics, int x, int y) {
   // no-op
  }

  @Override
  protected void renderTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    super.renderTooltip(graphics, mouseX, mouseY);

    if (this.tooltips == null) {
      return;
    }

    if (mouseX < this.leftPos || mouseX > this.guiRight()) {
      return;
    }

    // floating over tooltip info?
    int scaledFontHeight = this.getScaledFontHeight();
    if (this.hasTooltips() && mouseX >= this.guiRight() - this.border.w - this.font.width("?") / 2 && mouseX < this.guiRight()
        && mouseY > this.topPos + 5 && mouseY < this.topPos + 5 + scaledFontHeight) {
      // 1.21.8: deferred tooltips are first-wins; the old immediate tooltip drew over any slot tooltip set before it, so mark it focused
      graphics.setTooltipForNextFrame(this.font, this.font.split(Component.translatable("gui.tconstruct.general.hover"), 150), DefaultTooltipPositioner.INSTANCE, mouseX - 155, mouseY, true);
    }

    // are we hovering over an entry?
    float y = getTooltipStart(5 + this.topPos);
    float textHeight = (font.lineHeight + 0.5f) * this.textScale;
    float lowerBound = (this.topPos + this.imageHeight - 5);

    // get the index of the currently hovered line
    int index = -1;
    ListIterator<FormattedCharSequence> iter = this.getTotalLines().listIterator(slider.getValue());

    while (iter.hasNext()) {
      if (y + textHeight > lowerBound) {
        break;
      }

      if (mouseY > y && mouseY <= y + textHeight) {
        index = iter.nextIndex();
        break;
      }
      else {
        iter.next();
      }
      y += textHeight;
    }

    // no line hovered
    if (index < 0) {
      return;
    }

    // get the tooltip index from the hovered line
    int i = 0;
    while (this.tooltipLines.size() > i && index > this.tooltipLines.get(i)) {
      i++;
    }

    if (i >= this.tooltips.size() || this.tooltips.get(i).getString().isEmpty()) {
      return;
    }

    int w = Mth.clamp(this.width - mouseX - 12, 0, 200);

    if (w < 100) {
      mouseX -= 100 - w;
      w = 100;
    }

    List<FormattedCharSequence> lines = this.font.split(this.tooltips.get(i), w);

    graphics.setTooltipForNextFrame(this.font, lines, DefaultTooltipPositioner.INSTANCE, mouseX, (mouseY - lines.size() * this.getScaledFontHeight() / 2), true);
  }

  /**
   * Gets the location of the first tooltip for info tooltips
   */
  protected float getTooltipStart(float y) {
    if (this.hasCaption()) {
      y += this.getScaledFontHeight() + 3;
    }
    return y;
  }

  @Override
  protected void renderBg(GuiGraphicsExtractor graphics, float partialTicks, int mouseX, int mouseY) {
    this.border.draw(graphics);
    // overlay mode sits over the darkened crafting area (ITEM_COVER + faded icon) and panel.png is partly translucent,
    // so blit it a few times to build opacity: keeps the tint near the docked panel and stops the slot patterns
    // beneath from bleeding through at the edges. Docked panels (overlayMode false) blit once, unchanged.
    for (int i = this.overlayMode ? 3 : 1; i > 0; i--) {
      BACKGROUND.drawScaled(graphics, this.leftPos + 4, this.topPos + 4, this.imageWidth - 8, this.imageHeight - 8);
    }

    float y = 5 + this.topPos;
    float x = 5 + this.leftPos;
    int color = 0xfff0f0f0;

    // info ? in the top right corner
    if (this.hasTooltips()) {
      drawString(graphics, HELP_MARKER, guiRight() - this.border.w - this.font.width("?") / 2f, this.topPos + 5, 0xff5f5f5f, false);
    }

    // draw caption
    int scaledFontHeight = this.getScaledFontHeight();
    if (this.hasCaption()) {
      int x2 = this.imageWidth / 2;
      x2 -= this.font.width(this.caption) / 2;

      drawString(graphics, this.caption.getVisualOrderText(), (float) this.leftPos + x2, y, color, true);
      y += scaledFontHeight + 3;
    }

    if (this.text == null || this.text.size() == 0) {
      // no text to draw
      return;
    }

    float textHeight = font.lineHeight + 0.5f;
    float lowerBound = (this.topPos + this.imageHeight - 5) / this.textScale;
    Matrix3x2fStack matrices = graphics.pose();
    matrices.pushMatrix();
    matrices.scale(this.textScale, this.textScale);
    //RenderSystem.scalef(this.textScale, this.textScale, 1.0f);
    x /= this.textScale;
    y /= this.textScale;

    // render shown lines
    ListIterator<FormattedCharSequence> iter = this.getTotalLines().listIterator(this.slider.getValue());
    while (iter.hasNext()) {
      if (y + textHeight - 0.5f > lowerBound) {
        break;
      }

      FormattedCharSequence line = iter.next();
      drawString(graphics, line, x, y, color, true);
      y += textHeight;
    }

    matrices.popMatrix();
    //RenderSystem.scalef(1f / textScale, 1f / textScale, 1.0f);

    //RenderSystem.setShaderTexture(0, BACKGROUND_IMAGE);
    this.slider.update(mouseX, mouseY);
    this.slider.draw(graphics);
  }

  /** Draws text at a sub-pixel position, as {@link GuiGraphicsExtractor#drawString} only takes integer coordinates since 1.21.6 */
  protected void drawString(GuiGraphicsExtractor graphics, FormattedCharSequence text, float x, float y, int color, boolean shadow) {
    Matrix3x2fStack pose = graphics.pose();
    pose.pushMatrix();
    pose.translate(x, y);
    graphics.drawString(this.font, text, 0, 0, color, shadow);
    pose.popMatrix();
  }

  /** COLLAPSED tier: a hidden tab draws nothing; the open overlay is drawn above the slots by {@code ToolTableScreen.render} via {@link #drawOverlay}. */
  @Override
  public void handleDrawGuiContainerBackgroundLayer(GuiGraphicsExtractor graphics, float partialTicks, int mouseX, int mouseY) {
    if (this.hidden || this.overlayMode) {
      return;
    }
    super.handleDrawGuiContainerBackgroundLayer(graphics, partialTicks, mouseX, mouseY);
  }

  /** COLLAPSED tier: a hidden tab is inert; the overlay skips the normal tooltip pass (it would draw under the panel, which renders later). */
  @Override
  public void handleRenderHoveredTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    if (this.hidden || this.overlayMode) {
      return;
    }
    super.handleRenderHoveredTooltip(graphics, mouseX, mouseY);
  }

  @Override
  public boolean handleMouseClicked(double mouseX, double mouseY, int mouseButton) {
    if (this.hidden) {
      return false; // collapsed tab: never intercept; the parent falls through to the tab button
    }
    if (!this.slider.isEnabled()) {
      return false;
    }

    if (mouseButton == 0) {
      if (mouseX >= this.slider.xPos && mouseY >= this.slider.yPos && mouseX <= this.slider.xPos + this.slider.width && mouseY <= this.slider.yPos + this.slider.height) {
        this.slider.handleMouseClicked((int) mouseX, (int) mouseY, mouseButton);
        return true;
      }
    }

    return false;
  }

  @Override
  public boolean handleMouseReleased(double mouseX, double mouseY, int state) {
    if (this.hidden) {
      return false; // collapsed tab: never intercept
    }
    if (!this.slider.isEnabled()) {
      return false;
    }

    this.slider.handleMouseReleased();
    return mouseX >= this.slider.xPos && mouseY >= this.slider.yPos && mouseX <= this.slider.xPos + this.slider.width && mouseY <= this.slider.yPos + this.slider.height;
  }

  @Override
  public boolean handleMouseScrolled(double mouseX, double mouseY, double scrollData) {
    if (this.hidden) {
      return false; // collapsed tab: never intercept
    }
    if (!this.slider.isEnabled() || !this.isMouseInModule((int) mouseX, (int) mouseY) || this.isMouseOverFullSlot(mouseX, mouseY)) {
      return false;
    }

    return this.slider.mouseScrolled(scrollData, true);
  }
}
