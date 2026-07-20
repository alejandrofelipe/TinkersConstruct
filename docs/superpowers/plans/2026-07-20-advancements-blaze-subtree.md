# Advancements Sub-project 2, Plan B — Blaze Subtree Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Emit the three blaze-subtree foundry advancements (`foundry/blaze`, `foundry/plate_armor`, `foundry/manyullyn_lanterns`) at the singular `advancement/foundry/` path, completing the foundry tree (7/7) and the full tinkers advancement port (42 total).

**Architecture:** Extend Plan A's `AdvancementsProvider` with one `tankFluidCriterion` helper that matches an item whose `TinkerSmeltery.TANK_FLUID` data component equals `SimpleFluidContent(fluid, capacity)` (exact, via vanilla `DataComponentPredicate`), then append the three advancements to `generate()`. The `foundry/structure` builder (currently not captured) is reassigned to an `AdvancementHolder foundry` local so `blaze`/`manyullyn_lanterns` can parent to it.

**Tech Stack:** Java 21, NeoForge 21.1.234, NeoGradle (composite build with `../repo` Mantle), JUnit 5 + AssertJ, vanilla `DataComponentPredicate` + NeoForge `SimpleFluidContent`/`FluidStack`.

**Spec:** `docs/superpowers/specs/2026-07-20-advancements-blaze-subtree-design.md`

**Reference (read-only, do not run):** old provider at `git 7c0ff783ea~1:src/main/java/slimeknights/tconstruct/common/data/AdvancementsProvider.java` — the foundry `blazingBlood`/`plate_armor`/`manyullyn_lanterns` block, lines ~340-372.

## Global Constraints

- **Build/test on Windows via PowerShell**, `JAVA_HOME` = `C:\Users\aleja\scoop\apps\temurin21-jdk\current` in the SAME command as gradlew. Daemon disabled → cold runs are slow, not failures; background long runs. The JVM's "Sharing is only supported…" stderr warning makes PowerShell report exit 1 even on `BUILD SUCCESSFUL` — **check the `BUILD SUCCESSFUL`/`FAILED` line, not the exit code.**
- Canonical build cmd: `$env:JAVA_HOME="C:\Users\aleja\scoop\apps\temurin21-jdk\current"; & "C:\Users\aleja\DEV\New Tinkers\tinkers\gradlew.bat" -p "C:\Users\aleja\DEV\New Tinkers\tinkers" <task>`
- Advancement JSONs go under the **singular** `data/tconstruct/advancement/`.
- The `AdvancementsProvider` already uses `datapackRegistryProvider.getRegistryProvider()` (Plan A) and serializes via `provider.createSerializationContext(JsonOps.INSTANCE)` — do NOT change that wiring.
- Conventional-commits in English. **No `Co-Authored-By` / "Generated with" trailer.**
- **Exact matching** (user decision): match `TANK_FLUID == SimpleFluidContent(fluid, capacity)` — a full tank. No new sub-predicate; vanilla `DataComponentPredicate` only. Transcribe blocks/predicates from the reference, never invent.

---

### Task 1: `tankFluidCriterion` helper + the blaze subtree (3 advancements)

**Files:**
- Modify: `src/main/java/slimeknights/tconstruct/common/data/AdvancementsProvider.java`
- Modify: `src/test/java/slimeknights/tconstruct/common/data/AdvancementsPilotTest.java`
- Generated: `src/generated/resources/data/tconstruct/advancement/foundry/{blaze,plate_armor,manyullyn_lanterns}.json`

**Interfaces:**
- Consumes: Plan A helpers (`builder`, `hasItem`, `inventoryTrigger`) + `TinkerSmeltery.TANK_FLUID`.
- Produces: `tankFluidCriterion(ItemLike block, Fluid fluid, int capacity, MinMaxBounds.Ints count)`; the `foundry` `AdvancementHolder` local (from reassigning the `foundry/structure` builder).

- [ ] **Step 1: Write the failing tests** — add to `AdvancementsPilotTest`:

```java
  @Test
  void foundry_blazeUsesTankFluidComponent() {
    JsonObject json = loader.loadJson("tconstruct", "foundry/blaze");
    assertThat(json.get("parent").getAsString()).isEqualTo("tconstruct:foundry/structure");
    assertThat(json.getAsJsonObject("criteria").toString()).contains("tconstruct:tank_fluid");
    // OR strategy => one requirements group holding all the per-tank criteria
    assertThat(json.getAsJsonArray("requirements").size()).isEqualTo(1);
  }

  @Test
  void foundry_lanternsUseCountAndTankFluid() {
    String criteria = loader.loadJson("tconstruct", "foundry/manyullyn_lanterns").getAsJsonObject("criteria").toString();
    assertThat(criteria).contains("tconstruct:tank_fluid");
    assertThat(criteria).contains("count"); // the >=64 count bound (blaze passes ANY, which is omitted)
  }

  @Test
  void foundry_plateArmorParentsToBlaze() {
    assertThat(loader.loadJson("tconstruct", "foundry/plate_armor").get("parent").getAsString()).isEqualTo("tconstruct:foundry/blaze");
  }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `... gradlew.bat -p "...\tinkers" test --tests "*AdvancementsPilotTest"`
Expected: FAIL — `Resource … foundry/blaze.json doesn't exist`.

