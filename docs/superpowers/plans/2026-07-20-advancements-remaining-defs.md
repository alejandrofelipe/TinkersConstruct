# Advancements Sub-project 2, Plan A — Remaining Definitions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Emit the remaining 26 tinkers advancements (`smeltery/` 10, `foundry/` 4, `world/` 11, `internal/` 1) at the singular `data/tconstruct/advancement/` path by extending the pilot's `AdvancementsProvider`, replacing the removed `ConditionalAdvancement` with NeoForge's `neoforge:conditions`.

**Architecture:** Extend (do not rewrite) `AdvancementsProvider` from the pilot: add criterion helpers for the ported/vanilla triggers, a per-advancement requirements override, a `hiddenBuilder` (no display), and a `neoforge:conditions` injection driven by a `Map<ResourceLocation,List<ICondition>>`. Transcribe the four trees from the 1.20.1 reference with the same mechanical transform used for `tools/`.

**Tech Stack:** Java 21, NeoForge 21.1.234, NeoGradle (composite build with `../repo` Mantle), JUnit 5 + AssertJ, vanilla advancement API + Mantle predicate/Loadable system.

**Spec:** `docs/superpowers/specs/2026-07-20-advancements-remaining-defs-design.md`

**Reference (read-only, do not run):** old provider at `git 7c0ff783ea~1:src/main/java/slimeknights/tconstruct/common/data/AdvancementsProvider.java` — `generate()` lines 111-467 (tools already done), helpers 481-601.

## Global Constraints

- **Build/test on Windows via PowerShell**, `JAVA_HOME` = `C:\Users\aleja\scoop\apps\temurin21-jdk\current` in the SAME command as gradlew. Daemon disabled → cold runs are slow, not failures; background long runs. The JVM's "Sharing is only supported…" stderr warning makes PowerShell report exit 1 even on `BUILD SUCCESSFUL` — **check the `BUILD SUCCESSFUL`/`FAILED` line, not the exit code.**
- Canonical build cmd: `$env:JAVA_HOME="C:\Users\aleja\scoop\apps\temurin21-jdk\current"; & "C:\Users\aleja\DEV\New Tinkers\tinkers\gradlew.bat" -p "C:\Users\aleja\DEV\New Tinkers\tinkers" <task>`
- Advancement JSONs go under the **singular** `data/tconstruct/advancement/`.
- Serialize advancements with `provider.createSerializationContext(JsonOps.INSTANCE)` (NeoForge overrides it to recognise built-in mod-item holders); `RegistryOps.create(..)` fails `"not valid in current registry set"`.
- Conventional-commits in English. **No `Co-Authored-By` / "Generated with" trailer.**
- Surgical; **transcribe predicates/lists from the reference, never invent.** Scope = 26 advancements; the blaze subtree (`foundry/blaze`, `foundry/manyullyn_lanterns`, `foundry/plate_armor`) is Plan B.
- Predicate loaders register in `TinkerTools.registerRecipeSerializers(RegisterEvent)` which fires during `runData`, so tool sub-predicates serialize.

---

### Task 1: Config-gating mechanism + `internal/starting_book`

Front-loaded because `neoforge:conditions` injection + the tick/loot vanilla APIs are the only novel pieces — prove them on one advancement before porting 25.

**Files:**
- Modify: `src/main/java/slimeknights/tconstruct/common/data/AdvancementsProvider.java`
- Modify: `src/test/java/slimeknights/tconstruct/common/data/AdvancementsPilotTest.java`
- Generated: `src/generated/resources/data/tconstruct/advancement/internal/starting_book.json`

**Interfaces:**
- Produces (used by later tasks): `hiddenBuilder(ResourceLocation, ICondition, Consumer<Advancement.Builder>)`, `tickCriterion()`, the `conditions` map + `neoforge:conditions` injection in `run()`.

- [ ] **Step 1: Write the failing test** — add to `AdvancementsPilotTest`:

