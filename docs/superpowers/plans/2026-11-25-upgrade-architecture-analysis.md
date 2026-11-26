# Making version upgrades cheaper — architecture analysis (interfaces / hexagonal / DDD)

**Date:** 2026-11-25 · **Scope:** Mantle (`repo/`) + TinkersConstruct (`tinkers/`), ports 1.20.1 → 1.21.10 (M0–M5) and the
M6 (1.21.11) baseline · **Status:** analysis + recommendation, nothing implemented.

Evidence (full reports, kept with this doc):
- [`churn-report.md`](2026-11-25-upgrade-architecture-evidence/churn-report.md) — git diff between release tags: volume,
  subsystems, hotspots, platform coupling, mechanical vs substantive, hotfix backports.
- [`breaks-report.md`](2026-11-25-upgrade-architecture-evidence/breaks-report.md) — the 84 distinct breaks from the five
  migration guides, classified by category/layer with an "would an abstraction have absorbed it?" verdict.
- [`research-report.md`](2026-11-25-upgrade-architecture-evidence/research-report.md) — what the modding ecosystem does
  (JEI, Fabric API, Mekanism, AE2, SlimeKnights, Stonecutter, Xplat/MultiLoader/Architectury/Balm) + architecture
  literature, with URLs.

---

## 1. TL;DR

- **A hexagonal / ports-and-adapters rewrite would not pay off.** 87% of the ~2,100 main Java files import
  `net.minecraft`; only ~10% are platform-free. The breaks that *cost* the most (paradigm changes and silent semantic
  changes, concentrated in client rendering and block entities) are exactly the ones that leak through a port: the old
  abstraction had the wrong shape (transfer API, two-phase rendering) or the dependency was invisible (`CustomData`
  sharing between copies). Of 84 breaks: 18% would have been absorbed, 54% partially, 29% not at all. No large mod in
  the ecosystem does this; the evidence goes the other way (leaky abstractions, abstraction layers like Balm breaking
  themselves on 1.21.11, wrapper allocation on hot paths).
- **What *did* absorb churn is narrower and already in the codebase: Mantle owning the vocabulary.** Where Mantle's
  signatures used Mantle types (`Loadable`, `handleMouse(x, y, button)`, `NetworkWrapper`, `IEarlyReloadListener`,
  `MaterialId`/`ModifierId`), version changes stopped at Mantle. Where they exposed vanilla types (`PoseStack`,
  `MultiBufferSource`, `ValueOutput`, `IFluidHandler`, `GuiGraphics`), changes propagated to Tinkers. This is the DDD
  anti-corruption-layer idea applied *selectively*, to concepts that are stable in the domain but unstable in vanilla.
- **The highest-ROI insulation was testing, not architecture.** 9 of the 12 post-release hotfixes were silent semantic
  changes; none were caught by compiler/unit tests/singleplayer; every one was caught (later) by a boundary contract
  test (save round-trip, stream-codec byte equality, BE sync, copy comparison, JEI per-category).
- **Recommendation:** five incremental measures (§5), no big-bang refactor: (R1) boundary contract-test catalogue,
  (R2) vocabulary rule for Mantle's public API, (R3) rendering seams in Mantle before M7, (R4) rename-map tooling for
  mechanical breaks, (R5) scripted multi-line backports. Keep branch-per-version; don't adopt Stonecutter.

---

## 2. Where the upgrade cost actually is

### 2.1 Volume (main Java, from `churn-report.md` §1)

| Transition | Tinkers files (% tree) | Mantle files (% tree) | Note |
|---|---|---|---|
| 1.20→1.21.1 | 971 (52%) | 276 (47%) | the big port (Forge→NeoForge, components) |
| 1.21.1→1.21.4 | 563 (30%) | 157 (27%) | item models, recipes-as-registry |
| 1.21.4→1.21.5 | 251 (14%) | 55 (9%) | `BakedModel` removal, NBT `Optional` |
| 1.21.5→1.21.8 | 121 (7%) | 53 (9%) | deferred GUI, `ValueInput/Output` |
| 1.21.8→1.21.10 | 371 (20%) | 62 (10%) | transfer API, two-phase rendering, input events |
| 1.21.11 (baseline) | — | 1172 errors | 849 = `ResourceLocation`→`Identifier` |

### 2.2 What kind of change (from `breaks-report.md` §2)

