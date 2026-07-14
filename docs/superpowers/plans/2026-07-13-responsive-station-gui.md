# Responsive Tinker Station GUI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Tinker Station screen (tool selector + info panels) adapts to any GUI size down to the vanilla floor (320×240), per spec `docs/superpowers/specs/2026-07-13-responsive-station-gui-design.md` (commit `1716731eee`).

**Architecture:** A pure `ResponsiveLayout.computeLayout` decision function evaluated in `init()` (vanilla re-runs it on resize) yields per-side results: selector column count (6→2, then a tab at 0) and info panel width (natural→88, then tabs at 0). Phase 1 ships FULL+REFLOW; Phase 2 ships the COLLAPSED tabs+overlay. Client-side only; zero Mantle edits; M5 multi-module fixes untouched.

**Tech Stack:** Java 21, NeoForge 21.1.234, existing in-repo widgets (`SideButtonsWidgetPaged`, `SliderWidget`, `ElementScreen`), JUnit 5, Mantle uitest suite.

## Global Constraints

- Canonical gradle (PowerShell, JAVA_HOME in the SAME command, `-p` into tinkers):
  `$env:JAVA_HOME = "C:\Users\aleja\scoop\apps\temurin21-jdk\current"; & "C:\Users\aleja\DEV\New Tinkers\tinkers\gradlew.bat" -p "C:\Users\aleja\DEV\New Tinkers\tinkers" <task>`
- **Foreground agents: every gradle call SYNCHRONOUS** (PowerShell tool, timeout up to 600000 ms, never run_in_background — turn teardown kills background chains). Daemon disabled: slow cold start ≠ failure.
- Uitest freshness: results count only with a fresh `suite armed; N scenario(s)` log line, results.json timestamp after run start, zero `Error executing task`/FATAL lines.
- Commits: conventional-commits English, trailer `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`. Commit only your files; `git status --short` clean after each task.
- Process sweep after runs (Win32_Process filter from workspace CLAUDE.md) — must be empty before reporting.
- When a vanilla/JEI signature matters, verify against the decompiled sources in `C:\Users\aleja\scoop\apps\gradle\current\.gradle\caches\` (Glob for the class) or javap the JEI jar BEFORE editing. Sanctioned-adjustment convention: if reality differs from a snippet here, follow reality and report the delta.
- Verified geometry constants used throughout (do not re-derive): `SlotButtonItem.WIDTH/HEIGHT = 18`; `SideButtonsWidget` SPACING = 4 → `size(n,18) = 22n − 4`; selector gap to window = 2 (`TinkerStationScreen.init`); `InfoPanelScreen` natural width = `resW + 8` = 126, xOffset = 2; main window `imageWidth` = 176 (assert in T1 Step 5).

---

## Fase 1 — FULL + REFLOW (shippable alone)

### Task 1: `ResponsiveLayout` pure decision function + unit tests

**Files:**
- Create: `src/main/java/slimeknights/tconstruct/tables/client/inventory/ResponsiveLayout.java`
- Test: `src/test/java/slimeknights/tconstruct/tables/client/inventory/ResponsiveLayoutTest.java`

**Interfaces:**
- Produces: `ResponsiveLayout.Spec computeLayout(int guiWidth, int guiHeight, int mainWindowWidth, int sideInventoryWidth)`; `record Spec(Tier tier, int selectorColumns, int infoPanelWidth)`; `enum Tier {FULL, REFLOW, COLLAPSED}`; `static int selectorWidth(int columns)`; constants `INFO_MIN=88`, `INFO_NATURAL=126`, `MIN_COLUMNS=2`, `MAX_COLUMNS=6`. Semantics later tasks rely on: `selectorColumns == 0` → selector collapses to a tab; `infoPanelWidth == 0` → info panels collapse to tabs; `tier == COLLAPSED` iff either side collapsed (per-side sharpening of the spec — document in the class javadoc citing the spec).
- Note: signature adds `mainWindowWidth` vs the spec's 3-arg sketch (worktable width may differ from 176) — sanctioned, cite in commit body.

- [ ] **Step 1: Write the failing tests** (plain JUnit — the class must have NO Minecraft imports, so no `BaseMcTest` bootstrap):

```java
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
```

- [ ] **Step 2: Run to verify they fail**

Run: canonical gradle `test --tests "slimeknights.tconstruct.tables.client.inventory.ResponsiveLayoutTest"`
Expected: FAIL — class `ResponsiveLayout` does not exist (compile error is the expected red here).

- [ ] **Step 3: Implement**

```java
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
```

- [ ] **Step 4: Run the tests — all green.** Same command; expected `BUILD SUCCESSFUL`, 7 passed.
- [ ] **Step 5: Sanity-assert the window constant** — Read `TinkerStationScreen` (`imageHeight = 184` is set; `imageWidth` comes from `AbstractContainerScreen` default). Grep the class + parents for `imageWidth =` — expected: no override, so 176 (vanilla default). If it differs, fix the test constant `WINDOW`, not the function.
- [ ] **Step 6: Full unit suite** — canonical `test`: expected 185 + 7 new passed / 2 skipped / 0 failed.
- [ ] **Step 7: Commit** — `feat(tables): pure responsive layout decision for tool table screens`.

### Task 2: Wire REFLOW into the screens (columns + panel width)

**Files:**
- Modify: `src/main/java/slimeknights/tconstruct/tables/client/inventory/TinkerStationScreen.java` (init, ~lines 185-212; COLUMN_COUNT uses)
- Modify: `src/main/java/slimeknights/tconstruct/tables/client/inventory/ToolTableScreen.java` (field + helper)
- Modify: `src/main/java/slimeknights/tconstruct/tables/client/inventory/module/InfoPanelScreen.java` (width setter)

**Interfaces:**
- Consumes: `ResponsiveLayout.computeLayout/selectorWidth/Spec` (Task 1).
- Produces: `ToolTableScreen.layoutSpec` (protected field, recomputed each `init()`); `ToolTableScreen.sideInventoryWidth()` (protected int); `InfoPanelScreen.setPanelWidth(int)`. Task 5 relies on `layoutSpec` and on tier COLLAPSED semantics.

- [ ] **Step 1: `InfoPanelScreen.setPanelWidth`** — add below the constructor:

```java
  /** Sets the panel width; text wrapping and the slider re-derive from imageWidth (getTotalLines/updateSliderParameters). */
  public void setPanelWidth(int width) {
    this.imageWidth = net.minecraft.util.Mth.clamp(width, ResponsiveLayout.INFO_MIN, resW + 8);
    if (this.hasInitialized()) {
      this.updateSliderParameters();
    }
  }
