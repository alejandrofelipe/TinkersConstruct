# R3 pilot — Mantle fluid render seam (design C) Implementation Plan

> **For agentic workers:** execute task by task; commit after each green step. Design approved by the user 2026-11-26
> ("1" = design C). Source analysis: `2026-11-25-upgrade-architecture-analysis.md` §5 R3.

**Goal:** route Tinkers' remaining *world* fluid rendering through a small Mantle seam expressed in the current two-phase
paradigm, so the 26.2 (M8) world-render rewrite touches Mantle instead of 5 Tinkers files. Fewer places to fix — not
insulation.

**Branch:** `1.21.11` in both repos (after the `v1.21.11-*` release tags; M7 branches from here). No version bump, no
release — the new Mantle API ships with the next release.

**Continue/stop metric (measure before and after, record in the result):** Tinkers *world* fluid-path files that name
`submitCustomGeometry`, `VertexConsumer` or `IClientFluidTypeExtensions`. Baseline 2026-11-26 (read-only map): `RenderUtils`,
`FaucetBlockEntityRenderer`, `ChannelBlockEntityRenderer`, `SmelteryTankRenderer`, `HeatingStructureBlockEntityRenderer`
(+ `TinkerRenderTypes` copy of the fluid render type) → target ≤ 1. GUI (`GuiUtil`), `FluidParticle` and the baked fluid
models are **out of scope** (their 26.1 churn is mechanical → rename-map).

## Design

Mantle (`repo/src/main/java/slimeknights/mantle/client/render/`):
- **`FluidRenderInfo`** (new record): everything a fluid draw needs, captured at **extract** time from a `FluidStack` —
  still/flowing sprite (or their `Identifier`s resolved via `FluidRenderer.getBlockSprite`), tint (ARGB), light level
  (`IClientFluidTypeExtensions`/fluid type), gas flag. `static FluidRenderInfo of(FluidStack)` (empty/absent → `null` or
  an `EMPTY` constant — pick one and document it). This is the **only** place in the world path that touches
  `IClientFluidTypeExtensions`.
- **`FluidRenderer.submit(PoseStack, SubmitNodeCollector, FluidRenderInfo, …cuboid spec…, int light, …)`** covering what
  the Tinkers lambdas do today (single cuboid with from/to + optional opacity for the casting fade-out; faucet/channel
  cuboid lists with rotation and the "below" stream), and **`submitTiledBox(...)`** for the smeltery tank (6 faces, tiled
  per block, the existing `+0.001` z-fight offset, no culling). Signatures use Mantle types (`FluidRenderInfo`,
  `FluidCuboid`, `FaucetFluid`/`ChannelFluids` as appropriate), primitives/JOML, and the current two-phase types
  (`PoseStack`, `SubmitNodeCollector`) only. Do not wrap the pipeline. Existing public methods stay (deprecate only what
  becomes unused by both repos *and* is not meant for downstream — note it, don't delete public API).
- **`MantleRenderTypes.FLUID_NO_CULL`**: Mantle's `FLUID` with culling off (replaces Tinkers' `TinkerRenderTypes.SMELTERY_FLUID`
  copy, which is then deleted).
- Deferred-lambda hygiene: the submit lambdas must only read the captured `FluidRenderInfo` (no fluid-type lookups late in
  the frame).

Tinkers: `library/client/RenderUtils.java` (transparent cuboid + tank), `smeltery/client/render/FaucetBlockEntityRenderer`,
`ChannelBlockEntityRenderer` (its hand-copied `applyRotation` goes away if the seam covers it), `CastingBlockEntityRenderer`,
`SmelteryTankRenderer` (its `getBlockBounds` math may stay in Tinkers if it's smeltery-specific — only the drawing moves),
`HeatingStructureBlockEntityRenderer` (render state carries `FluidRenderInfo` instead of raw stacks if needed),
`client/TinkerRenderTypes` (drop `SMELTERY_FLUID`). Optional, only if trivial: Gauge/ProxyTank/Tank/TankInventory/
FluidEffectProjectile renderers take `FluidRenderInfo` (they already go through Mantle).

## Tasks

### T1 — Coverage first (before touching render code)
- Add uitest scenario **`smeltery_tank_world`**: an assembled smeltery (or foundry) seen from the world with **two fluids,
  one of them a gas** (gas renders flipped from the top), camera fixed; assert pixels in the fluid regions (non-background,
  expected hue per fluid, gas layer present) like `trimmed_armor`/`worn_armor` do; mutation-check it (e.g. skip the tank
  submit → fails). Commit.
- Capture a **baseline** of the scenarios that cover the touched paths — `smeltery_tank_world`, `fluid_blocks`,
  `casting_pour`, `casting_cooling` (+ any other scenario showing faucets/channels/casting) — and keep the PNGs in the
  scratchpad for a pixel diff after T3.

### T2 — Mantle seam
- `FluidRenderInfo`, `FluidRenderer.submit`/`submitTiledBox`, `MantleRenderTypes.FLUID_NO_CULL`, javadoc on each.
- Unit test whatever is testable without a client (e.g. `FluidRenderInfo` equality/empty handling if it can be built
  without registries; skip if it can't — say so).
- Mantle `build` green. Commit (Mantle).

### T3 — Tinkers migration
- Move the sites listed above onto the seam; delete `SMELTERY_FLUID` and the duplicated faucet/channel lambda bodies.
- Full battery: Tinkers `build` (796 unit), `runGameTestServer` (159/159), full `runClientUiTest` (49/49 with the new
  scenario). Pixel-diff each T1 baseline PNG against the new run: identical except known animation (flowing fluid frames,
  casting progress) — inspect every diff region by eye; any real change is a regression to fix.
- Commit per logical step (Tinkers).

### T4 — Measure, document, decide
- Recount the metric (before → after) and the M8 exposure (Tinkers files that would need edits for the 26.2 world-render
  rewrite on the fluid path).
- Docs: Mantle API addition in the relevant Mantle doc (`repo/docs/` client/rendering guide if one exists, else the
  1.21.10→1.21.11 guide's "Mantle API changes" note for the next release), the result + continue/stop recommendation in
  the analysis doc §5 R3 and in the roadmap's R1–R5 section (next R3 candidates if it paid off: item-in-world rendering,
  GUI tank widgets). Commit.

## Global constraints
- PowerShell; `JAVA_HOME` = `C:\Users\aleja\scoop\apps\temurin21-jdk\current` in the same command as Gradle; read
  `repo/docs/COMMANDS.md` before running Gradle; daemon disabled (slow cold starts are normal).
- Conventional commits in English; **never** `Co-Authored-By` nor "Generated with Claude Code". No push/tag.
- Process hygiene after every run (kill only processes whose command line matches 'New Tinkers').
- uitest needs the unlocked screen.

## Result

_(filled in at T4)_
