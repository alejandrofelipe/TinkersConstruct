# M6 — Tests + deferred compat (design)

Final milestone of the TinkersConstruct 1.20.1/Forge → 1.21.1/NeoForge port. M0–M5 are done
(build green, datagen regenerated, world loads, GUIs work, DoD proven by automation: `casting_pour`
uitest + `tool_crafting` gametest). This spec closes the port: the deferred test suite and the two
deferred user-visible integrations (book, JEI plugin), plus the small-debt items accumulated during
M0–M5. Scope decision (user, 2026-07-08): **tests + JEI plugin + book client + 4 quick fixes**;
other third-party compat mods stay out (see Out of scope).

## Definition of Done

1. `gradlew test` green — the 50 `src/test` files ported; `@Disabled(reason)` acceptable where the
   fix cost is disproportionate (original spec criterion). Pure-Java tests (material/modifier math)
   are the priority to keep green.
2. JEI plugin (`plugin/jei/**`, 49 files) compiles and a Tinkers recipe category **renders in a new
   uitest scenario**.
3. Book client (`library/client/book/**`, 32 files) compiles and an in-game book opens and
   **renders in a new uitest scenario**.
4. The 4 quick fixes are closed (Phase A below).
5. Final battery green: `build` + `gradlew test` + `runGameTestServer` (4 tests) +
   `runClientUiTest` (**7 scenarios** = current 5 + book + JEI), zero FATAL log lines.

Verification rule (workspace CLAUDE.md, standing): testing goes through the automated suites; a
missing check is added as a gametest/uitest scenario as part of the change — manual user testing
only where automation genuinely cannot capture it.

## Phase A — quick fixes

**End state: the small technical debt from M0–M5 is zero.** Melting/casting amethyst and quartz
blocks works in game; giant smelteries survive world reloads intact; dead code is gone; the boot
log is WARN-clean.

