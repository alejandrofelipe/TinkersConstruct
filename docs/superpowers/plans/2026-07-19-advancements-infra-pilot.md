# Advancements Infra + Tinkering-Path Pilot — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Re-enable the disabled Tinkers advancement datagen for the tinkering-path tree (`tools/`, ~13 advancements) by porting the scaffolding to the 1.21 API and building an `ItemSubPredicate` bridge so the existing tool predicates match in advancements.

**Architecture:** A registered `ItemSubPredicate` (`tconstruct:tool`) wraps the existing `IJsonPredicate<IToolStackView>` (serialized via the Mantle `Loadable.codec()` adapter). The `AdvancementsProvider` is rebuilt on the vanilla `AdvancementProvider`/`AdvancementSubProvider` seam, emitting at the singular `advancement/` path. Only the `tools/` tree is ported here; the rest is Sub-project 2.

**Tech Stack:** Java 21, NeoForge 21.1.234, NeoGradle (composite build with `../repo` Mantle), JUnit 5 + AssertJ, Mantle Loadable/predicate system.

**Spec:** `docs/superpowers/specs/2026-07-19-advancements-infra-pilot-design.md`

## Global Constraints

- **Build/test on Windows via PowerShell**, `JAVA_HOME` = `C:\Users\aleja\scoop\apps\temurin21-jdk\current` in the SAME command as gradlew. Daemon disabled → cold runs are slow, not failures; background long runs.
- Canonical build cmd: `$env:JAVA_HOME="C:\Users\aleja\scoop\apps\temurin21-jdk\current"; & "C:\Users\aleja\DEV\New Tinkers\tinkers\gradlew.bat" -p "C:\Users\aleja\DEV\New Tinkers\tinkers" <task>`
- Advancement JSONs go under the **singular** `data/tconstruct/advancement/` (1.20.5/1.21 dir rename), NOT `advancements/`.
- Conventional-commits messages in English. **No `Co-Authored-By` / "Generated with" trailer.**
- Surgical changes; follow existing patterns (`TinkerIngredients` for DeferredRegister, `ToolStackItemPredicate` for the tool-match rule).
- Scope: the `tools/` tinkering path only. Do NOT port `smeltery/`/`foundry/`/`world/`/`internal/`/`gameplay/` or touch `ConditionalAdvancement` (Sub-project 2).

---

### Task 1: `ToolItemSubPredicate` bridge + registration

**Files:**
- Create: `tinkers/src/main/java/slimeknights/tconstruct/library/json/predicate/tool/ToolItemSubPredicate.java`
- Create: `tinkers/src/main/java/slimeknights/tconstruct/library/json/predicate/tool/TinkerItemPredicates.java`
- Modify: `tinkers/src/main/java/slimeknights/tconstruct/TConstruct.java` (call `TinkerItemPredicates.init(bus)` next to `TinkerIngredients.init(bus)` ~line 141)
- Test: `tinkers/src/test/java/slimeknights/tconstruct/library/json/predicate/tool/ToolItemSubPredicateTest.java`

**Interfaces:**
- Produces: `ToolItemSubPredicate` implements `net.minecraft.advancements.critereon.ItemSubPredicate`; static `ofTool(IJsonPredicate<IToolStackView>)`, `ofContext(IJsonPredicate<IToolContext>)`; `Codec<ToolItemSubPredicate> CODEC`. `TinkerItemPredicates.TOOL` = `DeferredHolder<ItemSubPredicate.Type<?>, ItemSubPredicate.Type<ToolItemSubPredicate>>`; `TinkerItemPredicates.init(IEventBus)`.
- Consumes: `ToolStackPredicate.LOADER` (`.codec()` gives `Codec<IJsonPredicate<IToolStackView>>`), `ToolStackPredicate.context(...)`, `ToolStack.from`, `TinkerTags.Items.MODIFIABLE`.

- [ ] **Step 1: Write the failing test** — `ToolItemSubPredicateTest.java` (extends `BaseMcTest`):

