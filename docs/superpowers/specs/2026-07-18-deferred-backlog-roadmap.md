# Deferred backlog — priority matrix & implementation roadmap

Date: 2026-07-18 · Basis: 3-agent read-only sizing investigation

## Reality check — half the original list was already done or dead

The (2-day-old) project memory over-listed the backlog. Verified current state:

**Already implemented — drop from backlog:**
- **Smeltery slot NBT byte→int** — commits `13627822df`, `74e4fa5082`. Save-compatible via an explicit `TAG_BYTE` + `& 255` unsigned mask; ceiling `MAX_SIZE = 10920` (`MeltingModuleInventory.java:31,320,327,337,353`).
- **`forge:`→`c:` fluid tags** — migrated centrally in Mantle (`COMMON = "c"`, `Mantle.java:93-94`); 46 tags under `data/c/tags/fluid/**`; no `data/forge/` dir.
- **Custom `IngredientType` registration** — `ItemNameIngredient`/`NBTNameIngredient` `getType()` no longer throws; registered `MantleIngredients.java:28-29`, wired `Mantle.java:123`.
- **`MobTypePredicate` loader** — registered `Mantle.java:208`. Only a stale comment remains (`HolyModifier.java:14`).

**Dead code — not a functional gap:**
- **`CombatHelper.attack()` / `getOffhandAttribute()`** — zero call sites; the live attribute helpers beside them are already ported. Options: delete, or leave.

## Priority matrix (real pending items)

Priority = f(broken? · visible impact ÷ effort). **P1** = broken/degraded and cheap. **P2** = real value. **P3** = polish/cosmetic/hygiene.

| Item | Repo | Broken? | Impact | Effort | Risk | Priority |
|------|------|---------|--------|--------|------|----------|
| **A. Fluid transfer NBT (potions)** | Mantle | **Yes** (regression) | Med | S | Low | **P1** |
| **B. Piggyback capability** | Tinkers | Degraded (no crash) | Med | S | Low | **P1** |
| C. Powder-snow bucket capability | Tinkers | No (compat gap) | Low | S | Low | P2 |
| D. Advancements (tree) | Tinkers | No (absent) | Med (visible) | **L** | Low–Med | P2 |
| E. Extra heart HUD render | Mantle | No (cosmetic) | Low | M | Med | P3 |
| F. Generic Fill/EmptyFluidWithNBT pair | Mantle | No (dormant) | Low | M | Low | P3 |
| G. Cleanups (dead combat code, stale comment, StructureUpdater) | both | No | — | S | Low | P3 |

## Roadmap — 3 waves

### Wave 1 — Quick wins (P1 + trivial hygiene), one green step each
1. **A. Fluid transfer potions** (S, *actively broken*). Emptying a non-water potion into a Mantle-transfer tank/melter strips `PotionContents`; filling back yields a blank potion. Fix `EmptyPotionTransfer.getFluid` (copy `DataComponents.POTION_CONTENTS` via `DataComponentPatch`) and mirror on `FillFluidWithNBTTransfer.getFilled`. **Reference impl already in tconstruct** (`fluids/item/EmptyPotionTransfer.java:31-40`, `fluids/fluids/PotionFluidType.java:69-75`). Exercised by `data/mantle/mantle/fluid_transfer/potion/{empty,fill_potion}.json` gated on `c:potion` (populated).
2. **B. Piggyback cap** (S, *degraded*). `PiggybackCapability.PIGGYBACK` is unregistered → per-tick `updatePassengers()` never runs (null-checked, no crash). Add a `registerCapabilities` to `TinkerGadgets` with `event.registerEntity(...)`.
3. **C. Powder-snow cap** (S, opportunistic — same `RegisterCapabilitiesEvent` surface as B). `event.registerItem(...)` for `FluidEvents.powderSnowHandler`.
4. **G-lite.** Delete the stale `HolyModifier.java:14` comment.

### Wave 2 — Advancements (P2, L — deserves its own brainstorm + spec)
5. **D. Advancements.** ~50 defs (git `7c0ff783ea~1`) gone; `AdvancementsProvider.run()` emits nothing. Runtime triggers already work. Sub-sequence: (i) registered `ItemSubPredicate` bridge (`ItemPredicate` became a final record in 1.20.5, blocker at `ToolStackItemPredicate.java:16-24`); (ii) scaffolding port (`Advancement`→`AdvancementHolder` etc.); (iii) `ConditionalAdvancement` replacement; (iv) re-author defs. Needs a dedicated spec.

### Wave 3 — Polish (P3)
6. **E. Extra heart render** (M). Registered but short-circuits (`ExtraHeartRenderHandler.java:93`); vanilla already draws hearts, only custom styling missing. Needs `Gui.leftHeight` (likely an AT).
7. **F. Generic Fill/EmptyFluidWithNBT pair** (M, dormant — no shipped data).
8. **G. Cleanups.** Delete dead `CombatHelper.attack()`/`getOffhandAttribute()`; decide `StructureUpdater` (optional DataVersion 3465→current bake).

## Suggested entry point
Wave 1 → **A (fluid potions)** (only actively-broken, ~copy-paste from tconstruct reference), then **B (piggyback)**. **D (advancements)** earns its own spec before code.
