# Advancements — infra + tinkering-path pilot (Sub-project 1) — design spec

Date: 2026-07-19 · Status: **approved by user (brainstorm)** — decompose (infra+pilot first), vanilla provider, defer config-gated

## Context

The Tinkers advancement tree was disabled during the 1.21.1/NeoForge port. `AdvancementsProvider.run()`
returns `CompletableFuture.completedFuture(null)` (it is still wired into datagen at `TConstruct.java:229`,
but emits nothing). The 1.20.1 version (`git 7c0ff783ea~1`, 573 lines) generated ~40-50 advancements via a
`builder(icon, resource(id), parent, FrameType, criteria→)` helper across `tools/`, `smeltery/`, `foundry/`,
`world/`, `internal/`, `gameplay/`. Only the auto-generated **recipe-unlock** advancements survive today
(they come from the recipe provider, under `data/tconstruct/advancement/recipes/**`).

**Runtime triggers are already ported** — the one custom `CriterionTrigger`, `BlockContainerOpenedTrigger`,
is on the 1.21 `SimpleCriterionTrigger`+`Codec` API and registered (`TinkerCommons.java:220`). Only the
datagen JSON is missing. The tool predicates the advancements match against (`ToolStackPredicate`,
`StatInSetPredicate`, `HasMaterialPredicate`, `HasModifierPredicate`, `StatInRangePredicate`,
`ToolContextPredicate`) all exist and are unit-tested.

**Decomposition (user decision):** item D is delivered in two sub-projects. **This spec is Sub-project 1:
the infra + a pilot** proving it. Sub-project 2 (the remaining defs + the config-gated replacement) gets its
own spec/plan.

## The three port blockers

1. **Scaffolding (mechanical).** `Advancement`→`AdvancementHolder`, `FrameType`→`AdvancementType`,
   `RequirementsStrategy`→`AdvancementRequirements.Strategy`, `AdvancementRewards.Builder.loot` now takes
   `ResourceKey<LootTable>`, `ContextAwarePredicate.ANY`/`EnchantmentPredicate.NONE` removed,
   `LocationPredicate.inStructure` changed. Also: the datapack dir was renamed `advancements/`→`advancement/`
   (singular) in 1.20.5/1.21 — the same batch as the `structures/`→`structure/` rename fixed 2026-07-18. The
   stub's `basePath` is still the plural `"advancements"`.
2. **`ItemSubPredicate` bridge (the real work).** 14 criteria use
   `InventoryChangeTrigger.hasItems(ToolStackItemPredicate.ofTool(...))`. `ItemPredicate` became a `final
   record` in 1.20.5, so a tool predicate can no longer *be* an `ItemPredicate`; custom item matching in
   advancements now goes through a registered `ItemSubPredicate`.
3. **`ConditionalAdvancement`** — 5 config-gated advancements used NeoForge's `ConditionalAdvancement`, which
   was removed. **Deferred to Sub-project 2** — none of the config-gated advancements are on the pilot's
   tinkering path.

## Goals (Sub-project 1)

1. A registered `ItemSubPredicate` that bridges the existing tool predicates into 1.21 advancements.
2. A rewritten `AdvancementsProvider` on the 1.21 API that emits real JSON at the singular `advancement/` path.
3. The **tinkering path** pilot (`tools/`, ~13 advancements) generated and validated — the 4 tool-predicate
   ones (`netherite_tier`, `perfect_aim`, `one_shot`, `material_master`) prove the bridge end-to-end.
4. Full battery stays green; a unit test validates the pilot JSON.

## Non-goals (Sub-project 2)

- The `smeltery/`, `foundry/`, `world/`, `internal/`, `gameplay/` advancement defs.
- The `ConditionalAdvancement` replacement (config-gated advancements).
- In-game toast/screen polish beyond "they generate and resolve".

## Design

### A. `ToolItemSubPredicate` — the bridge (central piece)

A new `ItemSubPredicate` (record) wrapping an `IJsonPredicate<IToolStackView>`:
- `matches(ItemStack)` = `stack.is(TinkerTags.Items.MODIFIABLE) && predicate.matches(ToolStack.from(stack))`
  — the exact logic already in `ToolStackItemPredicate.matches` (which stays as the standalone predicate; the
  sub-predicate delegates to the same rule).
- **Serialization is the key enabler:** the Mantle `Loadable.codec()` (`repo/.../data/loadable/Loadable.java:90`,
  returns a `LoadableCodec`) turns any Loadable into a vanilla `Codec`. So the sub-predicate's `MapCodec` is
  built from `ToolStackPredicate.LOADER.codec()` — no hand-written codec for the predicate tree.
- Register its `ItemSubPredicate.Type` into `BuiltInRegistries.ITEM_SUB_PREDICATE_TYPE` via a `DeferredRegister`
  (id `tconstruct:tool`), wired on the mod bus (mirror how `TinkerIngredients.init(bus)` registers ingredient
  types at `TConstruct.java:141`).
- Factory `ToolItemSubPredicate.ofTool(pred)` / `.ofContext(pred)` mirroring the old `ToolStackItemPredicate`
  factories, so the provider call sites read the same.

### B. `AdvancementsProvider` rewrite

Rebuild on the vanilla **`AdvancementProvider` + `AdvancementSubProvider`** (idiomatic 1.21; it handles the
singular `advancement/` path and `AdvancementHolder` for free). A `TinkeringPathAdvancements` sub-provider
carries the `tools/` tree. The old `builder(icon, id, parent, FrameType, criteria→)` helper is re-expressed
with `Advancement.Builder.advancement()` + `AdvancementType`; `hasItems(ToolStackItemPredicate.ofTool(p))`
becomes `ItemPredicate.Builder.item().withSubPredicate(TinkerSubPredicates.TOOL, ToolItemSubPredicate.ofTool(p))`.
`hasItem`/`hasTag` criteria (non-tool) use vanilla `InventoryChangeTrigger` with plain `ItemPredicate`.

### C. Pilot — tinkering path (`tools/`, ~13 advancements)

`materials_and_you`(root) → `part_builder` → `make_part` / `tinker_station` → `tinker_tool` →
`netherite_tier`* → `perfect_aim`* / `one_shot`* → `material_master`* (and `travelers_gear`, `tool_smith`,
`modified` → `upgrade_slots`). Starred ones exercise the `ItemSubPredicate` bridge (`StatInSetPredicate`,
`StatInRangePredicate`, `HasMaterialPredicate`). This one coherent tree proves both blockers.

## Verification

- `runData` writes `data/tconstruct/advancement/tools/*.json` (singular path); review the git diff.
- **Unit test** (`src/test`, plain-JVM, `BaseMcTest` + `JsonFileLoader` style): for each pilot advancement
  JSON, assert it parses, has the expected parent, and — for the 4 tool-predicate ones — that the criterion
  carries an `items` predicate with a `tconstruct:tool` sub-predicate that round-trips through the registered
  type. This is the RED→GREEN gate (fails while the provider emits nothing / the sub-predicate is unregistered).
- Full `build` green (unit + the 654 existing) and `runData` `BUILD SUCCESSFUL`.

## Risks / notes

- The `ItemSubPredicate.Type` codec must round-trip: serialize (datagen) and deserialize (load). The unit
  test covers both directions on the pilot JSON.
- If the vanilla `AdvancementSubProvider` seam proves awkward with the Mantle `GenericDataProvider` base, fall
  back to keeping the custom provider and only swapping the helper internals — the pilot scope is unchanged.