```

- [ ] **Step 2: `ToolTableScreen`** — add the spec field + side-inventory probe. First grep how a chest attaches: `Grep "SideInventoryScreen" src/main/java/slimeknights/tconstruct/tables` — read the module class it uses and its `imageWidth`. Then:

```java
  /** Responsive layout decision for the current window size, recomputed every init() */
  protected ResponsiveLayout.Spec layoutSpec = new ResponsiveLayout.Spec(ResponsiveLayout.Tier.FULL, ResponsiveLayout.MAX_COLUMNS, ResponsiveLayout.INFO_NATURAL);

  /** Width the chest side-inventory module occupies left of the window, 0 when absent */
  protected int sideInventoryWidth() {
    for (ModuleScreen<?,?> module : this.modules) {
      if (module instanceof SideInventoryScreen<?,?> side && !side.right) {  // verify field/accessor while implementing
        return side.imageWidth;
      }
    }
    return 0;
  }
```
(Adapt the instanceof to the real class found in Step 2's grep; if `right` is not readable, add a package-visible accessor on the Tinkers module subclass — NOT on Mantle.)

- [ ] **Step 3: `TinkerStationScreen.init()`** — compute the spec FIRST, apply widths. Replace the buttons construction and armor-stand threshold block (current lines 194-209):

```java
    this.layoutSpec = ResponsiveLayout.computeLayout(this.width, this.height, this.imageWidth, sideInventoryWidth());
    int columns = Math.max(1, this.layoutSpec.selectorColumns()); // Phase 1: degrade collapsed selector to 1 column; Phase 2 replaces with the tab
    int infoWidth = this.layoutSpec.infoPanelWidth() > 0 ? this.layoutSpec.infoPanelWidth() : ResponsiveLayout.INFO_MIN; // Phase 2 replaces with tabs
    this.tinkerInfo.setPanelWidth(infoWidth);
    this.modifierInfo.setPanelWidth(infoWidth);

    // armor stand: same semantics as the old COLUMN_COUNT*5/6 constants, expressed in rows
    int size = layouts.size();
    int rows = SideButtonsWidget.rowsForCount(columns, size);
    int armorY = 195;
    if (rows > 6) {
      enableArmorStandPreview = false;
    } else if (rows > 5) {
      armorY = 210;
    }

    super.init();
    this.buttonsScreen = new TinkerStationButtonsWidget(this, this.cornerX - ResponsiveLayout.selectorWidth(columns) - 2,
      this.cornerY + this.centerBeam.h + this.buttonDecorationTop.h, columns, layouts, buttonsStyle);
