package slimeknights.tconstruct.tables.client.inventory;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import lombok.Getter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import slimeknights.mantle.client.screen.ElementScreen;
import slimeknights.mantle.client.screen.ModuleScreen;
import slimeknights.mantle.client.screen.ScalableElementScreen;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.common.network.TinkerNetwork;
import slimeknights.tconstruct.library.client.GuiUtil;
import slimeknights.tconstruct.library.recipe.partbuilder.Pattern;
import slimeknights.tconstruct.library.tools.layout.LayoutIcon;
import slimeknights.tconstruct.library.tools.layout.LayoutSlot;
import slimeknights.tconstruct.library.tools.layout.StationSlotLayout;
import slimeknights.tconstruct.library.tools.layout.StationSlotLayoutLoader;
import slimeknights.tconstruct.library.tools.nbt.LazyToolStack;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;
import slimeknights.tconstruct.tables.client.inventory.widget.PanelTabButton;
import slimeknights.tconstruct.tables.client.inventory.widget.SideButtonsWidget;
import slimeknights.tconstruct.tables.client.inventory.widget.SlotButtonItem;
import slimeknights.tconstruct.tables.client.inventory.widget.TinkerStationButtonsWidget;
import slimeknights.tconstruct.tables.menu.TinkerStationContainerMenu;
import slimeknights.tconstruct.tables.menu.slot.TinkerStationSlot;
import slimeknights.tconstruct.tables.network.TinkerStationRenamePacket;
import slimeknights.tconstruct.tables.network.TinkerStationSelectionPacket;

import javax.annotation.Nonnull;
import java.util.List;

import static slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity.INPUT_SLOT;
import static slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity.TINKER_SLOT;

public class TinkerStationScreen extends ToolTableScreen<TinkerStationBlockEntity,TinkerStationContainerMenu> {
  // titles to display
  private static final Component COMPONENTS_TEXT = TConstruct.makeTranslation("gui", "tinker_station.components");
 // fallback text for crafting with no named slots
  private static final Component ASCII_ANVIL = Component.literal("\n\n")
    .append("       .\n")
    .append("     /( _________\n")
    .append("     |  >:=========`\n")
    .append("     )(  \n")
    .append("     \"\"")
    .withStyle(ChatFormatting.DARK_GRAY);

  // parameters to display the still filled slots when changing layout
  private static final int STILL_FILLED_X = 112;
  private static final int STILL_FILLED_Y = 62;
  private static final int STILL_FILLED_SPACING = 18;

  // texture
  private static final ResourceLocation TINKER_TEXTURE = TConstruct.getResource("textures/gui/tinker.png");
  // texture elements
  private static final ElementScreen ACTIVE_TEXT_FIELD = new ElementScreen(TINKER_TEXTURE, 0, 232, 90, 12, 256, 256);
  private static final ElementScreen ITEM_COVER = ACTIVE_TEXT_FIELD.move(176, 18, 70, 64);
  // slots
  private static final ElementScreen SLOT_BACKGROUND = ACTIVE_TEXT_FIELD.move(176, 0, 18, 18);
  private static final ElementScreen SLOT_BORDER = ACTIVE_TEXT_FIELD.move(194, 0, 18, 18);
  private static final ElementScreen SLOT_SPACE_TOP = ACTIVE_TEXT_FIELD.move(0, 198, 18, 2);
  private static final ElementScreen SLOT_SPACE_BOTTOM = ACTIVE_TEXT_FIELD.move(0, 196, 18, 2);
  // panel
  private static final ElementScreen PANEL_SPACE_LEFT = ACTIVE_TEXT_FIELD.move(0, 196, 5, 4);
  private static final ElementScreen PANEL_SPACE_RIGHT = ACTIVE_TEXT_FIELD.move(9, 196, 9, 4);
  private static final ElementScreen LEFT_BEAM = ACTIVE_TEXT_FIELD.move(0, 202, 2, 7);
  private static final ElementScreen RIGHT_BEAM = ACTIVE_TEXT_FIELD.move(131, 202, 2, 7);
  private static final ScalableElementScreen CENTER_BEAM = new ScalableElementScreen(TINKER_TEXTURE, 2, 202, 129, 7, 256, 256);
  // text boxes
  private static final ElementScreen TEXT_BOX = ACTIVE_TEXT_FIELD.move(0, 244, 90, 12);

