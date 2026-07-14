# Comprehensive Test Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add measured coverage tooling (JaCoCo), comprehensive crafting/melting/casting/alloying gametests (representative in-world + near-exhaustive data-driven recipe validation), then a baseline-driven unit-test expansion of the core logic.

**Architecture:** Phase 1 is fully specified here — JaCoCo report (T1) + data-driven recipe validation (T2) + representative smeltery (T3) and tool (T4) gametests. Phase 2 (unit-test expansion toward ~80% on the scoped packages) is planned from T1's measured baseline, because its concrete targets are the classes JaCoCo shows as lowest-coverage.

**Tech Stack:** Gradle 9.2.1 + NeoGradle 7.1.38, JaCoCo, JUnit 5 + Mockito + AssertJ (existing test deps), NeoForge GameTest framework (`@GameTestHolder`/`@GameTest`), MC 1.21.1 / NeoForge 21.1.234, JDK 21.

## Global Constraints

- Canonical gradle — PowerShell, `JAVA_HOME` in the SAME command, `-p` into tinkers:
  `$env:JAVA_HOME = "C:\Users\aleja\scoop\apps\temurin21-jdk\current"; & "C:\Users\aleja\DEV\New Tinkers\tinkers\gradlew.bat" -p "C:\Users\aleja\DEV\New Tinkers\tinkers" <task>`
- **Foreground agents: every gradle call SYNCHRONOUS** (PowerShell tool, timeout ≤ 600000 ms, never run_in_background). Daemon disabled — a slow cold start is not a failure.
- Gametest run = `runGameTestServer`; unit = `test`; both must stay green. A gametest suite is only meaningfully green with tests PRESENT (a zero-test run still exits 0).
- Commits: conventional-commits English, trailer `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`. Commit only your files; `git status --short` clean after each task.
- Process sweep after every run (workspace CLAUDE.md filter) — must be empty before reporting.
- **Sanctioned-adjustment convention:** the recipe-interface getter names and the Part Builder API below are to be confirmed against the real source while implementing; if reality differs from a snippet here, follow reality and report the delta. No sub-agents, no web research.
- Spec: [`docs/superpowers/specs/2026-07-14-comprehensive-test-coverage-design.md`](../specs/2026-07-14-comprehensive-test-coverage-design.md).

---

## Phase 1 — tooling + crafting/melting coverage

### Task 1: JaCoCo report + measured baseline + docs

**Files:**
- Modify: `build.gradle` (plugins block line 6-12; the `test { useJUnitPlatform() }` line 163; add a `jacocoTestReport` block)
- Modify: `../repo/docs/COMMANDS.md` (document the coverage report — Mantle repo, where the test-pillar docs live)

**Interfaces:**
- Produces: a JaCoCo HTML+XML report at `build/reports/jacoco/test/`, and the measured baseline coverage % on the scoped packages (recorded in the task report — Phase 2 targets are chosen from it).

- [ ] **Step 1:** Add the JaCoCo plugin. In `build.gradle` plugins block (after `id 'io.freefair.lombok' version '8.6'`):

```groovy
    id 'jacoco'
```

- [ ] **Step 2:** Wire the report to `test` and configure the honest denominator. Replace the existing `test { useJUnitPlatform() }` (line 163) with:

```groovy
jacoco { toolVersion = '0.8.12' }

test {
    useJUnitPlatform()
    finalizedBy jacocoTestReport
}

// Coverage TARGET is the pure-logic surface. Exclude code that is not unit-testable in isolation so the
// percentage reflects real logic (these are covered by uitest / runData / load, not unit tests): client
// render/screens, datagen providers, compat plugins, registration holders, and generated/mixin classes.
jacocoTestReport {
    dependsOn test
    reports {
        xml.required = true
        html.required = true
    }
    def coverageExclusions = [
        'slimeknights/tconstruct/**/client/**',
        'slimeknights/tconstruct/**/*Screen*',
        'slimeknights/tconstruct/**/*Renderer*',
        'slimeknights/tconstruct/**/*Model*',
        'slimeknights/tconstruct/**/data/**',
        'slimeknights/tconstruct/**/*Provider*',
        'slimeknights/tconstruct/plugin/**',
        'slimeknights/tconstruct/gametest/**',
        'slimeknights/tconstruct/**/Tinker*',      // DeferredRegister holders (TinkerTools, TinkerSmeltery, ...)
    ]
    classDirectories.setFrom(files(classDirectories.files.collect {
        fileTree(dir: it, exclude: coverageExclusions)
    }))
}
```
(The `Tinker*` glob catches the registration holder classes whose names all start with `Tinker`; confirm it does not swallow a logic class you want measured — if it does, tighten the glob and report the delta.)

