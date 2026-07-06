# Dev-Loop Automation — hot-reload, GameTests, and automated GUI screenshots

**Date:** 2026-07-06
**Status:** approved design, pending implementation plan
**Scope:** two repos — the engine lands in Mantle (`../repo`), scenarios/tests/build wiring land in TinkersConstruct.

## Context & goals

The TinkersConstruct 1.21.1/NeoForge port is in M5 (runtime + smoke test). The current dev loop is
slow and manual: every code fix requires closing the client and a ~5 min `runClient` relaunch
(Gradle daemon disabled), and every visual regression (GUI fluids, slot layout, textures) needs a
human in front of the game. The workflow is terminal-only — there is no IDE in the workspace, and
Claude drives Gradle in background while the user tests.

Three goals, in one design because they share infrastructure:

1. **Hot-reload** — apply Java code changes to a running client without restart (terminal-only,
   no IDE/debugger).
2. **Automated logic tests** — smeltery melt→cast and friends, headless and repeatable
   (`runGameTestServer`, CI-ready exit code).
3. **Automated GUI screenshots** — a client run that opens the mod's screens by itself, captures
   stable-named PNGs Claude can read and visually analyze, and exits — no human interaction.

Research (see References) found: hot-reload without an IDE is a documented HotswapAgent flow
(JBR + `autoHotswap`); NeoForge ships an official GameTest framework on 1.21.1; but **no
client-gametest/screenshot framework exists for 1.21.1** (Fabric's module starts at 1.21.4+, the
community NeoForge port at MC 1.21.11) — so the screenshot harness is a small custom module,
using those frameworks as design reference.

## Component 1 — Hot-reload (JBR 21 + HotswapAgent, external mode)

**One-time install** (documented in `repo/docs/COMMANDS.md`):

- **JBR 21** (JetBrains Runtime — OpenJDK 21 with DCEVM enhanced class redefinition built in),
  from the JetBrainsRuntime GitHub releases, extracted to `C:\Users\aleja\tools\jbr21`.
- **`hotswap-agent.jar`** (latest HotswapAgent release) at `C:\Users\aleja\tools\hotswap-agent.jar`.

**Run config:** a new NeoGradle run **`clientHotswap`** in `tinkers/build.gradle` — identical to
`client` plus:

- JVM args: `-XX:+AllowEnhancedClassRedefinition -javaagent:C:/Users/aleja/tools/hotswap-agent.jar=autoHotswap=true`
- JVM: the JBR (per-run `javaLauncher` override on the generated run task; **fallback** if NeoGradle
  7.1 resists the override: launch that run with `JAVA_HOME` pointed at the JBR — safe here because
  the Gradle daemon is disabled).

External mode (`-javaagent`) is chosen over `fatjar` mode so nothing is copied into the JBR install
and no `hotswap-agent.properties` needs to live on the mod classpath.

**Usage loop:**

1. `gradlew runClientHotswap` (background) — client opens once and stays open.
2. Edit code → `gradlew compileJava` (background). `autoHotswap=true` watches the compiled class
   output and redefines classes in the running client. No debugger, no keypress.
3. Data/assets: `gradlew processResources`, then in-game `/reload` (datapack: recipes, tags,
   loot) or F3+T (resourcepack: textures, models, lang).

**Documented limits:** DCEVM redefines method bodies and most class-structure changes, but
**initialization code does not re-run** — new registrations (blocks/items/menus), newly added event
listeners, and changed static initializers still require a normal restart. This covers the dominant
M5/M6 fix classes (method logic, GUI/render code, recipe logic) and excludes registration work.

## Component 2 — Logic GameTests (official framework, headless)

**Location:** `tinkers/src/main/java/slimeknights/tconstruct/gametest/` (main sources — the
standard Forge/NeoForge pattern; test classes are inert outside gametest-enabled runs).

**Registration:** `@GameTestHolder(TConstruct.MOD_ID)` classes with `@GameTest` methods
(1.21.1 annotation API). `@PrefixGameTestTemplate(false)` where flat template names are wanted.

