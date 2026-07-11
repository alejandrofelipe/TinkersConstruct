# M6 — Tests + Deferred Compat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the final port milestone: 50-file test suite green, JEI plugin ported (API 15→19), book client ported, 4 debt fixes — all proven by the automated suites (which grow to 7 uitest scenarios + 5 gametests + `gradlew test`).

**Architecture:** Four independent phases ordered cheap→risky (A quick fixes, B tests, C book, D JEI), each with its own suite-verified gate; new coverage is ADDED as gametests/uitest scenarios per the workspace testing rule. Spec: `docs/superpowers/specs/2026-07-08-m6-tests-and-compat-design.md`.

**Tech Stack:** MC 1.21.1 / NeoForge 21.1.234, NeoGradle 7.1.38 + Gradle 9.2.1, JUnit 5.10.2 + Mockito 5.11.0 + AssertJ 3.25.3, JEI 19.21.0.247 (API jars local), Mantle via composite build (`../repo`).

## Global Constraints

- **Repos:** Tinkers = `C:\Users\aleja\DEV\New Tinkers\tinkers` (branch `1.21.1`); Mantle = `C:\Users\aleja\DEV\New Tinkers\repo` (branch `1.21.1`). Commit in the repo whose files you touched; Mantle fixes (Phase C) are separate commits in `repo`.
- **Gradle (canonical, PowerShell, JAVA_HOME in the SAME command):**
  ```powershell
  $env:JAVA_HOME = "C:\Users\aleja\scoop\apps\temurin21-jdk\current"; & "C:\Users\aleja\DEV\New Tinkers\tinkers\gradlew.bat" -p "C:\Users\aleja\DEV\New Tinkers\tinkers" <task>
  ```
  Daemon disabled: BACKGROUND every run (tee to a log), wait; slow cold start ≠ failure; never retry for slowness.
