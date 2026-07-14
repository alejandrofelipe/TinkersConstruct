package slimeknights.tconstruct.tables.client.inventory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag.Default;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import slimeknights.mantle.client.SafeClientAccess;
import slimeknights.mantle.client.screen.ModuleScreen;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.GuiUtil;
import slimeknights.tconstruct.library.client.Icons;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.item.ITinkerStationDisplay;
import slimeknights.tconstruct.library.tools.nbt.LazyToolStack;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.utils.TinkerTooltipFlags;
import slimeknights.tconstruct.tables.client.inventory.module.InfoPanelScreen;
import slimeknights.tconstruct.tables.client.inventory.module.SideInventoryScreen;
import slimeknights.tconstruct.tables.client.inventory.widget.PanelTabButton;
import slimeknights.tconstruct.tables.menu.TabbedContainerMenu;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Shared logic for the tinker station and the modifier worktable */
public abstract class ToolTableScreen<T extends BlockEntity, C extends TabbedContainerMenu<T>> extends BaseTabbedScreen<T,C> {
  private static final Component MODIFIERS_TEXT = TConstruct.makeTranslation("gui", "tinker_station.modifiers");
  private static final Component UPGRADES_TEXT = TConstruct.makeTranslation("gui", "tinker_station.upgrades");
  private static final Component TRAITS_TEXT = TConstruct.makeTranslation("gui", "tinker_station.traits");

  private static final ResourceLocation ICON_TEXTURE = TConstruct.getResource("textures/gui/icons.png");
  /** Translation for the rendered armor stand, matches vanilla SmithingScreen.ARMOR_STAND_TRANSLATION */
  private static final Vector3f ARMOR_STAND_TRANSLATION = new Vector3f(0.0F, 0.0F, 0.0F);

  /** Side panels, for tools and modifiers */
  protected final InfoPanelScreen<ToolTableScreen<T,C>,C> tinkerInfo;
  protected final InfoPanelScreen<ToolTableScreen<T,C>,C> modifierInfo;

  /** Responsive layout decision for the current window size, recomputed every init() */
  protected ResponsiveLayout.Spec layoutSpec = new ResponsiveLayout.Spec(ResponsiveLayout.Tier.FULL, ResponsiveLayout.MAX_COLUMNS, ResponsiveLayout.INFO_NATURAL);

  /** Which collapsed-tier panel is currently shown as an on-demand overlay ({@link OverlayPanel#NONE} = none). */
  public enum OverlayPanel { NONE, SELECTOR, TOOL_INFO, MODIFIER_INFO }

  protected OverlayPanel overlayOpen = OverlayPanel.NONE;
  /** Edge tabs registered in {@link #init()} for whichever sides collapsed; empty in the FULL/REFLOW tiers. */
  protected final List<PanelTabButton> panelTabs = new ArrayList<>();

  protected final Player player;
  @Nullable
  protected ArmorStand armorStandPreview;
  protected boolean enableArmorStandPreview = true;
  protected boolean clickedOnArmorStand = false;

  protected int armorStandX = 0;
  protected int armorStandY = 0;
  protected int armorStandScale = 10;
  protected float armorStandAngle = 0;
  protected double armorStandLastMouseX = -1;

  public ToolTableScreen(C c, Inventory playerInventory, Component title) {
    super(c, playerInventory, title);
    this.player = playerInventory.player;

    this.tinkerInfo = new InfoPanelScreen<>(this, c, playerInventory, title);
    this.tinkerInfo.setTextScale(8/9f);
    this.addModule(this.tinkerInfo);

    this.modifierInfo = new InfoPanelScreen<>(this, c, playerInventory, title);
    this.modifierInfo.setTextScale(7/9f);
    this.addModule(this.modifierInfo);
  }

