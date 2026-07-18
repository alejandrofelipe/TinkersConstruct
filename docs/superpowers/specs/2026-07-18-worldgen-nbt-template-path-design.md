# Worldgen NBT template path — diagnosis & fix spec

Date: 2026-07-18 · Status: **implemented & green 2026-07-18** (user decisions: include `pack_format` fix; both unit tests; canonical fix)

## Symptom

In-game (CurseForge instance `new`, MC 1.21.1/NeoForge), `/locate structure` finds the Tinkers slime
islands, but travelling to the coordinates shows **no island** — it does not physically generate. The
same is reported for the other island variants. Ores (cobalt) were also flagged to verify.

## Root cause — island NBT templates live in the pre-1.21 `structures/` directory

MC **1.20.5 / 1.21** renamed the datapack template directory from `data/<ns>/structures/` (plural) to
`data/<ns>/structure/` (singular), part of the same batch of registry-folder renames as
`recipes`→`recipe`, `loot_tables`→`loot_table`, etc. `StructureTemplateManager` in 1.21 reads
templates **only** from the singular `structure/` folder.

The port applied this rename to the new gametest provider but **not** to the island templates or the
repalleter that generates them. So at worldgen time:

- `IslandStructure.generatePieces` (`world/worldgen/islands/IslandStructure.java:85-95`) always adds an
  `IslandPiece` as long as the *ResourceLocation* weighted list is non-empty — it checks
  `template.isPresent()` on the RL, **not** on the NBT existing. A `StructureStart` is therefore always
  produced, so **`/locate` always succeeds**.
- `IslandPiece` (`world/worldgen/islands/IslandPiece.java:43-48`) forwards the template name to the
  vanilla `TemplateStructurePiece` super-constructor, which loads via
  `StructureTemplateManager.getOrCreate(...)`. In 1.21 that method **returns a new empty
  `StructureTemplate` with no error** when the resource is missing → `postProcess` (lines 136-145)
  places zero blocks.

Net effect: the piece exists (bounding box → `/locate` hits) but nothing is placed → **invisible
island**. This is the exact reported symptom, and it affects **all** islands (earth/sky/ender/blood/
clay/ocean) because they share the one `tconstruct:island` structure type and the one repalleter.

### Confirmed evidence (in-repo inconsistency)

| Provider | Base path | Result |
|----------|-----------|--------|
| `gametest/GameTestStructureProvider.java:22` | `"structure/gametest"` (**singular**) | gametest NBTs load; gametests pass |
| `library/data/AbstractStructureRepalleter.java:45` | `"structures"` (**plural**) | island NBTs written to old path |
| `library/data/AbstractStructureRepalleter.java:72` | reads source dirt NBTs from `"structures"` | source also on old path |

On disk:
- `src/main/resources/data/tconstruct/structures/islands/dirt/{0x1x0,2x2x4,4x1x6,8x1x11,11x1x11}.nbt` — hand-authored source, **plural**
- `src/generated/resources/data/tconstruct/structures/islands/{earth,sky,ender,blood}/*.nbt` — repalletized (5 each), **plural**
- `src/generated/resources/data/tconstruct/structure/gametest/*.nbt` — **singular** (the only thing under `structure/`)
- `data/tconstruct/structure/islands/` — **does not exist anywhere**

Structure JSONs reference e.g. `tconstruct:islands/earth/0x1x0` (`worldgen/structure/earth_slime_island.json`),
which 1.21 resolves under `data/tconstruct/structure/…` — where nothing exists.

## NBT scan — every other NBT site (per user request "outros problemas de NBTs")

The full NBT inventory across both repos, and each one's loader/status:

| NBT site | Loader | Directory-rename exposure | Status |
|----------|--------|---------------------------|--------|
| `data/tconstruct/structures/islands/**` (25) | vanilla `StructureTemplateManager` | **YES — reads singular `structure/`** | **BROKEN** (the bug) |
| `data/tconstruct/structure/gametest/**` (2) | vanilla `StructureTemplateManager` | already singular | OK |
| `assets/tconstruct/book/structures/{foundry,smeltery}.nbt` | Mantle `ContentStructure` → `BookRepository.getResourceLocation` + `NbtIo` | **NO** — book uses its own path resolver, not the template manager | OK (see risk below) |
| `assets/mantle/books/test/structure.nbt` | same Mantle book path | NO | OK |

- **Book structures are NOT affected by the rename.** `ContentStructure.load()`
  (`repo/.../book/data/content/ContentStructure.java:50-59`) resolves the NBT through the book's own
  `BookRepository`, then `NbtIo.readCompressed` + `StructureTemplate.load` — it never touches
  `StructureTemplateManager`. The plural `book/structures/` path is internal and self-consistent, and
  the book pages are already exercised by the `book_*` uitest scenarios.