- [ ] **Step 3: Add imports to `AdvancementsProvider`**

```java
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.smeltery.block.component.SearedLanternBlock;
import slimeknights.tconstruct.smeltery.item.TankItem;
```
(`SearedTankBlock`, `TinkerSmeltery`, `TinkerTools`, `ArmorItem`, `BuiltInRegistries`, `ItemStack`, `Consumer` are already imported from Plan A.)

- [ ] **Step 4: Add the `tankFluidCriterion` helper**

Add next to the other criterion helpers (after `containerCriterion`/`locatedCriterion`):
```java
  /** Criterion matching an item whose tank is filled to capacity with the given fluid (exact DataComponentPredicate). */
  protected static Criterion<?> tankFluidCriterion(ItemLike block, Fluid fluid, int capacity, MinMaxBounds.Ints count) {
    return inventoryTrigger(ItemPredicate.Builder.item().of(block).withCount(count).hasComponents(
      DataComponentPredicate.builder()
        .expect(TinkerSmeltery.TANK_FLUID.get(), SimpleFluidContent.copyOf(new FluidStack(fluid, capacity)))
        .build()));
  }
```
Note (implementer): pin the exact vanilla API (all standard 1.21, but unused elsewhere in the tree so verify via runData): `ItemPredicate.Builder.hasComponents(DataComponentPredicate)` + `.withCount(MinMaxBounds.Ints)`, `DataComponentPredicate.builder().expect(DataComponentType<T>, T).build()`, and `new FluidStack(Fluid, int)` (NeoForge; if it wants a `Holder<Fluid>`, use `fluid.builtInRegistryHolder()`). `TinkerSmeltery.TANK_FLUID.get()` → `DataComponentType<SimpleFluidContent>`; `SimpleFluidContent.copyOf(FluidStack)` is what `TankItem.setTank` uses.

- [ ] **Step 5: Capture the `foundry` holder + emit the blaze subtree in `generate()`**

First, reassign the existing `foundry/structure` builder (Plan A left it uncaptured) to a local:
```java
    AdvancementHolder foundry = builder(TinkerSmeltery.foundryController, resource("foundry/structure"), alloyer, AdvancementType.TASK, builder ->
      builder.addCriterion("open_foundry", containerCriterion(TinkerSmeltery.foundry.get())));
```
Then, immediately after that line (before the `// exploration path` comment), append the blaze subtree — transcribed from the reference with the transform `FrameType.X`→`AdvancementType.X`, `ItemPredicate.Builder.item().of(b).hasNbt(tank)` → `tankFluidCriterion(b, fluid, b.getCapacity(), count)`, `RequirementsStrategy.OR`→`AdvancementRequirements.Strategy.OR`, and `getTankWith(fluid, cap)` display → `new FluidStack(fluid, cap)`:
```java
    // foundry: blaze subtree (tank fluid content matching via DataComponentPredicate)
    AdvancementHolder blazingBlood = builder(TankItem.setTank(new ItemStack(TinkerSmeltery.scorchedTank.get(SearedTankBlock.TankType.FUEL_GAUGE)), new FluidStack(TinkerFluids.blazingBlood.get(), SearedTankBlock.TankType.FUEL_GAUGE.getCapacity())),
        resource("foundry/blaze"), foundry, AdvancementType.GOAL, builder -> {
      Consumer<SearedTankBlock> with = block ->
        builder.addCriterion(BuiltInRegistries.BLOCK.getKey(block).getPath(), tankFluidCriterion(block, TinkerFluids.blazingBlood.get(), block.getCapacity(), MinMaxBounds.Ints.ANY));
      TinkerSmeltery.searedTank.forEach(with);
      TinkerSmeltery.scorchedTank.forEach(with);
      builder.requirements(AdvancementRequirements.Strategy.OR);
    });
    builder(TinkerTools.plateArmor.get(ArmorItem.Type.CHESTPLATE).getRenderTool(), resource("foundry/plate_armor"), blazingBlood, AdvancementType.GOAL, builder ->
      TinkerTools.plateArmor.forEach((type, armor) -> builder.addCriterion("crafted_" + type.getName(), hasItem(armor))));
    builder(TankItem.setTank(new ItemStack(TinkerSmeltery.scorchedLantern), new FluidStack(TinkerFluids.moltenManyullyn.get(), TinkerSmeltery.scorchedLantern.get().getCapacity())),
        resource("foundry/manyullyn_lanterns"), foundry, AdvancementType.CHALLENGE, builder -> {
      Consumer<SearedLanternBlock> with = block ->
        builder.addCriterion(BuiltInRegistries.BLOCK.getKey(block).getPath(), tankFluidCriterion(block, TinkerFluids.moltenManyullyn.get(), block.getCapacity(), MinMaxBounds.Ints.atLeast(64)));
      with.accept(TinkerSmeltery.searedLantern.get());
      with.accept(TinkerSmeltery.scorchedLantern.get());
      builder.requirements(AdvancementRequirements.Strategy.OR);
    });
```
Note (implementer): confirm the reference accessors compile as-is (all used in the 1.20.1 reference): `TinkerSmeltery.searedTank`/`scorchedTank` (`EnumObject`, `.forEach(Consumer<SearedTankBlock>)` and `.get(TankType)`), `TinkerSmeltery.searedLantern`/`scorchedLantern` (`.get()` → `SearedLanternBlock`), `SearedTankBlock.getCapacity()` / `SearedLanternBlock.getCapacity()`, `SearedTankBlock.TankType.FUEL_GAUGE.getCapacity()`, `TinkerTools.plateArmor` (armor `EnumObject`, `.get(ArmorItem.Type)`→`getRenderTool()` and `.forEach((type,armor)->…)`), `TinkerFluids.blazingBlood`/`moltenManyullyn`.