  @Override
  protected void init() {
    super.init();
    if (enableArmorStandPreview) {
      assert this.minecraft != null;
      assert this.minecraft.level != null;
      this.armorStandPreview = new ArmorStand(this.minecraft.level, 0.0D, 0.0D, 0.0D);
      this.armorStandPreview.setNoBasePlate(true);
      this.armorStandPreview.setShowArms(true);
      this.armorStandPreview.yBodyRot = 210.0F;
      this.armorStandPreview.setXRot(25.0F);
      this.armorStandPreview.yHeadRot = this.armorStandPreview.getYRot();
      this.armorStandPreview.yHeadRotO = this.armorStandPreview.getYRot();
    } else {
      // the flag is recomputed on window resizes; drop the stale preview so a disabled stand stops rendering
      this.armorStandPreview = null;
    }

    // COLLAPSED tier: a resize out of COLLAPSED dissolves any open overlay, then re-dock/hide the info panels
    if (this.layoutSpec.tier() != ResponsiveLayout.Tier.COLLAPSED) {
      this.overlayOpen = OverlayPanel.NONE;
    }
    this.applyOverlayState();

    // rebuild the info-panel edge tabs (Screen.init cleared the widgets); TinkerStationScreen adds the selector tab after this
    this.panelTabs.clear();
    if (this.infoCollapsed()) {
      int style = this.panelTabStyle();
      int tabX = this.cornerX + this.realWidth + 2;
      int tabTop = this.cornerY + 20;
      this.panelTabs.add(this.addRenderableWidget(new PanelTabButton(tabX, tabTop, style,
        (g, ix, iy) -> Icons.PATTERN.draw(g, ix, iy), b -> this.openOverlay(OverlayPanel.TOOL_INFO))));
      this.panelTabs.add(this.addRenderableWidget(new PanelTabButton(tabX, tabTop + PanelTabButton.HEIGHT + 2, style,
        (g, ix, iy) -> Icons.INGOT.draw(g, ix, iy), b -> this.openOverlay(OverlayPanel.MODIFIER_INFO))));
    }
  }

  /** True when the info panels collapse to tabs in the current tier ({@code infoPanelWidth == 0}). */
  protected boolean infoCollapsed() {
    return this.layoutSpec.infoPanelWidth() == 0;
  }

  /** Style row for the edge tabs' button art (2 = wood); {@link TinkerStationScreen} overrides for its metal frame. */
  protected int panelTabStyle() {
    return 2;
  }

  /**
   * Opens or toggles a collapsed-tier panel overlay; safe to call from uitest scenarios and the tab buttons.
   * Toggling the already-open panel closes it.
   */
  public void openOverlay(OverlayPanel panel) {
    this.overlayOpen = (this.overlayOpen == panel) ? OverlayPanel.NONE : panel;
    this.applyOverlayState();
  }

  /** The collapsed-tier panel currently shown as an on-demand overlay ({@link OverlayPanel#NONE} = none); read by uitest scenarios to assert the overlay opened before capture. */
  public OverlayPanel getOpenOverlay() {
    return this.overlayOpen;
  }

  /** Pushes {@link #overlayOpen} onto the two info modules (overlay vs hidden vs docked) and repositions them. */
  protected void applyOverlayState() {
    boolean infoCollapsed = this.infoCollapsed();
    this.tinkerInfo.setOverlayMode(infoCollapsed && this.overlayOpen == OverlayPanel.TOOL_INFO);
    this.tinkerInfo.setHidden(infoCollapsed && this.overlayOpen != OverlayPanel.TOOL_INFO);
    this.modifierInfo.setOverlayMode(infoCollapsed && this.overlayOpen == OverlayPanel.MODIFIER_INFO);
    this.modifierInfo.setHidden(infoCollapsed && this.overlayOpen != OverlayPanel.MODIFIER_INFO);
    this.tinkerInfo.updatePosition(this.cornerX, this.cornerY, this.realWidth, this.realHeight);
    this.modifierInfo.updatePosition(this.cornerX, this.cornerY, this.realWidth, this.realHeight);
  }

  /** The info panel currently shown as an overlay, or null (selector/none). */
  @Nullable
  protected InfoPanelScreen<?,?> activeInfoOverlay() {
    return switch (this.overlayOpen) {
      case TOOL_INFO -> this.tinkerInfo;
      case MODIFIER_INFO -> this.modifierInfo;
      default -> null;
    };
  }