- **Process hygiene:** terminate every helper process the moment its purpose is fulfilled (including on error); verify with the CLAUDE.md `Win32_Process` sweep before reporting.
- **Testing rule (standing):** verification goes through the automated suites (`compileJava`/`build`, `gradlew test`, `runGameTestServer`, `runClientUiTest`); missing coverage is added as a gametest/uitest scenario in the same task; never ask the user to test what a suite can cover.
- **Commits:** conventional commits, English, body optional, ending with blank line + `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.
- **No web research; no sub-agents** (implementers). API ground truth = local sources: decompiled MC/NeoForge under `repo\build\neoForm\neoFormJoined1.21.1-20240808.144430\steps\patch\outputs.jar`, JEI API jars at `C:\Users\aleja\scoop\apps\gradle\current\.gradle\caches\modules-2\files-2.1\mezz.jei\jei-1.21.1-common-api\19.21.0.247\...\jei-1.21.1-common-api-19.21.0.247.jar` and `...\jei-1.21.1-neoforge-api\19.21.0.247\...\jei-1.21.1-neoforge-api-19.21.0.247.jar`.
- **Sanctioned-adjustment convention:** plan code is the spec; verify each flagged signature against local sources BEFORE compiling; rename-level fixes are fine (log them with evidence); structural deviations → report DONE_WITH_CONCERNS, don't improvise.
- **Style:** 2-space indent, sorted imports (no inline FQNs), javadoc on new classes/tests, fail-fast guards with diagnostic messages (established by T6–T11 reviews).
- **uitest suite result rule:** a FATAL `Error executing task` log line overrides an `"ok"` in `uitest-results.json`.

## File structure (what changes where)

- Phase A: `tinkers/src/main/java/slimeknights/tconstruct/smeltery/block/entity/module/MeltingModuleInventory.java` (widening); `common/data/tags/BlockTagProvider.java` + `ItemTagProvider.java` (+ regenerated `src/generated/...` tag/recipe outputs); `gametest/SmelteryGameTests.java` (new amethyst test); `library/utils/Util.java` (dead code); the solid-fuel WARN source (located in Task 4, expected in `smeltery/block/entity/module/fuel/` or `library/recipe/fuel/`).
- Phase B: `tinkers/src/test/java/**` (50 files, packages `common`/`fixture`/`library`/`test`) — no `src/main` changes expected.
- Phase C: `tinkers/build.gradle` (drop book exclusion), `library/client/book/**` (32 files), the book-item wiring site (located in Task 7), possible Mantle fixes in `repo/src/main/java/slimeknights/mantle/client/book/**`; `client/uitest/TinkerUiTestScenarios.java` (+1 scenario).
- Phase D: `tinkers/build.gradle` (drop jei exclusion), `plugin/jei/**` (49 files: subpackages casting/entity/material/melting/modifiers/partbuilder/transfer/util + `JEIPlugin.java`, `TConstructJEIConstants.java`, `AlloyRecipeCategory.java`, `MoldingRecipeCategory.java`, `ToolBuildingCategory.java`, `ShapedMaterialExtension.java`); `client/uitest/TinkerUiTestScenarios.java` (+1 scenario).
- Final: `repo/docs/COMMANDS.md`, `PROGRESS.md`, project memory.

---

## Phase A — quick fixes

### Task 1: TAG_SIZE/TAG_SLOT widening (byte→int)

**Files:**
- Modify: `tinkers/src/main/java/slimeknights/tconstruct/smeltery/block/entity/module/MeltingModuleInventory.java:317-356`

**Interfaces:** none (NBT format internal to this class). Old worlds keep loading: `CompoundTag.getInt` accepts any numeric tag (type 99), so byte values written by 1.20/pre-fix saves read fine.

- [ ] **Step 1: Apply the widening.** Exact current→new at the four sites (verified against the file at plan time):

```java
// line 320 — write slot:
moduleTag.putByte(TAG_SLOT, (byte)i);            // OLD
moduleTag.putInt(TAG_SLOT, i);                   // NEW

// line 327 — write size:
nbt.putByte(TAG_SIZE, (byte)modules.length);     // OLD
nbt.putInt(TAG_SIZE, modules.length);            // NEW

// line 337 — read size (drop the byte-wrap):
int newSize = nbt.getByte(TAG_SIZE) & 255;       // OLD
int newSize = nbt.getInt(TAG_SIZE);              // NEW

// lines 352-353 — read slot (guard must accept BOTH old byte and new int → ANY_NUMERIC):
if (item.contains(TAG_SLOT, Tag.TAG_BYTE)) {     // OLD
  int slot = item.getByte(TAG_SLOT) & 255;       // OLD
if (item.contains(TAG_SLOT, Tag.TAG_ANY_NUMERIC)) {  // NEW
  int slot = item.getInt(TAG_SLOT);                  // NEW
```

The `TAG_ANY_NUMERIC` guard is the load-bearing detail — a `TAG_INT` guard would silently drop items from old saves (the exact guard-class bug of memory #11). Add one comment line above the guard: `// ANY_NUMERIC: old saves wrote bytes, new saves write ints`.

- [ ] **Step 2:** Background `compileJava` → BUILD SUCCESSFUL.
- [ ] **Step 3:** Background `runGameTestServer` → `All 4 required tests passed` (regression net; the melting inventory is exercised by `smeltery_melts`).
- [ ] **Step 4: Commit** — `fix(smeltery): widen melting inventory slot/size NBT to int`.

### Task 2: amethyst/quartz storage-block tags (+ failing-first gametest)

**Files:**
- Modify: `tinkers/src/main/java/slimeknights/tconstruct/gametest/SmelteryGameTests.java` (add 1 test)
- Modify: `tinkers/src/main/java/slimeknights/tconstruct/common/data/tags/BlockTagProvider.java` and `ItemTagProvider.java`
- Generated: new `src/generated/resources/data/c/tags/{block,item}/storage_blocks/{amethyst,quartz}.json`

**Interfaces:** consumes `SmelteryRigs.buildSmeltery/insertMeltable` (T7 fail-fast versions) and `TinkerFluids.moltenAmethyst` (verify exact field name in `fluids/TinkerFluids.java`; if the fluid is named differently, e.g. `moltenAmethyst` absent, read `recipe/smeltery/melting/amethyst/block.json` in generated resources for the true output fluid id and use its field).

- [ ] **Step 1: Write the failing gametest** — append to `SmelteryGameTests` (mirror `smeltery_melts` exactly, different input/expected fluid):

```java
  /** Amethyst blocks melt once c:storage_blocks/amethyst is defined (M6 Phase A). */
  @GameTest(template = "gametest/empty_9x9x9", timeoutTicks = 1200)
  public static void smeltery_melts_amethyst(GameTestHelper helper) {
    BlockPos controller = SmelteryRigs.buildSmeltery(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)));
    helper.runAfterDelay(60, () ->
      SmelteryRigs.insertMeltable(helper.getLevel(), controller, new ItemStack(Items.AMETHYST_BLOCK)));
    helper.succeedWhen(() -> {
      if (!(helper.getLevel().getBlockEntity(controller) instanceof SmelteryBlockEntity smeltery)) {
        helper.fail("no smeltery controller BE", helper.relativePos(controller));
        return;
      }
      FluidStack contained = smeltery.getTank().getFluidInTank(0);
      helper.assertTrue(contained.getFluid() == TinkerFluids.moltenAmethyst.get() && contained.getAmount() > 0,
        "expected molten amethyst, got " + contained.getAmount());
    });
  }