  // configurable elements
  protected ElementScreen buttonDecorationTop = SLOT_SPACE_TOP;
  protected ElementScreen buttonDecorationBot = SLOT_SPACE_BOTTOM;
  protected ElementScreen panelDecorationL = PANEL_SPACE_LEFT;
  protected ElementScreen panelDecorationR = PANEL_SPACE_RIGHT;

  protected ElementScreen leftBeam = ACTIVE_TEXT_FIELD.move(0, 0, 0, 0);
  protected ElementScreen rightBeam = ACTIVE_TEXT_FIELD.move(0, 0, 0, 0);
  protected ScalableElementScreen centerBeam = CENTER_BEAM.move(0, 0, 0, 0);

  /** Gets the default layout to apply, the "repair" button */
  @Nonnull @Getter
  private final StationSlotLayout defaultLayout;
  /** Currently selected tool */
  @Nonnull @Getter
  private StationSlotLayout currentLayout;

  // components
  protected EditBox textField;
  protected TinkerStationButtonsWidget buttonsScreen;
  /** Tool layouts backing the selector; stored so the selector can be rebuilt (docked/centered) on overlay toggles */
  private List<StationSlotLayout> layouts;

  /** Maximum available slots */
  @Getter
  private final int maxInputs;
  /** How many of the available input slots are active */
  protected int activeInputs;


  @SuppressWarnings("deprecation")
  public TinkerStationScreen(TinkerStationContainerMenu container, Inventory playerInventory, Component title) {
    super(container, playerInventory, title);

    this.tinkerInfo.yOffset = 5;
    this.modifierInfo.yOffset = this.tinkerInfo.imageHeight + 9;

    this.imageHeight = 184;

    // determine number of inputs
    int max = 5;
    TinkerStationBlockEntity te = container.getTile();
    if (te != null) {
      max = te.getInputCount(); // TODO: not station sensitive
    }
    this.maxInputs = max;

    // large if at least 4, todo can configure?
    if (max > 3) {
      this.metal();
    } else {
      this.wood();
    }
    // apply base slot information
    if (te == null) {
      this.defaultLayout = StationSlotLayout.EMPTY;
    } else {
      this.defaultLayout = StationSlotLayoutLoader.getInstance().get(BuiltInRegistries.BLOCK.getKey(te.getBlockState().getBlock()));
    }
    this.currentLayout = this.defaultLayout;
    this.activeInputs = Math.min(defaultLayout.getInputCount(), max);
  }

