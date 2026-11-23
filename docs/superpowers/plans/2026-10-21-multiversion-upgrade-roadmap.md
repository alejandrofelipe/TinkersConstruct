# Multi-Version Upgrade Roadmap — New Tinkers 1.21.1 → 26.2 (NeoForge)

> Program-level roadmap. Each **milestone** is its own sub-project and gets its own detailed
> plan (via `superpowers:writing-plans`) written **when it starts** — because each needs its
> version primer read fresh. This doc is the map + shared method + decision log.

**Goal:** Port the fork (Mantle `repo/` + TinkersConstruct `tinkers/`) forward from
**MC 1.21.1 / NeoForge 21.1.234** up to **MC 26.2 / NeoForge 26.2**, milestone by milestone.

**Strategy:** Milestone-incremental — port only at the ~7 major-break checkpoints (trivial point
releases piggyback onto the next checkpoint), gating each with the full test battery, tagging each.

**Decision log (2026-10-21):**
- Target = **26.2** — most mod support in the ecosystem today; **26.3 is NeoForge-beta-only**, so not yet.
- Strategy = **milestone-incremental** (not jump-to-target, not every point release).
- **M1 lands on 1.21.4 / NF 21.4.161 (user decision, 2026-10-21) — folds the old M1 (1.21.2) + M2 (1.21.4).**
  Two findings drove it: (1) NeoForge for 1.21.2 never left beta (`21.2.0/1-beta`); (2) **JEI and Immersive
  Engineering skipped 1.21.2 AND 1.21.3 entirely** — JEI goes `jei-1.21.1` → `jei-1.21.4` (`20.0.0.4`), IE
  stayed on 1.21.1. So 1.21.3 is a dead version in the mod ecosystem (a tagged/deployed checkpoint there
  would have no JEI). 1.21.4 is the first stable, JEI-supported step. The 1.21.2/1.21.3 breaks are a subset,
  done on the way. **Consequence: the old M1 and M2 merge into one milestone** (adds the `GatherDataEvent` →
  `.Client`/`.Server` split + model-loading rewrite on top of the recipe/item/interaction walls); IE is
  re-excluded (was already an accepted known-issue crash). Exact pins: MC `1.21.4`, NeoForge `21.4.161`,
  Parchment `1.21.4 / 2025.03.23`, JEI `20.0.0.4` (new major → plugin API port), Java 21 (unchanged).
  Detailed plan: [`2026-10-21-m1-1.21.4-port.md`](2026-10-21-m1-1.21.4-port.md).
- **M3 starts 2026-10-24 on 1.21.5 / NF 21.5.101** — compat complete this time: JEI `21.4.0.27` (new major),
  Crafting Tweaks `21.5.3` + Balm `21.5.26`, Parchment `2025.06.15`, NeoGradle 7.1.39 + Gradle 9.2.1 unchanged.
  IE stays excluded (1.21.1 only); JSON Things has no 1.21.5 build (has 1.21.4/1.21.6 → re-add at M4). Mantle
  baseline: 258 compile errors. The real wall is **models (`BakedModel` removed, dynamic models → block-state
  level) + render pipeline (`RenderPipeline`) + NBT `Optional` getters + gametest framework rewrite**, more than
  the tool data components. Branch `1.21.5` in both repos. Detailed plan: [`2026-10-24-m3-1.21.5-port.md`](2026-10-24-m3-1.21.5-port.md).
- **M4 lands on 1.21.8 / NF 21.8.57 (user decision, 2026-10-24) — folds 1.21.6 + 1.21.7.** NeoForge 21.6 and 21.7
  never left beta and JEI skipped 1.21.6 (JEI 1.21.8 = `24.2.0.6`, new major). Parchment `2025.09.14`, Crafting
  Tweaks `21.8.2` + Balm `21.8.9`; IE and JSON Things excluded (JSON Things has 1.21.6/1.21.10+ only). Toolchain
  unchanged. Mantle baseline: 102 compile errors. Walls: **deferred GUI render state** (`Matrix3x2fStack`,
  `RenderPipeline` blits, PiP for 3D in GUIs, silent text-alpha/depth traps), **`ValueInput`/`ValueOutput`** for
  BEs/entities (tool NBT itself unaffected), **UBO shaders + fog rewrite**, **tag datagen appenders**. **M5 keeps
  only 1.21.9.** Branch `1.21.8`. Detailed plan: [`2026-10-24-m4-1.21.8-port.md`](2026-10-24-m4-1.21.8-port.md).