  /** True if the point is over any collapsed-tier edge tab (tabs toggle themselves, so an overlay must not dismiss on them). */
  protected boolean isOverPanelTab(double mouseX, double mouseY) {
    for (PanelTabButton tab : this.panelTabs) {
      if (tab.isMouseOver(mouseX, mouseY)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public List<Rect2i> getModuleAreas() {
    List<Rect2i> areas = super.getModuleAreas(); // modules (hidden info panels report an empty rect) + the tabs bar
    for (PanelTabButton tab : this.panelTabs) {
      areas.add(new Rect2i(tab.getX(), tab.getY(), tab.getWidth(), tab.getHeight()));
    }
    return areas;
  }

  @Override
  protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
    if (this.isOverPanelTab(mouseX, mouseY)) {
      return false; // edge tabs sit outside imageWidth; a click on one must not drop the carried stack
    }
    return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, mouseButton);
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
    super.render(graphics, mouseX, mouseY, partialTicks);
    // COLLAPSED tier: the open info panel is skipped in the under-slots module pass; draw it here, above the slots/widgets
    InfoPanelScreen<?,?> overlay = this.activeInfoOverlay();
    if (overlay != null) {
      overlay.drawOverlay(graphics, partialTicks, mouseX, mouseY);
      // the normal tooltip pass is gated off in overlayMode; run it here, above the overlay bg (help "?" + per-line entries)
      overlay.drawOverlayTooltip(graphics, mouseX, mouseY);
    }
  }

  /** Width the chest side-inventory module occupies left of the window, 0 when absent */
  protected int sideInventoryWidth() {
    for (ModuleScreen<?,?> module : this.modules) {
      if (module instanceof SideInventoryScreen<?,?> side && !side.onRightSide()) {
        return side.getArea().getWidth();
      }
    }
    return 0;
  }

  /**
   * Renders the armor stand
   * @param graphics  Graphics instance
   */
  protected void renderArmorStand(GuiGraphics graphics) {
    if (this.armorStandPreview != null) {
      // matches vanilla SmithingScreen.ARMOR_STAND_ANGLE (X 25 degrees plus the Z 180 flip renderEntityInInventory expects in the pose since 1.21), then animate around Y
      Quaternionf pose = new Quaternionf().rotationXYZ(0.43633232F, 0.0F, (float) Math.PI);
      pose.rotateY(this.armorStandAngle);
      InventoryScreen.renderEntityInInventory(graphics, this.armorStandX, this.armorStandY, this.armorStandScale, ARMOR_STAND_TRANSLATION, pose, null, this.armorStandPreview);

      graphics.blit(ICON_TEXTURE, armorStandX - 16, armorStandY - 16, 0, 184, 32, 32);
    }
  }

 /**
  * Setup clickable areas and position for the armor stand
  * @param x         Stand X position
  * @param y         Stand Y position
  * @param scale     Stand size
  */
  protected void setupArmorStandPreview(int x, int y, int scale) {
    this.armorStandX = this.cornerX + x;
    this.armorStandY = this.cornerY + y;
    this.armorStandScale = scale;
  }

  /** Updates the item displayed on the armor stand */
  protected void updateArmorStandPreview(ItemStack stack) {
    if (this.armorStandPreview != null) {
      for(EquipmentSlot equipmentslot : EquipmentSlot.values()) {
        this.armorStandPreview.setItemSlot(equipmentslot, ItemStack.EMPTY);
      }

      if (!stack.isEmpty()) {
        ItemStack copy = stack.copy();
        Item item = stack.getItem();
        if (item instanceof ArmorItem armor) {
          this.armorStandPreview.setItemSlot(armor.getEquipmentSlot(), copy);
        } else {
          this.armorStandPreview.setItemSlot(EquipmentSlot.OFFHAND, copy);
        }
      }

    }
  }

  /** Updates the tool panel area */
  protected void updateToolPanel(LazyToolStack lazyToolStack) {
    ToolStack tool = lazyToolStack.getTool();
    if (tool.getItem() instanceof ITinkerStationDisplay display) {
      tinkerInfo.setCaption(display.getLocalizedName());
      tinkerInfo.setText(display.getStatInformation(tool, Minecraft.getInstance().player, new ArrayList<>(), SafeClientAccess.getTooltipKey(), TinkerTooltipFlags.TINKER_STATION));
    }
    else {
      ItemStack result = lazyToolStack.getStack();
      tinkerInfo.setCaption(result.getHoverName());
      List<Component> list = new ArrayList<>();
      result.getItem().appendHoverText(result, Item.TooltipContext.of(Minecraft.getInstance().level), list, Default.NORMAL);
      tinkerInfo.setText(list);
    }
  }

  /** Updates the modifier panel with relevant info */
  protected void updateModifierPanel(ToolStack tool) {
    RegistryAccess access = player.level().registryAccess();
    List<Component> modifierNames = new ArrayList<>();
    List<Component> modifierTooltip = new ArrayList<>();
    Component title;
    // control displays just traits, bit trickier to do
    if (hasControlDown()) {
      title = TRAITS_TEXT;
      Map<Modifier,Integer> upgrades = tool.getUpgrades().getModifiers().stream()
                                           .collect(Collectors.toMap(ModifierEntry::getModifier, ModifierEntry::getLevel, Integer::sum));
      for (ModifierEntry entry : tool.getModifierList()) {
        Modifier mod = entry.getModifier();
        if (mod.shouldDisplay(true)) {
          int level = entry.getLevel() - upgrades.getOrDefault(mod, 0);
          if (level > 0) {
            ModifierEntry trait = new ModifierEntry(entry.getModifier(), level);
            modifierNames.add(mod.getDisplayName(tool, trait, access));
            modifierTooltip.add(mod.getDescription(tool, trait));
          }
        }
      }
    } else {
      // shift is just upgrades/abilities, otherwise all
      List<ModifierEntry> modifiers;
      if (hasShiftDown()) {
        modifiers = tool.getUpgrades().getModifiers();
        title = UPGRADES_TEXT;
      } else {
        modifiers = tool.getModifierList();
        title = MODIFIERS_TEXT;
      }
      for (ModifierEntry entry : modifiers) {
        Modifier mod = entry.getModifier();
        if (mod.shouldDisplay(true)) {
          modifierNames.add(mod.getDisplayName(tool, entry, access));
          modifierTooltip.add(mod.getDescription(tool, entry));
        }
      }
    }

    modifierInfo.setCaption(title);
    modifierInfo.setText(modifierNames, modifierTooltip);
  }

  @Override
  public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
    // COLLAPSED tier: the open info overlay is modal. The subclass already offered this click to the panel slider
    // before calling super; here, swallow clicks over the overlay and dismiss it on a true outside (non-tab) click.
    InfoPanelScreen<?,?> overlay = this.activeInfoOverlay();
    if (overlay != null) {
      if (overlay.isMouseInModule((int) mouseX, (int) mouseY)) {
        return true;
      }
      if (!this.isOverPanelTab(mouseX, mouseY)) {
        this.openOverlay(OverlayPanel.NONE);
      }
    }

    int armorStandBoxW = this.armorStandScale + 30;
    int armorStandBoxH = this.armorStandScale * 2;
    int armorStandBoxX = this.armorStandX - armorStandBoxW / 2;
    int armorStandBoxY = this.armorStandY - armorStandBoxH + 5;
    this.clickedOnArmorStand = this.enableArmorStandPreview && GuiUtil.isHovered((int) mouseX, (int) mouseY, armorStandBoxX, armorStandBoxY, armorStandBoxW, armorStandBoxH);

    return super.mouseClicked(mouseX, mouseY, mouseButton);
  }

  @Override
  public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
    // COLLAPSED tier: consume scroll over the open info overlay (drives its slider; keeps slots/lists beneath still)
    InfoPanelScreen<?,?> overlay = this.activeInfoOverlay();
    if (overlay != null && overlay.isMouseInModule((int) mouseX, (int) mouseY)) {
      overlay.handleMouseScrolled(mouseX, mouseY, scrollY);
      return true;
    }
    return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
  }

  @Override
  public boolean mouseReleased(double mouseX, double mouseY, int state) {
    this.clickedOnArmorStand = false;
    this.armorStandLastMouseX = -1;

    return super.mouseReleased(mouseX, mouseY, state);
  }

  @Override
  public boolean mouseDragged(double mouseX, double mouseY, int clickedMouseButton, double timeSinceLastClick, double unkowwn) {
    if (this.enableArmorStandPreview && this.clickedOnArmorStand && this.armorStandLastMouseX != -1) {
      this.armorStandAngle += (float) (mouseX - this.armorStandLastMouseX) / 10f;
    }
    this.armorStandLastMouseX = mouseX;

    return super.mouseDragged(mouseX, mouseY, clickedMouseButton, timeSinceLastClick, unkowwn);
  }
}