  @Override
  public void init() {

    assert this.minecraft != null;

    // workaround to line up the tabs on switching even though the GUI is a tad higher
    this.topPos += 4;
    this.cornerY += 4;

    this.tinkerInfo.xOffset = 2;
    this.tinkerInfo.yOffset = this.centerBeam.h + this.panelDecorationL.h;
    this.modifierInfo.xOffset = this.tinkerInfo.xOffset;
    this.modifierInfo.yOffset = this.tinkerInfo.yOffset + this.tinkerInfo.imageHeight + 4;

    for (ModuleScreen<?,?> module : this.modules) {
      module.topPos += 4;
    }

    int x = (this.width - this.imageWidth) / 2;
    int y = (this.height - this.imageHeight) / 2;
    textField = new EditBox(this.font, x + 80, y + 7, 82, 9, Component.empty());
    textField.setCanLoseFocus(true);
    textField.setTextColor(-1);
    textField.setTextColorUneditable(-1);
    textField.setBordered(false);
    textField.setMaxLength(50);
    textField.setResponder(this::onNameChanged);
    textField.setValue("");
    addWidget(textField);
    textField.visible = false;
    textField.setEditable(false);

    int buttonsStyle = this.maxInputs > 3 ? TinkerStationButtonsWidget.METAL_STYLE : TinkerStationButtonsWidget.WOOD_STYLE;

    List<StationSlotLayout> layouts = Lists.newArrayList();
    // repair layout
    layouts.add(this.defaultLayout);
    // tool layouts
    layouts.addAll(StationSlotLayoutLoader.getInstance().getSortedSlots().stream()
      .filter(layout -> layout.getInputSlots().size() <= this.maxInputs).toList());
    this.layouts = layouts;

    this.layoutSpec = ResponsiveLayout.computeLayout(this.width, this.height, this.imageWidth, sideInventoryWidth());
    int columns = Math.max(1, this.layoutSpec.selectorColumns()); // Phase 1: degrade collapsed selector to 1 column; Phase 2 replaces with the tab
    int infoWidth = this.layoutSpec.infoPanelWidth() > 0 ? this.layoutSpec.infoPanelWidth() : ResponsiveLayout.INFO_MIN; // Phase 2 replaces with tabs
    this.tinkerInfo.setPanelWidth(infoWidth);
    this.modifierInfo.setPanelWidth(infoWidth);

    // if we have more than 5 rows of buttons, offset armor stand down a bit
    // more than 6 rows causes us to just disable it fully
    // same semantics as the old COLUMN_COUNT*5/6 constants, expressed in rows; recomputed every init as columns follow the window size
    int size = layouts.size();
    int rows = SideButtonsWidget.rowsForCount(columns, size);
    int armorY = 195;
    enableArmorStandPreview = true;
    if (rows > 6) {
      enableArmorStandPreview = false;
    } else if (rows > 5) {
      armorY = 210;
    }
    // the armor stand anchor (-55) sits inside the selector zone below 4 columns, so the stand would overlap the buttons
    if (columns < 4) {
      enableArmorStandPreview = false;
    }
    // no room for the stand once either side collapses to a tab
    if (this.layoutSpec.tier() == ResponsiveLayout.Tier.COLLAPSED) {
      enableArmorStandPreview = false;
    }

    // init after we set the enable boolean (super.init() resets overlayOpen + applies the info-panel overlay state)
    super.init();
    this.rebuildSelector();

    // left-edge tab standing in for the collapsed selector (super.init already added the right-edge info tabs)
    if (this.selectorCollapsed()) {
      this.panelTabs.add(this.addRenderableWidget(new PanelTabButton(
        this.cornerX - PanelTabButton.WIDTH - 2, this.cornerY + this.centerBeam.h + this.buttonDecorationTop.h,
        buttonsStyle, (g, ix, iy) -> renderIcon(g, this.currentLayout.getIcon(), ix, iy),
        b -> this.openOverlay(OverlayPanel.SELECTOR))));
    }

    this.setupArmorStandPreview(-55, armorY, 35);

    this.updateLayout();
  }

  /** True when the selector collapses to a tab in the current tier ({@code selectorColumns == 0}). */
  private boolean selectorCollapsed() {
    return this.layoutSpec.selectorColumns() == 0;
  }

  /** True when the selector grid should render/handle input: docked (FULL/REFLOW) or shown as the centered overlay. */
  private boolean selectorVisible() {
    return !this.selectorCollapsed() || this.overlayOpen == OverlayPanel.SELECTOR;
  }