- [ ] **Step 3:** Run the suite with the report (SYNC):
  `<canonical gradle> test`
  Expected: `BUILD SUCCESSFUL`; unit results unchanged (**192 passing / 2 skipped / 0 failing**); a report exists at `build/reports/jacoco/test/html/index.html` and `build/reports/jacoco/test/jacocoTestReport.xml`.

- [ ] **Step 4:** Read the **baseline coverage %** for the scoped packages from `jacocoTestReport.xml` (the top-level `<counter type="INSTRUCTION">` and `type="LINE"`, and per-package under `slimeknights/tconstruct/library/**`). Record: overall scoped line %, and the 8-10 lowest-coverage `library/` packages/classes (these seed Phase 2). Put these numbers in your task report verbatim.

- [ ] **Step 5:** Document in `../repo/docs/COMMANDS.md` — under the test-pillar section, add a short "Coverage (JaCoCo)" note: run `gradlew test`; the report is at `tinkers/build/reports/jacoco/test/html/index.html`; the % excludes client/datagen/registration/plugin (it measures the logic core).

- [ ] **Step 6:** Sweep processes (must be empty). Commit: `test(coverage): add JaCoCo report over the logic core`. Report the baseline numbers.

### Task 2: Data-driven recipe validation gametest (near-exhaustive)

**Files:**
- Create: `src/main/java/slimeknights/tconstruct/gametest/RecipeValidationGameTests.java`

**Interfaces:**
- Consumes: `TinkerRecipeTypes.MELTING` (`RecipeType<IMeltingRecipe>`), `CASTING_TABLE`/`CASTING_BASIN` (`RecipeType<ICastingRecipe>`), `ALLOYING` (`RecipeType<AlloyRecipe>`) — all `DeferredHolder`s in `slimeknights.tconstruct.library.recipe.TinkerRecipeTypes`. `GameTestHelper.getLevel().getRecipeManager()` returns the fully datapack-loaded `RecipeManager`.
- Produces: 3 gametests (`all_melting_recipes_valid`, `all_casting_recipes_valid`, `all_alloy_recipes_valid`) — suite 5 → 8.