```java
  @Test
  void startingBook_isConfigGatedHidden() {
    JsonObject json = loader.loadJson("tconstruct", "internal/starting_book");
    // config-gated via neoforge:conditions (replaces ConditionalAdvancement)
    assertThat(json.has("neoforge:conditions")).isTrue();
    assertThat(json.getAsJsonArray("neoforge:conditions").toString()).contains("tconstruct:config");
    // hidden internal advancement: no display, has the tick criterion + a loot reward
    assertThat(json.has("display")).isFalse();
    assertThat(json.getAsJsonObject("criteria").has("tick")).isTrue();
    assertThat(json.getAsJsonObject("rewards").has("loot")).isTrue();
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `... gradlew.bat -p "...\tinkers" test --tests "*AdvancementsPilotTest"`
Expected: FAIL — `Resource … internal/starting_book.json doesn't exist`.

- [ ] **Step 3: Add the config-gating infra to `AdvancementsProvider`**

Add imports:
```java
import com.google.gson.JsonObject;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.critereon.PlayerTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.common.conditions.ICondition;
import slimeknights.tconstruct.common.json.ConfigEnabledCondition;
import java.util.HashMap;
import java.util.Map;
```

Add the conditions field next to `advancements`:
```java
  private final Map<ResourceLocation, List<ICondition>> conditions = new HashMap<>();
```

Replace `run()` to clear conditions and inject `neoforge:conditions`:
```java
  @Override
  public CompletableFuture<?> run(CachedOutput cache) {
    return this.registries.thenCompose(provider -> {
      this.advancements.clear();
      this.conditions.clear();
      generate();
      RegistryOps<JsonElement> ops = provider.createSerializationContext(JsonOps.INSTANCE);
      return allOf(this.advancements.stream().map(holder -> {
        JsonObject json = Advancement.CODEC.encodeStart(ops, holder.value()).getOrThrow().getAsJsonObject();
        List<ICondition> conds = this.conditions.get(holder.id());
        if (conds != null && !conds.isEmpty()) {
          json.add("neoforge:conditions", ICondition.LIST_CODEC.encodeStart(ops, conds).getOrThrow());
        }
        return saveJson(cache, holder.id(), json);
      }));
    });
  }
```
Note (implementer): confirm the NeoForge constant is `ICondition.LIST_CODEC` (`Codec<List<ICondition>>`) and the JSON key is `neoforge:conditions` (the generated recipe advancements already carry exactly `"neoforge:conditions":[{"type":"tconstruct:config",...}]`, so the shape is known). If `LIST_CODEC` differs, the recipe-advancement output is the ground-truth format to match.

Add the `hiddenBuilder` + `tickCriterion` helpers (near the other builders/criteria):
```java
  /** Hidden, config-gated advancement (replaces the removed ConditionalAdvancement): no display/parent. */
  protected AdvancementHolder hiddenBuilder(ResourceLocation name, ICondition condition, Consumer<Advancement.Builder> consumer) {
    Advancement.Builder builder = Advancement.Builder.advancement();
    builder.requirements(AdvancementRequirements.Strategy.AND);
    consumer.accept(builder);
    AdvancementHolder holder = builder.build(name);
    this.advancements.add(holder);
    this.conditions.put(name, List.of(condition));
    return holder;
  }

  /** Criterion firing every player tick (hidden reward advancements). */
  protected static Criterion<?> tickCriterion() {
    return CriteriaTriggers.TICK.createCriterion(new PlayerTrigger.TriggerInstance(Optional.empty()));
  }
```
Note (implementer): `CriteriaTriggers.TICK` is a `PlayerTrigger`; confirm `PlayerTrigger.TriggerInstance` in 1.21.1 takes `Optional<ContextAwarePredicate>` (so `new PlayerTrigger.TriggerInstance(Optional.empty())`). The reference used `new PlayerTrigger.TriggerInstance(CriteriaTriggers.TICK.getId(), ContextAwarePredicate.ANY)` (1.20.1 shape — do NOT copy verbatim).

- [ ] **Step 4: Emit `starting_book` in `generate()`**

