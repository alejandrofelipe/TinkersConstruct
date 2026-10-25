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
| **M4** | 1.21.6 / NF 21.6 | — | 🔴 Serialization `CompoundTag` → **`ValueInput`/`ValueOutput`**; `RenderPipelines.GUI_TEXTURE` blitting | 🔴 **ENORME** — ToolStack/materials/modifiers (de)serialization + GUIs |
| **M5** | 1.21.9 / NF 21.9 | 1.21.7, 1.21.8 | Keybind categories string-based; `SkinTextures`/`PlayerSkin` relocation; waypoint API; `YawProvider` | **Baixo/médio** — client/keybinds |
| **M6** | 1.21.11 / NF 21.11 | 1.21.10 | **`ResourceLocation` → `Identifier`** (Mojang rename); `FMLLoader.isProduction()` removed | 🟠 **Mecânico mas pervasivo** (MaterialId/ModifierId já saíram de ResourceLocation no 1.21.1 → ajuda) |
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

## Progress

- [x] **M1** — 1.21.4 / NF 21.4.161 (1.21.2 + 1.21.3 + old M2 folded in) 🏁 2026-10-24 — Mantle `1.13` / Tinkers `3.12.0`, tags `v1.21.4-1.13` / `v1.21.4-3.12.0`; battery: build + 672 unit + 31 gametest + 14/14 uitest (visual verified). Gotchas: [`repo/docs/migration/1.21.1-to-1.21.4.md`](../../../../repo/docs/migration/1.21.1-to-1.21.4.md)
- [x] ~~**M2**~~ — merged into M1
- [x] **M1 patch** — Tinkers `3.12.1` (2026-10-24): first-boot staged-tags fix + worldgen gametests (38 gametest)
- [x] **M3** — 1.21.5 / NF 21.5.101 🏁 2026-10-24 — Mantle `1.14` / Tinkers `3.13.0`, tags `v1.21.5-1.14` / `v1.21.5-3.13.0`; battery: build + 673 unit + 40 gametest (39 + vanilla `always_pass`) + 15/15 uitest (visual verified). Plan [`2026-10-24-m3-1.21.5-port.md`](2026-10-24-m3-1.21.5-port.md); gotchas [`repo/docs/migration/1.21.4-to-1.21.5.md`](../../../../repo/docs/migration/1.21.4-to-1.21.5.md)
- [ ] **M4** — 1.21.6 / NF 21.6 🔴
- [ ] **M5** — 1.21.9 / NF 21.9
- [ ] **M6** — 1.21.11 / NF 21.11
- [ ] **M7** — 26.1 → 26.2 / NF 26.2 (Java 25) 🏁 target