```java
package slimeknights.tconstruct.library.json.predicate.tool;

import com.mojang.serialization.JsonOps;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.test.BaseMcTest;
import java.util.Objects;
import static org.assertj.core.api.Assertions.assertThat;

class ToolItemSubPredicateTest extends BaseMcTest {
  @Test
  void codec_roundTrips() {
    ToolItemSubPredicate original = ToolItemSubPredicate.ofTool(
      new StatInSetPredicate<>(ToolStats.HARVEST_TIER, net.minecraft.world.item.Tiers.NETHERITE));
    var encoded = ToolItemSubPredicate.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
    ToolItemSubPredicate decoded = ToolItemSubPredicate.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
    // re-encoding the decoded value must match the original's JSON (structural round-trip)
    var reEncoded = ToolItemSubPredicate.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow();
    assertThat(reEncoded).isEqualTo(encoded);
  }

  @Test
  void matches_nonTool_isFalse() {
    ToolItemSubPredicate predicate = ToolItemSubPredicate.ofTool(ToolStackPredicate.ANY);
    assertThat(predicate.matches(new ItemStack(net.minecraft.world.item.Items.STICK))).isFalse();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `... gradlew.bat -p "...\tinkers" test --tests "*ToolItemSubPredicateTest"`
Expected: FAIL — `ToolItemSubPredicate` does not exist (compile error).

- [ ] **Step 3: Create `ToolItemSubPredicate`**

```java
package slimeknights.tconstruct.library.json.predicate.tool;

import com.mojang.serialization.Codec;
import net.minecraft.advancements.critereon.ItemSubPredicate;
import net.minecraft.world.item.ItemStack;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/** Bridges a Tinkers tool predicate into the 1.21 advancement item-matching system. */
public record ToolItemSubPredicate(IJsonPredicate<IToolStackView> predicate) implements ItemSubPredicate {
  /** Codec built off the Mantle Loadable adapter (Loadable#codec) so no hand-written predicate codec is needed. */
  public static final Codec<ToolItemSubPredicate> CODEC =
    ToolStackPredicate.LOADER.codec().xmap(ToolItemSubPredicate::new, ToolItemSubPredicate::predicate);

  public static ToolItemSubPredicate ofTool(IJsonPredicate<IToolStackView> predicate) {
    return new ToolItemSubPredicate(predicate);
  }

  public static ToolItemSubPredicate ofContext(IJsonPredicate<IToolContext> predicate) {
    return new ToolItemSubPredicate(ToolStackPredicate.context(predicate));
  }

  @Override
  public boolean matches(ItemStack stack) {
    // tag check prevents reading NBT of non-tools, matching ToolStackItemPredicate
    return stack.is(TinkerTags.Items.MODIFIABLE) && predicate.matches(ToolStack.from(stack));
  }
}
```

Note (implementer): `ItemSubPredicate#matches` is `boolean matches(ItemStack)` in 1.21.1 — confirm the exact method name against the mapped source and adjust the `@Override` if the signature differs (some mappings use `matches(ItemStack)` only). `ToolStackPredicate.LOADER.codec()` returns `Codec<IJsonPredicate<IToolStackView>>` (see `Loadable.java:90`).

- [ ] **Step 4: Create `TinkerItemPredicates` (registration)**

```java
package slimeknights.tconstruct.library.json.predicate.tool;

import net.minecraft.advancements.critereon.ItemSubPredicate;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import slimeknights.tconstruct.TConstruct;

/** Registers Tinkers item sub-predicates (advancement item matching). Mirrors TinkerIngredients. */
public class TinkerItemPredicates {
  private TinkerItemPredicates() {}

  private static final DeferredRegister<ItemSubPredicate.Type<?>> ITEM_SUB_PREDICATES =
    DeferredRegister.create(Registries.ITEM_SUB_PREDICATE_TYPE, TConstruct.MOD_ID);

  /** Call from the mod constructor with the mod bus. */
  public static void init(IEventBus bus) {
    ITEM_SUB_PREDICATES.register(bus);
  }

  public static final DeferredHolder<ItemSubPredicate.Type<?>, ItemSubPredicate.Type<ToolItemSubPredicate>> TOOL =
    ITEM_SUB_PREDICATES.register("tool", () -> new ItemSubPredicate.Type<>(ToolItemSubPredicate.CODEC));
}
```