- **M4 done 🏁 (2026-11-23): released Mantle `1.15` / Tinkers `3.14.0`, annotated tags `v1.21.8-1.15` /
  `v1.21.8-3.14.0` + branch `1.21.8` pushed to origin.** Not deployed (no 1.21.8 game instance). Mantle 102 → 0 and Tinkers 546 → 0 compile
  errors; datagen tags byte-identical; battery green on the final code: `build` both repos + 674 unit + 58 gametest
  (57 + vanilla `always_pass`) + 31/31 uitest (30 scenarios + `mantle:suite_setup`), visual review side by side with a
  1.21.5 reference run. The port surfaced shipped bugs in earlier lines (built tools vanishing from saves, castless
  casting, thrown tool reload, JEI categories aborted/empty, recipe network codecs) — fixes for the save/casting/
  thrown-tool/JEI-fuel/`/mantle sources` ones were backported to the `1.21.1`/`1.21.3`/`1.21.5` branches. Known issue
  kept open: stackable + damageable tools vs `ItemStack.validateStrict` (creative-slot packet). Release targets: Mantle
  `1.15` / Tinkers `3.14.0`, tags `v1.21.8-1.15` / `v1.21.8-3.14.0`. Gotchas:
  [`repo/docs/migration/1.21.5-to-1.21.8.md`](../../../../repo/docs/migration/1.21.5-to-1.21.8.md).
- **M5 lands on 1.21.10 / NF 21.10.64 (user decision, 2026-11-23) — folds 1.21.9.** NeoForge 21.9 never left beta and
  JEI abandoned 1.21.9; 1.21.10 has JEI `26.3.0.31` (new major), Crafting Tweaks `21.10.1` + Balm `21.10.11`, JSON Things
  `0.16.0` (**re-added in M5's compat phase**), Parchment `2025.10.12`; IE stays excluded. Toolchain unchanged (NeoGradle
  7.1.39 / Gradle 9.2.1 / Java 21 verified on 1.21.10). Mantle baseline: 100 compile errors / 48 files. The roadmap row
  (`YawProvider`/`SkinTextures`, "baixo/médio") was wrong again: the walls are the **NeoForge transfer API rework**
  (`ResourceHandler`/`ItemAccess`/transactions; the old `Capabilities.FluidHandler/ItemHandler/EnergyStorage` are
  removed and NeoForge only bridges new → legacy), **two-phase rendering** (BER/entity/layer/particle
  `extractRenderState` + `submit(SubmitNodeCollector)`) and **event-based input**, plus a mechanical sweep
  (`Level#isClientSide()`, FML `getDist()`, reload-listener `SharedState`, …). Transfer decision (user): first only
  registration/lookup on the new capabilities with the legacy handlers kept (Mantle adds a small legacy → new adapter),
  then a separate full-migration phase inside M5. Branch `1.21.10`. Detailed plan:
  [`2026-11-23-m5-1.21.10-port.md`](2026-11-23-m5-1.21.10-port.md).

## Global constraints (apply to every milestone)

- **Composite order: Mantle ports first**, then Tinkers consumes it. Same as the 1.21.1 port.
- **Gate per milestone:** `build` + unit + `runGameTestServer` + `runClientUiTest` all green
  (baseline 649 unit / 28 gametests / 13 uitest). Missing coverage → ADD a test as part of the change.
- **Build via PowerShell** + per-version `JAVA_HOME`. **Java 21** through MC 1.21.11; **Java 25 from 26.x**.
- Conventional commits, **no co-author trailer**. Tag each milestone (`v<mc>-<modver>`), annotated.
- Per milestone: confirm **NeoForge + Parchment** builds exist for the version; check **compat mods**
  (JEI / Immersive Engineering / Crafting Tweaks / JSON Things) have target builds, else re-exclude (M6-style).
- `ARTIFACT_VERSION` env drops `.DEV` for release jars (build Mantle/Tinkers **separately** — the env
  var leaks through the composite). LICENSE now ships in the jar (`from("LICENSE")`) — keep it.

## The ladder & milestones

> **M1 scope note (2026-10-21):** M1 now lands on **1.21.4** (NF `21.4.161`) with 1.21.2 + 1.21.3 folded in
> **and the old M2 merged in** (JEI/IE skipped 1.21.3) — see the decision log above. Milestones renumber
> implicitly from here: old M3 (1.21.5) is the next after this merged M1+M2.

Above 1.21.1: `1.21.2 → 1.21.3 → 1.21.4 → 1.21.5 → 1.21.6 → 1.21.7 → 1.21.8 → 1.21.9 → 1.21.10 →
1.21.11 → 26.1 → 26.2 (→ 26.3)`. The **2026 numbering change** moved MC from `1.21.x` to year-based `26.x`.

