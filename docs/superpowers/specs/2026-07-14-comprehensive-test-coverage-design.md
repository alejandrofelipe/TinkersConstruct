# Comprehensive test coverage — design spec

Date: 2026-07-14 · Status: approved by user (brainstorm; option 1 on all three scoping questions)

## Context

The TinkersConstruct 1.21.1/NeoForge port is complete and shipped (suite: **192 unit + 5 gametests
+ 12 uitest**), but there is **no coverage measurement** (no JaCoCo) and the large crafting/melting
content surface is only thinly exercised — the existing gametests are `tool_crafting`,
`smeltery_melts`, `smeltery_casts`, `alloyer_alloys` (one representative each). Historically it was
real gameplay, not the automated suites, that surfaced the runtime/data bugs (empty fluid outputs,
unpopulated tags, capability/NBT gaps). This work adds measured coverage tooling, substantially
expands unit coverage of the testable core, and builds a comprehensive crafting/melting/casting/
alloying test layer that provably exercises each mechanism **and** validates every recipe's data.

## Goals

1. **Measurable coverage** — a JaCoCo report over the testable-logic packages, so the real coverage
   number is visible (there is none today).
2. **Substantially raised unit coverage** on the core logic (`library/` etc.), targeting ~80% as a
   direction (report-only, not a build gate).
3. **Comprehensive crafting/melting/casting/alloying tests** — representative in-world gametests that
   prove each mechanism end-to-end, plus near-exhaustive **data-driven validation** that iterates
   every recipe of each type checking it is well-formed and resolvable.
4. The **full battery stays green** (build / test / gametest / uitest).

## Non-goals

- **Literal 100% line coverage** — impractical on ~1902 files / 187k LOC and low-value; registration/
  `DeferredRegister` boilerplate, client render, and datagen are excluded from the coverage
  denominator (they are covered by uitest / `runData` / load, not unit tests).
- **A build-failing coverage gate** — report-only per user decision; no `jacocoTestCoverageVerification`.
- **Unit-testing client render, registration, or datagen** — out of the coverage target by design.
- **Re-enabling the 2 `@Disabled` layout tests** (`LayoutIconTest.item_bufferReadWrite`,
  `StationSlotLayoutTest`) — blocked by NeoForge's `RegistryManager` sync-gate on ItemStack/Ingredient
  stream codecs in bare JUnit (no public seam); stays deferred.
- **Mantle coverage/tests** — this effort targets the Tinkers repo. (If a data-driven check surfaces a
  Mantle bug, fix it there as an in-flight exception, like prior port work.)

## A. Coverage tooling (JaCoCo)

- Add the **`jacoco`** Gradle plugin to `tinkers/build.gradle`, wired to the `test` task, producing
  **HTML + XML** reports at `build/reports/jacoco/test/`. Verify it coexists with the Gradle 9.2.1 /
  NeoGradle 7.1.38 test runtime (first implementation step is "JaCoCo produces a report at all").
- **Honest denominator** — `jacocoTestReport.classDirectories` **excludes** the code that is not
  unit-testable in isolation, so the percentage reflects real logic: `**/client/**` (screens/render →
  uitest), datagen providers (`**/data/**`, `**/*Provider*` → `runData`), `**/plugin/**` (compat/JEI),
  registration classes (`Tinker*` register/`DeferredRegister` holders, `**/*Registration*`), generated
  sources, and mixins.
- **Coverage target** is the pure-logic surface: `library/` (tools, materials, modifiers, recipe,
  stats, utils), plus the logic parts of `smeltery`, `tools`, `common`, `shared`. **Report-only**;
  ~80% is a direction to approach, not a gate.
- Document in `repo/docs/COMMANDS.md` (Mantle repo, where the test-pillar docs live): how to run the
  report and where it lands.

## B. Crafting / melting / casting / alloying tests

### B1. Representative in-world gametests
Extend `ToolGameTests` and `SmelteryGameTests`, reusing the `SmelteryRigs` builders and the existing
fail-fast gametest patterns (arena template, `IN_STRUCTURE` formation, direct-body assertions where
possible). Cover each mechanism with a representative sample (not every material/recipe — that is B2):