- **Separate latent risk in the book path** (not a rename bug): `ContentStructure` reads the private
  `StructureTemplate.palettes` field via **reflection** (`readFirstPaletteBlocks`, lines 65-96) because
  1.21.1 removed the public accessor. It relies on the Mojang-official field name `palettes` (correct
  in NeoForge prod mappings), so it works today, but it is fragile and worth a sanity test.

## Secondary migration gaps (found during the scan; not the blocker)

- **`pack.mcmeta` still declares `pack_format: 15`** (1.20.1). 1.21.1 data packs are format **48**.
  Non-fatal (NeoForge loads mod data regardless — which is why features and `/locate` still work), but
  it is the fingerprint of the same partial datapack migration that left the NBTs behind.
- **`StructureUpdater` is commented out** (`world/TinkerStructures.java:120-121`) — the datafixer/
  re-pather that would have upgraded and re-emitted the NBTs is disabled, so nothing auto-corrected the
  path. Its line 121 form even targets `"book/structures"`, confirming the book NBTs were meant to be
  handled by a separate path.

## Ores — fully wired, should generate (no bug)

Cobalt ore + geodes are intact end-to-end; nothing to fix:

- Block `cobaltOre` (`world/TinkerWorld.java:137`); geodes 254-268.
- Configured features `cobalt_ore_small/large` (`world/data/WorldgenProvider.java:251-264`).
- Placed features (268-278): small count 5 @ y8, large count 3 @ y8-32.
- BiomeModifier `cobalt_ore.json` = `neoforge:add_features` → `#minecraft:is_nether` →
  `[cobalt_ore_small, cobalt_ore_large]` at `underground_decoration`.

All corresponding JSONs exist under `worldgen/` and `neoforge/biome_modifier/`, which are **already
singular** and untouched by the rename. Cobalt is Nether-only, so it only appears in the Nether in
newly-generated chunks.

## Proposed fix (islands)

1. Move the 5 hand-authored dirt source templates
   `src/main/resources/data/tconstruct/structures/islands/dirt/` → `.../structure/islands/dirt/`.
2. In `AbstractStructureRepalleter`, change the output base path `"structures"` → `"structure"`
   (line 45) **and** the source read folder `"structures"` → `"structure"` (line 72).
3. Re-run `runData`; delete the stale generated NBTs under `src/generated/resources/.../structures/`.
4. Fix `pack.mcmeta` `pack_format` 15 → 48 (correctness; low-risk).

## Test strategy (the "faça testes para isso" deliverable)

Primary — a **plain-JVM unit test** that reproduces the bug and guards against regression, matching the
existing `src/test` style (JUnit 5, `BaseMcTest` bootstrap, `JsonFileLoader`):

- **`IslandTemplateResourceTest`** — for each `tconstruct:island` structure JSON under
  `data/tconstruct/worldgen/structure/`, parse `templates[].data`, and assert each referenced template
  resolves to an **existing, non-empty** NBT at the path the `StructureTemplateManager` uses
  (`data/<ns>/structure/<path>.nbt`, singular) — load it and assert palette/size > 0.
  - **Fails today** (templates only exist under plural `structures/`) → reproduces the bug.
  - **Passes after the fix** → regression guard, and automatically covers any future island template.
- Prerequisite to confirm at implementation time: `src/generated/resources` is on the test classpath
  (NeoGradle wires generated resources into `main`); if not, the test reads via the source tree.

Secondary (in scope per user — covers the other NBT frontier):

- **`BookStructureTemplateTest`** — load `foundry.nbt` + `smeltery.nbt` through the same NBT path the
  book uses and assert the palette-block list (the reflective `readFirstPaletteBlocks`) is non-empty —
  a sanity check on the fragile reflection, independent of the island bug.

Not doing: a worldgen **gametest** that grows a real island and counts blocks — non-deterministic
placement and heavy boot cost for little gain over the resource-resolution test, which pinpoints the
defect directly.

## Implementation result (2026-07-18)

Canonical fix applied, whole battery green:

- `AbstractStructureRepalleter` output base + source read folder `"structures"` → `"structure"` (lines 45, 72).
- The 5 hand-authored dirt base templates moved `main/…/structures/islands/dirt/` → `…/structure/islands/dirt/`;
  stale plural trees deleted from both `main` and `generated`.
- `runData` regenerated the 20 repalletized variants under `structure/islands/{earth,sky,ender,blood}/`.
- `pack.mcmeta` `pack_format` 15 → 48 (cosmetic — NeoForge ignores it for mods; Mantle deliberately ships 4).
- Tests: `IslandTemplateResourceTest` (RED on the plural path, GREEN after the move) + `BookStructureTemplateTest`.
- Full `build`: **651 unit / 0 failures / 2 @Disabled**; `runData` `BUILD SUCCESSFUL`.

In-world confirmation (islands actually rendering with blocks) is left to launching the CurseForge instance
with the redeployed jar; the resource-resolution test proves the templates now resolve where the game reads them.