```

- [ ] **Step 2: Run to verify it FAILS** — background `runGameTestServer`; expected: 4 pass + `smeltery_melts_amethyst` fails by timeout (the melting recipe's ingredient tag `c:storage_blocks/amethyst` is unbound, so the insert either never melts — or `insertMeltable` throws "rejected" if the melting inventory filters by meltability; either failure shape is the correct red).
- [ ] **Step 3: Define the tags in datagen.** In `BlockTagProvider` (find the method body that already builds common tags — line ~160 uses `commonResource(...)`), ADD definitions with vanilla content, following the provider's own creation idiom (verify the helper: the class extends a vanilla/Mantle tag provider; use its `tag(...)`/`getOrCreateBuilder`-equivalent):

```java
    // M6: define the vanilla-backed common storage tags our melting/casting recipes reference.
    // Raw steel's equivalents stay undefined on purpose: no vanilla raw steel — they bind when a
    // steel mod defines them.
    tag(BlockTags.create(commonResource("storage_blocks/amethyst"))).add(Blocks.AMETHYST_BLOCK);
    tag(BlockTags.create(commonResource("storage_blocks/quartz"))).add(Blocks.QUARTZ_BLOCK);
```

In `ItemTagProvider`, mirror with the provider's block→item `copy(...)` idiom for the same two tags (the file already copies other c: tags — match it).

- [ ] **Step 4:** Background `runData` → BUILD SUCCESSFUL; `git status` diff review: expect ONLY the 4 new tag JSONs (block+item × 2) — any other churn is a stop-and-report.
- [ ] **Step 5: Run to verify it PASSES** — background `runGameTestServer` → `All 5 required tests passed`.
- [ ] **Step 6: Commit** — `fix(data): define c:storage_blocks/{amethyst,quartz} so their melting recipes resolve`.

### Task 3: delete dead IS_NEO_FORGE

**Files:**
- Modify: `tinkers/src/main/java/slimeknights/tconstruct/library/utils/Util.java:266-276` (approx — the `IS_NEO_FORGE` field, its lazy getter method, and the now-unused `ModList` import if nothing else uses it)

- [ ] **Step 1:** `grep -rn "isNeoForge\|IS_NEO_FORGE" tinkers/src` — confirm the only hits are the definition block (main) and possibly `src/test`; delete test references along with it if trivially removable, otherwise report.
- [ ] **Step 2:** Delete the field + getter (+ orphaned import). Nothing replaces it — on this port the answer was a constant `false` (mod id `"forge"` doesn't exist), i.e. dead weight.
- [ ] **Step 3:** Background `compileJava` → BUILD SUCCESSFUL.
- [ ] **Step 4: Commit** — `chore: drop dead IS_NEO_FORGE check (mod id forge does not exist on NeoForge 21)`.

### Task 4: solid-fuel duplicate WARN — diagnose at the source

**Files:**
- Modify: the WARN's source (locate in Step 1; expected in the smeltery fuel module/lookup code)

The literal boot-log line (from the 2026-07-08 12:43:48 uitest run, `run/clientUiTest/logs/latest.log`):
`[Netty Local Client IO #0/WARN] [tconstruct/]: Multiple fuel recipes for solid fuel. This usually indicates a datapack error and may cause desyncs. Original null, latest null`

- [ ] **Step 1:** `grep -rn "Multiple fuel recipes" tinkers/src/main/java` → the emitting class (fuel lookup/module).
- [ ] **Step 2: Diagnose the `Original null, latest null` smell** — both "duplicates" print null, so the dedup key/lookup itself is broken, not the datapack: on 1.21 recipes arrive as `RecipeHolder<T>` and ids moved from `Recipe.getId()` to `RecipeHolder.id()`; a ported logging/lookup path that still asks the recipe for its id gets null and every solid-fuel recipe collides on the null key. Read the emitting method + its caller chain and confirm against the decompiled `RecipeHolder`/`RecipeManager`.
- [ ] **Step 3: Fix at the source** (make the dedup key the real recipe id — or the holder — so ONE solid-fuel recipe registers and the WARN only fires on genuine datapack duplicates). Minimal diff; no behavior redesign.
- [ ] **Step 4:** Background `runClientUiTest` (full suite — also the Phase A closing check): `uitest-results.json` all `"ok"`, and `Select-String "Multiple fuel recipes" latest.log` → **zero matches**; zero FATAL.
- [ ] **Step 5: Commit** — `fix(smeltery): dedupe solid fuel by real recipe id (1.21 RecipeHolder)` (adjust wording to the actual root cause found).

---

## Phase B — test suite (50 files)

### Task 5: sweep src/test to compiling

**Files:**
- Modify: `tinkers/src/test/java/**` (50 files; packages `slimeknights/tconstruct/{common,fixture,library,test}`)

**Interfaces:** produces a green `compileTestJava` and the fixture surface Phase B Task 6 runs.

- [ ] **Step 1: Inventory the delta** — `grep -rln "net.minecraftforge" tinkers/src/test/java` + compile once (`compileTestJava`, background) teeing the error list; bucket errors by cause.
- [ ] **Step 2: Mechanical sweep** — apply the SAME mappings the main-source port used (all documented in the project memory and migration guide `repo/docs/migration/1.20-to-1.21.1.md`): `net.minecraftforge.*`→`net.neoforged.*`, `RegistryObject`→`DeferredHolder`, `ForgeRegistries`→`BuiltInRegistries`, `ResourceLocation` ctor→`fromNamespaceAndPath`/`parse`, `isSameItemSameTags`→`isSameItemSameComponents`, FluidStack component API (`copyWithAmount`, `isSameFluidSameComponents`), `getEffect()` returns Holder (`.value()`), BE NBT signatures take `HolderLookup.Provider`. Test-specific: fixtures under `fixture/` bootstrap registries — port their registration plumbing to the NeoForge test-friendly equivalents used by main (`DeferredRegister` against `BuiltInRegistries`; if a fixture spins Forge's test harness, replace with plain JUnit `@BeforeAll` static bootstrap mirroring what `slimeknights.tconstruct.test` helpers already do).
- [ ] **Step 3: Converge** — iterate background `compileTestJava` until BUILD SUCCESSFUL (mini M1→M3; fix real signature errors, no `@Disabled` yet, no test-logic changes beyond compile necessity — log every semantic-adjacent change for the report).
- [ ] **Step 4: Commit** — `test: port src/test to 1.21.1/NeoForge (compiling)`.

### Task 6: converge `gradlew test` to green

**Files:**
- Modify: individual test files under `tinkers/src/test/java/**` (fix or `@Disabled`)

- [ ] **Step 1:** Background `gradlew test` (first run; tee). Collect the failure list from the JUnit report (`build/reports/tests/test/index.html` + console).
- [ ] **Step 2: Triage each failure** — fix cheap ones (assertion drift from 1.21 value changes, fixture wiring); for disproportionate ones apply `@Disabled("1.21 port: <specific reason — what would be needed>")`. Priority per spec: pure-Java material/modifier math tests MUST be green, not disabled.
- [ ] **Step 3:** Iterate background `gradlew test` until green. Hard rule: every `@Disabled` carries the reason string; count them.
- [ ] **Step 4: Commit** — `test: green test suite (N passing, M @Disabled with reasons)` with the real N/M in the message body.

---

## Phase C — book client (32 files)

### Task 7: lift exclusion, port the package, re-wire the book item

**Files:**
- Modify: `tinkers/build.gradle:67-68` (remove the two book-exclusion lines: the comment + `exclude 'slimeknights/tconstruct/library/client/book/**'`)
- Modify: `tinkers/src/main/java/slimeknights/tconstruct/library/client/book/**` (32 files)
- Modify: the item→book wiring site — locate with `grep -rn "TinkerBookItem\|book" tinkers/src/main/java/slimeknights/tconstruct/common tinkers/src/main/java/slimeknights/tconstruct/shared --include=*.java -il` and read how the book item's `use()` currently avoids the excluded classes (guard/stub); restore the real screen-open call.

**Interfaces:** consumes Mantle's ported book API (`repo/src/main/java/slimeknights/mantle/client/book/**`, 71 files, compiles; NEVER yet run). Produces: usable book items in game, consumed by Task 8's scenario.

- [ ] **Step 1:** Remove the sourceSets exclusion; background `compileJava` teeing errors — this is the honest inventory of the 32 files' delta.
- [ ] **Step 2: Converge to compiling** — same mechanical mappings as Task 5 plus the client-side set from the port (GuiGraphics APIs, `Screen.render` no explicit `renderBackground` (memory #12), `TextureAtlasSprite.getU/getV` normalized [0,1] (memory #7), `Slot.x/y` final (memory #8), mouse/keyboard handler signature drift). Verify against Mantle's own ported book screens — they already crossed this exact bridge; mirror their choices.
- [ ] **Step 3: Re-wire the book item** so right-click opens the book screen (whatever the guard was, restore the direct path Mantle's `BookLoader`/`AbstractBookItem` expects — read Mantle's `TinkerBookItem` equivalent usage in its test module for the canonical open call).
- [ ] **Step 4:** Background `compileJava` → BUILD SUCCESSFUL; background `runGameTestServer` → 5/5 (no logic regressions).
- [ ] **Step 5: Commit (tinkers)** — `feat(book): port the book client (library/client/book) to 1.21.1`.

### Task 8: book uitest scenario + first real run of the book system

**Files:**
- Modify: `tinkers/src/main/java/slimeknights/tconstruct/client/uitest/TinkerUiTestScenarios.java` (+1 scenario, +1 registration)
- Possible: Mantle render fixes under `repo/src/main/java/slimeknights/mantle/client/book/**` (in-flight, separate Mantle commits)

**Interfaces:** consumes `UiTestContext.sendCommand/player/mc` and the Task 7 book item. Produces the 6th uitest scenario `tconstruct:book_materials_and_you`.

- [ ] **Step 1: Add the scenario** (register AFTER `casting_pour` to keep existing `[n/5]` expectations append-only; suite becomes 6):

```java
  /** Opens "Materials and You" and captures the rendered page (first real run of Mantle's book system). */
  private static class BookScenario implements UiTestScenario {
    @Override
    public ResourceLocation id() {
      return TConstruct.getResource("book_materials_and_you");
    }

    @Override
    public void prepare(UiTestContext ctx) {
      // put the book in the main hand: item id verified in Step 2 (materials_and_you)
      ctx.sendCommand("give @s tconstruct:materials_and_you");
    }

    @Override
    public int prepareSettleTicks() {
      return 20;
    }

    @Override
    public void open(UiTestContext ctx) {
      InteractionResultHolder<ItemStack> result = ctx.player().getItemInHand(InteractionHand.MAIN_HAND)
        .getItem().use(ctx.mc().level, ctx.player(), InteractionHand.MAIN_HAND);
      if (result.getResult() == InteractionResult.PASS) {
        throw new IllegalStateException("book item use() passed - book screen did not open");
      }
    }

    @Override
    public int settleTicks() {
      return 40; // book textures/pages lazy-load
    }
  }
```

  Verify while writing: the real book item registry id (`grep -rn "materials_and_you" tinkers/src/main/resources tinkers/src/main/java`), whether `use()` client-side is the right opener or the item exposes a direct `openBook`-style helper (prefer the mod's own path; the fail-fast guard stays either way), and `InteractionResultHolder` package on 1.21.1.
- [ ] **Step 2:** Background `compileJava` → green.
- [ ] **Step 3:** Background `runClientUiTest` → 6 scenarios; expect `book_materials_and_you: ok` + inspect log for Mantle book ERROR/FATAL lines. If the PNG comes out broken/black or the log shows Mantle render exceptions: fix the Mantle-side bug (in-flight pattern, own commit in `repo`), re-run. Iterate max 4 diagnosed rounds; then report.
- [ ] **Step 4: Controller gate (not yours):** the controller reads the PNG and validates pages render.
- [ ] **Step 5: Commit (tinkers)** — `feat(uitest): book scenario`; Mantle fixes committed separately in `repo` as they landed.

---

## Phase D — JEI plugin (49 files)

### Task 9: lift exclusion, port the plugin to compiling (API 15→19)

**Files:**
- Modify: `tinkers/build.gradle:64-66` (remove the JEI exclusion comment + `exclude 'slimeknights/tconstruct/plugin/jei/**'`)
- Modify: `tinkers/src/main/java/slimeknights/tconstruct/plugin/jei/**` (49 files)

**Interfaces:** produces a compiling `@JeiPlugin` (`JEIPlugin.java`) whose categories Task 10 renders. API ground truth: the two local JEI 19 API jars (Global Constraints) — enumerate `mezz.jei.api` classes from the jar (`jar tf` or zip listing) and read signatures from the decompiled/`javap` output as needed.

- [ ] **Step 1:** Remove the exclusion; background `compileJava` teeing the full error inventory; bucket by API surface.
- [ ] **Step 2: Apply the 15→19 mapping.** Known deltas to verify-then-apply (each against the jar before editing):
  - Category identity: `IRecipeCategory.getUid()/getRecipeClass()` → single typed `RecipeType<T>` (`getRecipeType()`); constants move to `mezz.jei.api.recipe.RecipeType.create(modid, path, Class)` — rebuild `TConstructJEIConstants` around typed `RecipeType`s.
  - Layout: `setRecipe(IRecipeLayoutBuilder builder, T recipe, IFocusGroup focuses)`; slots via `builder.addSlot(RecipeIngredientRole, x, y)`; fluids via `.addFluidStack(fluid, amount)`.
  - Drawing: `draw(T recipe, IRecipeSlotsView slotsView, GuiGraphics graphics, double mouseX, double mouseY)` — GuiGraphics not PoseStack.
  - Platform fluid type: `ForgeTypes.FLUID_STACK` → the NeoForge API's fluid ingredient type in `jei-1.21.1-neoforge-api` (find the `NeoForgeTypes`-style holder in the jar).
  - `IShapedRecipe` is gone: `ShapedMaterialExtension`/crafting extensions now implement the 19-era `ICraftingCategoryExtension<R>` generic over the recipe, receiving `RecipeHolder<R>`; shaped dimensions come from the recipe itself (`ShapedRecipe.getWidth()/getHeight()` still exist on 1.21 — verify in decompiled MC).
  - Recipes from the manager arrive as `RecipeHolder<T>` — unwrap `.value()` at the registration boundary (`registerRecipes(IRecipeRegistration)`), keep category generics on the recipe type used before.
  - Runtime: `IModPlugin.onRuntimeAvailable(IJeiRuntime)` — CAPTURE the runtime in a static field on `JEIPlugin` (Task 10 needs it): `public static volatile IJeiRuntime runtime;`.
- [ ] **Step 3: Converge to compiling** (background `compileJava` loop). **Per-category fallback (spec):** if one category/extension is intractable on API 19, re-exclude ONLY that file with an inline `// TODO(M6-fallback): <what blocks it>` in a small dedicated sourceSets exclude, keep the rest — the milestone doesn't block on 49/49. Log any fallback prominently.
- [ ] **Step 4:** Background `runGameTestServer` → 5/5 (no logic regressions).
- [ ] **Step 5: Commit** — `feat(jei): port the JEI plugin to API 19` (+ fallback list in the body if any).

### Task 10: JEI uitest scenario (category renders)

**Files:**
- Modify: `tinkers/src/main/java/slimeknights/tconstruct/client/uitest/TinkerUiTestScenarios.java` (+1 scenario, +1 registration — suite becomes 7)

**Interfaces:** consumes `JEIPlugin.runtime` (Task 9) and `TConstructJEIConstants`' typed `RecipeType` for melting.

- [ ] **Step 1: Add the scenario** (registered last):

```java
  /** Opens the JEI recipes GUI on the Tinkers melting category and captures it. */
  private static class JeiCategoryScenario implements UiTestScenario {
    @Override
    public ResourceLocation id() {
      return TConstruct.getResource("jei_melting_category");
    }

    @Override
    public void prepare(UiTestContext ctx) {
      // nothing to build; JEI runtime becomes available once the world/screens settle
    }

    @Override
    public void open(UiTestContext ctx) {
      IJeiRuntime runtime = JEIPlugin.runtime;
      if (runtime == null) {
        throw new IllegalStateException("JEI runtime not captured - onRuntimeAvailable never fired");
      }
      runtime.getRecipesGui().showTypes(List.of(TConstructJEIConstants.MELTING));
    }

    @Override
    public int settleTicks() {
      return 40;
    }
  }
```

  Verify while writing: `IRecipesGui.showTypes(List<RecipeType<?>>)` exists in the 19 jar (else use the closest `show`/`showRecipeType` overload); the real constant name for the melting `RecipeType` in the ported `TConstructJEIConstants`.
- [ ] **Step 2:** Background `compileJava` → green.
- [ ] **Step 3:** Background `runClientUiTest` → **7 scenarios all `"ok"`**, zero FATAL; iterate (max 4 diagnosed rounds) on failures.
- [ ] **Step 4: Controller gate:** controller reads the JEI PNG (category visible, recipes drawn, no missing textures).
- [ ] **Step 5: Commit** — `feat(uitest): JEI melting category scenario`.

---

## Final

### Task 11: full battery + docs + declare the port complete

**Files:**
- Modify: `repo/docs/COMMANDS.md` ("Automated tests & screenshots": add `gradlew test` as the third pillar; update scenario count 5→7 and gametest count 3→5)
- Modify: `PROGRESS.md` (workspace root, no git) + project memory (controller does these)

- [ ] **Step 1: Battery, in order, all background:** `build` → `gradlew test` → `runGameTestServer` (expect `All 5 required tests passed`) → `runClientUiTest` (expect 7× `"ok"`, zero FATAL, client self-exit). Any red stops the task — report instead of patching drive-by.
- [ ] **Step 2:** Update COMMANDS.md (counts + the `gradlew test` command block with the canonical form + where reports land: `build/reports/tests/test/`).
- [ ] **Step 3: Commit (mantle)** — `docs: test suite joins the verification battery; suite counts updated`.
- [ ] **Step 4:** Report the battery evidence (the four green lines) — the controller closes PROGRESS.md/memory and declares **Tinkers port 7/7 complete**.

---

## Self-review notes (kept for the record)

- Spec coverage: DoD-1→Tasks 5-6; DoD-2→9-10; DoD-3→7-8; DoD-4→1-4; DoD-5→11. Raw-steel latency documented in Task 2 Step 3 comment. Fallback-per-category in Task 9 Step 3. ✓
- Type consistency: `JEIPlugin.runtime` (T9) consumed by T10; `smeltery_melts_amethyst` naming consistent T2; scenario ids `book_materials_and_you`/`jei_melting_category` consistent with spec's "7 scenarios" (spec names them generically — these are the concrete ids). Suite-count progression 4→5 gametests (T2), 5→6→7 uitest (T8, T10) is monotone and each gate states the expected count at that point. ✓
- Placeholders: none — every code step carries real code plus explicit verify-against-source instructions where 1.21.1/JEI-19 signatures must be confirmed locally (sanctioned-adjustment convention).
