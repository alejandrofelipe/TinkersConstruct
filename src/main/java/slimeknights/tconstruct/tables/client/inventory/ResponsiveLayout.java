package slimeknights.tconstruct.tables.client.inventory;

/**
 * Pure layout-tier decision for the tool table screens. No Minecraft imports — unit-tested directly.
 * Per-side sharpening of the design spec (docs/superpowers/specs/2026-07-13-responsive-station-gui-design.md):
 * each side collapses independently ({@code selectorColumns == 0} / {@code infoPanelWidth == 0});
 * {@link Tier#COLLAPSED} means "at least one side collapsed" and gates the armor stand + tabs.
 * Every division is clamped — degenerate windows (the EMI #69 crash class) yield a valid spec.
 */
public final class ResponsiveLayout {
  /** SlotButtonItem.WIDTH (18) + SideButtonsWidget button SPACING (4) */
  private static final int BUTTON_CELL = 22;
  /** gap between the selector widget and the main window (TinkerStationScreen.init: -2) */
  private static final int SELECTOR_GAP = 2;
  /** InfoPanelScreen xOffset */
  private static final int INFO_GAP = 2;
  /** InfoPanelScreen natural width: resW (118) + 8 */
  public static final int INFO_NATURAL = 126;
  /** minimum readable info panel width (spec ~88; calibrated visually in Task 2) */
  public static final int INFO_MIN = 88;
  public static final int MIN_COLUMNS = 2;
  public static final int MAX_COLUMNS = 6;

  public enum Tier { FULL, REFLOW, COLLAPSED }
  public record Spec(Tier tier, int selectorColumns, int infoPanelWidth) {}

  private ResponsiveLayout() {}

  /** Selector widget width at the given column count; mirrors {@code SideButtonsWidget.size(columns, 18)}. */
  public static int selectorWidth(int columns) {
    return 18 * columns + 4 * (columns - 1);
  }

  public static Spec computeLayout(int guiWidth, int guiHeight, int mainWindowWidth, int sideInventoryWidth) {
    int side = Math.max(0, (guiWidth - mainWindowWidth) / 2);
    int availLeft = Math.max(0, side - SELECTOR_GAP - Math.max(0, sideInventoryWidth));
    int availRight = Math.max(0, side - INFO_GAP);

    // width(n) = 22n - 4 <= availLeft  →  n <= (availLeft + 4) / 22
    int columns = Math.min(MAX_COLUMNS, (availLeft + 4) / BUTTON_CELL);
    int infoWidth = Math.min(INFO_NATURAL, availRight);

    boolean selectorCollapsed = columns < MIN_COLUMNS;
    boolean infoCollapsed = infoWidth < INFO_MIN;
    if (selectorCollapsed || infoCollapsed) {
      return new Spec(Tier.COLLAPSED, selectorCollapsed ? 0 : columns, infoCollapsed ? 0 : infoWidth);
    }
    if (columns == MAX_COLUMNS && infoWidth == INFO_NATURAL) {
      return new Spec(Tier.FULL, MAX_COLUMNS, INFO_NATURAL);
    }
    return new Spec(Tier.REFLOW, columns, infoWidth);
  }
}