```
`TinkerStationButtonsWidget` gains the `int columns` parameter (replace both `TinkerStationScreen.COLUMN_COUNT` uses inside it with the parameter; keep `COLUMN_COUNT` as the Phase-1 max in `ResponsiveLayout.MAX_COLUMNS`'s comment and delete the screen constant + `width(int)` helper if now unused — grep callers first). The armor stand `setupArmorStandPreview(-55, armorY, 35)` x-anchor sits inside the selector zone: when `columns < 4`, also `enableArmorStandPreview = false` (the stand would overlap buttons; note the rationale inline).

- [ ] **Step 4: Compile + FULL regression** — canonical `compileJava` green, then `runClientUiTest`: 9/9 ok, zero FATAL; `tinker_station.png` must be pixel-equivalent to the pre-change run at 1280×720 (FULL tier — acceptance #1). Keep the pre-change PNG copy for comparison. *(Executed correction: 1280×720 at auto scale is 427 GUI = REFLOW, not FULL — the canonical PNG legitimately changed to the un-clipped 5-column layout; FULL equivalence was instead proven by a scale-2 retro-diff against stashed old code, 0 differing GUI pixels. Spec Testing section updated accordingly.)*
- [ ] **Step 5: Commit** — `feat(tables): tinker station selector and info panels reflow to window size`.

### Task 3: JEI exclusion areas for the tool table screens

**Files:**
- Create: `src/main/java/slimeknights/tconstruct/plugin/jei/util/ToolTableGuiHandler.java`
- Modify: `src/main/java/slimeknights/tconstruct/plugin/jei/JEIPlugin.java` (`registerGuiHandlers`)

**Interfaces:**
- Consumes: `MultiModuleScreen.getModuleAreas()` (exists, returns live `List<Rect2i>`); `SideButtonsWidget.getArea()` (exists); `TinkerStationScreen.buttonsScreen`.
- Produces: JEI keeps its panel off the side modules and (Phase 2) the overlay automatically — areas are read live every frame.

- [ ] **Step 1:** Verify against the JEI 19 jar (javap `mezz.jei.api.registration.IGuiHandlerRegistration` + `mezz.jei.api.gui.handlers.IGuiContainerHandler`): the `addGuiContainerHandler(Class, IGuiContainerHandler)` signature and `getGuiExtraAreas` default. `GuiContainerTankHandler` in the same package is the in-repo registration example — read how JEIPlugin registers it.
- [ ] **Step 2:** Implement:

```java
package slimeknights.tconstruct.plugin.jei.util;

import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import net.minecraft.client.renderer.Rect2i;
import slimeknights.tconstruct.tables.client.inventory.TinkerStationScreen;
import slimeknights.tconstruct.tables.client.inventory.ToolTableScreen;

import java.util.List;