| # | Checkpoint (MC / NeoForge) | Piggyback | Headline breaks | Tinkers impact |
|---|---|---|---|---|
| **M1** | 1.21.4 / NF 21.4.161 | 1.21.2, 1.21.3 | 🔴 **Recipes → datapack registry** (`display()`/`PlacementInfo`, `RecipeHolder`→`ResourceKey`); **item/block `setId` required**; `InteractionResultHolder` removed; `GuiGraphics.blit`+`CoreShaders`; **EntityRenderState**; model `bake`/`resolveDependencies`; `Tier`→`ToolMaterial`, `ArmorItem.Type`→`ArmorType`; `FoodProperties`→`Consumable`; registry/holder renames; `getMaxY` off-by-one; `hurt`→`hurtServer`; **+ folded M2:** `GatherDataEvent`→`.Client`/`.Server`, model loading, **JEI 19→20** | 🔴 **Alto** (M1+M2 merged) — recipes + InteractionResult (~73 files) + item/block ID (~200 sites) + client-render rewrite + JEI-20 port (see M1 plan) |
| ~~**M2**~~ | ~~1.21.4 / NF 21.4~~ | — | **Merged into M1** (2026-10-21) — JEI/IE skipped 1.21.3, so M1 lands directly on 1.21.4 | — |
| **M3** | 1.21.5 / NF 21.5 | — | 🔴 **Data components for weapons/tools/armor**; model-loading rewrite; `@Mod` constructed before MC | 🔴 **ENORME** — the core tool/armor/stat system |
| **M4** | 1.21.8 / NF 21.8.57 (was 1.21.6) | 1.21.6, 1.21.7 | 🔴 Serialization `CompoundTag` → **`ValueInput`/`ValueOutput`**; **deferred GUI** (`RenderPipelines` blits, `Matrix3x2fStack`, PiP, GUI item atlas); UBO shaders + fog; tag datagen appenders | 🟠 **Alto** (smaller than predicted — tool NBT untouched; the cost was silent GUI breakage) |
| **M5** | 1.21.10 / NF 21.10.64 (was 1.21.9) | 1.21.9 | 🔴 NeoForge **transfer API rework** (`ResourceHandler<FluidResource/ItemResource>`, `ItemAccess`, transactions; old capabilities removed); **two-phase rendering** (BER/entity/layer/particle extract + `submit`, `ExtractBlockOutlineRenderStateEvent`, `AtlasManager`); **event-based input** (`MouseButtonEvent`/`KeyEvent`/`InputWithModifiers`, `KeyMapping.Category`); `Level#isClientSide()`, `Entity#getServer()` removed, reload `SharedState`, FML `getDist()`/`isProduction()`, `TypedEntityData`, `ContainerUser`, `entityInside(…, boolean)` | 🟠 **Médio-alto** — Mantle 100 errors / 48 files; Tinkers est. 450–650 (half mechanical `isClientSide`); transfer in two steps (bridge first, full migration later in M5) |
| **M6** | 1.21.11 / NF 21.11 | — (1.21.10 moved into M5) | **`ResourceLocation` → `Identifier`** (Mojang rename); `FMLLoader.isProduction()` removed | 🟠 **Mecânico mas pervasivo** (MaterialId/ModifierId já saíram de ResourceLocation no 1.21.1 → ajuda). Ecosystem exists (checked 2026-11-23): NF `21.11.48`, JEI `27.55.0.122`, Crafting Tweaks `21.11.5`, Balm `21.11.9.1`, Parchment `2025.12.20`, JSON Things `0.17.0` |
| **M7** | 26.2 / NF 26.2 | 26.1 | **Java 25**; new-series changes | **Médio** — toolchain bump + sweep |