Append to `generate()` (after the `tools/` block):
```java
    // internal advancements
    hiddenBuilder(resource("internal/starting_book"), ConfigEnabledCondition.SPAWN_WITH_BOOK, builder -> {
      builder.addCriterion("tick", tickCriterion());
      builder.rewards(AdvancementRewards.Builder.loot(ResourceKey.create(Registries.LOOT_TABLE, resource("gameplay/starting_book"))));
    });
```
Note (implementer): confirm `AdvancementRewards.Builder.loot(ResourceKey<LootTable>)` in 1.21.1 (the reward JSON key is `loot`). `ConfigEnabledCondition.SPAWN_WITH_BOOK` is a ported static `ICondition`.

- [ ] **Step 5: Run datagen, then the test**

Run (background): `... gradlew.bat -p "...\tinkers" runData`
Then: `... gradlew.bat -p "...\tinkers" test --tests "*AdvancementsPilotTest"`
Expected: `runData` BUILD SUCCESSFUL; test PASS. Review the diff — `advancement/internal/starting_book.json` has `neoforge:conditions`, no `display`, a `tick` criterion, and a `loot` reward.

- [ ] **Step 6: Commit**
```
git -C "...\tinkers" add src/main/java/slimeknights/tconstruct/common/data/AdvancementsProvider.java src/test/java/slimeknights/tconstruct/common/data/AdvancementsPilotTest.java src/generated/resources/data/tconstruct/advancement/internal/
git -C "...\tinkers" commit -m "feat(advancement): config-gating via neoforge:conditions + starting_book"
```

---

### Task 2: Smeltery tree (10) + requirements override + placed-block/container helpers

**Files:**
- Modify: `AdvancementsProvider.java`
- Modify: `AdvancementsPilotTest.java`
- Generated: `.../advancement/smeltery/*.json`

**Interfaces:**
- Consumes: pilot helpers + Task-1 infra.
- Produces (used by Tasks 3-4): the requirements-override behaviour, `placedBlockCriterion(Block)`, `containerCriterion(BlockEntityType<?>)`.

- [ ] **Step 1: Write the failing tests** — add to `AdvancementsPilotTest`:

```java
  @Test
  void smeltery_structureUsesContainerTrigger() {
    JsonObject json = loader.loadJson("tconstruct", "smeltery/structure");
    assertThat(json.get("parent").getAsString()).isEqualTo("tconstruct:smeltery/mighty_smelting");
    assertThat(json.getAsJsonObject("criteria").toString()).contains("tconstruct:block_container_opened");
  }

  @Test
  void smeltery_anvilUsesOrRequirements() {
    JsonObject json = loader.loadJson("tconstruct", "smeltery/tinkers_anvil");
    // OR strategy => the two criteria share one requirements group
    assertThat(json.getAsJsonArray("requirements").size()).isEqualTo(1);
    assertThat(json.getAsJsonArray("requirements").get(0).getAsJsonArray().size()).isEqualTo(2);
  }

  @Test
  void smeltery_melterUsesCountRequirements() {
    JsonObject json = loader.loadJson("tconstruct", "smeltery/melter");
    // CountRequirementsStrategy => several requirement groups (not a single AND flattening)
    assertThat(json.getAsJsonArray("requirements").size()).isGreaterThan(1);
  }
```
Note (implementer): confirm the container trigger's registered id — grep the generated `smeltery/structure.json` after datagen; the assert string (`tconstruct:block_container_opened`) must match `TinkerCommons.CONTAINER_OPENED_TRIGGER`'s id.

- [ ] **Step 2: Run to verify failure**

Run: `... test --tests "*AdvancementsPilotTest"` → FAIL (smeltery JSONs absent).

- [ ] **Step 3: Requirements override (small refactor)**