| Category | Breaks | Absorbable Y/P/N | Share of *effort* |
|---|---|---|---|
| (a) mechanical rename/move | 8 (10%) | 1/5/2 | dominates *error counts* (½ of M5, ~90% of M6) but cheap: scripted |
| (b) signature/shape change | 22 (26%) | 4/15/3 | moderate, spread across call sites |
| (c) paradigm replaced | 24 (29%) | 5/13/6 | **high** — transfer "done twice", rendering rewrites |
| (d) silent semantic change | 18 (21%) | 5/8/5 | **highest** — "most of the real cost was silent visual breakage" (M4); 9/12 hotfixes |
| (e) data format | 5 | 0/0/5 | low after 1.21.4 |
| (f) build/toolchain | 3 | 0/0/3 | low |
| (g) third-party API (JEI) | 4 | 0/4/0 | low |

Only 15–26% of changed lines are purely mechanical; the typical changed file needs local signature/call-site rewrites,
and redesign concentrates in few files (at 1.21.10, 24 Tinkers + 6 Mantle files carry ~⅓ of the lines).

### 2.3 Where (layers and hotspots)

- **Client (render/GUI/models) is the recurring cost:** 26% of Tinkers and 42% of Mantle churn, and it changed in
  *every* transition (item models → `BakedModel` → deferred GUI → two-phase rendering → 1.21.11 `BakedQuad`/
  `RenderTypes`). Known ahead: `GuiGraphics`→`GuiGraphicsExtractor` in 26.1, `MultiBufferSource` gone in 26.2.
- **Block entities / inventories / capabilities:** two spikes (port: 3.2k lines; 1.21.10 transfer API: 4.9k lines).
- **Recipes/codecs and datagen:** heavy in the first two transitions, near zero since 1.21.5 — the `Loadable` layer held.
- **Hotspots:** 117 Tinkers files (28 in all 5 transitions) carry 33% of all Tinkers churn; 28 Mantle files carry 24%.
  Top: `ToolModel`, `MaterialBlockModel`, `FluidContainerModel`, `SmelteryTank`, `CastingFluidHandler`, `GuiUtil`,
  Mantle `RetexturedModel`, `NBTKeyModel`, `FluidRenderer`, `ClientEvents`, `BookScreen`, `MantleShaders`.

### 2.4 Coupling (from `churn-report.md` §4)

| | Files | import `net.minecraft` | platform-free |
|---|---|---|---|
| Tinkers | 1,592 | 87% | 9.6% (153) |
| Mantle | 522 | 88% | 9.4% (49) |

59% of Tinkers files import Mantle. Most-imported vanilla types in Tinkers: `ResourceLocation` 475, `ItemStack` 442,
`Component` 297, `Level` 297, `LivingEntity` 288. Even the "domain" packages are mostly coupled: `library.modifiers`
18.9% platform-free, `library.tools` 17.6%, `library.materials` 8%.

---

## 3. Pattern-by-pattern assessment

### 3.1 Full hexagonal / ports & adapters (domain core + vanilla adapters)

**Would solve:** categories (a) and (b) for code behind the ports — renames and signature changes would be fixed once in
the adapter instead of at every call site.

**Would not solve, with evidence:**
- **The domain *is* the integration.** A tool is an `ItemStack` with data components; a modifier hooks vanilla events,
  attributes, loot, rendering; the smeltery is block entities + fluids + a renderer. 87% coupling is not accidental
  sloppiness, it is the shape of a content mod. A port for `ItemStack`, `Level`, `LivingEntity`, `Component` would
  re-implement half of vanilla's API surface.
- **Paradigm changes break the port contract itself.** Two-phase rendering split `renderItem(PoseStack,
  MultiBufferSource, …)` into extract + submit — Mantle's existing helper *was* a port and was deleted. The transfer API
  made the old `IFluidHandler`-shaped contract wrong for providers (transactions, root commit, `CLOSING` lifecycle,
  `TransactionContext` threaded into `IFluidContainerTransfer.transfer`); NeoForge itself states an old→new wrapper is
  "not possible". A port designed in 1.21.1 would have been rewritten in 1.21.10 along with its adapters.
- **Silent changes are invisible at any interface** (`CustomData` sharing, atlas split, `verifyComponentsAfterLoad`
  side effect). Only tests found them.
- **Runtime cost on hot paths:** rendering and ticking would allocate wrapper objects per frame/tick (Lithium ships a
  whole `alloc.*` mixin category to remove such allocations from vanilla).
- **Codecs, data components, registries, mixins and datagen are tied to vanilla types** — they can't sit behind a port
  without duplicating the type system.