> Sources to pull each milestone's exact checklist from: NeoForge release notes
> ([21.4](https://neoforged.net/news/21.4release/), [21.5](https://neoforged.net/news/21.5release/),
> [21.6](https://neoforged.net/news/21.6release/), [26.1](https://neoforged.net/news/26.1release/)),
> ChampionAsh5357's per-version Minecraft-change primers (NeoForge links them), and the Fabric
> porting pages (`docs.fabricmc.net/<ver>/develop/porting/`, third-party but useful for vanilla changes).

## Per-milestone method (the recipe — repeat each checkpoint)

1. **Read the primer** for the version (NeoForge notes + ChampionAsh5357 + Fabric porting) → extract a break checklist. Write the milestone's detailed plan (`writing-plans`).
2. **Bump versions** in `gradle.properties` (both repos): `minecraft_version`, `neoforge_version`, `parchment_version` (+ `mantle_version` dep pin on the Tinkers side).
3. **Port Mantle** (`repo/`): `compileJava` → fix breaks → full `build` green.
4. **Port Tinkers** (`tinkers/`): `compileJava` → fix breaks → `build` → `runData` → `runGameTestServer` → `runClientUiTest` green.
5. **Compat mods:** bump deps to target builds, or re-exclude the ones without a build (re-add later).
6. **Record gotchas** in the migration doc (`repo/docs/migration/`), one section per milestone.
7. **Commit per green step; tag the milestone** (`v<mc>-<modver>`); deploy to the instance if desired.
8. **Write the next milestone's detailed plan.**

## Cross-cutting risks / notes

- **M3 (1.21.5) + M4 (1.21.6) are the two walls.** Budget the most time there — data components for
  equipment (M3) and the ValueInput/ValueOutput serialization rewrite (M4) hit the heart of Tinkers
  (ToolStack, materials, modifiers, tool/armor stats). Consider splitting each into sub-milestones if needed.
- **M6 rename (`ResourceLocation` → `Identifier`)** is huge in surface area but mechanical; a mapping
  sweep + the compiler is the tool. The 1.21.1 port already decoupled MaterialId/ModifierId from
  ResourceLocation, which softens the blow.
- **M7 Java 25:** install a JDK 25 (scoop), repoint `JAVA_HOME`, bump the Gradle toolchain; verify
  NeoGradle/Gradle-wrapper support 26.x.
- **Compat is optional per milestone** — core Tinkers green is the gate; a compat mod without a target
  build gets re-excluded (M6 decision pattern), re-added when a build appears.
- **uitest flake:** the AMD `atio6axx.dll` GL-init crash will recur; retry, and if it stays env-blocked,
  accept visual-neutral changes as in the 1.21.1 close. (It never fired during M1 — every uitest run booted.)
- **Lessons from M1 (apply to every milestone):**
  - Pick the checkpoint by **compat availability** (JEI/IE skipped 1.21.2–1.21.3), not just NeoForge stability.
  - Each gate hides the next bug class — "compiles + unit green" was ~5 layers from "playable": mod-load
    (`setId`, eager tags) → datapack load (registry-aware ops) → **client join** (recipe-sync decode, which
    server-only gametests can't see) → visual (uitest `"ok"` ≠ correct pixels: *view the screenshots*).
    Run `runServer`/`runClientUiTest` early, not only at the end.
  - Datagen needs `runData` **and** `runServerData`, which purge each other's outputs — regenerate one side,
    restore the other from HEAD.
  - Flow-gate (`-XDshould-stop.ifError=FLOW` via a scratchpad init script) makes `compileJava` report
    method-body errors past the first attribution failure — essential for category-by-category porting.
  - Sessions restarted mid-batch several times: have subagents commit **per green stage**.
- **Lessons from M3 (1.21.5):**
  - The roadmap's predicted wall (tool data components) was the small part; the real walls were **models**
    (`BakedModel` removed → block-state-level dynamic models), the **render pipeline** (`RenderPipeline`), **NBT
    `Optional` getters** and the **gametest framework** rewrite. Read the primer before trusting the roadmap row.
  - Pre-reading the new API in the decompiled tree and handing subagents a **verified design** (Phase 1c, 2B) worked
    better than letting them improvise; sequential subagents per package group (shared Gradle build dir).
  - **API changes that turn a return type into `Optional` compile wherever an `Object` is accepted** — 4 silent bugs
    (one disabled a whole family of modifier reads). The worldgen gametests caught the first; a regex sweep found the
    rest. Add this sweep to every milestone where getters change shape.
  - Review every uitest PNG *and* the client log: three bugs (creative tabs, empty book recipes, magenta items) passed a
    14/14 `"ok"`; two had been there since 1.21.4. Add a scenario for whatever a rewrite touches (here:
    `dynamic_block_models`).
- **Lessons from M4 (1.21.8):**
  - **uitest `"ok"` ≠ correct, even with the PNGs open.** The first run said 16/16 ok while every capture held the
    *previous* scenario's frame (async GPU readback raced the persistently mapped buffer). Reviewing PNGs alone would not
    have caught it. The real gate is a **reference run of the previous milestone** (its release tags in throwaway
    worktrees outside the workspace, same suite) plus a **side-by-side review of every PNG pair**. That review also found
    the GUI item atlas identity bug (station tabs showing an arrow) and showed that 1.21.5 itself drew the station cover
    opaque.
  - **Singleplayer hides network codec bugs.** The integrated server never serializes packets, so a broken
    `STREAM_CODEC` passes every client test. A gametest that round-trips every synced recipe through
    `RecipeHolder.STREAM_CODEC` found two Mantle bugs on its first run. Do the same for any new synced type.
  - **Round-trip save gametests find shipped data loss.** Checking BE/entity/tool save → load against the previous
    on-disk shapes found built tools vanishing from saves (1.21.4+) and, in the same area, modifier inventories losing
    items (1.21.1+). Both had shipped. Add a round-trip test whenever serialization changes shape.
  - **The GUI rewrite breaks silently**: text without alpha draws nothing, `renderBackground` moved before `render`,
    deferred tooltips are first-wins and drawn without the pose, PiP bounds ignore the pose, GUI items share one atlas slot
    without a model identity. None of these produce an error. Sweep color literals and list these checks in the plan for
    the uitest review.
  - **Read the client log for JEI plugin errors** — an exception in `registerRecipes` removed half the JEI categories
    since 1.21.4 while every uitest passed. One uitest scenario per JEI category now fails when a category is missing or
    empty.
  - **uitest needs an unlocked screen.** While Windows is locked, GL window creation fails in `atio6axx.dll`. That is
    not the driver flake from the 1.21.1 close, and retrying is pointless until the screen is unlocked.
  - The roadmap row again predicted the wrong wall (tool NBT); the decompiled-tree pre-investigation (shaders, NBT parity,
    PiP, JEI API diff) before Phase 1 gave subagents verified designs and avoided rework.

## Progress

- [x] **M1** — 1.21.4 / NF 21.4.161 (1.21.2 + 1.21.3 + old M2 folded in) 🏁 2026-10-24 — Mantle `1.13` / Tinkers `3.12.0`, tags `v1.21.4-1.13` / `v1.21.4-3.12.0`; battery: build + 672 unit + 31 gametest + 14/14 uitest (visual verified). Gotchas: [`repo/docs/migration/1.21.1-to-1.21.4.md`](../../../../repo/docs/migration/1.21.1-to-1.21.4.md)
- [x] ~~**M2**~~ — merged into M1
- [x] **M1 patch** — Tinkers `3.12.1` (2026-10-24): first-boot staged-tags fix + worldgen gametests (38 gametest)
- [x] **M3** — 1.21.5 / NF 21.5.101 🏁 2026-10-24 — Mantle `1.14` / Tinkers `3.13.0`, tags `v1.21.5-1.14` / `v1.21.5-3.13.0`; battery: build + 673 unit + 40 gametest (39 + vanilla `always_pass`) + 15/15 uitest (visual verified). Plan [`2026-10-24-m3-1.21.5-port.md`](2026-10-24-m3-1.21.5-port.md); gotchas [`repo/docs/migration/1.21.4-to-1.21.5.md`](../../../../repo/docs/migration/1.21.4-to-1.21.5.md)
- [x] **M3 patch** — Tinkers `3.13.1` (2026-10-24): mob head item rendering + legacy tool model cleanup (16 uitest)
- [x] **M4** — **1.21.8** / NF 21.8.57 (1.21.6 + 1.21.7 folded in) 🔴 — **FECHADO 🏁 2026-11-23**: Fases 0–7 ✅ (2026-10-24 → 2026-11-23), release Mantle `1.15` / Tinkers `3.14.0`, tags `v1.21.8-1.15` / `v1.21.8-3.14.0` pushadas; battery: build + 674 unit + 58 gametest (57 + vanilla `always_pass`) + 31/31 uitest (visual verified vs 1.21.5 reference). Plano [`2026-10-24-m4-1.21.8-port.md`](2026-10-24-m4-1.21.8-port.md); gotchas [`repo/docs/migration/1.21.5-to-1.21.8.md`](../../../../repo/docs/migration/1.21.5-to-1.21.8.md)
- [ ] **M5** — **1.21.10** / NF 21.10.64 (1.21.9 folded in) 🟠 — **em andamento**: Fase 0 ✅ 2026-11-23 (pins, toolchain verificado, baseline Mantle 100 erros / 48 arquivos, assinaturas verificadas no decompilado), branch `1.21.10`; próximo: Fase 1 (Mantle compila). Plano [`2026-11-23-m5-1.21.10-port.md`](2026-11-23-m5-1.21.10-port.md)
- [ ] **M6** — 1.21.11 / NF 21.11 (1.21.10 moved into M5)
- [ ] **M7** — 26.1 → 26.2 / NF 26.2 (Java 25) 🏁 target
