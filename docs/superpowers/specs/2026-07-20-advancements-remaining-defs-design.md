# Advancements Sub-project 2, Plan A — Remaining Definitions + Config-Gating (Design)

Date: 2026-07-20 · Status: **approved by user (brainstorm)** — extend the pilot provider; config-gating via
`neoforge:conditions`; defer the two tank-content-matched foundry advancements (and their child) to Plan B.

## Context

The [advancements infra + pilot](2026-07-19-advancements-infra-pilot-design.md) (2026-07-20) re-enabled the
advancement datagen for the **tinkering path** (`tools/`, 13 advancements), building the `tconstruct:tool`
`ItemSubPredicate` bridge and rebuilding `AdvancementsProvider` on the vanilla 1.21 API at the singular
`advancement/` path. It explicitly deferred "the remaining defs + the config-gated replacement" to Sub-project 2.

This spec is **Sub-project 2, Plan A**: port the remaining advancement categories that are mechanical (reuse the
pilot's helpers + already-ported triggers/strategies) plus the one config-gated advancement. The two foundry
advancements that need item-matching by **tank fluid content** (`foundry/blaze`, `foundry/manyullyn_lanterns`),
and `foundry/plate_armor` (a child of `blaze` in the reference tree), are **Plan B** (a follow-up) — they require
`DataComponentPredicate` rework that carries real 1.21 uncertainty and shouldn't gate the mechanical bulk.

Reference (read-only): the 1.20.1 provider at `git 7c0ff783ea~1:src/main/java/slimeknights/tconstruct/common/data/AdvancementsProvider.java`.

## Goal

Emit the remaining tinkers advancement trees (`smeltery/`, `foundry/` sans the blaze subtree, `world/`, `internal/`)
at the singular `data/tconstruct/advancement/` path, matching the 1.20.1 structure, by **extending** the pilot's
`AdvancementsProvider` — no rewrite. One advancement (`internal/starting_book`) is config-gated and replaces the
removed `ConditionalAdvancement` with NeoForge's `neoforge:conditions` mechanism.

## Scope — 26 advancements

- **`smeltery/` (10):** puny_smelting, melter, sand_casting, gold_casting, cast_collector, mighty_smelting,
  structure, tinkers_anvil, tool_forge, abilities.
- **`foundry/` (4):** fantastic_foundry, encyclopedia, alloyer, structure.
- **`world/` (11):** tinkers_gadgetry, earth_island, sky_island, blood_island, ender_island, clay_island,
  slime_collector, piggybackpack, slimesuit, slimeskull, ancient_tools.
- **`internal/` (1, config-gated):** starting_book.

All predicates, modifier lists, material lists, and tree parents are **transcribed from the reference**, never
invented.

## Non-goals (Plan B / later)

- **`foundry/blaze`, `foundry/manyullyn_lanterns`** — item matching by tank fluid content. The reference uses a
  removed `ItemPredicate` constructor + `NbtPredicate`; 1.21 needs `DataComponentPredicate` on the tank component.
- **`foundry/plate_armor`** — trivial itself (`hasItem` per armor piece) but its reference parent is `blaze`;
  deferring it with the blaze subtree keeps Plan A a clean, self-contained subtree with no later reparenting.
- Any advancement category not listed above (none remain — there is no separate "gameplay" advancement category;
  `gameplay/starting_book` in the reference is the loot-table folder for the reward, not an advancement).

## Architecture

**Extend the existing `AdvancementsProvider`** (from the pilot). Its `builder`/`hasItem`/`hasTag`/`toolCriterion`/
`toolContextCriterion` helpers, `run()` serialization (`Advancement.CODEC` via
`provider.createSerializationContext(JsonOps.INSTANCE)`), and singular `advancement/` base path are reused as-is.

### 1. New criterion helpers (triggers already available)

All target triggers are vanilla 1.21 or already-ported Tinkers classes (verified present, used by the recipe
providers that generate cleanly):

- `placedBlockCriterion(Block)` → `ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(block)` — melter, alloyer.
- `containerCriterion(...)` → `BlockContainerOpenedTrigger` (`shared/inventory/BlockContainerOpenedTrigger.java`,
  ported) — smeltery/foundry `structure`.
- `locatedCriterion(structureHolder)` → `PlayerTrigger.TriggerInstance.located(LocationPredicate.inStructure(...))`
  — the 5 island advancements. (1.21 `LocationPredicate.inStructure` signature — the plan pins the exact form.)
- `itemUsedOnEntityCriterion(item, entityType)` → `PlayerInteractTrigger.TriggerInstance.itemUsedOnEntity(...)` —
  piggybackpack.
- `tickCriterion()` → `CriteriaTriggers.TICK.createCriterion(...)` — starting_book.

`CountRequirementsStrategy` (`common/data/CountRequirementsStrategy.java`, ported, used by recipe providers) is
reused for melter/alloyer.

### 2. Requirements override (small refactor)

The pilot's core `builder` calls `builder.requirements(AdvancementRequirements.Strategy.AND)` **after** the
consumer runs, which would clobber a consumer-set `OR`/count strategy. Fix: move the default `AND` to **before**
`consumer.accept(builder)`, so advancements that need `OR` (tinkers_anvil) or `CountRequirementsStrategy`
(melter/alloyer) set it in their consumer and win (last-write). No behaviour change for the pilot's advancements
(single-criterion → AND either way; re-verified by the existing pilot tests).

### 3. `hiddenBuilder` (no display)

A no-display, no-parent builder variant for `starting_book`: `Advancement.Builder.advancement()` with only a
criterion (`tickCriterion()`) and a loot reward (`AdvancementRewards.Builder.loot(ResourceKey<LootTable>)` — 1.21
takes a `ResourceKey`, built from `resource("gameplay/starting_book")`), routed through the config-gating below.

### 4. Config-gating — the `ConditionalAdvancement` replacement

NeoForge removed `ConditionalAdvancement`, but conditional data works via a `neoforge:conditions` array on the
data file — **proven**: the generated recipe advancements already carry e.g.
`"neoforge:conditions":[{"type":"tconstruct:config","prop":"glass_recipe_fix"}]`, and `ConfigEnabledCondition`
(`common/json/ConfigEnabledCondition.java`, with `SPAWN_WITH_BOOK`) is ported and used by those providers.

Design: the provider keeps a `Map<ResourceLocation, List<ICondition>>` filled alongside `advancements`. In `run()`,
after `Advancement.CODEC.encodeStart(...)` yields the advancement `JsonObject`, if that id has conditions, inject
`json.add("neoforge:conditions", <serialized conditions>)` before `saveJson`. Conditions serialize through the
NeoForge condition codec (the plan pins the exact call — `ICondition.LIST_CODEC` / the `neoforge:conditions` key)
against the same `RegistryOps`. `starting_book` is gated by `ConfigEnabledCondition.SPAWN_WITH_BOOK`.

### 5. Transcribe the trees into `generate()`

Append the smeltery/foundry(-blaze-subtree)/world/internal blocks to `generate()`, applying the same mechanical
transform used for `tools/`: `FrameType.X`→`AdvancementType.X`; `ToolStackItemPredicate.ofTool/ofContext(P)`→
`toolCriterion/toolContextCriterion(P)`; `Advancement`→`AdvancementHolder`; the vanilla `InventoryChangeTrigger`
`hasItems(...)` item/tag criteria → `hasItem`/`hasTag`. Predicates, modifier lists (abilities ≈ 60 modifiers via
`toolContextCriterion(HasModifierPredicate.hasUpgrade(id, 1))`), and material lists (slimeskull) are transcribed
verbatim from the reference.

## Data flow

`gatherData` → `new AdvancementsProvider(packOutput, lookupProvider)` (unchanged call site) → `run()` →
`registries.thenCompose` → `generate()` fills `advancements` + `conditions` → serialize each holder via
`Advancement.CODEC` with the NeoForge serialization context → inject `neoforge:conditions` where present →
`saveJson` at `advancement/<path>.json`.

## Testing

Extend `AdvancementsPilotTest` (or a sibling `AdvancementsTreeTest`):

- **Per-category sample** — one advancement from each of smeltery/foundry/world with the correct `parent` and a
  criterion of the expected trigger type (e.g. `smeltery/structure` uses the container trigger; `world/earth_island`
  uses the located trigger).
- **Config-gating** — `internal/starting_book` has a top-level `neoforge:conditions` array containing the
  `tconstruct:config` condition, has **no** `display`, and has the loot reward.
- **Requirements shapes** — `smeltery/tinkers_anvil` serializes an `OR` requirements array (both criteria in one
  group); `smeltery/melter` serializes the `CountRequirementsStrategy` grouping.
- **Tree parents resolve** — every Plan-A advancement id has a resolvable `parent` (or is the config-gated
  hidden one).

Then `runData` (regenerate + review diff — all under singular `advancement/`, no plural leak) and a full
`gradlew build` (battery + new tests, no regression).

## Verification / open items for the plan

- Pin the exact 1.21 signatures for `LocationPredicate.inStructure`, `PlayerInteractTrigger…itemUsedOnEntity`,
  `PlayerTrigger…located`, `ItemUsedOnLocationTrigger…placedBlock`, `CriteriaTriggers.TICK.createCriterion`,
  `AdvancementRewards.Builder.loot(ResourceKey)`, and the `neoforge:conditions` write call — the writing-plans
  step resolves these against the mapped sources / the ported recipe providers before inlining code.
- Confirm the ported Tinkers helpers used by the world tree: `ToolContextPredicate.set(Item)`,
  `HasMaterialPredicate(MaterialVariantId, int)`, `MaterialIdNBT.updateStack` (slimesuit/slimeskull display).
- `BlockContainerOpenedTrigger` container API shape in 1.21 (the reference used `.Instance.container(block)`).

None of these are design risks (the classes exist and the recipe providers use them); they are implementation
details the plan pins with verified code.