- **Tool crafting** — build tool definitions spanning **1/2/3-part** tools (e.g. pickaxe, hand axe,
  broadsword, sledge hammer) via `TinkerStationBlockEntity.calcResult` (the menu's own recipe path)
  with representative materials (wood / stone / iron); assert a valid `ToolStack`, durability > 0, the
  expected materials, and the expected tool item — mirroring the existing `tool_crafting` test.
- **Part building** — build a part (e.g. pickaxe head) on the Part Builder from a material + pattern;
  assert the part item and its material.
- **Melting** — melt representative inputs (iron ingot / ore / block / raw iron; a non-metal such as
  glass or obsidian) in the melter/smeltery; assert the output fluid and amount.
- **Casting** — cast representative cast types (ingot cast → ingot, gem cast, rod cast, plate cast)
  from a fluid on the casting table/basin; assert the output item.
- **Alloying** — alloy representative alloys (e.g. bronze from copper + tin; one 3-input alloy) in the
  alloyer; assert the output fluid and amount.

### B2. Data-driven recipe validation (near-exhaustive, no in-world ticking)
A gametest (it has a `ServerLevel` and a fully-loaded `RecipeManager`) iterates **every** recipe of
each type and validates well-formedness — cheap because it inspects the recipe objects, it does not
tick a smeltery per recipe:

- **Melting** (`RecipeTypes.MELTING`): input ingredient resolves to ≥ 1 real item; output `FluidStack`
  non-empty; temperature > 0; time > 0.
- **Casting** (table + basin casting recipe types): the cast (item/tag) and the input fluid resolve;
  the output item is non-empty.
- **Alloying** (`RecipeTypes.ALLOYING`): every input fluid resolves and is non-empty; the output fluid
  is non-empty; the ratios are sane.
- (Optional, if cheap) every registered tool material has the stat types its parts require.

On failure the test reports the offending recipe id(s), so it pinpoints latent data bugs (empty
output, unpopulated tag) across the **whole** recipe set at once. **Known-intentional latent recipes**
(pre-existing upstream recipes referencing tags nothing populates — same as 1.20) are handled
explicitly: the validation **fails on any malformed recipe by default**, and each id confirmed
intentional-latent during implementation is added to a small **documented allow-list** the test skips,
so it fails only on **new** breakage. The implementer confirms the
exact recipe-type registry keys and the casting recipe class(es) against the source before writing.

### "e2e"
For a Minecraft mod there is no separate e2e layer: the in-world **gametests** (server-side flows) and
the **uitest** (client GUI flows) *are* the end-to-end tests. B1 + B2 + the existing uitest constitute
the e2e coverage; no new harness is introduced.

## C. Unit-test expansion

Driven by the JaCoCo baseline from Phase 1 — target the **lowest-coverage, highest-value** classes in
the scoped packages: tool stat computation, material stats, modifier hooks/logic, recipe matching,
loadable serialization round-trips (where the registry-sync gate does not block — the ItemStack/
Ingredient stream-codec cases stay `@Disabled`), and math/util. Reuse the existing `BaseMcTest`
harness (registry bootstrap + `LoadingModList` guard) and `TestHelper`. Push the scoped-package
coverage substantially toward ~80%.

## Phasing

- **Phase 1** — JaCoCo wired + baseline coverage measured + the B1 representative gametests + the B2
  data-driven validation. Deliverable: a measured baseline number and comprehensive, provable
  crafting/melting/casting/alloying coverage.
- **Phase 2** — unit-test expansion (C) guided by the Phase-1 baseline gaps, raising the scoped % toward
  ~80%. Its exact scope is set from the measured baseline (Phase 1 tells us how much unit work remains),
  and the user decides from that number whether to push the last mile.

## Testing / verification

- **Full battery green**: `build`, `test` (all unit incl. new), `runGameTestServer` (all gametests incl.
  new B1/B2), `runClientUiTest` (12/12, unaffected).
- **JaCoCo report** generated; the scoped-package % captured (Phase 1 baseline, and after Phase 2).
- **B2 validation** runs over 100% of melting/casting/alloy recipes and is green, or each surfaced
  failure is triaged (fixed, or added to the documented known-intentional allow-list).
- Suite counts updated in `repo/docs/COMMANDS.md`.

## Acceptance criteria

1. `gradlew test` produces a **JaCoCo report** and the scoped-package coverage % is reported (baseline +
   post-Phase-2).
2. The B1 representative crafting/melting/casting/alloying gametests are **green**.
3. The B2 data-driven validation iterates **every** melting/casting/alloy recipe and is green (failures
   triaged: fixed or documented-intentional).
4. Scoped-package unit coverage is **substantially raised** toward ~80% (Phase 2), reported before/after.
5. **Full battery green**; the new suite counts are documented.

## References

- Existing tests/infra: `slimeknights.tconstruct.gametest.ToolGameTests`, `SmelteryGameTests`,
  `SmelteryRigs`; `slimeknights.tconstruct.test.BaseMcTest` + `TestHelper`; the uitest suite
  (`TinkerUiTestScenarios` + Mantle `UiTestSuite`, now with the `-PuitestOnly` filter).
- Recipe sources: the smeltery recipe providers under `smeltery/data/` (melting/casting/alloy); recipe
  types under the Tinkers `RecipeTypes`/registry.
- Deferred/known: the 2 `@Disabled` layout tests (NeoForge registry-sync gate); intentionally-latent
  upstream recipes (allow-list in B2).
- Commands: `repo/docs/COMMANDS.md` (the three test pillars).