Note (implementer): confirm the registry key is `Registries.ITEM_SUB_PREDICATE_TYPE` and the `ItemSubPredicate.Type<>(Codec)` constructor shape in 1.21.1 (it is a record `Type(Codec<T> codec)`). If `register` on the mod bus needs the type registered before `RegisterCapabilitiesEvent`-style timing, no special handling is needed — DeferredRegister handles ordering.

- [ ] **Step 5: Wire `TinkerItemPredicates.init(bus)` in `TConstruct`**

Modify `TConstruct.java` right after `TinkerIngredients.init(bus);` (~line 141):
```java
    TinkerIngredients.init(bus);
    TinkerItemPredicates.init(bus);
```
Add import `import slimeknights.tconstruct.library.json.predicate.tool.TinkerItemPredicates;`.

- [ ] **Step 6: Run tests to verify they pass**

Run: `... gradlew.bat -p "...\tinkers" test --tests "*ToolItemSubPredicateTest"`
Expected: PASS (2 tests). If the codec/`matches` API differs, fix per the notes and re-run.

- [ ] **Step 7: Commit**

```
git -C "...\tinkers" add src/main/java/slimeknights/tconstruct/library/json/predicate/tool/ToolItemSubPredicate.java src/main/java/slimeknights/tconstruct/library/json/predicate/tool/TinkerItemPredicates.java src/main/java/slimeknights/tconstruct/TConstruct.java src/test/java/slimeknights/tconstruct/library/json/predicate/tool/ToolItemSubPredicateTest.java
git -C "...\tinkers" commit -m "feat(advancement): add tconstruct:tool ItemSubPredicate bridge"
```

---

### Task 2: `AdvancementsProvider` scaffolding on the 1.21 API (root advancement only)

**Files:**
- Rewrite: `tinkers/src/main/java/slimeknights/tconstruct/common/data/AdvancementsProvider.java`
- Reference (read-only, do not run): old impl at `git 7c0ff783ea~1:src/main/java/slimeknights/tconstruct/common/data/AdvancementsProvider.java`
- Reference: `TConstruct.java:231` already wires `new AdvancementsProvider(packOutput)` into `gatherData` — keep that call site working.

**Interfaces:**
- Consumes: `ToolItemSubPredicate.ofTool/ofContext` and `TinkerItemPredicates.TOOL` (Task 1).
- Produces: a working provider that emits `data/tconstruct/advancement/tools/materials_and_you.json` when `runData` runs; protected helpers `resource(String)`, `builder(display, id, parent, type, criteria→)`, `hasItem(ItemLike)`, `hasTag(TagKey<Item>)`, `toolCriterion(IJsonPredicate<IToolStackView>)` reused by Task 3.

- [ ] **Step 1: Write the failing test** — reuse/extend a pilot JSON test (created in full in Task 4). For this task, a minimal check: `tinkers/src/test/java/slimeknights/tconstruct/common/data/AdvancementsPilotTest.java`

```java
package slimeknights.tconstruct.common.data;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.test.JsonFileLoader;
import static org.assertj.core.api.Assertions.assertThat;

class AdvancementsPilotTest {
  private final JsonFileLoader loader = new JsonFileLoader(new Gson(), "advancement");

  @Test
  void root_materialsAndYou_generated() {
    JsonObject json = loader.loadJson("tconstruct", "tools/materials_and_you");
    assertThat(json.has("criteria")).isTrue();
    assertThat(json.getAsJsonObject("display").get("show_toast").getAsBoolean()).isTrue();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `... gradlew.bat -p "...\tinkers" test --tests "*AdvancementsPilotTest"`
Expected: FAIL — `Resource with path data/tconstruct/advancement/tools/materials_and_you.json doesn't exist` (provider emits nothing).

- [ ] **Step 3: Rewrite `AdvancementsProvider` on the vanilla `AdvancementProvider` seam**

