# Advancements Sub-project 2, Plan B — Blaze Subtree (Design)

Date: 2026-07-20 · Status: **approved by user (brainstorm)** — exact tank-content matching via vanilla
`DataComponentPredicate`; completes the foundry tree and the full advancement port.

## Context

[Plan A](2026-07-20-advancements-remaining-defs-design.md) ported 26 of the remaining advancements but deferred the
**blaze subtree** — the three foundry advancements that need item matching by **tank fluid content**. The 1.20.1
reference matched them with a removed `ItemPredicate` constructor + `NbtPredicate` carrying the tank NBT
(`NBTTags.TANK` + `FluidTank.writeToNBT`). In 1.21 the tank content is a data component
(`TinkerSmeltery.TANK_FLUID` = `DataComponentType<SimpleFluidContent>`), so the match must be reworked onto a
`DataComponentPredicate`. This is Plan B.

Reference (read-only): the foundry block of the 1.20.1 provider,
`git 7c0ff783ea~1:src/main/java/slimeknights/tconstruct/common/data/AdvancementsProvider.java` lines ~324-372.

## Goal

Emit the three blaze-subtree foundry advancements at the singular `advancement/foundry/` path by extending Plan A's
`AdvancementsProvider`, **completing the foundry tree (7/7) and the full tinkers advancement port** (42 total).

## Scope — 3 advancements

- **`foundry/blaze`** (GOAL, parent `foundry/structure`): one criterion per seared+scorched tank block, each matching
  that block filled to capacity with blazing blood; `OR` strategy. Display: a `TankItem.setTank`-filled
  `scorchedTank(FUEL_GAUGE)` of blazing blood.
- **`foundry/plate_armor`** (GOAL, parent `foundry/blaze`): `hasItem` per plate-armor piece
  (`TinkerTools.plateArmor.forEach`). Trivial itself — deferred from Plan A only because its parent is `blaze`.
- **`foundry/manyullyn_lanterns`** (CHALLENGE, parent `foundry/structure`): one criterion per seared+scorched
  lantern, each matching that lantern filled to capacity with molten manyullyn AND `count >= 64`; `OR` strategy.
  Display: a filled `scorchedLantern` of molten manyullyn.

## Design — exact matching via `DataComponentPredicate`

The chosen approach (user decision) is **exact** matching, faithful to the 1.20.1 exact-NBT behavior: match an item
whose `TANK_FLUID` component equals `SimpleFluidContent(fluid, capacity)` — i.e., a tank filled to capacity. No new
sub-predicate is registered; the pilot's `tconstruct:tool` bridge is for tools, not tanks, and vanilla
`DataComponentPredicate` covers exact component matching.

New criterion helper on `AdvancementsProvider`:
```java
protected static Criterion<?> tankFluidCriterion(ItemLike block, Fluid fluid, int capacity, MinMaxBounds.Ints count) {
  return inventoryTrigger(ItemPredicate.Builder.item().of(block).withCount(count).hasComponents(
    DataComponentPredicate.builder()
      .expect(TinkerSmeltery.TANK_FLUID.get(), SimpleFluidContent.copyOf(new FluidStack(fluid, capacity)))
      .build()));
}
```
- `TinkerSmeltery.TANK_FLUID` is `DeferredHolder<…, DataComponentType<SimpleFluidContent>>` (`.get()` gives the type);
  `SimpleFluidContent.copyOf(FluidStack)` builds the value (both already used by `TankItem`).
- Per-block capacity from `block.getCapacity()` (`SearedTankBlock`/`SearedLanternBlock`).
- `blaze` passes `MinMaxBounds.Ints.ANY`; `manyullyn_lanterns` passes `MinMaxBounds.Ints.atLeast(64)`.
- Fluids: `TinkerFluids.blazingBlood.get()`, `TinkerFluids.moltenManyullyn.get()`.
- Display stacks: `TankItem.setTank(new ItemStack(block), new FluidStack(fluid, capacity))`.

The reference iterates `TinkerSmeltery.searedTank.forEach(...)` + `scorchedTank.forEach(...)` (tanks) and
`searedLantern`/`scorchedLantern` (lanterns), keying each criterion by the block's registry path and setting the
`OR` strategy so any single filled tank/lantern completes the advancement.

## Architecture

Extend (do not rewrite) `AdvancementsProvider`. Add the `tankFluidCriterion` helper and append the three
advancements to `generate()` right after the foundry roots (Plan A's `foundry/structure`), reparenting nothing —
`blaze` and `manyullyn_lanterns` parent to the existing `foundry/structure` holder, `plate_armor` to `blaze`.

## Testing

Extend `AdvancementsPilotTest`:
- `foundry/blaze` — criteria contain the `tconstruct:tank_fluid` component predicate; `requirements` is an `OR`
  group (all tank criteria in one group); parent is `tconstruct:foundry/structure`.
- `foundry/manyullyn_lanterns` — criteria carry a `count` ≥ 64 bound alongside the `tank_fluid` component.
- `foundry/plate_armor` — parent is `tconstruct:foundry/blaze`.

Then `runData` (regenerate; review the 3 new `foundry/*.json`) and a full `gradlew build` (battery + all pilot tests,
no regression).

## Verification / open items for the plan

Pin the exact 1.21 signatures during writing-plans (none are design risks — vanilla APIs; the plan verifies via
runData like Plan A's triggers):
- `DataComponentPredicate.builder().expect(DataComponentType<T>, T).build()`.
- `ItemPredicate.Builder.hasComponents(DataComponentPredicate)` and `.withCount(MinMaxBounds.Ints)`.
- `new FluidStack(Fluid, int)` (vs a `Holder<Fluid>` overload) and `SimpleFluidContent.copyOf(FluidStack)`.
- `SearedTankBlock.getCapacity()` / `SearedLanternBlock.getCapacity()` and the `TinkerSmeltery.searedTank`/
  `scorchedTank`/`searedLantern`/`scorchedLantern` / `TinkerTools.plateArmor` accessors used by the reference.

## Non-goals

None — this is the last piece; after Plan B the tinkers advancement port is complete (all categories). Any future
work (e.g., extra achievements) is out of scope.