- **Cost to adopt:** touching most of ~2,100 files, i.e. more than a full port, with no ecosystem precedent.

**Verdict:** no.

### 3.2 DDD

- **Ubiquitous language / value objects — already present and proven.** `MaterialId`, `ModifierId`, `ToolStack`, stat
  types, `Loadable` schemas. Concrete payoff: 176 of 327 files using `MaterialId`/`ModifierId` never name
  `ResourceLocation`, so the M6 rename skips them; `RecordLoadable` field definitions across 401 files changed only in
  1.21.4 and zero times since.
- **Bounded contexts:** Mantle vs Tinkers already is one, and Mantle is the natural place for the ACL.
- **Anti-corruption layer — yes, but selective.** It works for concepts that are *stable in the domain and unstable in
  vanilla* (ids, serialization schema, input, network send, lifecycle hooks, fluid action). It fails for concepts whose
  paradigm vanilla changes (rendering pipeline, transfer semantics).
- **Pure domain core extraction:** possible for the ~10% already platform-free (modifier math, stat calculation,
  material rules), but those packages barely churn, so it buys little upgrade cost. Not worth a dedicated effort.

**Verdict:** keep and extend the vocabulary/ACL where it already works (R2); no tactical-DDD restructuring.

### 3.3 Interfaces in general

Useful as **seams** (one place to change), not as **insulation** (nothing changes). The measurable win is converting
"fix N call sites" into "fix 1 helper" — e.g. the GUI atlas identity fixed once in `ModelHelper.addItemLayer`, input
unpacked once at the vanilla boundary. That's R3.

### 3.4 Loader abstractions (Xplat, MultiLoader-Template, Architectury, Balm)

They abstract the *loader*, not the Minecraft version — MultiLoader's `common` "compiles against vanilla". Balm itself
broke heavily on 1.21.11. Irrelevant here (single loader).

### 3.5 Single-source multi-version (Stonecutter, Manifold, ReplayMod preprocessor)

Solves **hotfix duplication**, not port cost. Only one version active in `src/` at a time; conditions don't work in
JSON/TOML; used mostly by small/medium mods; at least one Manifold user is dropping multi-version targeting. Our
backports are small and mostly byte-identical (J = 1.00 for 6 of 10 fixes) and diverge exactly where the platform
changed — where a preprocessor would need `//? if` blocks anyway. **Verdict:** no; script cherry-picks instead (R5).

### 3.6 What the big mods do

JEI (7 MC versions), Fabric API, Mekanism, AE2, SlimeKnights upstream: **branch per MC version + small stable API module
+ selective backports**, regression tests, scripted/manual renames. That is already our model.

---

## 4. Why Mantle worked where it worked (the key finding)

| Mantle surface | Signature vocabulary | Result across ports |
|---|---|---|
| `data.loadable` (`RecordLoadable`, `Loadable`) | Mantle | 9 files changed in T1–T4; 401 Tinkers files untouched since 1.21.4 |
| `network` (`NetworkWrapper`) | Mantle | 3 files; `sendToServer` client-only change absorbed |
| Input (`ModuleScreen.handleMouse*(x, y, button)`) | Mantle (primitives) | 1.21.10 event input absorbed |
| `IEarlyReloadListener` | Mantle callbacks | reload `SharedState` absorbed |
| `MaterialId`/`ModifierId` via `ResourceId` | Mantle (composition) | M6 rename skips 176 files |
| `RenderingHelper.renderItem(PoseStack, MultiBufferSource…)` | **vanilla** | deleted/split at 1.21.10 |
| BE save via `ValueOutput` | **vanilla** | propagated at 1.21.8 |
| fluid handlers (`IFluidHandler`) | **vanilla/NeoForge** | 9 downstream breaks at 1.21.10 |
| `client.model`, `client.screen` (`GuiGraphics`, `RenderType`) | **vanilla** | 41 files, the largest Mantle churn |

Rule extracted: **a Mantle API absorbs version churn iff its signature is expressed in Mantle-owned or primitive types
*and* the concept it models is stable in the domain.** Mantle mostly *extends* vanilla classes, so today it behaves as a
shared base layer, not as a set of ports — that's fine for the unstable paradigms, but it means stable concepts leak
vanilla types unnecessarily.

---

## 5. Recommendation (incremental, ordered by ROI)