Rebuild the provider using vanilla `net.minecraft.data.advancements.AdvancementProvider` + an `AdvancementSubProvider`. Port the old helpers (from the reference), replacing `FrameType`→`AdvancementType`, `Advancement.Builder#save(consumer, id)` now returns `AdvancementHolder`, and the consumer type is `Consumer<AdvancementHolder>`. The `display(...)` signature keeps `(ItemStack, title, desc, background, AdvancementType, showToast, announceChat, hidden)`. Implement `hasItem`/`hasTag` via vanilla `InventoryChangeTrigger.TriggerInstance.hasItems(...)`, and `toolCriterion(pred)` via:
```java
protected static Criterion<?> toolCriterion(IJsonPredicate<IToolStackView> predicate) {
  return InventoryChangeTrigger.TriggerInstance.hasItems(
    ItemPredicate.Builder.item().withSubPredicate(TinkerItemPredicates.TOOL.get(), ToolItemSubPredicate.ofTool(predicate)).build());
}
```
For this task, only emit the **root** `materials_and_you` (criterion `hasItem(TinkerCommons.materialsAndYou)`), to prove the scaffolding + path. The full tree comes in Task 3. Ensure the base path is the singular `advancement` (the vanilla `AdvancementProvider` already uses it; if keeping a custom `GenericDataProvider`, set `basePath = "advancement"`).

Note (implementer): the exact `AdvancementProvider` constructor in 1.21.1 is `new AdvancementProvider(output, registries, List.of(subProvider))`; `gatherData` at `TConstruct.java:231` must pass the `CompletableFuture<HolderLookup.Provider>` (`event.getLookupProvider()`) — update the `AdvancementsProvider` constructor to accept it (mirror how `TConstructLootTableProvider` on line 230 takes `lookupProvider`). Confirm `withSubPredicate(type, instance)` — in 1.21.1 it takes the `ItemSubPredicate.Type` and the instance.

- [ ] **Step 4: Run datagen, then the test**

Run (background): `... gradlew.bat -p "...\tinkers" runData`
Then: `... gradlew.bat -p "...\tinkers" test --tests "*AdvancementsPilotTest"`
Expected: `runData` BUILD SUCCESSFUL; test PASS. Review the git diff — `data/tconstruct/advancement/tools/materials_and_you.json` created (singular path).

- [ ] **Step 5: Commit**

```
git -C "...\tinkers" add src/main/java/slimeknights/tconstruct/common/data/AdvancementsProvider.java src/main/java/slimeknights/tconstruct/TConstruct.java src/test/java/slimeknights/tconstruct/common/data/AdvancementsPilotTest.java src/generated/resources/data/tconstruct/advancement/
git -C "...\tinkers" commit -m "feat(advancement): rebuild provider on 1.21 API, emit tinkering root"
```

---

### Task 3: Port the tinkering-path tree (`tools/`)

**Files:**
- Modify: `tinkers/src/main/java/slimeknights/tconstruct/common/data/AdvancementsProvider.java` (add the remaining `tools/` advancements)
- Reference: old impl `git 7c0ff783ea~1` — the `tools/` block (materials_and_you → … → material_master, travelers_gear, tool_smith, modified → upgrade_slots).

**Interfaces:**
- Consumes: the helpers from Task 2 (`builder`, `hasItem`, `hasTag`, `toolCriterion`).

- [ ] **Step 1: Extend the test with the tool-predicate leaves** — add to `AdvancementsPilotTest.java`:

```java
  @Test
  void netheriteTier_usesToolSubPredicate() {
    JsonObject json = loader.loadJson("tconstruct", "tools/netherite_tier");
    JsonObject criteria = json.getAsJsonObject("criteria");
    // the harvest_level criterion must carry an item predicate with a tconstruct:tool sub-predicate
    String asString = criteria.toString();
    assertThat(asString).contains("tconstruct:tool");
    assertThat(json.get("parent").getAsString()).isEqualTo("tconstruct:tools/tinker_tool");
  }

  @Test
  void treeParentsResolve() {
    for (String id : new String[]{"tools/part_builder","tools/make_part","tools/tinker_station",
        "tools/tinker_tool","tools/perfect_aim","tools/one_shot","tools/material_master",
        "tools/travelers_gear","tools/tool_smith","tools/modified","tools/upgrade_slots"}) {
      assertThat(loader.loadJson("tconstruct", id).has("parent")).as(id).isTrue();
    }
  }
```