**Initial tests (first wave):**

| Test | Rig template | Assertion |
|---|---|---|
| `smeltery_melts` | formed 3×3×3 smeltery, fuel tank with lava, iron in a melting slot | `succeedWhen` the smeltery tank contains molten iron |
| `smeltery_casts` | tank with molten iron + faucet + casting table with ingot cast | `succeedWhen` an iron ingot sits in the table output |
| `alloyer_alloys` | alloyer with two input fluids and fuel | `succeedWhen` output fluid present |

Tool crafting via Tinker Station menu is a second wave (server-side menu driving is fiddlier);
the framework choice does not change for it.

**Structure templates:** `.nbt` files under `tinkers/src/main/resources/data/tconstruct/structure/gametest/`,
authored once in-game with structure blocks (block-entity NBT — pre-filled tanks, fuel — is
captured in the template). **These same templates are reused by the uitest scenarios** (one rig
set for both systems).

**Build wiring** (`tinkers/build.gradle`):

- `gameTestServer` run (NeoGradle `type: gameTestServer`) with
  `property 'neoforge.enabledGameTestNamespaces', 'tconstruct'` and the documented
  `setForceExit false` daemon workaround.
- Usage: `gradlew runGameTestServer` — headless; exit code = number of failed required tests.

## Component 3 — GUI screenshot suite (`mantle.client.uitest` + Tinkers scenarios)

### Engine (Mantle, package `slimeknights.mantle.client.uitest`, ~4 classes)

**Activation:** only when `-Dmantle.uitest=true` is set. Without the property no listener is
registered and no suite class is touched at runtime — inert in production; ships in the normal jar
(same technique as common test hooks; no separate sourceset/artifact).

**`UiTestScenario`** (interface):

- `ResourceLocation id()` — becomes the PNG name.
- `void prepare(UiTestContext ctx)` — set up the rig (typically: place a structure template near
  the player via server-side `StructureTemplate` placement, then position the player).
- `void open(UiTestContext ctx)` — open the target GUI (typically: interact with the rig's block —
  a real `useItemOn` on the controller — so the real menu opens with real integrated-server data).
- `int settleTicks()` — ticks to wait after open before capture (default 20).
- `void close(UiTestContext ctx)` — close the screen (default: `setScreen(null)`).

**`UiTestContext`:** accessors for `Minecraft`, player, level, helper methods (place template at
offset, run a command as the player, teleport).

**`UiTestSuite`** (client tick state machine on `ClientTickEvent.Post`):

1. `WAIT_WORLD` — wait until the quick-play world is loaded and chunks rendered.
2. Per scenario, in registration order: `PREPARE → OPEN → SETTLE (settleTicks) → CAPTURE →
   CLOSE → next`.
3. `CAPTURE` uses the vanilla screenshot utility (`net.minecraft.client.Screenshot.grab`) with a
   **stable name**, written to `run/client/uitest-screenshots/<id>.png` (directory cleared at
   suite start).
4. After the last scenario: write `run/client/uitest-results.json`
   (`{ scenario id → ok | fail + error message }`) and stop the client cleanly
   (`Minecraft.stop()`).

**Robustness:** every phase has a tick timeout — a scenario that hangs or throws is recorded as
`fail` in the results JSON and the suite moves on; a whole-suite wall-clock ceiling forces exit so
the run can never hang the terminal.

**Registration:** static registry `UiTestScenarios.register(Supplier<UiTestScenario>)`, called from
consumer client-setup **only when `UiTestSuite.isActive()`** (property check), keeping the inactive
path allocation-free.

### Scenarios (Tinkers, `slimeknights.tconstruct.client.uitest.TinkerUiTestScenarios`)

Initial list — the M5 smoke-test screens, one small class each:

1. `smeltery` — place formed-smeltery template (fuel + molten metal in tank NBT), open controller GUI.
2. `tinker_station` — place station rig, open GUI.
3. `part_builder` — place rig, open GUI.
4. `melter` — place melter rig (fuel + melting item), open GUI.
5. `casting_table` — place casting rig mid-pour, open... *(casting has no menu — this scenario
   captures the in-world render instead: no `open`, camera aimed at the rig; validates the
   FluidRenderer in-world path)*.