  /**
   * (Re)builds {@link #buttonsScreen} for the current tier and overlay state. The widget's position fields are
   * final, so a move is a reconstruction; the selected layout is preserved via the constructor's pressed flag.
   */
  private void rebuildSelector() {
    if (this.layouts == null) {
      return; // pre-init guard; init() calls this again once layouts/corners exist
    }
    int style = this.maxInputs > 3 ? TinkerStationButtonsWidget.METAL_STYLE : TinkerStationButtonsWidget.WOOD_STYLE;
    if (this.selectorCollapsed() && this.overlayOpen == OverlayPanel.SELECTOR) {
      // centered overlay: a column count that fits the window width (selectorWidth(n) = 22n - 4 <= realWidth)
      int cols = Math.max(ResponsiveLayout.MIN_COLUMNS, Math.min(ResponsiveLayout.MAX_COLUMNS, (this.realWidth + 4) / 22));
      int width = ResponsiveLayout.selectorWidth(cols);
      this.buttonsScreen = new TinkerStationButtonsWidget(this, this.cornerX + (this.realWidth - width) / 2,
        this.cornerY + 18, cols, this.layouts, style);
    } else {
      // docked left of the window (FULL/REFLOW), or parked when collapsed+closed (never rendered/clicked)
      int columns = Math.max(1, this.layoutSpec.selectorColumns());
      this.buttonsScreen = new TinkerStationButtonsWidget(this, this.cornerX - ResponsiveLayout.selectorWidth(columns) - 2,
        this.cornerY + this.centerBeam.h + this.buttonDecorationTop.h, columns, this.layouts, style);
    }
  }

  @Override
  public void openOverlay(OverlayPanel panel) {
    super.openOverlay(panel);   // toggles overlayOpen + repositions the info panels
    this.rebuildSelector();     // the selector position depends on overlayOpen (final position fields ⇒ rebuild)
  }

  @Override
  protected int panelTabStyle() {
    return this.maxInputs > 3 ? TinkerStationButtonsWidget.METAL_STYLE : TinkerStationButtonsWidget.WOOD_STYLE;
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
    super.render(graphics, mouseX, mouseY, partialTicks); // base render + the info-overlay pass
    // COLLAPSED tier: the centered selector overlay draws above the slots (its docked draw in renderBg is gated off)
    if (this.overlayOpen == OverlayPanel.SELECTOR) {
      // same opaque-blit setup as InfoPanelScreen.drawOverlay: this overlay sits over the station's high item-z
      // faded icon, so disable depth test (and reset color/blend) or the icon culls the panel and bleeds through
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
      RenderSystem.disableDepthTest();
      this.buttonsScreen.render(graphics, mouseX, mouseY, partialTicks);
    }
  }

  /** Updates all slots for the current slot layout */
  public void updateLayout() {
    int stillFilled = 0;
    for (int i = 0; i <= maxInputs; i++) {
      Slot slot = this.getMenu().getSlot(i);
      LayoutSlot layoutSlot = currentLayout.getSlot(i);
      if (layoutSlot.isHidden()) {
        // put the position in the still filled line
        slot.x = STILL_FILLED_X - STILL_FILLED_SPACING * stillFilled;
        slot.y = STILL_FILLED_Y;
        stillFilled++;
        if (slot instanceof TinkerStationSlot tinkerSlot) {
          tinkerSlot.deactivate();
        }
      } else {
        slot.x = layoutSlot.getX();
        slot.y = layoutSlot.getY();
        if (slot instanceof TinkerStationSlot tinkerSlot) {
          tinkerSlot.activate(layoutSlot);
        }
      }
    }

    this.updateDisplay();
  }