- [ ] **Step 2: Run to verify failure**

Run: `... gradlew.bat -p "...\tinkers" test --tests "*AdvancementsPilotTest"`
Expected: FAIL — those advancement JSONs don't exist yet.

- [ ] **Step 3: Port the `tools/` advancements**

Copy the `tools/` block from the reference impl into `generate()`/the sub-provider, applying the mechanical transform: `FrameType.X`→`AdvancementType.X`; each `InventoryChangeTrigger.TriggerInstance.hasItems(ToolStackItemPredicate.ofTool(P))` → `toolCriterion(P)` (and `.ofContext(P)` → `toolCriterion(ToolStackPredicate.context(P))` via a `toolContextCriterion` helper). The exact predicates are in the reference (e.g. `netherite_tier` = `StatInSetPredicate(HARVEST_TIER, Tiers.NETHERITE)`, `perfect_aim` = `and(tag(BOWS), StatInRangePredicate.match(ACCURACY, 1))`, `one_shot` = `StatInRangePredicate.min(ATTACK_DAMAGE, 20)`, `material_master` = per-material `HasMaterialPredicate` criteria). Do NOT invent predicates — transcribe them from the reference. Keep every non-tool criterion (`hasItem`/`hasTag`) as-is on vanilla `ItemPredicate`.

- [ ] **Step 4: Run datagen, then tests**

Run (background): `... gradlew.bat -p "...\tinkers" runData`
Then: `... gradlew.bat -p "...\tinkers" test --tests "*AdvancementsPilotTest"`
Expected: `runData` SUCCESSFUL; all pilot tests PASS. Review diff — all `tools/*.json` present, singular path.

- [ ] **Step 5: Commit**

```
git -C "...\tinkers" add src/main/java/slimeknights/tconstruct/common/data/AdvancementsProvider.java src/test/java/slimeknights/tconstruct/common/data/AdvancementsPilotTest.java src/generated/resources/data/tconstruct/advancement/
git -C "...\tinkers" commit -m "feat(advancement): port the tinkering-path advancement tree (tools/)"
```

---

### Task 4: Full-battery validation

**Files:** none (verification task).

- [ ] **Step 1: Full build**

Run (background): `... gradlew.bat -p "...\tinkers" build`
Expected: BUILD SUCCESSFUL; unit battery green (654 + the new pilot tests); no other test regressed.

- [ ] **Step 2: Confirm generated tree + no plural leak**

Run: PowerShell listing `src/generated/resources/data/tconstruct/advancement/tools/*.json` (should list the tree) and assert `src/generated/resources/data/tconstruct/advancements/` does NOT exist (plural).

- [ ] **Step 3: (No commit)** — build is verification only; the generated files were committed in Task 3.

---

## Self-Review

- **Spec coverage:** Goal 1 (ItemSubPredicate bridge) → Task 1; Goal 2 (provider on 1.21 API, singular path) → Task 2; Goal 3 (tinkering pilot, tool-predicate leaves) → Task 3; Goal 4 (battery green + JSON test) → Tasks 2-4. Non-goals (other categories, ConditionalAdvancement) are explicitly excluded in every task's scope line. ✓
- **Placeholder scan:** predicates for the leaves are transcribed-from-reference (not invented); the "confirm API" notes are implementer guidance for 1.21.1 mapping nuances, not deferred work. No TBD/TODO. ✓
- **Type consistency:** `TinkerItemPredicates.TOOL` (Task 1) is used via `.get()` in `toolCriterion` (Task 2) and Task 3; `ToolItemSubPredicate.ofTool/ofContext` names consistent across tasks; `toolCriterion(IJsonPredicate<IToolStackView>)` defined in Task 2, used in Task 3. ✓