- [ ] **Step 1:** Read `IMeltingRecipe`, `ICastingRecipe`, and `AlloyRecipe` (under `slimeknights/tconstruct/library/recipe/{melting,casting,alloying}`) to learn the exact getters: for melting the input ingredient/items, the output `FluidStack`, temperature, and time; for casting the cast (item/tag) + input fluid + output item; for alloying the input fluids + output fluid. The validation checks below name the CONCEPT — bind them to the real getters you find (report any that don't exist).

- [ ] **Step 2:** Write `RecipeValidationGameTests` mirroring `SmelteryGameTests`'s annotations. The melting method (the other two follow the same shape):

```java
package slimeknights.tconstruct.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.melting.IMeltingRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Data-driven: validates that every melting/casting/alloy recipe is well-formed (no in-world ticking). */
@PrefixGameTestTemplate(false)
@GameTestHolder(TConstruct.MOD_ID)
public class RecipeValidationGameTests {

  /** Recipe ids confirmed intentional-latent (input tag nothing populates, same as upstream 1.20). */
  private static final Set<String> KNOWN_INTENTIONAL = Set.of(
    // populated during Step 4 from the first run's triage, each with a one-line justification comment
  );

  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void all_melting_recipes_valid(GameTestHelper helper) {
    RecipeManager recipes = helper.getLevel().getRecipeManager();
    List<String> bad = new ArrayList<>();
    for (RecipeHolder<IMeltingRecipe> holder : recipes.getAllRecipesFor(TinkerRecipeTypes.MELTING.get())) {
      IMeltingRecipe r = holder.value();
      String id = holder.id().toString();
      // CONCEPT checks (bind to real getters from Step 1):
      //  - the input ingredient resolves to >= 1 real item (getItems().length > 0 / !ingredient.isEmpty())
      //  - the output fluid is non-empty (amount > 0)
      //  - temperature > 0 and time > 0
      if (/* input empty || output empty || temp <= 0 || time <= 0 */ false) {
        bad.add(id);
      }
    }
    bad.removeAll(KNOWN_INTENTIONAL);
    if (!bad.isEmpty()) {
      helper.fail(bad.size() + " malformed melting recipe(s): " + bad);
    } else {
      helper.succeed();
    }
  }

  // all_casting_recipes_valid: iterate CASTING_TABLE.get() AND CASTING_BASIN.get(); check cast+fluid resolve, output non-empty.
  // all_alloy_recipes_valid: iterate ALLOYING.get(); check every input fluid resolves/non-empty, output fluid non-empty.
}
```

- [ ] **Step 3:** Fill the three methods with the real getters. Compile green:
  `<canonical gradle> compileJava` → `BUILD SUCCESSFUL`.

- [ ] **Step 4:** Run the gametests (SYNC): `<canonical gradle> runGameTestServer`. Expected: `8 GAME TESTS COMPLETE`. If a validation test FAILS, it lists offending recipe ids — **triage each**: a genuine data bug (empty output, dropped tag) is FIXED at its source (recipe provider / tag); a confirmed pre-existing intentional-latent id (matches the spec's known class, verify it is empty in the generated JSON and harmless) is added to `KNOWN_INTENTIONAL` with a one-line justification. Re-run to green. Report every id and its disposition.

- [ ] **Step 5:** Sweep. Commit: `test(gametest): data-driven validation of every melting/casting/alloy recipe`.

### Task 3: Representative smeltery gametests (melting / casting / alloying)

**Files:**
- Modify: `src/main/java/slimeknights/tconstruct/gametest/SmelteryGameTests.java`

**Interfaces:**
- Consumes: `SmelteryRigs.{buildSmeltery,insertMeltable,buildCastingRig,fillTank}`, `TinkerFluids`, `TinkerSmeltery`, `FluidValues`, `CastingBlockEntity`, `FaucetBlockEntity` — all already used by the existing methods in this file (mirror them exactly).
- Produces: representative melt/cast/alloy methods broadening B1 (suite grows by the count you add — target ~4-6 new methods).

- [ ] **Step 1:** Mirror `smeltery_melts` for a broader melting sample — one ORE input (e.g. `Items.RAW_IRON` or a copper ore → its molten fluid), one non-metal (e.g. `Items.GLASS` → `TinkerFluids.moltenGlass`, confirm the fluid name), and one gem/obsidian if a melting recipe exists. Each: `buildSmeltery` → `insertMeltable` → `succeedWhen` asserting the expected `TinkerFluids.X` and amount. Verify each input actually has a melting recipe (Task 2's validation output is your index) before asserting its output fluid.

- [ ] **Step 2:** Mirror `smeltery_casts` for more cast types — a **gem** cast, a **rod** cast, and a **plate** cast (the cast items live on `TinkerSmeltery`, e.g. `gemCast`/`rodCast`/`plateCast`; confirm the field names and pick a fluid that has a casting recipe for that cast). Each: `buildCastingRig` → put the cast on the table → `faucet.activate()` → `succeedWhen` asserting the expected output item. (Use `faucet.activate()` directly, exactly as the existing `smeltery_casts` documents.)

- [ ] **Step 3:** Mirror `alloyer_alloys` for a second alloy (e.g. bronze from molten copper + molten tin, ratio per its generated `alloys/*.json`) — the same rig shape (fuel tank below, alloyer `IN_STRUCTURE`, ingredient tanks beside), asserting the output fluid via the file's private `contains(...)` helper.

- [ ] **Step 4:** Compile, then run (SYNC): `<canonical gradle> runGameTestServer`. Expected: all gametests green (`N GAME TESTS COMPLETE`, N = 8 + your new count), zero failures. Some in-world melts are slow — keep `timeoutTicks` generous (1200) like the existing melting tests.

- [ ] **Step 5:** Sweep. Commit: `test(gametest): representative melting, casting, and alloying coverage`.

### Task 4: Representative tool-crafting + part-building gametests

**Files:**
- Modify: `src/main/java/slimeknights/tconstruct/gametest/ToolGameTests.java`

**Interfaces:**
- Consumes: the existing `tool_crafting` pattern in this file — `TinkerStationBlockEntity.calcResult(null)`, the `partStack(IToolPart, MaterialId)` helper (already present, reuse it), `TinkerToolParts`, `MaterialIds`, `TinkerTools`, `ToolStack`, `ToolStats`.
- Produces: representative tool-build methods for more tool types + a Part Builder test (suite grows by the count you add — target ~3-4 new methods).

- [ ] **Step 1:** Mirror `tool_crafting` for **2-3 more tool types** spanning different part counts — e.g. a **broadsword** (`TinkerTools.sword`/`broadSword` — confirm the field + its parts via its `ToolDefinition`), a **sledge hammer** (`TinkerTools.sledgeHammer`, a 3-part large tool), and a **hand axe** (`TinkerTools.handAxe`). For each: build its parts with `partStack(part, material)` in the part order its definition declares (read the tool's `ToolDefinition`/`PartStatsModule` if unsure), `station.setItem(INPUT_SLOT + i, part)`, `station.calcResult(null)`, assert the result `is(TinkerTools.X.get())`, `ToolStack` durability > 0, and the material count matches the part count.

- [ ] **Step 2:** Add a **Part Builder** test. Read `slimeknights/tconstruct/tables/block/entity/table/PartBuilderBlockEntity` and its recipe (`PartRecipe` under `library/recipe/partbuilder`) to find the result seam (analogous to the station's `calcResult`). Place a `PartBuilderBlockEntity`, set its material item + a pattern, resolve the part result, and assert it is the expected part item with the expected material (use `IMaterialItem.getMaterialFromStack`). If the Part Builder result path genuinely needs world ticking or a menu, use `succeedWhen` (mirror the smeltery style); otherwise assert directly (mirror `tool_crafting`). Report the exact seam you used.

- [ ] **Step 3:** Compile, then run (SYNC): `<canonical gradle> runGameTestServer`. Expected: all gametests green, zero failures.

- [ ] **Step 4:** Sweep. Commit: `test(gametest): representative tool crafting and part building`.

- [ ] **Step 5 (Phase-1 close):** Full battery (SYNC): `build` (green; unit 192/2/0) → `runGameTestServer` (all green) → `runClientUiTest` (12/12, unaffected). Confirm the JaCoCo report still generates. Report the numbers.

---

## Phase 2 — baseline-driven unit-test expansion

Phase 2 is **planned from Task 1's measured baseline** (its concrete targets are the classes JaCoCo reports as lowest-coverage in the scoped `library/` packages — unknown until T1 runs). It is NOT specified task-by-task here on purpose (writing tests for coverage gaps we cannot see yet would be guesswork).

**Method (repeatable per target class):** pick a lowest-coverage, high-value class in scope (tool stat computation, material stats, modifier hooks/logic, recipe matching, loadable round-trips where the registry-sync gate doesn't block, math/util) → write focused JUnit tests via the existing `BaseMcTest` + `TestHelper` harness (see any current test in `src/test/java/slimeknights/tconstruct/**` for the pattern) → run `test` → confirm the class's coverage rose in the JaCoCo report → commit. Repeat until the scoped-package line coverage approaches ~80% or hits diminishing returns.

**Handoff:** after Task 1 reports the baseline, the controller writes the concrete Phase-2 task list (one task per cluster of related low-coverage classes) as an addendum to this plan, then continues subagent-driven execution. The user decides from the baseline number how far to push.

**Phase 2 acceptance:** scoped-package line coverage substantially raised toward ~80% (reported before/after); `test` green; no client/datagen/registration classes added to the coverage denominator.

---

## Self-Review

- **Spec coverage:** A (JaCoCo tooling/denominator/report-only) → T1. B1 (representative in-world) → T3 (smeltery) + T4 (tools/parts). B2 (data-driven validation + allow-list) → T2. "e2e = gametests+uitest" → satisfied by T2-T4 (no new harness). C (unit expansion, baseline-driven) → Phase 2. Testing/battery → T4 Step 5. Acceptance #1 (report+%) → T1; #2 (B1 green) → T3/T4; #3 (B2 all recipes) → T2; #4 (unit % up) → Phase 2; #5 (battery green + counts) → T4 Step 5 + COMMANDS.md. All spec sections mapped.
- **Placeholders:** the recipe-getter checks in T2 and the exact tool/cast/part field names in T3/T4 are deliberately bound-to-source under the sanctioned-adjustment convention (the exact getters are not knowable without reading the interfaces, which the implementer does in Step 1 of each) — not lazy placeholders; every task has concrete structure, real annotations, and a concrete verify command.
- **Type consistency:** `TinkerRecipeTypes.MELTING/CASTING_TABLE/CASTING_BASIN/ALLOYING` used consistently in T2; `partStack`/`calcResult`/`SmelteryRigs` names match the real files read for T3/T4.