In the core `builder(ItemStack, name, parent, background, frame, consumer)`, MOVE the default requirements to **before** the consumer so per-advancement overrides win. Change:
```java
    builder.display(new DisplayInfo(display, …, false));
    consumer.accept(builder);
    builder.requirements(AdvancementRequirements.Strategy.AND);   // <-- DELETE this line
    AdvancementHolder holder = builder.build(name);
```
to:
```java
    builder.requirements(AdvancementRequirements.Strategy.AND);   // default; a consumer may override with OR / CountRequirementsStrategy
    builder.display(new DisplayInfo(display, …, false));
    consumer.accept(builder);
    AdvancementHolder holder = builder.build(name);
```
(The pilot's single-criterion advancements are unchanged — AND either way; the existing pilot tests still pass.)

- [ ] **Step 4: Add the smeltery helpers**

```java
  /** Criterion for placing a block (melter/alloyer structures). */
  protected static Criterion<?> placedBlockCriterion(Block block) {
    return ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(block);
  }

  /** Criterion for opening a Tinkers container (smeltery/foundry structure). */
  protected static Criterion<?> containerCriterion(BlockEntityType<?> type) {
    return BlockContainerOpenedTrigger.Instance.container(type);
  }
```
Imports: `net.minecraft.advancements.critereon.ItemUsedOnLocationTrigger`, `net.minecraft.world.level.block.Block`, `net.minecraft.world.level.block.entity.BlockEntityType`, `slimeknights.tconstruct.shared.inventory.BlockContainerOpenedTrigger`, `slimeknights.tconstruct.common.data.CountRequirementsStrategy`, and the smeltery/tables classes referenced below.
Note (implementer): `containerCriterion` is CONFIRMED (`BlockContainerOpenedTrigger.Instance.container(BlockEntityType<?>)` — read the class). Verify `ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(Block)` returns `Criterion<…>` in 1.21.1 (reference used it at 1.20.1 line 219).

- [ ] **Step 5: Transcribe the smeltery block into `generate()`**

Port reference lines **211-303** (the `// smeltery path` block through `abilities`), applying the transform:
`FrameType.X`→`AdvancementType.X`; `Advancement`→`AdvancementHolder`; `RequirementsStrategy.OR`→`AdvancementRequirements.Strategy.OR`; `ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(b)`→`placedBlockCriterion(b)`; `BlockContainerOpenedTrigger.Instance.container(x)`→`containerCriterion(x)`; `InventoryChangeTrigger.TriggerInstance.hasItems(ToolStackItemPredicate.ofContext(P))`→`toolContextCriterion(P)`; `hasItem`/`hasTag` unchanged. Keep `new CountRequirementsStrategy(...)` calls verbatim (class is ported). The 10: puny_smelting, melter, sand_casting, gold_casting, cast_collector, mighty_smelting, structure, tinkers_anvil, tool_forge, abilities. The `abilities` modifier list (reference lines ~277-303, ≈60 entries via `with.accept(...)` / `withL.accept(...)`) transcribes verbatim, each mapped to `toolContextCriterion(HasModifierPredicate.hasUpgrade(id, 1))`. The `cast_collector` cast list (reference ~205-252) is `hasItem(cast.get())` per cast.

Key concrete pieces (do NOT guess — from the reference):
```java
    // roots parent to the pilot's materialsAndYou holder; hold it in a field or re-fetch.
    AdvancementHolder punySmelting = builder(TinkerCommons.punySmelting, resource("smeltery/puny_smelting"), materialsAndYou, AdvancementType.TASK, b ->
      b.addCriterion("crafted_book", hasItem(TinkerCommons.punySmelting)));
    AdvancementHolder melter = builder(TinkerSmeltery.searedMelter, resource("smeltery/melter"), punySmelting, AdvancementType.TASK, b -> {
      Consumer<Block> with = block -> b.addCriterion(BuiltInRegistries.BLOCK.getKey(block).getPath(), placedBlockCriterion(block));
      with.accept(TinkerSmeltery.searedMelter.get());
      with.accept(TinkerSmeltery.searedTable.get());
      with.accept(TinkerSmeltery.searedBasin.get());
      with.accept(TinkerSmeltery.searedFaucet.get());
      with.accept(TinkerSmeltery.searedHeater.get());
      TinkerSmeltery.searedTank.forEach(with);
      b.requirements(new CountRequirementsStrategy(1, 1, 1, 1, 1 + SearedTankBlock.TankType.values().length));
    });
    AdvancementHolder anvil = builder(TinkerTables.tinkersAnvil, resource("smeltery/tinkers_anvil"), /*structure*/ smeltery, AdvancementType.GOAL, b -> {
      b.addCriterion("crafted_overworld", hasItem(TinkerTables.tinkersAnvil));
      b.addCriterion("crafted_nether", hasItem(TinkerTables.scorchedAnvil));
      b.requirements(AdvancementRequirements.Strategy.OR);
    });
    AdvancementHolder smeltery = builder(TinkerSmeltery.smelteryController, resource("smeltery/structure"), mightySmelting, AdvancementType.TASK, b ->
      b.addCriterion("open_smeltery", containerCriterion(TinkerSmeltery.smeltery.get())));
```
(Resolve the `materialsAndYou` reference: after Task 1 the `tools/` root is built first in `generate()`; capture its `AdvancementHolder` in a local as the pilot already does, and reuse it as the parent for the smeltery/foundry/world roots.)

- [ ] **Step 6: Run datagen, then tests**

Run (background): `... runData`; then `... test --tests "*AdvancementsPilotTest"`.
Expected: SUCCESSFUL; all pilot+smeltery tests PASS. Diff shows `advancement/smeltery/*.json` (10 files), singular path.

- [ ] **Step 7: Commit**
```
git -C "...\tinkers" add src/main/java/.../AdvancementsProvider.java src/test/java/.../AdvancementsPilotTest.java src/generated/resources/data/tconstruct/advancement/smeltery/
git -C "...\tinkers" commit -m "feat(advancement): port the smeltery advancement tree"
```

---

### Task 3: Foundry tree (4)

**Files:** `AdvancementsProvider.java`, `AdvancementsPilotTest.java`, `.../advancement/foundry/*.json`.

**Interfaces:** consumes pilot + Task-2 helpers (`placedBlockCriterion`, `containerCriterion`, CountRequirementsStrategy). Emits only `fantastic_foundry`, `encyclopedia`, `alloyer`, `structure` — **NOT** `blaze`/`plate_armor`/`manyullyn_lanterns` (Plan B; `plate_armor` parents to `blaze`).

- [ ] **Step 1: Write the failing test** — add:
```java
  @Test
  void foundry_structureAndAlloyer() {
    assertThat(loader.loadJson("tconstruct", "foundry/alloyer").getAsJsonArray("requirements").size()).isGreaterThan(1); // count strategy
    JsonObject structure = loader.loadJson("tconstruct", "foundry/structure");
    assertThat(structure.get("parent").getAsString()).isEqualTo("tconstruct:foundry/alloyer");
    assertThat(structure.getAsJsonObject("criteria").toString()).contains("tconstruct:block_container_opened");
  }
```

- [ ] **Step 2: Run to verify failure** — `... test --tests "*AdvancementsPilotTest"` → FAIL.

- [ ] **Step 3: Transcribe the foundry roots into `generate()`**

Port reference lines **306-323** (the `// foundry path` block) BUT STOP before `blazingBlood` — emit only the first four. Same transform as Task 2. Concrete:
```java
    AdvancementHolder fantasticFoundry = builder(TinkerCommons.fantasticFoundry, resource("foundry/fantastic_foundry"), materialsAndYou, AdvancementType.TASK, b ->
      b.addCriterion("crafted_book", hasItem(TinkerCommons.fantasticFoundry)));
    builder(TinkerCommons.encyclopedia, resource("foundry/encyclopedia"), fantasticFoundry, AdvancementType.GOAL, b ->
      b.addCriterion("crafted_book", hasItem(TinkerCommons.encyclopedia)));
    AdvancementHolder alloyer = builder(TinkerSmeltery.scorchedAlloyer, resource("foundry/alloyer"), fantasticFoundry, AdvancementType.TASK, b -> {
      Consumer<Block> with = block -> b.addCriterion(BuiltInRegistries.BLOCK.getKey(block).getPath(), placedBlockCriterion(block));
      with.accept(TinkerSmeltery.scorchedAlloyer.get());
      with.accept(TinkerSmeltery.scorchedFaucet.get());
      with.accept(TinkerSmeltery.scorchedTable.get());
      with.accept(TinkerSmeltery.scorchedBasin.get());
      for (SearedTankBlock.TankType type : SearedTankBlock.TankType.values()) {
        with.accept(TinkerSmeltery.scorchedTank.get(type));
      }
      b.requirements(new CountRequirementsStrategy(1, 1, 1, 1, 2, 2));
    });
    builder(TinkerSmeltery.foundryController, resource("foundry/structure"), alloyer, AdvancementType.TASK, b ->
      b.addCriterion("open_foundry", containerCriterion(TinkerSmeltery.foundry.get())));
```

- [ ] **Step 4: Run datagen, then tests** — `... runData`; `... test --tests "*AdvancementsPilotTest"`. Expected SUCCESSFUL, PASS; `advancement/foundry/*.json` (4 files).

- [ ] **Step 5: Commit**
```
git -C "...\tinkers" add src/main/java/.../AdvancementsProvider.java src/test/java/.../AdvancementsPilotTest.java src/generated/resources/data/tconstruct/advancement/foundry/
git -C "...\tinkers" commit -m "feat(advancement): port the foundry advancement roots (blaze subtree deferred)"
```

---

### Task 4: World/exploration tree (11) + located/item-used-on-entity helpers

**Files:** `AdvancementsProvider.java`, `AdvancementsPilotTest.java`, `.../advancement/world/*.json`.

**Interfaces:** consumes pilot helpers + `toolContextCriterion`. Adds `locatedCriterion`, `itemUsedOnEntityCriterion`.

- [ ] **Step 1: Write the failing tests** — add:
```java
  @Test
  void world_islandUsesLocatedTrigger() {
    JsonObject json = loader.loadJson("tconstruct", "world/sky_island");
    assertThat(json.get("parent").getAsString()).isEqualTo("tconstruct:world/tinkers_gadgetry");
    assertThat(json.getAsJsonObject("criteria").toString()).contains("minecraft:location");
  }

  @Test
  void world_slimeskullUsesToolSubPredicate() {
    JsonObject json = loader.loadJson("tconstruct", "world/slimeskull");
    assertThat(json.getAsJsonObject("criteria").toString()).contains("tconstruct:tool");
    assertThat(json.get("parent").getAsString()).isEqualTo("tconstruct:world/slimesuit");
  }
```
Note (implementer): confirm the located trigger's serialized id (grep the generated `world/sky_island.json`) — vanilla `CriteriaTriggers.LOCATION` serializes as `minecraft:location`; adjust the assert if the mapping differs.

- [ ] **Step 2: Run to verify failure** — `... test --tests "*AdvancementsPilotTest"` → FAIL.

- [ ] **Step 3: Add the world helpers**

```java
  /** Criterion firing when the player is inside the given structure (island advancements). */
  protected static Criterion<?> locatedCriterion(ResourceKey<Structure> structure) {
    return CriteriaTriggers.LOCATION.createCriterion(
      new PlayerTrigger.TriggerInstance(Optional.of(ContextAwarePredicate.create(
        LocationCheck.checkLocation(LocationPredicate.Builder.inStructure(structure)).build()))));
  }

  /** Criterion firing when an item is used on a specific entity type (piggybackpack). */
  protected static Criterion<?> itemUsedOnEntityCriterion(ItemLike item, EntityType<?> entity) {
    return CriteriaTriggers.PLAYER_INTERACTED_WITH_ENTITY.createCriterion(
      PlayerInteractTrigger.TriggerInstance.itemUsedOnEntity(
        ItemPredicate.Builder.item().of(item),
        EntityPredicate.wrap(EntityPredicate.Builder.entity().of(entity))));
  }
```
Note (implementer): these two are the plan's **highest-uncertainty** APIs (advancement-only triggers unused elsewhere in the ported tree). Verify against the mapped sources and adjust:
- `locatedCriterion`: the reference (line 434) was `PlayerTrigger.TriggerInstance.located(LocationPredicate.inStructure(TinkerStructures.earthSlimeIsland))`. Resolve `TinkerStructures.<island>`'s 1.21 type (likely `ResourceKey<Structure>` — grep `TinkerStructures.java`) and the exact `PlayerTrigger.TriggerInstance.located(...)` / `LocationPredicate.Builder.inStructure(Holder<Structure>)` form. If `located(...)` exists in 1.21.1, prefer it over the hand-built `LocationCheck` above.
- `itemUsedOnEntityCriterion`: reference (line 449) `PlayerInteractTrigger.TriggerInstance.itemUsedOnEntity(ContextAwarePredicate.ANY, ItemPredicate.Builder.item().of(item), EntityPredicate.wrap(...))`. Confirm the 1.21.1 arity (the pilot uses `ItemPredicate.Builder.item().of(item)` fine).
Imports as needed: `net.minecraft.advancements.critereon.{PlayerInteractTrigger, EntityPredicate, ContextAwarePredicate, LocationPredicate, LocationCheck}`, `net.minecraft.world.entity.EntityType`, `net.minecraft.world.level.levelgen.structure.Structure`.

- [ ] **Step 4: Transcribe the world block into `generate()`**

Port reference lines **425-467** (the `// exploration path` through `ancient_tools`), same transform, plus `PlayerTrigger.TriggerInstance.located(...)`→`locatedCriterion(structure)` and `PlayerInteractTrigger…itemUsedOnEntity(...)`→`itemUsedOnEntityCriterion(item, entity)`. The 11: tinkers_gadgetry, earth/sky/blood/ender/clay_island, slime_collector, piggybackpack, slimesuit, slimeskull, ancient_tools. `slimesuit`/`slimeskull` display stacks use `new MaterialIdNBT(List.of(...)).updateStack(new ItemStack(...))` (helper ported). `slimeskull` criteria transcribe the material list (reference ~455-475) each via `toolContextCriterion(ToolContextPredicate.and(ToolContextPredicate.set(helmet), new HasMaterialPredicate(mat, 0)))`.

- [ ] **Step 5: Run datagen, then tests** — `... runData`; `... test --tests "*AdvancementsPilotTest"`. Expected SUCCESSFUL, PASS; `advancement/world/*.json` (11 files).

- [ ] **Step 6: Commit**
```
git -C "...\tinkers" add src/main/java/.../AdvancementsProvider.java src/test/java/.../AdvancementsPilotTest.java src/generated/resources/data/tconstruct/advancement/world/
git -C "...\tinkers" commit -m "feat(advancement): port the world/exploration advancement tree"
```

---

### Task 5: Full-battery validation

**Files:** none (verification).

- [ ] **Step 1: Full build** — `... gradlew.bat -p "...\tinkers" build` (background). Expected: BUILD SUCCESSFUL; unit battery + all `AdvancementsPilotTest` cases green; no regression.

- [ ] **Step 2: Confirm the generated tree + no plural leak**

PowerShell: list `src/generated/resources/data/tconstruct/advancement/{smeltery,foundry,world,internal}/*.json` (10+4+11+1 = 26 files, plus the pilot's 13 `tools/`), and assert `src/generated/resources/data/tconstruct/advancements/` (plural) does NOT exist.

- [ ] **Step 3: (No commit)** — verification only; generated files were committed per task.

---

## Self-Review

- **Spec coverage:** smeltery(10)→Task 2; foundry(4)→Task 3; world(11)→Task 4; internal/config-gating(1)→Task 1; battery+no-plural→Task 5. The blaze subtree is explicitly excluded in Tasks 3 & Global Constraints (Plan B). ✓
- **Placeholder scan:** no TBD/TODO. The long verbatim lists (abilities ≈60 modifiers, cast_collector casts, slimeskull materials) are "transcribe from reference lines X-Y with this uniform transform" — DRY, not vague; the transform and an example are given. The uncertain vanilla triggers carry best-effort code + a concrete reference line + the 1.21 form to verify — implementer guidance, not deferred work. ✓
- **Type consistency:** `hiddenBuilder`/`tickCriterion` (Task 1) → used in Task 1; `placedBlockCriterion`/`containerCriterion` (Task 2) → used in Tasks 2-3; `locatedCriterion`/`itemUsedOnEntityCriterion` (Task 4) → used in Task 4; `materialsAndYou`/`mightySmelting`/`smeltery`/`alloyer` holders captured as locals and reused as parents. `containerCriterion(BlockEntityType<?>)` matches the confirmed `BlockContainerOpenedTrigger.Instance.container` signature. ✓
- **Ordering:** Task 1 front-loads the novel config-gating; Tasks 2-4 are independent category trees (all parent to the pilot's `materialsAndYou`); Task 5 validates. The requirements-override refactor (Task 2) is needed before foundry's count strategy (Task 3) — Task 2 precedes Task 3. ✓
