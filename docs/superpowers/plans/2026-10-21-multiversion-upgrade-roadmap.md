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
- **M1 lands on 1.21.3 / NF 21.3.100, not 1.21.2 (user decision).** NeoForge for 1.21.2 never left beta
  (only `21.2.0-beta` / `21.2.1-beta`); the first stable line of this checkpoint is 21.3.x (betas end at
  21.3.55, stable from 21.3.56, latest `21.3.100`). 1.21.3 is a vanilla bugfix over 1.21.2 with ~identical
  mod breaks (ChampionAsh's primer is "1.21.1 → 1.21.2" and covers both). So the **1.21.3 piggyback moves
  from M2 into M1**, keeping each tagged checkpoint on a stable base. Exact pins: MC `1.21.3`, NeoForge
  `21.3.100`, Parchment `1.21.3 / 2024.12.07`, Java 21 (unchanged). Detailed plan:
  [`2026-10-21-m1-1.21.3-port.md`](2026-10-21-m1-1.21.3-port.md).

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

> **M1 scope note (2026-10-21):** M1 now lands on **1.21.3** (NF `21.3.100`) with 1.21.2 folded in —
> see the decision log above. The headline/impact for M1/M2 in the table reflects this.

Above 1.21.1: `1.21.2 → 1.21.3 → 1.21.4 → 1.21.5 → 1.21.6 → 1.21.7 → 1.21.8 → 1.21.9 → 1.21.10 →
1.21.11 → 26.1 → 26.2 (→ 26.3)`. The **2026 numbering change** moved MC from `1.21.x` to year-based `26.x`.

| # | Checkpoint (MC / NeoForge) | Piggyback | Headline breaks | Tinkers impact |
|---|---|---|---|---|
| **M1** | 1.21.3 / NF 21.3.100 | 1.21.2 | 🔴 **Recipes → datapack registry** (`display()`/`PlacementInfo`, `RecipeHolder`→`ResourceKey`); **item/block `setId` required**; `InteractionResultHolder` removed; `GuiGraphics.blit`+`CoreShaders`; **EntityRenderState**; model `bake`/`resolveDependencies`; `Tier`→`ToolMaterial`, `ArmorItem.Type`→`ArmorType`; `FoodProperties`→`Consumable`; registry/holder renames; `getMaxY` off-by-one; `hurt`→`hurtServer` | 🟠 **Médio-Alto** — recipes + InteractionResult (~73 files) + item/block ID (~200 sites) + client-render rewrite are the walls (see M1 plan) |
| **M2** | 1.21.4 / NF 21.4 | — | `GatherDataEvent` → `.Client`/`.Server` split; model loading | **Médio** — datagen (single `clientData` run), models |
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
  accept visual-neutral changes as in the 1.21.1 close.

## Progress

- [ ] **M1** — 1.21.3 / NF 21.3.100 (1.21.2 folded in) 🟠 — plan written 2026-10-21, execution pending
- [ ] **M2** — 1.21.4 / NF 21.4
- [ ] **M3** — 1.21.5 / NF 21.5 🔴
- [ ] **M4** — 1.21.6 / NF 21.6 🔴
- [ ] **M5** — 1.21.9 / NF 21.9
- [ ] **M6** — 1.21.11 / NF 21.11
- [ ] **M7** — 26.1 → 26.2 / NF 26.2 (Java 25) 🏁 target
