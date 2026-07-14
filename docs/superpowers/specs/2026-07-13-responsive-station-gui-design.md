# Responsive Tinker Station GUI — design spec

Date: 2026-07-13 · Status: approved by user (brainstorm via /research, approach C of 3)

## Context

Real-gameplay testing of the 1.21.1 port surfaced that the Tinker Station's side panels (tool-type
selector on the left, two info panels on the right) do not adapt to the screen: the assembly is a
fixed ~440 GUI px wide and clips at narrow effective GUI sizes. Investigation (2026-07-13) proved
this is **not a port regression** — code and behavior are identical to upstream 1.20.1, which
acknowledges the limitation (`TinkerStationScreen.java:86`: "TODO: a scrollbar for this instead
would be good") and closed the matching report without a fix
([SlimeKnights/TinkersConstruct#3736](https://github.com/SlimeKnights/TinkersConstruct/issues/3736)).
Upstream has no 1.21 branch; this design is original work for our fork.

## Goals

1. The whole Tinker Station **screen** (selector + info panels) renders fully on-screen and stays
   usable at any GUI size down to the vanilla floor (**320×240 GUI px**).
2. Graceful degradation in stages ("approach C"): the familiar full layout whenever it fits, a
   reflowed compact layout when space tightens, tabs + overlay only at the extreme.
3. Visual fidelity: the mod's existing visual vocabulary wherever possible; discreet new elements
   (edge tabs, overlay) only where no in-mod precedent exists, styled from existing textures.
4. Client-side only. No menu, network, or server changes.

## Non-goals

- Other multi-module GUIs (smeltery, part builder, chests) — the shared-base implementation lets
  the Modifier Worktable inherit the behavior for free, but only the Tinker Station is in the
  acceptance matrix.
- Rearranging the main window's own slots/texture (fixed by menu + art).
- Mantle framework changes. Target is **zero Mantle edits**; if the plan proves a minimal hook
  unavoidable, it enters the plan as a documented exception.
- Config options for thresholds (derived from geometry; YAGNI).

## Architecture — layout tiers

A **pure decision function**, evaluated in the screen's `init()` (vanilla re-runs it on every
window resize — no new listeners):

```java
/** Pure: no Minecraft state. Unit-testable. */
static LayoutSpec computeLayout(int guiWidth, int guiHeight, int sideInventoryWidth)
// → record LayoutSpec(Tier tier, int selectorColumns, int infoPanelWidth)
enum Tier { FULL, REFLOW, COLLAPSED }
```

- **FULL** — 6 selector columns + info panels at natural width fit beside the centered main
  window: today's layout, pixel-identical.
- **REFLOW** — `selectorColumns = clamp(floor(availLeft / BUTTON_W), 2, 6)`;
  `infoPanelWidth = clamp(availRight, INFO_MIN, INFO_NATURAL)`. Existing row paging
  (`SideButtonsWidgetPaged`) and the info panels' existing `SliderWidget` scrollbar absorb the
  overflow the reduction creates.
- **COLLAPSED** — entered when fewer than 2 columns fit or `availRight < INFO_MIN`: panels become
  edge tabs + on-demand overlay (below).
- `availLeft`/`availRight` are the spaces beside the centered main window, minus margins and minus
  `sideInventoryWidth` when a chest module is attached (left side).
- Every division is clamped with explicit floors — degenerate inputs (e.g. 120×1, the crash class
  EMI hit in [emilyploszaj/emi#69](https://github.com/emilyploszaj/emi/issues/69)) must produce a
  valid COLLAPSED spec, never negative sizes.
- `INFO_MIN` ≈ 88 GUI px, calibrated visually during implementation (plan task), worst-case
  pt_BR strings included.

Code lives in Tinkers only: `ToolTableScreen` (shared base — decision + state),
`TinkerStationScreen` (wiring), `TinkerStationButtonsWidget` (column count becomes a constructor
parameter instead of the `COLUMN_COUNT = 6` constant), `InfoPanelScreen` (variable width between
`INFO_MIN` and natural).

## Component behavior

- **Selector**: same buttons, wood/metal styles, and page buttons; fewer columns simply means more
  pages. No new UI at FULL/REFLOW.
- **Info panels**: width varies; text wrapping and the existing scrollbar handle the rest. Text
  scale rules unchanged.
- **COLLAPSED**: three edge tabs attached to the main window — selector (left), tool info and
  modifier info (right) — styled from the existing `TabsWidget`/`ElementScreen` wood art. Clicking
  a tab opens exactly **one** panel as a floating overlay centered over the inventory area at
  `min(natural panel width, guiWidth − 16)`; clicking again (or another tab) closes/switches. The overlay **is the module
  itself repositioned and drawn last** — `MultiModuleScreen.getModuleForPoint` hit-testing follows
  module positions, so clicks/scroll/tooltips work unchanged.
- **Armor stand preview**: existing rules stand (already auto-offsets/disables by button count);
  additionally hidden in COLLAPSED.
- **Chest side-inventory module** (upstream's known worst case, #3736): it contains slots, so it
  has priority — REFLOW computes selector columns from the space left after it; in COLLAPSED the
  selector collapses to its tab while the side inventory stays (its own scrollbar already handles
  vertical overflow). Slots are never hidden behind an overlay-only path.

## State & integration

- Client state: current `Tier` + `openOverlay ∈ {NONE, SELECTOR, TOOL_INFO, MODIFIER_INFO}`,
  recomputed on `init()`; when a resize leaves COLLAPSED, the overlay dissolves back to side
  panels.
- **M5 invariants preserved**: the `WrapperSlot` x/y copy in `MultiModuleScreen.renderSlot` and
  the single-background render order are untouched — modules containing slots reposition as whole
  units exactly as they already do (slot clicks send indices, not positions).
- **JEI exclusion areas**: the plugin's `IGuiContainerHandler` must report the side panels (and
  the overlay when open) so JEI's panel does not overlap. Plan task verifies whether the current
  handler reads live module positions (then it is automatic) or returns fixed rects (then it
  becomes tier-aware).

## Edge cases

- Resize mid-interaction (overlay open, armor-stand drag): recompute dissolves state gracefully.
- The rename `EditBox` lives inside the fixed main window — untouched.
- Degenerate window sizes: COLLAPSED fits by construction (window 176 + 2×9 px tabs = 194 « 320).
- pt_BR strings (~15% longer than en_US) are the wrap test's worst case at `INFO_MIN`.

## Testing

- **Unit**: table-driven tests for `computeLayout` — width/height → expected tier/columns/width,
  including clamp floors and degenerate inputs. Joins the 185-test suite.
- **uitest**: three new scenarios that resize the game window in `prepare` (via
  `Minecraft.getWindow()`, restored in `close`): `station_reflow` (760×480 @ scale 2 = GUI
  380×240), `station_collapsed` (640×480 @ scale 2 = GUI 320×240), `station_collapsed_overlay`
  (same size, opens a tab, captures). The existing `tinker_station` scenario runs at auto GUI
  scale (1280×720 → 427 GUI px, which is REFLOW by this spec's own thresholds — a Task-2 finding
  correcting this line's original "gates FULL" claim) and now gates the reflowed layout; FULL-tier
  pixel-equivalence to the legacy layout was proven empirically in Task 2 (scale-2 retro-diff
  against the pre-change code, 0 differing GUI pixels) and stays gated by the unit tests' FULL
  case. Suite 9 → 12; controller gates each PNG (nothing clipped, selector paging visible, overlay
  legible).
- **Battery**: `test` and `runGameTestServer` must stay green (client-only change).

## Delivery — two independently shippable phases

1. **Phase 1 — FULL + REFLOW**: tiers wired, columns/width adapt; at the extreme, degrade to
   minimum reflow (clipping already eliminated for nearly all real screens). Ships alone.
2. **Phase 2 — COLLAPSED**: tabs + overlay + the two collapsed uitest scenarios.

## Acceptance criteria

1. At GUI ≥ ~440 px wide: pixel-identical to today (FULL).
2. At GUI 380×240: no element clipped; selector ≥ 3 columns; info panels readable (REFLOW PNG).
3. At GUI 320×240: no element clipped; tabs render; overlay opens, is legible, and closes
   (COLLAPSED PNGs).
4. Double-chest-attached station at 380 GUI: side inventory intact, selector reflows around it.
5. `computeLayout` unit tests green; full battery green; suite 12/12, zero FATAL.

## References

- [Upstream issue #3736 — GUI does not fit on screen](https://github.com/SlimeKnights/TinkersConstruct/issues/3736) (closed, no fix)
- [Upstream branches — no 1.21 development](https://github.com/SlimeKnights/TinkersConstruct/branches)
- [EMI](https://github.com/emilyploszaj/emi) — space-driven column reflow (`EmiScreenManager$ScreenSpace`)
- [EMI issue #69 — NegativeArraySizeException on tiny windows](https://github.com/emilyploszaj/emi/issues/69) (the clamp lesson)
- [NeoForge docs — Screens (1.21.1)](https://docs.neoforged.net/docs/1.21.1/gui/screens/) — `init()` re-runs on resize; `leftPos/topPos/imageWidth` semantics
- In-repo precedents: `SideButtonsWidgetPaged` (row paging), Mantle `SliderWidget` (info scroll), `TabsWidget` (tab art), M5 multi-module fixes (WrapperSlot copy, render order)
