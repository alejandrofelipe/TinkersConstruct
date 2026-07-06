# Dev-Loop Automation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Terminal-only hot-reload for the running client, headless NeoForge GameTests for smeltery logic, and a self-driving GUI screenshot suite (`mantle.client.uitest` engine + Tinkers scenarios) whose PNGs Claude analyzes.

**Architecture:** Three additive components sharing infrastructure. Hot-reload = JBR 21 JVM + HotswapAgent in external mode on a new `clientHotswap` run. Logic tests = official 1.21.1 GameTest annotations, rigs built **in code** by a shared `SmelteryRigs` helper on empty datagen-generated templates. Screenshots = a property-gated client tick state machine in Mantle that quick-plays into a committed superflat world, runs registered scenarios (prepare rig → open GUI → settle → capture → close), writes stable-named PNGs + a results JSON, and exits.

**Tech Stack:** NeoForge 21.1.234 / MC 1.21.1, NeoGradle 7.1.38 (Gradle 9.2.1, daemon off), JDK 21 (Temurin for builds; JBR 21 only as the `clientHotswap` runtime), JetBrains Runtime + HotswapAgent, vanilla GameTest framework, vanilla `Screenshot`/`NativeImage`, Gson.

**Spec:** `docs/superpowers/specs/2026-07-06-dev-loop-automation-design.md`.
**Approved refinement over the spec:** rigs are built **programmatically** (shared `SmelteryRigs`, used by both gametests and uitest scenarios) on **datagen-generated empty templates**, instead of hand-authored `.nbt` rigs. Same observable outcome, but reproducible (no manual structure-block authoring) and immune to absolute-master-position breakage when templates relocate. Fluids/items are injected via BE APIs after placement.

**Repos:** Mantle = `C:\Users\aleja\DEV\New Tinkers\repo` (branch `1.21.1`), Tinkers = `C:\Users\aleja\DEV\New Tinkers\tinkers` (branch `1.21.1`), composite build (tinkers `includeBuild ../repo`). All gradle commands below run from the tinkers repo in **PowerShell** with the pinned JDK:

```powershell
$env:JAVA_HOME = "C:\Users\aleja\scoop\apps\temurin21-jdk\current"
& "C:\Users\aleja\DEV\New Tinkers\tinkers\gradlew.bat" -p "C:\Users\aleja\DEV\New Tinkers\tinkers" <task>
```

Conventions: conventional-commit messages in English ending with `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`. Long gradle runs go in background (daemon is off; slow first run is normal). After any run session ends, apply the CLAUDE.md process-cleanup rule.

---

## Phase A — Hot-reload

### Task 1: Install JBR 21 + HotswapAgent