  @Override
  public void updateDisplay() {
    if (this.tile == null) {
      return;
    }

    // fetch the tool version of the result for the screen
    LazyToolStack lazyResult = tile.getResult();

    // if we have a message, display instead of refreshing the tool
    Component currentError = tile.getCurrentError();
    if (currentError != null) {
      error(currentError);
      return;
    }

    // only get to rename new tool in the station
    // anvil can rename on any tool change
    if (lazyResult == null || (tile.getInputCount() <= 4 && this.getMenu().getSlot(TINKER_SLOT).hasItem())) {
      textField.setEditable(false);
      textField.setValue("");
      textField.visible = false;
    } else if (!textField.visible) {
      textField.setEditable(true);
      textField.setValue("");
      textField.visible = true;
    } else {
      // ensure the text matches
      textField.setValue(tile.getItemName());
    }

    // if there is no result, use the input
    if (lazyResult == null) {
      lazyResult = tile.getTool();
    }
    updateArmorStandPreview(lazyResult.getStack());

    // if the contained stack is modifiable, display some information
    if (lazyResult.hasTag(TinkerTags.Items.MODIFIABLE)) {
      ToolStack tool = lazyResult.getTool();
      updateToolPanel(lazyResult);
      updateModifierPanel(tool);
    }
    // tool build info
    else {
      this.tinkerInfo.setCaption(this.currentLayout.getDisplayName());
      this.tinkerInfo.setText(this.currentLayout.getDescription());

      // for each named slot, color the slot if the slot is filled
      // typically all input slots should be named, or none of them
      MutableComponent fullText = Component.literal("");
      boolean hasComponents = false;
      for (int i = 0; i <= activeInputs; i++) {
        LayoutSlot layout = currentLayout.getSlot(i);
        String key = layout.getTranslationKey();
        if (!layout.isHidden() && !key.isEmpty()) {
          hasComponents = true;
          MutableComponent textComponent = Component.literal(" * ");
          ItemStack slotStack = this.getMenu().getSlot(i).getItem();
          if (!layout.isValid(slotStack)) {
            textComponent.withStyle(ChatFormatting.RED);
          }
          textComponent.append(Component.translatable(key)).append("\n");
          fullText.append(textComponent);
        }
      }
      // if we found any components, set the text, use the anvil if no components
      if (hasComponents) {
        this.modifierInfo.setCaption(COMPONENTS_TEXT);
        this.modifierInfo.setText(fullText);
      } else {
        this.modifierInfo.setCaption(Component.empty());
        this.modifierInfo.setText(ASCII_ANVIL);
      }
    }
  }

  @Override
  protected void drawContainerName(GuiGraphics graphics) {
    graphics.drawString(this.font, this.getTitle(), 8, 8, 4210752, false);
  }

  public static void renderIcon(GuiGraphics graphics, LayoutIcon icon, int x, int y) {
    Pattern pattern = icon.getValue(Pattern.class);
    if (pattern != null) {
      // draw pattern sprite
      GuiUtil.renderPattern(graphics, pattern, x, y);
      return;
    }

    ItemStack stack = icon.getValue(ItemStack.class);
    if (stack != null) {
      graphics.renderItem(stack, x, y);
    }
  }

  @Override
  protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
    this.drawBackground(graphics, TINKER_TEXTURE);

    int x = 0;
    int y = 0;

    // draw the item background
    final float scale = 3.7f;
    final float xOff = 12.5f;
    final float yOff = 22f;

    // render the background icon
    PoseStack renderPose = graphics.pose();
    renderPose.pushPose();
    renderPose.translate(xOff, yOff, 0.0F);
    renderPose.scale(scale, scale, 1.0f);
    renderIcon(graphics, currentLayout.getIcon(), (int) (this.cornerX / scale), (int) (this.cornerY / scale));
    renderPose.popPose();