### R1 — Boundary contract-test catalogue (highest ROI; targets category (d))
Make "every vanilla integration point has a contract test" an explicit, enumerated checklist instead of tests added after
a hotfix. Catalogue (existing ✅ / to add ➕):
- ✅ BE save/load round-trip (empty, filled, transition) — `BlockEntityRoundTripGameTests`
- ✅ stream-codec byte equality; ✅ tool copy independence; ✅ JEI one scenario per category; ✅ uitest fails on non-ok
- ➕ datagen golden diff: `runData` must leave `src/generated` byte-identical unless the change says otherwise (fail the
  battery on unexpected diffs)
- ➕ client-sync parity per recipe type (the alloying/melting/severing "not synced" class of bug)
- ➕ capability/handler registration per block/item that should expose one (the 3.14.2 class of bug)
- ➕ a coverage table in `COMMANDS.md`/test README mapping integration point → test, reviewed at each milestone's Phase 0

Cost: ~1–2 sessions. Payoff: catches silent breaks *during* the port instead of as hotfixes on 4–5 lines.

### R2 — Vocabulary rule for Mantle's public API (targets (b), parts of (c))
For **stable domain concepts**, new or touched Mantle APIs consumed by Tinkers take/return Mantle-owned or primitive
types; vanilla types stay inside Mantle. Apply *opportunistically* when a milestone forces the API to change anyway,
never as a standalone refactor. Candidates: a Mantle-owned save/load facade over `ValueInput/ValueOutput`
(`store(key, codec, value)` shape — 1.21.8 showed it would have held), fluid/item transfer *consumer* helpers (keep
providers native — the paradigm leaks there), permission levels for commands (M6 propagates them to 9 subcommands).
Do **not** apply to rendering pipeline types or registries. Write the rule in Mantle's docs. Cost: ~0 upfront, small per
milestone.

### R3 — Rendering seams in Mantle before M7 (targets the recurring client churn)
Client code changed in every transition and 26.1/26.2 already announce `GuiGraphicsExtractor` and the end of
`MultiBufferSource`. Goal is not insulation but **fewer places to fix**: route Tinkers' repeated vanilla rendering
patterns (fluid cuboids in tanks/faucets/channels/casting, item-in-world rendering, GUI fluid/tank widgets, `GuiUtil`)
through a small set of Mantle helpers expressed in the *current* paradigm (two-phase: render-state + submit). Pilot:
fluid rendering (`FluidRenderer` + Faucet/Channel/Casting/Heating renderers — all hotspots). Measure call sites
before/after; continue only if the pilot reduces the M7 rendering diff. Cost: medium (~2–3 sessions for the pilot).

### R4 — Rename-map tooling for mechanical breaks (targets (a))
Category (a) dominates error counts. Generalize the M6 token script into a reusable `rename-map` (text file of
old→new FQN/member pairs + a script, or OpenRewrite `ChangeType`/`ChangeMethodName` recipes) seeded from the NeoForge
porting primer. From 26.1 (unobfuscated jars) a jar-to-jar API diff can generate candidate maps. Cost: ~1 session; reuse
every milestone.

### R5 — Scripted multi-line backports (targets hotfix duplication)
Keep branch-per-version. Script: cherry-pick a fix commit across the release lines, run the battery per line in its own
worktree, report which lines need manual adaptation. 6/10 past fixes were byte-identical, so most become a one-command
job. Cost: <1 session. Stonecutter rejected (§3.5).

### Explicitly not recommended
Full hexagonal rewrite; DDD restructuring of packages; extracting a pure domain module; wrapping rendering pipeline,
registries, codecs or data components; single-source preprocessor.

---

## 6. Suggested sequencing

1. **Before M6 Phase 1:** R4 (the M6 rename is the next big mechanical step) and R1 catalogue + datagen golden diff.
2. **During M6:** R2 applied to whatever Mantle API M6 forces to change (permissions are the first candidate).
3. **Between M6 and M7:** R3 pilot (fluid rendering), measured against the M7 rendering diff.
4. **Any time:** R5 (next hotfix is the trigger).

## 7. Caveats

- Transitions mix porting with other work (post-port fixes, test suites, voluntary transfer migration); volumes are
  upper bounds for port cost.
- "Absorbable" verdicts are judgments against the documented breaks, not experiments.
- Platform-coupling counts are import-based (supertypes and Mantle classes can carry vanilla dependencies silently).
- Ecosystem claims are tagged by verification level in `research-report.md`; Stonecutter docs and Create/Iris practices
  couldn't be verified.