**Files:** none (tools install under `C:\Users\aleja\tools\`).

- [ ] **Step 1: Create the tools dir and download JBR 21**

Open https://github.com/JetBrains/JetBrainsRuntime/releases and identify the newest **JBR 21** Windows x64 asset (name pattern `jbr_jcef-21.*-windows-x64-b*.tar.gz`; the non-jcef `jbr-21.*` variant also works and is smaller). Then:

```powershell
New-Item -ItemType Directory -Force C:\Users\aleja\tools | Out-Null
# substitute <URL> with the asset URL found above
Invoke-WebRequest -Uri "<URL>" -OutFile C:\Users\aleja\tools\jbr21.tar.gz
tar -xzf C:\Users\aleja\tools\jbr21.tar.gz -C C:\Users\aleja\tools
# the archive extracts to a versioned folder; normalize the name:
Get-ChildItem C:\Users\aleja\tools -Directory | Where-Object Name -like "jbr*" | Rename-Item -NewName "jbr21"
```

- [ ] **Step 2: Verify the JBR**

Run: `& C:\Users\aleja\tools\jbr21\bin\java.exe -version`
Expected: version string mentioning `21.` and `JBR`/`JetBrains`.

- [ ] **Step 3: Download HotswapAgent**

Open https://github.com/HotswapProjects/HotswapAgent/releases and take the newest stable `hotswap-agent-<version>.jar` asset:

```powershell
Invoke-WebRequest -Uri "<agent asset URL>" -OutFile C:\Users\aleja\tools\hotswap-agent.jar
```

- [ ] **Step 4: Verify the agent jar**

Run: `& C:\Users\aleja\tools\jbr21\bin\java.exe -jar C:\Users\aleja\tools\hotswap-agent.jar --help` (prints agent usage or version banner; any non-crash output is fine — the jar is an agent, not an app, so an "agent" usage note is expected).

### Task 2: `clientHotswap` run config

**Files:**
- Modify: `tinkers/build.gradle` (the `runs { ... }` block, currently around lines 73–90)

- [ ] **Step 1: Add the run** — inside the existing `runs { ... }` block, after `client { }`:

```gradle
    // long-lived client with live class reload: JBR 21 + HotswapAgent (external mode).
    // JVM is forced below via task executable override (toolchain launcher would pick Temurin).
    clientHotswap {
        configure 'client'
        jvmArgument '-XX:+AllowEnhancedClassRedefinition'
        jvmArgument '-javaagent:C:/Users/aleja/tools/hotswap-agent.jar=autoHotswap=true'
    }
```

Note: `configure 'client'` inherits the client run type in NeoGradle 7.1. If that keyword errors on sync, the fallback is `type = 'client'` (older DSL) — try `configure` first, adjust if the sync error names the property.

- [ ] **Step 2: Force the run task onto the JBR** — after the `runs { ... }` block (top level):

```gradle
// NeoGradle run tasks use the toolchain launcher (Temurin); hotswap needs the JBR's DCEVM.
// Overriding the JavaExec executable bypasses the toolchain for exactly this run.
tasks.matching { it.name == 'runClientHotswap' }.configureEach {
    it.executable = 'C:/Users/aleja/tools/jbr21/bin/java.exe'
}
```

- [ ] **Step 3: Sync check**

Run: `gradlew.bat -p tinkers tasks --group neogradle` (background; cold start is slow)
Expected: task list includes `runClientHotswap`, BUILD SUCCESSFUL.

- [ ] **Step 4: Boot check**

Run: `gradlew.bat -p tinkers runClientHotswap` (background, tee to a log).
Expected in the log, early: `Loading Hotswap agent {<version>} - unlimited runtime class redefinition` and the client reaches the main menu. Close the client afterward (or keep it open for Task 3).

- [ ] **Step 5: Commit (tinkers)**

```powershell
git -C "C:\Users\aleja\DEV\New Tinkers\tinkers" add build.gradle
git -C "C:\Users\aleja\DEV\New Tinkers\tinkers" commit -m "build: add clientHotswap run (JBR 21 + HotswapAgent external mode)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

### Task 3: Live-reload smoke test

**Files:** temporary edit in any hot code path — use `tinkers/src/main/java/slimeknights/tconstruct/library/materials/MaterialRegistry.java` (its `onDatapackSync` logs on every `/reload`).

- [ ] **Step 1:** With the `runClientHotswap` client OPEN and inside any world, find the log line emitted by `MaterialRegistry.onDatapackSync` (search the class for its `LOG` call) and append a marker to its message, e.g. `" [HOTSWAP-OK]"`.
- [ ] **Step 2:** Run: `gradlew.bat -p tinkers compileJava` (background). Expected: BUILD SUCCESSFUL; the client log prints a HotswapAgent line about reloading changed classes.
- [ ] **Step 3:** In the client, run `/reload`. Expected: the log now shows the message WITH ` [HOTSWAP-OK]` — class was redefined without restart.
- [ ] **Step 4:** Revert the marker edit, `compileJava` again (also proves the reverse reload), and `git -C ...tinkers status --short` must be clean.

### Task 4: Document hot-reload in COMMANDS.md

**Files:**
- Modify: `repo/docs/COMMANDS.md` (add a "Hot-reload (clientHotswap)" section next to the run-task docs)

- [ ] **Step 1:** Add a section covering: one-time install (Task 1 paths), `runClientHotswap` usage, the edit→`compileJava`→auto-reload loop, data/asset reload (`processResources` + `/reload` or F3+T), and the hard limit (registrations/static init/new listeners ⇒ normal restart).
- [ ] **Step 2: Commit (mantle repo)** — `docs: document clientHotswap hot-reload loop` with the standard trailer.

---

## Phase B — Logic GameTests

### Task 5: GameTest build wiring

**Files:**
- Modify: `tinkers/build.gradle` (`runs { ... }`)

- [ ] **Step 1: Add the run + namespace property** — inside `runs { ... }`:

```gradle
    gameTestServer {
        systemProperty 'neoforge.enabledGameTestNamespaces', 'tconstruct'
    }
```

and inside the existing `client { }` block (so `/test` works in dev clients):

```gradle
    client {
        systemProperty 'neoforge.enabledGameTestNamespaces', 'tconstruct'
    }
```

(The spec's `setForceExit false` daemon workaround is intentionally omitted: our Gradle daemon is disabled, so the forced exit is harmless.)

- [ ] **Step 2: Sync check** — `gradlew.bat -p tinkers tasks --group neogradle` lists `runGameTestServer`.
- [ ] **Step 3:** Run `gradlew.bat -p tinkers runGameTestServer` (background). Expected: boots, reports `0 required tests` (none exist yet), exits 0. This is the "failing-first" baseline proving the harness runs before any test exists.
- [ ] **Step 4: Commit (tinkers)** — `build: wire gameTestServer run + tconstruct gametest namespace`.

### Task 6: Empty structure template via datagen

**Files:**
- Create: `tinkers/src/main/java/slimeknights/tconstruct/gametest/GameTestStructureProvider.java`
- Modify: `tinkers/src/main/java/slimeknights/tconstruct/smeltery/TinkerSmeltery.java` (the `gatherData` method, ~line 537)

- [ ] **Step 1: Write the provider** — emits an air-filled template the gametests use as their arena:

```java
package slimeknights.tconstruct.gametest;

import net.minecraft.core.Vec3i;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.PackOutput.Target;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.SharedConstants;
import net.minecraft.util.datafix.DataFixTypes;
import slimeknights.tconstruct.TConstruct;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Emits empty NxNxN gametest arena templates so no hand-authored NBT is needed. */
public class GameTestStructureProvider implements DataProvider {
  private final PackOutput output;
  public GameTestStructureProvider(PackOutput output) {
    this.output = output;
  }

  @Override
  public CompletableFuture<?> run(CachedOutput cache) {
    return CompletableFuture.allOf(
      save(cache, "empty_5x5x5", new Vec3i(5, 5, 5)),
      save(cache, "empty_9x9x9", new Vec3i(9, 9, 9)));
  }

  private CompletableFuture<?> save(CachedOutput cache, String name, Vec3i size) {
    CompoundTag nbt = new CompoundTag();
    nbt.put("size", newIntList(size.getX(), size.getY(), size.getZ()));
    nbt.put("blocks", new ListTag());   // all air
    nbt.put("entities", new ListTag());
    ListTag palette = new ListTag();
    palette.add(NbtUtils.writeBlockState(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()));
    nbt.put("palette", palette);
    nbt.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
    Path path = output.getOutputFolder(Target.DATA_PACK)
      .resolve(TConstruct.MOD_ID).resolve("structure").resolve("gametest").resolve(name + ".nbt");
    return CompletableFuture.runAsync(() -> {
      try {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.writeCompressed(nbt, new DataOutputStream(bytes));
        cache.writeIfNeeded(path, bytes.toByteArray(),
          com.google.common.hash.Hashing.sha1().hashBytes(bytes.toByteArray()));
      } catch (Exception e) {
        throw new RuntimeException("Failed to save gametest template " + name, e);
      }
    });
  }

  private static ListTag newIntList(int x, int y, int z) {
    ListTag list = new ListTag();
    list.add(net.minecraft.nbt.IntTag.valueOf(x));
    list.add(net.minecraft.nbt.IntTag.valueOf(y));
    list.add(net.minecraft.nbt.IntTag.valueOf(z));
    return list;
  }

  @Override
  public String getName() {
    return "TConstruct gametest structures";
  }
}
```

Implementation note: verify at compile time the exact 1.21.1 signatures of `CachedOutput.writeIfNeeded` (path, bytes, hash) and `SharedConstants.getCurrentVersion().getDataVersion().getVersion()`; both exist in 1.21.1 but the hash parameter type is `com.google.common.hash.HashCode`. An empty `blocks` list with an air palette is a valid template (structure templates fill unlisted positions with structure void — air palette + empty blocks yields an air arena; if the placed arena turns out to be structure-void instead of air, switch to explicitly listing all N³ block entries pointing at palette index 0 — still generated code, no hand authoring).

- [ ] **Step 2: Register in datagen** — in `TinkerSmeltery.gatherData` (~line 537), alongside the existing providers:

```java
generator.addProvider(event.includeServer(), new slimeknights.tconstruct.gametest.GameTestStructureProvider(packOutput));
```

(match the local variable names already used in that method — it already has `generator`/`packOutput` equivalents.)

- [ ] **Step 3:** Run `gradlew.bat -p tinkers runData` (background). Expected: BUILD SUCCESSFUL and `tinkers/src/generated/resources/data/tconstruct/structure/gametest/empty_5x5x5.nbt` + `empty_9x9x9.nbt` exist.
- [ ] **Step 4: Commit (tinkers)** — `feat(gametest): generate empty arena structure templates via datagen` (include the two generated `.nbt` files).

### Task 7: `SmelteryRigs` — programmatic rig builder

**Files:**
- Create: `tinkers/src/main/java/slimeknights/tconstruct/gametest/SmelteryRigs.java`

- [ ] **Step 1: Write the rig builder.** Server-safe, no client imports — used by gametests AND (via integrated server) the uitest scenarios:

```java
package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.library.recipe.FluidValues;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.SearedTankBlock.TankType;
import slimeknights.tconstruct.smeltery.block.entity.component.TankBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.SmelteryBlockEntity;

/**
 * Builds known-good smeltery/casting rigs in a level, entirely in code.
 * Positions are relative to an origin corner; callers pass absolute origins.
 */
public final class SmelteryRigs {
  private SmelteryRigs() {}

  /** Smallest smeltery: 3x3 outer footprint (1x1 inner), 3 tall, controller on south wall, fuel tank in wall. */
  public static BlockPos buildSmeltery(ServerLevel level, BlockPos origin) {
    BlockState bricks = TinkerSmeltery.searedBricks.get().defaultBlockState();
    // floor 3x3 at y=0
    for (int x = 0; x < 3; x++)
      for (int z = 0; z < 3; z++)
        level.setBlockAndUpdate(origin.offset(x, 0, z), bricks);
    // wall ring at y=1 and y=2 (center 1,*,1 stays air)
    for (int y = 1; y <= 2; y++)
      for (int x = 0; x < 3; x++)
        for (int z = 0; z < 3; z++)
          if (x == 1 && z == 1) level.setBlockAndUpdate(origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
          else level.setBlockAndUpdate(origin.offset(x, y, z), bricks);
    // fuel tank replaces one wall block at y=1
    BlockPos tankPos = origin.offset(0, 1, 1);
    level.setBlockAndUpdate(tankPos, TinkerSmeltery.searedTank.get(TankType.FUEL_TANK).defaultBlockState());
    fillTank(level, tankPos, new FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 4000));
    // controller replaces the south-middle wall block at y=1, facing outward (south)
    BlockPos controller = origin.offset(1, 1, 2);
    level.setBlockAndUpdate(controller,
      TinkerSmeltery.smelteryController.get().defaultBlockState()
        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
    return controller;
  }

  /** Inserts an item into a formed smeltery's melting inventory (call after formation settles). */
  public static void insertMeltable(ServerLevel level, BlockPos controller, ItemStack stack) {
    if (level.getBlockEntity(controller) instanceof SmelteryBlockEntity smeltery) {
      smeltery.getMeltingInventory().insertItem(0, stack, false);
    }
  }

  /** Casting rig: seared tank (pre-filled molten iron) + faucet on its side + casting table below the faucet. */
  public static CastingRig buildCastingRig(ServerLevel level, BlockPos origin) {
    BlockPos tank = origin.above(1);
    level.setBlockAndUpdate(tank, TinkerSmeltery.searedTank.get(TankType.INGOT_TANK).defaultBlockState());
    fillTank(level, tank, new FluidStack(TinkerFluids.moltenIron.get(), FluidValues.INGOT * 4));
    BlockPos faucet = tank.relative(Direction.EAST);
    level.setBlockAndUpdate(faucet,
      TinkerSmeltery.searedFaucet.get().defaultBlockState()
        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
    BlockPos table = faucet.below();
    level.setBlockAndUpdate(table, TinkerSmeltery.searedTable.get().defaultBlockState());
    return new CastingRig(tank, faucet, table);
  }

  public record CastingRig(BlockPos tank, BlockPos faucet, BlockPos table) {}

  /** Fills any TankBlockEntity-style block at pos via its fluid handler. */
  public static void fillTank(ServerLevel level, BlockPos pos, FluidStack fluid) {
    if (level.getBlockEntity(pos) instanceof TankBlockEntity tank) {
      tank.getFluidHandler(null).fill(fluid, FluidAction.EXECUTE);
    }
  }
}
```

Implementation notes to verify while compiling (each is a rename-level fix, not a design change): exact registry field names in `TinkerSmeltery` (`searedBricks`, `searedTank` EnumObject + `TankType.FUEL_TANK`/`INGOT_TANK`, `smelteryController`, `searedFaucet`, `searedTable`); faucet's facing property name (some versions use their own FACING); `FluidValues.INGOT` constant. All exist in this codebase per earlier greps (`TinkerSmeltery.java` lines 244–331).

- [ ] **Step 2:** `gradlew.bat -p tinkers compileJava` → BUILD SUCCESSFUL.
- [ ] **Step 3: Commit (tinkers)** — `feat(gametest): programmatic smeltery/casting rig builder`.

### Task 8: The three gametests

**Files:**
- Create: `tinkers/src/main/java/slimeknights/tconstruct/gametest/SmelteryGameTests.java`

- [ ] **Step 1: Write the tests**

```java
package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.library.recipe.FluidValues;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.SmelteryBlockEntity;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;

@PrefixGameTestTemplate(false)
@GameTestHolder(TConstruct.MOD_ID)
public class SmelteryGameTests {

  /** Iron melts inside a formed, fueled smeltery. */
  @GameTest(template = "gametest/empty_9x9x9", timeoutTicks = 1200)
  public static void smeltery_melts(GameTestHelper helper) {
    BlockPos controller = SmelteryRigs.buildSmeltery(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)));
    // give the multiblock a few ticks to form, then feed it
    helper.runAfterDelay(60, () ->
      SmelteryRigs.insertMeltable(helper.getLevel(), controller, new ItemStack(Items.IRON_INGOT)));
    helper.succeedWhen(() -> {
      if (!(helper.getLevel().getBlockEntity(controller) instanceof SmelteryBlockEntity smeltery)) {
        helper.fail("no smeltery controller BE", helper.relativePos(controller));
        return;
      }
      FluidStack contained = smeltery.getTank().getFluidInTank(0);
      helper.assertTrue(contained.getFluid() == TinkerFluids.moltenIron.get()
                        && contained.getAmount() >= FluidValues.INGOT,
        "expected >= 1 ingot of molten iron, got " + contained.getAmount());
    });
  }

  /** A faucet pours molten iron from a tank into a casting table holding an ingot cast. */
  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 600)
  public static void smeltery_casts(GameTestHelper helper) {
    SmelteryRigs.CastingRig rig = SmelteryRigs.buildCastingRig(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 2)));
    helper.runAfterDelay(10, () -> {
      // put the ingot cast on the table, then open the tap
      if (helper.getLevel().getBlockEntity(rig.table()) instanceof CastingBlockEntity table) {
        table.setItem(CastingBlockEntity.INPUT, new ItemStack(TinkerSmeltery.ingotCast.get()));
      }
      helper.useBlock(helper.relativePos(rig.faucet()));
    });
    helper.succeedWhen(() -> {
      if (!(helper.getLevel().getBlockEntity(rig.table()) instanceof CastingBlockEntity table)) {
        helper.fail("no casting table BE", helper.relativePos(rig.table()));
        return;
      }
      helper.assertTrue(table.getItem(CastingBlockEntity.OUTPUT).is(Items.IRON_INGOT),
        "expected iron ingot in casting output");
    });
  }

  /** Copper + gold alloy into rose gold inside a fueled alloyer. */
  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 1200)
  public static void alloyer_alloys(GameTestHelper helper) {
    BlockPos base = helper.absolutePos(new BlockPos(2, 1, 2));
    // fuel tank below, alloyer on top
    helper.getLevel().setBlockAndUpdate(base,
      TinkerSmeltery.searedTank.get(slimeknights.tconstruct.smeltery.block.SearedTankBlock.TankType.FUEL_TANK).defaultBlockState());
    SmelteryRigs.fillTank(helper.getLevel(), base, new FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 4000));
    BlockPos alloyer = base.above();
    helper.getLevel().setBlockAndUpdate(alloyer, TinkerSmeltery.scorchedAlloyer.get().defaultBlockState());
    helper.runAfterDelay(10, () -> {
      var be = helper.getLevel().getBlockEntity(alloyer);
      if (be instanceof slimeknights.tconstruct.smeltery.block.entity.controller.AlloyerBlockEntity alloyerBe) {
        alloyerBe.getFluidHandler().fill(new FluidStack(TinkerFluids.moltenCopper.get(), FluidValues.INGOT * 3), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        alloyerBe.getFluidHandler().fill(new FluidStack(TinkerFluids.moltenGold.get(), FluidValues.INGOT), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
      }
    });
    helper.succeedWhen(() -> {
      var be = helper.getLevel().getBlockEntity(alloyer);
      helper.assertTrue(be instanceof slimeknights.tconstruct.smeltery.block.entity.controller.AlloyerBlockEntity alloyerBe
          && contains(alloyerBe, TinkerFluids.moltenRoseGold.get()),
        "expected molten rose gold in alloyer tank");
    });
  }

  private static boolean contains(slimeknights.tconstruct.smeltery.block.entity.controller.AlloyerBlockEntity be, net.minecraft.world.level.material.Fluid fluid) {
    var handler = be.getFluidHandler();
    for (int i = 0; i < handler.getTanks(); i++) {
      if (handler.getFluidInTank(i).getFluid() == fluid) return true;
    }
    return false;
  }
}
```

Implementation notes to verify while compiling: `CastingBlockEntity.INPUT`/`OUTPUT` slot constants (INPUT exists per `MoldingContainerWrapper(itemHandler, INPUT)`; confirm OUTPUT — if named differently, use the constant the class defines); `TinkerSmeltery.ingotCast` field name (`ingotCast` is an ItemObject); the rose-gold ratio against `src/generated/resources/data/tconstruct/recipe/alloys/molten_rose_gold.json` (adjust the two `fill` amounts to match the recipe ratio); melting/alloying only progress while fueled — the lava amounts above are generous. If a test times out from structure-formation latency, raise `runAfterDelay(60, ...)` before failing the approach.

- [ ] **Step 2:** `gradlew.bat -p tinkers compileJava` → BUILD SUCCESSFUL.
- [ ] **Step 3:** Run `gradlew.bat -p tinkers runGameTestServer` (background). Expected: `3 required tests`, all passed, exit code 0. Iterate on assertion/slot/ratio mismatches here — this step is the test run.
- [ ] **Step 4: Commit (tinkers)** — `feat(gametest): smeltery melt/cast/alloy logic tests`.

---

## Phase C — uitest engine (Mantle)

### Task 9: Scenario API (`UiTestScenario`, `UiTestContext`, `UiTestScenarios`)

**Files:**
- Create: `repo/src/main/java/slimeknights/mantle/client/uitest/UiTestScenario.java`
- Create: `repo/src/main/java/slimeknights/mantle/client/uitest/UiTestContext.java`
- Create: `repo/src/main/java/slimeknights/mantle/client/uitest/UiTestScenarios.java`

- [ ] **Step 1: `UiTestScenario`**

```java
package slimeknights.mantle.client.uitest;

import net.minecraft.resources.ResourceLocation;

/** One automated GUI capture: prepare a rig, open a screen, settle, screenshot, close. */
public interface UiTestScenario {
  /** Stable id; the last path segment names the PNG. */
  ResourceLocation id();

  /** Build the scene (server-side mutations go through {@link UiTestContext#runOnServer}). */
  void prepare(UiTestContext ctx);

  /** Ticks to wait after prepare before opening (multiblock formation, chunk/BE sync). */
  default int prepareSettleTicks() {
    return 60;
  }

  /** Open the target screen (or aim the camera for an in-world capture). */
  void open(UiTestContext ctx);

  /** Ticks to wait after open before capturing. */
  default int settleTicks() {
    return 20;
  }

  /** Restore state; default closes any open screen. */
  default void close(UiTestContext ctx) {
    ctx.mc().setScreen(null);
  }
}
```

- [ ] **Step 2: `UiTestContext`**

```java
package slimeknights.mantle.client.uitest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;

import java.util.Objects;

/** Helpers handed to scenarios; client-thread unless noted. */
public record UiTestContext(Minecraft mc) {
  public LocalPlayer player() {
    return Objects.requireNonNull(mc.player, "uitest: no player");
  }

  /** The integrated server's overworld (uitest always runs singleplayer via quick play). */
  public ServerLevel serverLevel() {
    MinecraftServer server = Objects.requireNonNull(mc.getSingleplayerServer(), "uitest: no integrated server");
    return server.overworld();
  }

  /** Schedules work on the server thread (rig building, BE mutation). */
  public void runOnServer(Runnable work) {
    Objects.requireNonNull(mc.getSingleplayerServer()).execute(work);
  }

  /** Sends a command as the player (cheats are on in the uitest world). */
  public void sendCommand(String command) {
    player().connection.sendCommand(command);
  }

  /** Client-side block interaction — opens the block's real menu with real server data. */
  public void useBlock(BlockPos pos) {
    Vec3 hit = Vec3.atCenterOf(pos);
    Objects.requireNonNull(mc.gameMode).useItemOn(player(), InteractionHand.MAIN_HAND,
      new BlockHitResult(hit, Direction.NORTH, pos, false));
  }

  /** Aims the player camera at a position (for in-world captures). */
  public void lookAt(BlockPos pos) {
    player().lookAt(Anchor.EYES, Vec3.atCenterOf(pos));
  }
}
```

- [ ] **Step 3: `UiTestScenarios`**

```java
package slimeknights.mantle.client.uitest;

import java.util.ArrayList;
import java.util.List;

/** Static scenario registry; consumers register during client setup when {@link #isActive()}. */
public final class UiTestScenarios {
  private static final List<UiTestScenario> SCENARIOS = new ArrayList<>();
  private UiTestScenarios() {}

  /** True only when the suite was requested on the command line. */
  public static boolean isActive() {
    return Boolean.getBoolean("mantle.uitest");
  }

  public static void register(UiTestScenario scenario) {
    SCENARIOS.add(scenario);
  }

  static List<UiTestScenario> all() {
    return List.copyOf(SCENARIOS);
  }
}
```

- [ ] **Step 4:** `gradlew.bat -p tinkers compileJava` (composite build compiles Mantle) → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit (mantle)** — `feat(uitest): scenario API for the automated GUI screenshot suite`.

### Task 10: `UiTestSuite` state machine + wiring

**Files:**
- Create: `repo/src/main/java/slimeknights/mantle/client/uitest/UiTestSuite.java`
- Modify: `repo/src/main/java/slimeknights/mantle/client/ClientEvents.java` (the `clientSetup` method, ~line 108)

- [ ] **Step 1: `UiTestSuite`**

```java
package slimeknights.mantle.client.uitest;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.commons.io.FileUtils;
import slimeknights.mantle.Mantle;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-tick state machine driving registered {@link UiTestScenario}s:
 * waits for the quick-play world, then per scenario prepare -> open -> capture -> close,
 * finally writes uitest-results.json and stops the client. Active only with -Dmantle.uitest=true.
 */
public class UiTestSuite {
  private enum Phase { WAIT_WORLD, PREPARE, PREPARE_SETTLE, OPEN, SETTLE, CAPTURE, CLOSE, FINISH }

  /** Ticks with a player present before the suite starts (world/chunk warmup). */
  private static final int WORLD_WARMUP_TICKS = 100;
  /** Per-scenario hard ceiling; overruns record a fail and move on. */
  private static final int SCENARIO_TIMEOUT_TICKS = 20 * 60;
  /** Whole-suite ceiling; overruns finish (and report) whatever ran. */
  private static final int SUITE_TIMEOUT_TICKS = 20 * 60 * 10;

  public static void init() {
    NeoForge.EVENT_BUS.addListener(new UiTestSuite()::onClientTick);
    Mantle.logger.info("uitest: suite armed, {} scenario(s) will run once the world loads",
      UiTestScenarios.all().size());
  }

  private final Map<String, String> results = new LinkedHashMap<>();
  private Phase phase = Phase.WAIT_WORLD;
  private int phaseTicks = 0;
  private int scenarioTicks = 0;
  private int suiteTicks = 0;
  private int index = 0;
  private List<UiTestScenario> scenarios;
  private File outputDir;

  private void onClientTick(ClientTickEvent.Post event) {
    Minecraft mc = Minecraft.getInstance();
    suiteTicks++;
    phaseTicks++;
    scenarioTicks++;
    if (suiteTicks > SUITE_TIMEOUT_TICKS && phase != Phase.FINISH) {
      results.put("suite", "fail: suite timeout");
      enter(Phase.FINISH);
    }

    switch (phase) {
      case WAIT_WORLD -> {
        if (mc.level != null && mc.player != null && mc.screen == null) {
          if (phaseTicks >= WORLD_WARMUP_TICKS) {
            scenarios = UiTestScenarios.all();
            outputDir = new File(mc.gameDirectory, "uitest-screenshots");
            try {
              FileUtils.deleteDirectory(outputDir);
            } catch (Exception ignored) {}
            outputDir.mkdirs();
            startScenario(mc);
          }
        } else {
          phaseTicks = 0; // only count ticks while actually in-world at the title-free screen
        }
      }
      case PREPARE_SETTLE -> {
        if (scenarioTimedOut()) return;
        if (phaseTicks >= current().prepareSettleTicks()) {
          runGuarded(mc, "open", () -> current().open(new UiTestContext(mc)));
          enter(Phase.SETTLE);
        }
      }
      case SETTLE -> {
        if (scenarioTimedOut()) return;
        if (phaseTicks >= current().settleTicks()) {
          capture(mc);
        }
      }
      case FINISH -> finish(mc);
      default -> {}
    }
  }

  private UiTestScenario current() {
    return scenarios.get(index);
  }

  private void startScenario(Minecraft mc) {
    if (scenarios.isEmpty() || index >= scenarios.size()) {
      enter(Phase.FINISH);
      return;
    }
    scenarioTicks = 0;
    Mantle.logger.info("uitest: [{}/{}] {}", index + 1, scenarios.size(), current().id());
    runGuarded(mc, "prepare", () -> current().prepare(new UiTestContext(mc)));
    enter(Phase.PREPARE_SETTLE);
  }

  private void capture(Minecraft mc) {
    runGuarded(mc, "capture", () -> {
      NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
      try (image) {
        image.writeToFile(new File(outputDir, current().id().getPath() + ".png"));
      }
      results.putIfAbsent(current().id().toString(), "ok");
    });
    runGuarded(mc, "close", () -> current().close(new UiTestContext(mc)));
    index++;
    startScenario(mc);
  }

  /** Runs a scenario phase, converting any throwable into a fail result + skip to next scenario. */
  private void runGuarded(Minecraft mc, String stage, Runnable work) {
    try {
      work.run();
    } catch (Throwable t) {
      Mantle.logger.error("uitest: {} failed during {}", current().id(), stage, t);
      results.put(current().id().toString(), "fail: " + stage + ": " + t);
      index++;
      startScenario(mc);
    }
  }

  private boolean scenarioTimedOut() {
    if (scenarioTicks > SCENARIO_TIMEOUT_TICKS) {
      results.put(current().id().toString(), "fail: timeout");
      index++;
      startScenario(Minecraft.getInstance());
      return true;
    }
    return false;
  }

  private void enter(Phase next) {
    phase = next;
    phaseTicks = 0;
  }

  private void finish(Minecraft mc) {
    try {
      File resultFile = new File(mc.gameDirectory, "uitest-results.json");
      Files.writeString(resultFile.toPath(),
        new GsonBuilder().setPrettyPrinting().create().toJson(results), StandardCharsets.UTF_8);
      Mantle.logger.info("uitest: done — {} result(s) written to {}", results.size(), resultFile);
    } catch (Exception e) {
      Mantle.logger.error("uitest: failed to write results", e);
    }
    phase = Phase.WAIT_WORLD; // prevent re-entry while stopping
    mc.stop();
  }
}
```

Implementation notes: `Screenshot.takeScreenshot(RenderTarget)` returns a `NativeImage` in 1.21.1 (the F2 path uses it) — capture runs on the render thread because client ticks do. `Mantle.logger` — use the logger field Mantle actually exposes (check `Mantle.java`; if it's `Mantle.LOG` or a private logger, add/use accordingly). `FileUtils` is commons-io, already on the MC classpath; if unavailable at compile, replace with a small recursive-delete walk using `java.nio.file.Files`.

- [ ] **Step 2: Wire in `ClientEvents.clientSetup`** (Mantle, ~line 108) — append inside the method:

```java
    // automated GUI screenshot suite — inert unless -Dmantle.uitest=true
    if (slimeknights.mantle.client.uitest.UiTestScenarios.isActive()) {
      event.enqueueWork(slimeknights.mantle.client.uitest.UiTestSuite::init);
    }
```

- [ ] **Step 3:** `gradlew.bat -p tinkers compileJava` → BUILD SUCCESSFUL.
- [ ] **Step 4:** Inertness check: `gradlew.bat -p tinkers runClient` (background), reach the main menu, grep the log for `uitest` — expected **no** uitest lines (property unset). Close the client (cleanup rule).
- [ ] **Step 5: Commit (mantle)** — `feat(uitest): client tick suite runner with screenshot capture and results JSON`.

---

## Phase D — Tinkers scenarios + run config

### Task 11: Tinkers scenarios

**Files:**
- Create: `tinkers/src/main/java/slimeknights/tconstruct/client/uitest/TinkerUiTestScenarios.java`

- [ ] **Step 1: Write the scenarios + registrar** (single file; scenarios are small anonymous-style records):

```java
package slimeknights.tconstruct.client.uitest;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import slimeknights.mantle.client.uitest.UiTestContext;
import slimeknights.mantle.client.uitest.UiTestScenario;
import slimeknights.mantle.client.uitest.UiTestScenarios;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.gametest.SmelteryRigs;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.tables.TinkerTables;

/** Registers TConstruct's automated GUI screenshot scenarios (active only with -Dmantle.uitest=true). */
@EventBusSubscriber(modid = TConstruct.MOD_ID, value = Dist.CLIENT, bus = Bus.MOD)
public class TinkerUiTestScenarios {
  /** Fixed build site in the committed superflat world, far from spawn interference. */
  private static final BlockPos SITE = new BlockPos(100, -60, 100);

  @SubscribeEvent
  static void clientSetup(FMLClientSetupEvent event) {
    if (!UiTestScenarios.isActive()) {
      return;
    }
    UiTestScenarios.register(new BlockGuiScenario("tinker_station", SITE.offset(0, 0, 0), () -> TinkerTables.tinkerStation.get().defaultBlockState()));
    UiTestScenarios.register(new BlockGuiScenario("part_builder", SITE.offset(4, 0, 0), () -> TinkerTables.partBuilder.get().defaultBlockState()));
    UiTestScenarios.register(new SmelteryScenario());
    UiTestScenarios.register(new MelterScenario());
    UiTestScenarios.register(new CastingPourScenario());
  }

  /** Places a single block and opens its GUI. */
  private record BlockGuiScenario(String name, BlockPos pos,
                                  java.util.function.Supplier<net.minecraft.world.level.block.state.BlockState> state) implements UiTestScenario {
    @Override
    public ResourceLocation id() {
      return TConstruct.getResource(name);
    }

    @Override
    public void prepare(UiTestContext ctx) {
      ctx.sendCommand("tp @s " + (pos.getX() - 2) + " " + pos.getY() + " " + pos.getZ());
      ctx.runOnServer(() -> ctx.serverLevel().setBlockAndUpdate(pos, state.get()));
    }

    @Override
    public int prepareSettleTicks() {
      return 20;
    }

    @Override
    public void open(UiTestContext ctx) {
      ctx.useBlock(pos);
    }
  }

  private static class SmelteryScenario implements UiTestScenario {
    private BlockPos controller;

    @Override
    public ResourceLocation id() {
      return TConstruct.getResource("smeltery");
    }

    @Override
    public void prepare(UiTestContext ctx) {
      BlockPos origin = SITE.offset(10, 0, 0);
      ctx.sendCommand("tp @s " + (origin.getX() + 1) + " " + origin.getY() + " " + (origin.getZ() + 5));
      ctx.runOnServer(() -> {
        controller = SmelteryRigs.buildSmeltery(ctx.serverLevel(), origin);
        // give the GUI something to show once formed: an ingot melting + molten iron in the tank
        ctx.serverLevel().getServer().execute(() ->
          SmelteryRigs.insertMeltable(ctx.serverLevel(), controller,
            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT)));
      });
    }

    @Override
    public int prepareSettleTicks() {
      return 100; // multiblock formation + first melt ticks
    }

    @Override
    public void open(UiTestContext ctx) {
      ctx.useBlock(controller);
    }
  }

  private static class MelterScenario implements UiTestScenario {
    private final BlockPos pos = SITE.offset(20, 1, 0);

    @Override
    public ResourceLocation id() {
      return TConstruct.getResource("melter");
    }

    @Override
    public void prepare(UiTestContext ctx) {
      ctx.sendCommand("tp @s " + (pos.getX() - 2) + " " + (pos.getY() - 1) + " " + pos.getZ());
      ctx.runOnServer(() -> {
        var level = ctx.serverLevel();
        BlockPos fuel = pos.below();
        level.setBlockAndUpdate(fuel, TinkerSmeltery.searedTank.get(slimeknights.tconstruct.smeltery.block.SearedTankBlock.TankType.FUEL_TANK).defaultBlockState());
        SmelteryRigs.fillTank(level, fuel, new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 4000));
        level.setBlockAndUpdate(pos, TinkerSmeltery.searedMelter.get().defaultBlockState());
      });
    }

    @Override
    public void open(UiTestContext ctx) {
      ctx.useBlock(pos);
    }
  }

  /** No menu: captures the in-world pour (validates FluidRenderer's world path). */
  private static class CastingPourScenario implements UiTestScenario {
    private SmelteryRigs.CastingRig rig;

    @Override
    public ResourceLocation id() {
      return TConstruct.getResource("casting_pour");
    }

    @Override
    public void prepare(UiTestContext ctx) {
      BlockPos origin = SITE.offset(30, 0, 0);
      ctx.sendCommand("tp @s " + (origin.getX() - 3) + " " + origin.getY() + " " + origin.getZ());
      ctx.runOnServer(() -> rig = SmelteryRigs.buildCastingRig(ctx.serverLevel(), origin));
    }

    @Override
    public void open(UiTestContext ctx) {
      ctx.runOnServer(() -> {
        if (ctx.serverLevel().getBlockEntity(rig.table()) instanceof slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity table) {
          table.setItem(slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity.INPUT,
            new net.minecraft.world.item.ItemStack(TinkerSmeltery.ingotCast.get()));
        }
      });
      ctx.useBlock(rig.faucet()); // opens the tap — pour starts
      ctx.lookAt(rig.faucet());
    }

    @Override
    public int settleTicks() {
      return 30; // capture mid-pour
    }

    @Override
    public void close(UiTestContext ctx) { /* nothing open */ }
  }
}
```

Implementation notes: `TinkerTables.tinkerStation`/`partBuilder` are the block fields (confirm names in `TinkerTables.java`; the BE-type fields end in `Tile`, the block fields don't). `useBlock` on the faucet fires the interaction from the client like a real click. If `useItemOn` needs the crosshair actually aimed for the server-side reach check on 1.21.1, add `ctx.lookAt(pos)` in `prepare` before `open` — the plan's scenarios already position the player within 4 blocks of every target.

- [ ] **Step 2:** `gradlew.bat -p tinkers compileJava` → BUILD SUCCESSFUL.
- [ ] **Step 3: Commit (tinkers)** — `feat(uitest): TConstruct GUI screenshot scenarios`.

### Task 12: Test world + `clientUiTest` run

**Files:**
- Create: `tinkers/src/uitest/uitest-world.zip` (committed binary)
- Modify: `tinkers/build.gradle` (runs block + a `prepareUiTestWorld` task)

- [ ] **Step 1: Create the world once (manual-assisted).** Launch `gradlew.bat -p tinkers runClient`, create a singleplayer world named exactly `UITest`: **Superflat**, Creative, cheats ON, difficulty Peaceful, then run `/gamerule doDaylightCycle false`, `/gamerule doWeatherCycle false`, `/time set noon`, and quit the world + client.
- [ ] **Step 2: Zip it into the repo:**

```powershell
New-Item -ItemType Directory -Force "C:\Users\aleja\DEV\New Tinkers\tinkers\src\uitest" | Out-Null
Compress-Archive -Force -Path "C:\Users\aleja\DEV\New Tinkers\tinkers\run\client\saves\UITest" -DestinationPath "C:\Users\aleja\DEV\New Tinkers\tinkers\src\uitest\uitest-world.zip"
```

- [ ] **Step 3: Gradle task + run config** — in `tinkers/build.gradle`, top level:

```gradle
// extracts the committed uitest world into the clientUiTest run dir when absent
tasks.register('prepareUiTestWorld', Copy) {
    from zipTree(file('src/uitest/uitest-world.zip'))
    into file('run/clientUiTest/saves')
    onlyIf { !file('run/clientUiTest/saves/UITest/level.dat').exists() }
}
tasks.matching { it.name == 'runClientUiTest' }.configureEach { dependsOn 'prepareUiTestWorld' }
```

and inside `runs { ... }`:

```gradle
    clientUiTest {
        configure 'client'
        systemProperty 'mantle.uitest', 'true'
        arguments.addAll '--quickPlaySingleplayer', 'UITest', '--width', '1280', '--height', '720'
    }
```

- [ ] **Step 4: Sync check** — `gradlew.bat -p tinkers tasks --group neogradle` lists `runClientUiTest`; `gradlew.bat -p tinkers prepareUiTestWorld` extracts `run/clientUiTest/saves/UITest/level.dat`.
- [ ] **Step 5: Commit (tinkers)** — `build: clientUiTest run + committed UITest world` (include the zip).

### Task 13: Full suite run + analysis

**Files:** none (verification task).

- [ ] **Step 1:** Run `gradlew.bat -p tinkers runClientUiTest` (background, tee to log). Expected without ANY interaction: client boots → quick-plays into `UITest` → log shows `uitest: [1/5] tconstruct:tinker_station` … `[5/5] tconstruct:casting_pour` → `uitest: done — 5 result(s)` → client exits on its own, BUILD SUCCESSFUL.
- [ ] **Step 2:** Read `tinkers/run/clientUiTest/uitest-results.json` — all five entries `"ok"`.
- [ ] **Step 3:** Claude reads the five PNGs in `tinkers/run/clientUiTest/uitest-screenshots/` with the Read tool and reports a visual analysis (GUIs rendered, fluids where expected, no artifacts). Iterate scenario timings/positions here if a capture is black/menu/mid-load.
- [ ] **Step 4:** Apply the CLAUDE.md process-cleanup rule (no monitors, no orphan java).

### Task 14: Documentation

**Files:**
- Modify: `repo/docs/COMMANDS.md` (new "Automated tests & screenshots" section)
- Modify: `C:\Users\aleja\DEV\New Tinkers\CLAUDE.md` (Workflow pointer)

- [ ] **Step 1:** COMMANDS.md: document `runGameTestServer` (headless, exit code = failures), `runClientUiTest` (self-driving; outputs `uitest-screenshots/*.png` + `uitest-results.json`; world regeneration via deleting `run/clientUiTest/saves/UITest` + `prepareUiTestWorld`), and scenario-adding (one class + `UiTestScenarios.register`).
- [ ] **Step 2:** CLAUDE.md: in the Workflow section add one line — after runtime-affecting changes, prefer `runGameTestServer` (logic) and `runClientUiTest` (visual) before manual smoke tests.
- [ ] **Step 3: Commits** — mantle: `docs: document gametest + uitest runs`; workspace CLAUDE.md has no git (workspace root is not a repo — just save the file).

---

## Self-review (done at write time)

- **Spec coverage:** hot-reload install/run/loop/limits → Tasks 1–4; gametests (3 tests, namespace, headless run) → Tasks 5–8; engine (activation, scenario API, state machine, results JSON, self-exit) → Tasks 9–10; scenarios (5, incl. casting in-world) → Task 11; world+quickPlay+window size → Task 12; DoD checks → Tasks 3/8/13; docs → Tasks 4/14. Spec's "authored .nbt rigs" consciously replaced by the approved rigs-as-code refinement (header note).
- **Placeholders:** none — every code step has full code; "verify at compile" notes are rename-level checks with the fallback stated inline.
- **Type consistency:** `UiTestScenario`/`UiTestContext`/`UiTestScenarios.isActive|register|all` used identically in Tasks 9/10/11; `SmelteryRigs.buildSmeltery|insertMeltable|buildCastingRig|fillTank|CastingRig` identical in Tasks 7/8/11.