1. **`TAG_SIZE`/`TAG_SLOT` widening (byte→int)** in `MeltingModuleInventory`
   (`smeltery/block/entity/module/MeltingModuleInventory.java`): write with `putInt`; reads use
   `getInt`, which accepts old byte tags (NBT numeric type 99) — existing worlds keep loading.
   Fixes item mis-restore in >255-inner-slot smelteries (documented upstream quirk, memory #11).
2. **Latent unbound-tag recipes (6)**: define `c:storage_blocks/amethyst` and
   `c:storage_blocks/quartz` in the block/item tag datagen providers pointing at the vanilla blocks
   (`minecraft:amethyst_block`, `minecraft:quartz_block`), bringing the 4 amethyst/quartz
   melt+cast recipes to life (they reference tags precisely to accept modded variants; nobody
   defines them today). The 2 raw-steel melting recipes stay **intentionally latent** (no vanilla
   raw steel; they activate when a steel mod defines the tag) — documented in the provider with a
   comment.
3. **Delete dead `IS_NEO_FORGE`** (`library/utils/Util.java:268-275`): field + lazy getter check
   the mod id `"forge"`, which does not exist on NeoForge 21 (always false); confirmed unreferenced
   in `src/main`. Check `src/test` before deleting; remove any test-side references with it.
4. **Solid-fuel duplicate WARN**: locate the boot-log WARN about a duplicated solid-fuel
   registration/recipe, fix the duplication at its source.

Gate: `compileJava` + `runData` (item 2 regenerates tags; diff reviewed — expect only the two new
tag files plus their recipe references) + `runGameTestServer` 4/4 green.

## Phase B — test suite port (50 files)

**End state: `gradlew test` runs the mod's 50 unit tests green — a permanent third verification
pillar (fine-grained logic regression) alongside gametests and uitest.**

- JUnit 5.10.2 / Mockito 5.11.0 / AssertJ 3.25.3 already pinned in `build.gradle`
  (`test { useJUnitPlatform() }` wired) — the work is the test code itself.
- Method: mechanical import sweep (Forge→NeoForge, 1.21 signatures) then compiler-driven
  convergence inside `src/test` (mini M1→M3), including the registry-bootstrap fixtures the
  material/modifier tests rely on.
- Acceptance (original spec criterion): green, or `@Disabled("reason")` for tests needing
  disproportionate rework. Every `@Disabled` carries a reason string.

Gate: `gradlew test` green; the command joins the standard verification battery.

## Phase C — book client (32 files)

**End state: the in-game books ("Materials and You", "Puny Smelting", "Mighty Smelting", …) open
and render — the mod's canonical in-game encyclopedia.**

- Lift the `library/client/book/**` exclusion in `tinkers/build.gradle` `sourceSets`; port the 32
  files (1.20→1.21 client API deltas — same class of work as M2/M3, concentrated in one package).
- Re-wire the item→book path: `TinkerBookItem`/`AbstractBookItem` compile today with book access
  guarded/stubbed; reconnect screen opening.
- Book content (JSON/lang under `assets/tconstruct/book/`) has shipped all along. Mantle's book
  system (71 files) is ported and compiles but **has never run** — this phase is its first real
  consumer; Mantle-side rendering bugs are expected and in scope (same in-flight-fix pattern
  Mantle has received throughout).
- New uitest scenario **`book_materials_and_you`**: give the player the book item
  (`/give`/creative), use it, settle, screenshot; PNG analysis checks pages render without broken
  textures.

Gate: `compileJava` + the scenario capturing an open, readable book page + no new FATAL/ERROR in
the uitest log.

## Phase D — JEI plugin (49 files, API 15→19)

**End state: JEI explains Tinkers — melting, alloying, casting, molding, modifier and part-builder
recipes appear in Tinkers-branded categories, clickable from any item.** Today JEI only lists
items (runtime-only dep); the mod's machines are opaque to it.

- Lift the `plugin/jei/**` exclusion; port against the **local API jars**
  (`jei-1.21.1-common-api` / `jei-1.21.1-neoforge-api`, already `compileOnly` on the classpath —
  the 15→19 mapping is read from the jars/javadocs, no web research): typed `RecipeType`,
  removal of `IShapedRecipe`, `ForgeTypes.FLUID_STACK` → the NeoForge-API equivalent, `@JeiPlugin`
  / `IModPlugin` registration surface.
- **Fallback (kept from the original port spec)**: if a specific subsystem proves intractable on
  API 19, its categories stay excluded with a documented TODO; JEI remains runtime-only for those.
  The milestone does not block on 49/49.
- New uitest scenario **`jei_category`**: capture `IJeiRuntime` in the plugin's
  `onRuntimeAvailable`, open a Tinkers category programmatically via
  `IJeiRuntime.getRecipesGui()`, settle, screenshot.

Gate: `compileJava` + the scenario capturing a rendered Tinkers category + no new FATAL/ERROR.

## Final verification

Full battery, all green, zero FATAL: `build` → `gradlew test` → `runGameTestServer` (4) →
`runClientUiTest` (7 scenarios). Then: `PROGRESS.md` (Tinkers 7/7 🏁), project memory, docs
(`COMMANDS.md` gains `gradlew test` + the 2 new scenarios in the suite listing), and the port is
declared **complete** — parity with upstream 1.20.1 except third-party compat mods.

## Risks & fallbacks

1. **JEI 15→19 is the largest API delta** → per-category fallback (above); milestone never blocks
   on a single category.
2. **Mantle book never ran** → uitest screenshot catches visual breakage; Mantle in-flight fixes
   are established practice; worst case the book phase grows Mantle-side fix tasks.
3. **Test-suite fixtures may hide expensive bootstrap work** → `@Disabled(reason)` is acceptable
   by spec; pure-Java tests are the priority.

## Out of scope

- Other third-party compat (Immersive Engineering, JSON Things, Diet, Balm, Crafting Tweaks,
  Moonlight, corail tombstone, The One Probe): each depends on that mod having a 1.21.1/NeoForge
  build; ported on demand as individual follow-ups.
- Book HTML export (Mantle side, deferred with the book system originally).
- Full feature-parity QA beyond the automated suites.

## References

- Original port spec: `docs/superpowers/specs/2026-06-27-tinkers-1.21.1-neoforge-port-design.md`
  (§M6, §9 test suite, JEI risk flag §7).
- Dev-loop automation (the verification infrastructure this spec relies on):
  `docs/superpowers/plans/2026-07-06-dev-loop-automation.md` + `repo/docs/COMMANDS.md`
  "Automated tests & screenshots".
- Accumulated-debt provenance: project memory `tinkers-1211-neoforge-port` entries #11 (byte
  tags), #15 (latent recipes, forge:→c: sweep), #16-18 (gametest/uitest learnings, M5 closure).