Adding a scenario later = one ~20-line class + one template.

### Test world & launch

- **World:** a minimal superflat save (cheats on, peaceful, fixed seed/time/weather) committed as
  `tinkers/src/uitest/uitest-world.zip` (~50 KB). A small Gradle task (`prepareUiTestWorld`)
  extracts it to `run/client/saves/UITest` when absent (and on `--refresh` flag, re-extracts).
- **Launch:** new NeoGradle run **`clientUiTest`** = client +
  program args `--quickPlaySingleplayer "UITest" --width 1280 --height 720` +
  `-Dmantle.uitest=true`. The 1.20+ quick-play flag boots straight into the world with zero menu
  interaction; fixed window size keeps PNG dimensions stable across runs.
- **Usage:** `gradlew runClientUiTest` → window opens, suite runs, client exits by itself. Claude
  reads `uitest-results.json` + the PNGs (visual analysis), comparing against the previous run's
  set.

## Run configs summary

| Run | Purpose | Interaction |
|---|---|---|
| `runClient` | normal play/manual smoke test | human |
| `runClientHotswap` | long-lived client with live class reload | human plays, Claude recompiles |
| `runClientUiTest` | automated GUI screenshot suite | none — exits alone |
| `runGameTestServer` | headless logic tests, exit code = failures | none |
| `runData` / `runServer` | unchanged | — |

## Verification (feature DoD)

1. **Hotswap:** with `runClientHotswap` open, change a visible GUI string, `compileJava`, and see
   the change in the running client without restart.
2. **GameTests:** `gradlew runGameTestServer` exits 0 with the 3 first-wave tests passing.
3. **UI suite:** `gradlew runClientUiTest` runs with no interaction, produces one PNG per scenario
   plus `uitest-results.json` with all `ok`, and the client closes itself; Claude reads and
   analyzes the PNGs.

## Out of scope (deliberate, YAGNI)

- **Golden-image pixel diffing** — Claude's visual analysis of stable-named PNGs covers the need;
  revisit only if a pixel-perfect gate is ever wanted.
- **Headless CI** — mc-runtime-test/Xvfb can run the logic gametests in GitHub Actions later; its
  rendering stubs cannot produce real screenshots, so the visual suite stays local by design.
- **Input simulation** (Fabric-style) — not needed to open screens and capture.
- **Backporting the Fabric/Wurst client-gametest APIs** — heavier than the need; our engine is
  ~4 classes.

## References

- HotswapAgent — JDK17/21 + JBR setup, agent modes, `autoHotswap`:
  <https://github.com/HotswapProjects/HotswapAgent/blob/master/README.md>
- HotswapAgent configuration (`autoHotswap`, `extraClasspath`, `watchResources`):
  <https://hotswapagent.org/mydoc_configuration.html>
- JetBrains Runtime releases (JBR 21, DCEVM built in):
  <https://github.com/JetBrains/JetBrainsRuntime/releases>
- Modding hotswap guide (limitations — init code does not re-run):
  <https://moddev.nea.moe/hotswap/>
- NeoForge GameTests, 1.21.1 docs (annotations, namespaces, `runGameTestServer`, `setForceExit`):
  <https://docs.neoforged.net/docs/1.21.1/misc/gametest/>
- Fabric Client GameTest API (design reference: screenshots, world builder, run config):
  <https://docs.fabricmc.net/develop/automatic-testing>
- Unofficial NeoForge port of the Fabric client gametest (exists only for MC ≥1.21.11 — confirms
  the gap on 1.21.1): <https://github.com/Wurst-Imperium/neoforge-client-gametest-api>
- mc-runtime-test (headless CI prior art; rendering stubs → logic-only):
  <https://github.com/headlesshq/mc-runtime-test>
- Quick Play launch flags (`--quickPlaySingleplayer`, MC 1.20+):
  <https://github.com/PrismLauncher/PrismLauncher/issues/1154>