/** Reports the live side-module and selector areas so JEI's panel never overlaps them (any tier). */
public class ToolTableGuiHandler implements IGuiContainerHandler<ToolTableScreen<?,?>> {
  @Override
  public List<Rect2i> getGuiExtraAreas(ToolTableScreen<?,?> screen) {
    List<Rect2i> areas = screen.getModuleAreas();
    if (screen instanceof TinkerStationScreen station && station.getButtonsScreen() != null) {
      areas.add(station.getButtonsScreen().getArea());
    }
    return areas;
  }
}
```
Add a `@Getter` (or plain getter) for `buttonsScreen` on `TinkerStationScreen`; register in `JEIPlugin.registerGuiHandlers` beside the tank handler with the raw class cast idiom the file already uses.

- [ ] **Step 3:** Compile green; `runClientUiTest` 9/9 (the `jei_melting_category` + `tinker_station` scenarios both exercise JEI presence).
- [ ] **Step 4: Commit** — `feat(jei): exclusion areas for tool table side modules and selector`.

### Task 4: `station_reflow` uitest scenario (window-resize harness) + Phase-1 battery

**Files:**
- Modify: `src/main/java/slimeknights/tconstruct/client/uitest/TinkerUiTestScenarios.java` (+1 scenario, registered LAST — suite 9→10)

**Interfaces:**
- Consumes: `UiTestScenario` (`id/prepare/prepareSettleTicks/open/settleTicks/close`, `ctx.mc()`), `BlockGuiScenario` as the station-opening reference, Task 2's reflow behavior.
- Produces: the resize-harness pattern (`resizeWindow(ctx, w, h)` private static helper) that Task 6's scenarios reuse.

- [ ] **Step 1:** Verify the resize API in the decompiled `com.mojang.blaze3d.platform.Window` (Glob the gradle caches): expected `setWindowed(int width, int height)`; also note `getWidth/getHeight` for restore. If the real name differs, adapt and report.
- [ ] **Step 2:** Read `BlockGuiScenario` (same file) for how `tinker_station` places + opens the station; read how the double chest attaches (Task 2 Step 2 grep — place the station with a double chest so acceptance #4 is in the same PNG: two adjacent `Blocks.CHEST` with matching `ChestType` facing, verify against the menu's attachment logic).
- [ ] **Step 3:** Add (following the file's javadoc/comment conventions, fail-fast style):

```java
  /** Reflow tier: 760x480 @ auto scale 2 = 380x240 GUI; station + double chest — guards acceptance #2 and #4. */
  private static class StationReflowScenario implements UiTestScenario {
    private int restoreW = 1280, restoreH = 720;

    @Override
    public ResourceLocation id() {
      return TConstruct.getResource("station_reflow");
    }

    @Override
    public void prepare(UiTestContext ctx) {
      restoreW = ctx.mc().getWindow().getWidth();
      restoreH = ctx.mc().getWindow().getHeight();
      ctx.mc().execute(() -> ctx.mc().getWindow().setWindowed(760, 480));
      // place station + double chest rig here (mirror BlockGuiScenario's placement + chest pair)
    }

    @Override
    public void open(UiTestContext ctx) {
      // open the station GUI exactly like BlockGuiScenario does (use-block on the placed station)
    }

    @Override
    public int settleTicks() {
      return 30; // resize + reinit + panel reflow settle
    }

    @Override
    public void close(UiTestContext ctx) {
      ctx.mc().setScreen(null);
      ctx.mc().execute(() -> ctx.mc().getWindow().setWindowed(restoreW, restoreH));
    }
  }
```
Fill the two placement comments with real code copied from the sibling scenarios (they are the law; the plan cannot inline them without drifting — fail-fast if the BE/screen is absent, per house style).
- [ ] **Step 4:** Compile green; `runClientUiTest`: `suite armed; 10 scenario(s)`, 10/10 ok, zero FATAL. Inspect `station_reflow.png` yourself for sanity (nothing clipped, ≥3 selector columns visible or selector tab if the chest eats the side, chest slots visible, and the narrowed info text still readable — pt_BR runs ~15% longer, so if the wrap looks cramped at this width, raising `INFO_MIN` is the spec-sanctioned calibration knob) — the controller does the binding visual gate.
- [ ] **Step 5:** Phase-1 battery: `build` green, `test` (192/2/0), `runGameTestServer` (`All 5 required tests passed`).
- [ ] **Step 6: Commit** — `test(uitest): station reflow scenario with window-resize harness`.

---

## Fase 2 — COLLAPSED (tabs + overlay)

### Task 5: Collapsed tabs + overlay state, rendering, and input

**Files:**
- Modify: `src/main/java/slimeknights/tconstruct/tables/client/inventory/ToolTableScreen.java` (overlay state + tabs + render/input hooks)
- Modify: `src/main/java/slimeknights/tconstruct/tables/client/inventory/module/InfoPanelScreen.java` (overlay positioning mode)
- Modify: `src/main/java/slimeknights/tconstruct/tables/client/inventory/TinkerStationScreen.java` (selector tab when `selectorColumns == 0`)

**Interfaces:**
- Consumes: `layoutSpec` (Task 2), `ModuleScreen.updatePosition(parentX, parentY, parentSizeX, parentSizeY)` override point, `MultiModuleScreen` render order (module bg in `renderBg` BEFORE slots; module fg in `renderLabels` after).
- Produces: `ToolTableScreen.openOverlay(OverlayPanel)` public (Task 6's scenario calls it); `enum OverlayPanel { NONE, SELECTOR, TOOL_INFO, MODIFIER_INFO }`.

- [ ] **Step 1: Overlay positioning on the module** — `InfoPanelScreen` gains an overlay flag honored in its existing `updatePosition` override:

```java
  /** When set, the panel centers over the parent window instead of docking to a side (COLLAPSED overlay). */
  @Setter
  private boolean overlayMode = false;

  // inside updatePosition(parentX, parentY, parentSizeX, parentSizeY), FIRST line:
    if (this.overlayMode) {
      this.imageWidth = Math.min(resW + 8, parentSizeX - 16);       // spec: min(natural, guiWidth-16) bounded by the window
      this.leftPos = parentX + (parentSizeX - this.imageWidth) / 2;
      this.topPos = parentY + 18;                                    // below the title/beam, over the inventory area
      this.border.setPosition(this.leftPos, this.topPos);
      this.border.setSize(this.imageWidth, this.imageHeight);
      this.slider.setPosition(this.guiRight() - this.border.w - 2, this.topPos + this.border.h + 12);
      this.slider.setSize(this.imageHeight - this.border.h * 2 - 2 - 12);
      this.updateSliderParameters();
      return;
    }
