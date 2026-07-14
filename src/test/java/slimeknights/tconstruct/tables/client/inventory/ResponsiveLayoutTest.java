package slimeknights.tconstruct.tables.client.inventory;

import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.tables.client.inventory.ResponsiveLayout.Spec;
import slimeknights.tconstruct.tables.client.inventory.ResponsiveLayout.Tier;

import static org.assertj.core.api.Assertions.assertThat;
import static slimeknights.tconstruct.tables.client.inventory.ResponsiveLayout.computeLayout;

class ResponsiveLayoutTest {
  private static final int WINDOW = 176;

  @Test void fullWhenEverythingFits() {
    // 455 GUI (1366x768 auto): avail side = 139; selector needs 128+2, info 126+2 — grazes but fits
    assertThat(computeLayout(455, 256, WINDOW, 0)).isEqualTo(new Spec(Tier.FULL, 6, 126));
  }

  @Test void reflowShavesColumnsFirst() {
    // 427 GUI: side 125 → availLeft 123 → 5 columns (22*5-4=106 fits), info 123
    assertThat(computeLayout(427, 240, WINDOW, 0)).isEqualTo(new Spec(Tier.REFLOW, 5, 123));
  }

  @Test void reflowAt380() {
    // acceptance #2: side 102 → availLeft 100 → 4 columns; info 100 (≥ INFO_MIN)
    assertThat(computeLayout(380, 240, WINDOW, 0)).isEqualTo(new Spec(Tier.REFLOW, 4, 100));
  }

  @Test void vanillaFloorCollapsesInfoKeepsSelector() {
    // acceptance #3: 320 GUI: side 72 → availLeft 70 → 3 columns fit (62); info 70 < 88 → info tabs
    assertThat(computeLayout(320, 240, WINDOW, 0)).isEqualTo(new Spec(Tier.COLLAPSED, 3, 0));
  }

  @Test void doubleChestEatsSelectorSide() {
    // acceptance #4: 380 GUI + 104-wide side inventory: selector side exhausted → selector tab; info reflows
    assertThat(computeLayout(380, 240, WINDOW, 104)).isEqualTo(new Spec(Tier.COLLAPSED, 0, 100));
  }

  @Test void degenerateWindowNeverGoesNegative() {
    // the EMI issue #69 crash class: absurd sizes must yield a valid all-collapsed spec
    assertThat(computeLayout(120, 1, WINDOW, 0)).isEqualTo(new Spec(Tier.COLLAPSED, 0, 0));
    assertThat(computeLayout(0, 0, WINDOW, 0)).isEqualTo(new Spec(Tier.COLLAPSED, 0, 0));
  }

  @Test void selectorWidthMirrorsSideButtonsWidgetSize() {
    assertThat(ResponsiveLayout.selectorWidth(6)).isEqualTo(128); // 18*6 + 4*5
    assertThat(ResponsiveLayout.selectorWidth(2)).isEqualTo(40);
  }
}