    // rebind gui texture since itemstack drawing sets it to something else
    RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.82f);
    RenderSystem.enableBlend();
    //RenderSystem.enableAlphaTest();
    //RenderHelper.turnOff();
    RenderSystem.disableDepthTest();
    ITEM_COVER.draw(graphics, this.cornerX + 7, this.cornerY + 18);

    // slot backgrounds, are transparent
    RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.28f);
    if (!this.currentLayout.getToolSlot().isHidden()) {
      Slot slot = this.getMenu().getSlot(TINKER_SLOT);
      SLOT_BACKGROUND.draw(graphics, x + this.cornerX + slot.x - 1, y + this.cornerY + slot.y - 1);
    }
    for (int i = 0; i < this.activeInputs; i++) {
      Slot slot = this.getMenu().getSlot(i + INPUT_SLOT);
      SLOT_BACKGROUND.draw(graphics, x + this.cornerX + slot.x - 1, y + this.cornerY + slot.y - 1);
    }

    // slot borders, are opaque
    RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    for (int i = 0; i <= maxInputs; i++) {
      Slot slot = this.getMenu().getSlot(i);
      if ((slot instanceof TinkerStationSlot tinkerSlot && (!tinkerSlot.isDormant() || slot.hasItem()))) {
        SLOT_BORDER.draw(graphics, x + this.cornerX + slot.x - 1, y + this.cornerY + slot.y - 1);
      }
    }

    // sidebar beams
    y = this.cornerY;
    // selector beams: docked selector only (skipped when collapsed to a tab / shown as the bare centered overlay)
    if (!this.selectorCollapsed()) {
      x = this.buttonsScreen.getLeftPos() - this.leftBeam.w;
      this.leftBeam.draw(graphics, x, y);
      x += this.leftBeam.w;
      x += this.centerBeam.drawScaledX(graphics, x, y, this.buttonsScreen.getImageWidth());
      this.rightBeam.draw(graphics, x, y);
    }

    // info panel beams: only while the panels are docked (skipped when collapsed to tabs)
    if (!this.infoCollapsed()) {
      x = tinkerInfo.leftPos - this.leftBeam.w;
      this.leftBeam.draw(graphics, x, y);
      x += this.leftBeam.w;
      x += this.centerBeam.drawScaledX(graphics, x, y, this.tinkerInfo.imageWidth);
      this.rightBeam.draw(graphics, x, y);
    }

    // draw the decoration for the buttons (docked selector only)
    if (!this.selectorCollapsed()) {
      for (SlotButtonItem button : this.buttonsScreen.getButtons()) {
        this.buttonDecorationTop.draw(graphics, button.getX(), button.getY() - this.buttonDecorationTop.h);
        // don't draw the bottom for the buttons in the last row
        if (button.buttonId < this.buttonsScreen.getButtons().size() - this.buttonsScreen.getColumns()) {
          this.buttonDecorationBot.draw(graphics, button.getX(), button.getY() + button.getHeight());
        }
      }
    }

    // draw the decorations for the panels (docked panels only)
    if (!this.infoCollapsed()) {
      this.panelDecorationL.draw(graphics, this.tinkerInfo.leftPos + 5, this.tinkerInfo.topPos - this.panelDecorationL.h);
      this.panelDecorationR.draw(graphics, this.tinkerInfo.guiRight() - 5 - this.panelDecorationR.w, this.tinkerInfo.topPos - this.panelDecorationR.h);
      this.panelDecorationL.draw(graphics, this.modifierInfo.leftPos + 5, this.modifierInfo.topPos - this.panelDecorationL.h);
      this.panelDecorationR.draw(graphics, this.modifierInfo.guiRight() - 5 - this.panelDecorationR.w, this.modifierInfo.topPos - this.panelDecorationR.h);
    }

    // render slot background icons
    for (int i = 0; i <= maxInputs; i++) {
      Slot slot = this.getMenu().getSlot(i);
      if (!slot.hasItem()) {
        Pattern icon = currentLayout.getSlot(i).getIcon();
        if (icon != null) {
          GuiUtil.renderPattern(graphics, icon, this.cornerX + slot.x, this.cornerY + slot.y);
        }
      }
    }

    RenderSystem.enableDepthTest();

    super.renderBg(graphics, partialTicks, mouseX, mouseY);

    // docked selector draws here (under the slots, to the window's left); the centered overlay draws above slots in render()
    if (!this.selectorCollapsed()) {
      this.buttonsScreen.render(graphics, mouseX, mouseY, partialTicks);
    }

    // text field
    if (textField != null && textField.visible) {
      TEXT_BOX.draw(graphics, this.cornerX + 79, this.cornerY + 5);
      this.textField.render(graphics, mouseX, mouseY, partialTicks);
    }

    renderArmorStand(graphics);
  }



  @Override
  public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
    // COLLAPSED tier: the selector overlay is modal — route clicks to it, dismiss on a true outside (non-tab) click
    if (this.overlayOpen == OverlayPanel.SELECTOR) {
      if (this.buttonsScreen.isMouseOver(mouseX, mouseY)) {
        this.buttonsScreen.handleMouseClicked(mouseX, mouseY, mouseButton);
        return true;
      }
      if (!this.isOverPanelTab(mouseX, mouseY)) {
        this.openOverlay(OverlayPanel.NONE);
      }
    }

    if (this.tinkerInfo.handleMouseClicked(mouseX, mouseY, mouseButton)) {
      return false;
    }

    if (this.modifierInfo.handleMouseClicked(mouseX, mouseY, mouseButton)) {
      return false;
    }

    if (this.selectorVisible() && this.buttonsScreen.handleMouseClicked(mouseX, mouseY, mouseButton)) {
      return false;
    }

    return super.mouseClicked(mouseX, mouseY, mouseButton);
  }

  @Override
  public boolean mouseDragged(double mouseX, double mouseY, int clickedMouseButton, double timeSinceLastClick, double unkowwn) {
    if (this.tinkerInfo.handleMouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick)) {
      return false;
    }

    if (this.modifierInfo.handleMouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick)) {
      return false;
    }

    return super.mouseDragged(mouseX, mouseY, clickedMouseButton, timeSinceLastClick, unkowwn);
  }

  @Override
  public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
    if (this.tinkerInfo.handleMouseScrolled(mouseX, mouseY, delta)) {
      return false;
    }

    if (this.modifierInfo.handleMouseScrolled(mouseX, mouseY, delta)) {
      return false;
    }

    return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
  }

  @Override
  public boolean mouseReleased(double mouseX, double mouseY, int state) {
    if (this.tinkerInfo.handleMouseReleased(mouseX, mouseY, state)) {
      return false;
    }

    if (this.modifierInfo.handleMouseReleased(mouseX, mouseY, state)) {
      return false;
    }

    if (this.selectorVisible() && this.buttonsScreen.handleMouseReleased(mouseX, mouseY, state)) {
      return false;
    }

    return super.mouseReleased(mouseX, mouseY, state);
  }

  /** Returns true if a key changed that requires a display update */
  static boolean needsDisplayUpdate(int keyCode) {
    if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
      return true;
    }
    if (Minecraft.ON_OSX) {
      return keyCode == GLFW.GLFW_KEY_LEFT_SUPER || keyCode == GLFW.GLFW_KEY_RIGHT_SUPER;
    }
    return keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL;
  }

  @Override
  public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
      this.onClose();
      return true;
    }
    if (needsDisplayUpdate(keyCode)) {
      updateDisplay();
    }
    if (textField.canConsumeInput()) {
      textField.keyPressed(keyCode, scanCode, modifiers);
      return true;
    }
    return super.keyPressed(keyCode, scanCode, modifiers);
  }

  @Override
  public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
    if (needsDisplayUpdate(keyCode)) {
      updateDisplay();
    }
    return super.keyReleased(keyCode, scanCode, modifiers);
  }

  @Override
  public void renderSlot(GuiGraphics graphics, Slot slotIn) {
    // don't draw dormant slots with no item
    if (slotIn instanceof TinkerStationSlot && ((TinkerStationSlot) slotIn).isDormant() && !slotIn.hasItem()) {
      return;
    }
    super.renderSlot(graphics, slotIn);
  }

  @Override
  public boolean isHovering(Slot slotIn, double mouseX, double mouseY) {
    if (slotIn instanceof TinkerStationSlot && ((TinkerStationSlot) slotIn).isDormant() && !slotIn.hasItem()) {
      return false;
    }
    return super.isHovering(slotIn, mouseX, mouseY);
  }

  protected void wood() {
    this.tinkerInfo.wood();
    this.modifierInfo.wood();

    this.buttonDecorationTop = SLOT_SPACE_TOP.shift(SLOT_SPACE_TOP.w, 0);
    this.buttonDecorationBot = SLOT_SPACE_BOTTOM.shift(SLOT_SPACE_BOTTOM.w, 0);
    this.panelDecorationL = PANEL_SPACE_LEFT.shift(18, 0);
    this.panelDecorationR = PANEL_SPACE_RIGHT.shift(18, 0);

    this.leftBeam = LEFT_BEAM;
    this.rightBeam = RIGHT_BEAM;
    this.centerBeam = CENTER_BEAM;
  }

  protected void metal() {
    this.tinkerInfo.metal();
    this.modifierInfo.metal();

    this.buttonDecorationTop = SLOT_SPACE_TOP.shift(SLOT_SPACE_TOP.w * 2, 0);
    this.buttonDecorationBot = SLOT_SPACE_BOTTOM.shift(SLOT_SPACE_BOTTOM.w * 2, 0);
    this.panelDecorationL = PANEL_SPACE_LEFT.shift(18 * 2, 0);
    this.panelDecorationR = PANEL_SPACE_RIGHT.shift(18 * 2, 0);

    this.leftBeam = LEFT_BEAM.shift(0, LEFT_BEAM.h);
    this.rightBeam = RIGHT_BEAM.shift(0, RIGHT_BEAM.h);
    this.centerBeam = CENTER_BEAM.shift(0, CENTER_BEAM.h);
  }

  @Override
  public void error(Component message) {
    this.tinkerInfo.setCaption(COMPONENT_ERROR);
    this.tinkerInfo.setText(message);
    this.modifierInfo.setCaption(Component.empty());
    this.modifierInfo.setText(Component.empty());
  }

  @Override
  public void warning(Component message) {
    this.tinkerInfo.setCaption(COMPONENT_WARNING);
    this.tinkerInfo.setText(message);
    this.modifierInfo.setCaption(Component.empty());
    this.modifierInfo.setText(Component.empty());
  }

  /**
   * Called when a tool button is pressed
   * @param layout      Data of the slot selected
   */
  public void onToolSelection(StationSlotLayout layout) {
    this.activeInputs = Math.min(layout.getInputCount(), maxInputs);
    this.currentLayout = layout;
    this.updateLayout();

    // update the active slots and filter in the container
    // this.container.setToolSelection(layout); TODO: needed?
    TinkerNetwork.getInstance().sendToServer(new TinkerStationSelectionPacket(layout.getName()));
  }

  @Override
  public List<Rect2i> getModuleAreas() {
    List<Rect2i> list = super.getModuleAreas();
    // null before the first init(), like tabsScreen in the super method: recipe viewers may query
    // extra areas early (super already logs that case), so degrade instead of crashing.
    // collapsed+closed: the selector is parked off-layout, so report the tab (added by super) instead of the parked grid
    if (this.buttonsScreen != null && this.selectorVisible()) {
      list.add(this.buttonsScreen.getArea());
    }
    return list;
  }

  @Override
  protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
    return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, mouseButton)
      && !this.buttonsScreen.isMouseOver(mouseX, mouseY);
  }


  /* Text field stuff */

  private void onNameChanged(String name) {
    if (tile != null) {
      this.tile.setItemName(name);
      TinkerNetwork.getInstance().sendToServer(new TinkerStationRenamePacket(name));
    }
  }

  @Override
  public void containerTick() {
    super.containerTick();
    // EditBox.tick() was removed in 1.21.1; cursor blink is now time-based
  }

  @Override
  public void resize(Minecraft pMinecraft, int pWidth, int pHeight) {
    String s = this.textField.getValue();
    super.resize(pMinecraft, pWidth, pHeight);
    this.textField.setValue(s);
  }

  @Override
  public void removed() {
    super.removed();
    assert this.minecraft != null;
  }

  @Override
  public void onClose() {
    super.onClose();

    assert this.minecraft != null;
  }
}