```
(Overlay spans within the window, so `MultiModuleScreen.updateSubmodule`'s bounds-expansion sees nothing to expand — leftPos/topPos stay inside `[cornerX, cornerX+realWidth]`; verify by reading `updateSubmodule` while implementing.)

- [ ] **Step 2: State + tabs on `ToolTableScreen`:**

```java
  public enum OverlayPanel { NONE, SELECTOR, TOOL_INFO, MODIFIER_INFO }

  protected OverlayPanel overlayOpen = OverlayPanel.NONE;
  protected final List<PanelTabButton> panelTabs = new ArrayList<>();

  /** Opens/toggles a collapsed panel overlay; safe to call from uitest scenarios. */
  public void openOverlay(OverlayPanel panel) {
    this.overlayOpen = (this.overlayOpen == panel) ? OverlayPanel.NONE : panel;
    this.applyOverlayState();
  }

  /** Applies overlayMode to the two info modules + repositions; called from openOverlay and init. */
  protected void applyOverlayState() {
    this.tinkerInfo.setOverlayMode(this.overlayOpen == OverlayPanel.TOOL_INFO);
    this.modifierInfo.setOverlayMode(this.overlayOpen == OverlayPanel.MODIFIER_INFO);
    this.tinkerInfo.updatePosition(this.cornerX, this.cornerY, this.realWidth, this.realHeight);
    this.modifierInfo.updatePosition(this.cornerX, this.cornerY, this.realWidth, this.realHeight);
  }
```
`PanelTabButton` = small `Button` subclass drawing an `ElementScreen` wood tab + the panel's icon, positioned hugging the window edge (left for SELECTOR when `layoutSpec.selectorColumns() == 0`; right stack for the two info tabs when `layoutSpec.infoPanelWidth() == 0`). Art: reuse `SlotButtonItem`'s `Icons.BUTTON` trio (shift for wood style, exactly like `TinkerStationButtonsWidget.addInfoButton` does) — no new textures. Tabs are `addRenderableWidget`-registered in `init()` only for collapsed sides; when a side is NOT collapsed its module renders exactly as in Phase 1 (`setPanelWidth`), and collapsed modules are parked off-layout (`setPanelWidth(INFO_MIN)` + `xOffset` pushing them fully off-screen is FORBIDDEN — instead gate their bg/fg draw on `overlayMode || !collapsed`, see Step 3).

- [ ] **Step 3: Render order** — module backgrounds draw in `renderBg` (before slots): a centered overlay drawn there would sit UNDER the slot items. Override in `ToolTableScreen`:

```java
  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
    super.render(graphics, mouseX, mouseY, partialTicks);
    // overlay pass: draw the open panel ABOVE slots/widgets (module bg+fg at its overlay position)
    InfoPanelScreen<?,?> overlay = switch (this.overlayOpen) {
      case TOOL_INFO -> this.tinkerInfo;
      case MODIFIER_INFO -> this.modifierInfo;
      default -> null;
    };
    if (overlay != null) {
      overlay.handleDrawGuiContainerBackgroundLayer(graphics, partialTicks, mouseX, mouseY);
      PoseStack poses = graphics.pose();
      poses.pushPose();
      poses.translate(overlay.getGuiLeft() - this.leftPos, overlay.getGuiTop() - this.topPos, 0);
      overlay.handleDrawGuiContainerForegroundLayer(graphics, mouseX, mouseY);
      poses.popPose();
    }
  }