- [ ] **Step 6: Run datagen, then the tests**

Run (background): `... gradlew.bat -p "...\tinkers" runData`
Then: `... gradlew.bat -p "...\tinkers" test --tests "*AdvancementsPilotTest"`
Expected: `runData` BUILD SUCCESSFUL; tests PASS. Review the diff — `advancement/foundry/{blaze,plate_armor,manyullyn_lanterns}.json` created; `blaze`/`manyullyn_lanterns` criteria carry a `components.tconstruct:tank_fluid` predicate; `manyullyn_lanterns` also a `count` ≥ 64.

- [ ] **Step 7: Commit**
```
git -C "...\tinkers" add src/main/java/slimeknights/tconstruct/common/data/AdvancementsProvider.java src/test/java/slimeknights/tconstruct/common/data/AdvancementsPilotTest.java src/generated/resources/data/tconstruct/advancement/foundry/
git -C "...\tinkers" commit -m "feat(advancement): port the foundry blaze subtree (tank-fluid matching)"
```

---

### Task 2: Full-battery validation

**Files:** none (verification).

- [ ] **Step 1: Full build** — `... gradlew.bat -p "...\tinkers" build` (background). Expected: BUILD SUCCESSFUL; unit battery + all `AdvancementsPilotTest` cases green; no regression.

- [ ] **Step 2: Confirm the completed tree**

PowerShell: assert `advancement/foundry/*.json` now has **7** files (fantastic_foundry, encyclopedia, alloyer, structure, blaze, plate_armor, manyullyn_lanterns), the whole `advancement/{tools,smeltery,foundry,world,internal}` tree totals **42**, and `advancement`**s**`/` (plural) does NOT exist.

- [ ] **Step 3: (No commit)** — verification only; generated files were committed in Task 1.

---

## Self-Review

- **Spec coverage:** `blaze`+`manyullyn_lanterns` (tank-fluid exact match) + `plate_armor` (hasItem, parent blaze) → Task 1; the `tankFluidCriterion` helper (DataComponentPredicate on `TANK_FLUID`) → Task 1 Step 4; the `foundry` holder reassignment → Task 1 Step 5; battery + tree-complete + no-plural → Task 2. ✓
- **Placeholder scan:** no TBD/TODO; the vanilla-API notes are implementer verification guidance (standard 1.21 APIs unused elsewhere), not deferred work; predicates/blocks are transcribed from the reference. ✓
- **Type consistency:** `tankFluidCriterion(ItemLike, Fluid, int, MinMaxBounds.Ints)` defined in Step 4, called in Step 5 with `block` (`SearedTankBlock`/`SearedLanternBlock`, both `ItemLike`), `TinkerFluids.*.get()` (`Fluid`), `block.getCapacity()` (`int`), `MinMaxBounds.Ints.ANY`/`.atLeast(64)`. `foundry` holder defined (Step 5) before `blaze`/`manyullyn_lanterns` parent to it; `blazingBlood` holder defined before `plate_armor` parents to it. ✓