```
And suppress the normal side-draw of collapsed modules: in the same class, skip a module in the inherited bg/fg loops when it is collapsed-and-not-the-open-overlay (check `MultiModuleScreen.renderBg/renderLabels` — they loop `modules`; the cleanest Tinkers-side seam is a `protected boolean shouldRenderModule(ModuleScreen<?,?>)` hook... `MultiModuleScreen` has no such hook and Mantle is frozen — instead gate INSIDE `InfoPanelScreen.handleDrawGuiContainerBackgroundLayer/Foreground` with a `hidden` flag set by `applyOverlayState`; verify both loops route through those handle* methods — they do, lines 87-96 of `ModuleScreen`).
The SELECTOR overlay (only when `selectorColumns == 0`): reposition `buttonsScreen` to the window center in `init()` when `overlayOpen == SELECTOR` — it is screen-owned (not a module), so just construct it at the centered position and render/click it only while open (it already renders via the screen's own hooks — grep `buttonsScreen` uses in `TinkerStationScreen.render/mouse*` and gate them).

- [ ] **Step 4: Input** — clicks inside the open overlay's rect must not fall through to slots beneath: in `ToolTableScreen.mouseClicked`, BEFORE `super`, if overlay open and `overlay.isMouseInModule((int)mouseX, (int)mouseY)` → route to `overlay.handleMouseClicked` (and swallow: `return true` even on false — the overlay area is modal); clicking OUTSIDE the overlay closes it (`openOverlay(NONE)`) before normal handling. Mirror for `mouseScrolled` (route to `handleMouseScrolled` — the slider must work) and `mouseReleased`. Resize dissolves state: in `init()`, if `layoutSpec.tier() != COLLAPSED` force `overlayOpen = NONE` before `applyOverlayState()`. Armor stand: `enableArmorStandPreview = false` whenever `tier == COLLAPSED`.
- [ ] **Step 5:** Compile green; manual-free check via existing suite: `runClientUiTest` 10/10 (nothing collapses at 1280×720 — this guards NO regression on FULL; collapsed coverage arrives in Task 6).
- [ ] **Step 6: Commit** — `feat(tables): collapsed tier tabs and overlay panels for narrow windows`.

### Task 6: Collapsed uitest scenarios + final battery + docs

**Files:**
- Modify: `src/main/java/slimeknights/tconstruct/client/uitest/TinkerUiTestScenarios.java` (+2 scenarios, registered LAST — suite 10→12)
- Modify: `../repo/docs/COMMANDS.md` (scenario count 10→12 + the two new ids; Mantle repo commit)

**Interfaces:**
- Consumes: Task 4's resize harness pattern; Task 5's `ToolTableScreen.openOverlay(OverlayPanel.TOOL_INFO)`.

- [ ] **Step 1:** `StationCollapsedScenario` (`station_collapsed`): clone the reflow scenario at `setWindowed(640, 480)` (auto scale 2 → GUI 320×240), plain station (no chest). Expected on screen: selector at 3 columns, two info tabs on the right, no info panels, nothing clipped (acceptance #3 first half).
- [ ] **Step 2:** `StationCollapsedOverlayScenario` (`station_collapsed_overlay`): same size; in `open()`, after opening the station exactly like the sibling, fail-fast cast `ctx.mc().screen` to `TinkerStationScreen` and call `screen.openOverlay(ToolTableScreen.OverlayPanel.TOOL_INFO)`; settle 30; capture (acceptance #3 second half: overlay legible). `close()` restores window size.
- [ ] **Step 3:** Compile green; `runClientUiTest`: `suite armed; 12 scenario(s)`, 12/12 ok, zero FATAL, fresh. Inspect the 3 responsive PNGs; controller does the binding gates.
- [ ] **Step 4:** Full battery: `build` → `test` (192/2/0) → `runGameTestServer` (5) → suite already green in Step 3.
- [ ] **Step 5:** COMMANDS.md: update the scenario count and id list (the file's "drives the N registered scenarios" line) — commit in the MANTLE repo (`C:\Users\aleja\DEV\New Tinkers\repo`): `docs: uitest suite count 10->12 (responsive station scenarios)`.
- [ ] **Step 6: Commit (tinkers)** — `test(uitest): collapsed-tier station scenarios`. Report the battery evidence — the controller closes PROGRESS.md and declares the feature complete.
