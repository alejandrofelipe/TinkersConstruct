# Churn report: Mantle + TinkersConstruct, 1.20 → 1.21.10

Method: `git diff --numstat -M` between consecutive release tags. The base→1.21.1 diff uses `origin/1.20` (Mantle) and `origin/1.20.1` (Tinkers); both are exactly the merge-base. Lines = added + deleted. "main" means hand-written `src/main/java` **excluding** the in-main test packages (`*.gametest`, `client.uitest`), which are counted as tests. No pure Java renames were detected in any transition. Pure renames show up only in data: 1,835 in generated data at 1.21.1 and 73 resources at 1.21.8. Raw outputs are in this folder: `tables.txt`, `coupling.txt`, `imports.txt`, `classify.txt`, `hotfix.txt`, `ctypes.txt`, `hotspots_by_lines.txt`.

Caveat: the transitions are not port-only work. 1.20→1.21.1 also contains post-port fixes, advancements and the test suite. 1.21.8→1.21.10 contains a voluntary migration off the deprecated transfer API, JSON Things re-add, and M5 bug fixes.

## 1. Per-transition volume

**Mantle** (main Java total ≈ 593–601 files)

| Transition | main files (% of tree) | main +/− | tests +/− | gen files, +/− | main-res +/− | build +/− |
|---|---|---|---|---|---|---|
| 1.20→1.21.1 | 276 (47%) | +3251 / −3836 | +318 / 0 | 29, +128/−122 | +28/−125 | +77/−132 |
| 1.21.1→1.21.4 | 157 (27%) | +2269 / −1653 | +1/−1 | 24, +44/−84 | +17/−128 | +22/−11 |
| 1.21.4→1.21.5 | 55 (9%) | +1960 / −915 | +4/−2 | 0 | +5/−39 | +10/−10 |
| 1.21.5→1.21.8 | 53 (9%) | +822 / −443 | +203/−12 | 5, +5/−5 | +44/−35 | +9/−9 |
| 1.21.8→1.21.10 | 62 (10%) | +1696 / −716 | +19/−3 | 5, +5/−5 | +45/0 | +9/−9 |

**Tinkers** (main Java total ≈ 1,852–1,869 files, incl. ~277 package-info)

| Transition | main files (%) | main +/− | tests (src/test + in-main) +/− | generated files, +/− | main-res | build |
|---|---|---|---|---|---|---|
| 1.20→1.21.1 | 971 (52%) | +11061 / −11002 | 82 f, +7918/−209 | 7,493 (A2569 D2489 R2103, 1835 pure), +61179/−61426 | 59 f, +236/−254 | +249/−318 |
| 1.21.1→1.21.4 | 563 (30%) | +6425 / −5641 | 19 f, +310/−138 | 3,533 (2967 M), +11515/−13984 | 261 **added** (+1566) | +50/−24 |
| 1.21.4→1.21.5 | 251 (14%) | +2312 / −1531 | 26 f, +821/−191 | 121, +268/−137 | 37 f, +164/−162 | +31/−21 |
| 1.21.5→1.21.8 | 121 (7%) | +1173 / −984 | 10 f, +1320/−34 | 241 (152 D), +72/−5228 | 73 pure renames | +15/−15 |
| 1.21.8→1.21.10 | 371 (20%) | +6341 / −3399 | 41 f, +8171/−66 | 25, +33/−19 | 26 f, +44/−93 | +149/−24 |

- After the big port, the post-1.21.4 transitions touch 7–14% (Tinkers) and 9–10% (Mantle) of files. The exception is 1.21.10 at 20%, driven by the transfer API and two-phase rendering.
- Files ever touched (main): Mantle 328 / ~593, Tinkers 1,128 / ~1,852. Of the touched Tinkers files, 417 changed in only one transition, 418 in two and 176 in three.

## 2. Churn by subsystem (main Java lines, all 5 transitions)

**Top 15 by package (depth 2 below the root):**

| # | Mantle | lines | Tinkers | lines |
|---|---|---|---|---|
| 1 | client.model | 4168 | library.client | 5594 |
| 2 | client.screen | 1024 | smeltery.block | 5097 |
| 3 | client.render | 945 | library.recipe | 4352 |
| 4 | recipe.data | 704 | library.tools | 4055 |
| 5 | util | 662 | common.data | 2916 |
| 6 | fluid | 620 | tools.data | 1862 |
| 7 | registration.deferred | 564 | smeltery.client | 1464 |
| 8 | client.book | 484 | plugin.jei | 1408 |
| 9 | data.loadable | 474 | library.modifiers | 1266 |
| 10 | item | 470 | tables.client | 1050 |
| 11 | command | 448 | library.transfer (new in 1.21.10) | 1049 |
| 12 | inventory | 443 | tools.client | 962 |
| 13 | recipe.crafting | 440 | tables.block | 932 |
| 14 | recipe.ingredient | 420 | tools.modules | 882 |
| 15 | client (root) | 388 | library.data | 841 |

At depth 3, the largest Tinkers buckets are smeltery.block.entity (4,539), library.client.model (3,927), tools.data (1,474), library.tools.capability (1,421) and common.data.model (1,278). For Mantle, client.model.util (2,149) leads.

**By theme** (path-regex, first match wins in the listed order, so this is approximate). Per-transition lines are in brackets.

| Theme | Mantle | Tinkers |
|---|---|---|
| client/render/gui | 7469 (42.5%) [1088, 2200, 2592, 947, 642] | 12927 (25.9%) [2419, 5295, 1767, 715, 2731] |
| block entities/inventory/caps/fluids | 2569 (14.6%) [664, 276, 104, 260, 1265] | 9308 (18.7%) [3181, 125, 373, 708, 4921] |
| recipes/codecs/serialization/network | 2316 (13.2%) [1904, 349, 5, 19, 39] | 7058 (14.2%) [5174, 1671, 86, 25, 102] |
| gameplay (modifiers/stats/materials/tools) | n/a | 6945 (13.9%) [3983, 1322, 726, 169, 745] |
| data/datagen | 1664 (9.5%) [1052, 435, 2, 22, 153] | 6181 (12.4%) [3498, 1891, 204, 260, 328] |
| other (registration, events, entities, commands, util) | 3543 (20.2%) | 7450 (14.9%) |

- Recipes/codecs and datagen churn is almost entirely in 1.20→1.21.1 and 1.21.1→1.21.4. From 1.21.5 on it is close to zero.
- Client churn recurs in every transition: 1.21.4 item models, 1.21.5 model/rendering, 1.21.8 deferred GUI, 1.21.10 two-phase BER/entity rendering.
- Block-entity, capability and fluid churn spikes at 1.20→1.21.1 and again at 1.21.10 (transfer API).

## 3. Hotspots (main Java touched in ≥4 of 5 transitions)

- **Mantle:** 28 files (7 in 5/5). They hold 23.8% of all Mantle main churn. By theme: client 13, block entities/inventory 8, other 7.
- **Tinkers:** 117 files (28 in 5/5). They hold 32.7% of all Tinkers main churn. By top-level package: smeltery 30, library 23, tools 18, tables 15. By theme: block entities/caps 31, client 26, gameplay 23 (mostly entities and tool items), other 33.

Top 30 by total lines. The per-transition vector is [→1.21.1, →1.21.4, →1.21.5, →1.21.8, →1.21.10].

| n | lines | file (Tinkers) | vector |
|---|---|---|---|
| 4 | 879 | library/client/model/tools/ToolModel | 38,778,54,0,9 |
| 4 | 810 | library/client/model/tools/MaterialBlockModel | 24,313,466,0,7 |
| 4 | 543 | common/data/AdvancementsProvider | 482,33,26,0,2 |
| 4 | 442 | smeltery/TinkerSmeltery | 208,153,17,0,64 |
| 5 | 408 | library/client/model/FluidContainerModel | 63,275,32,13,25 |
| 4 | 376 | smeltery/block/entity/tank/SmelteryTank | 21,0,7,24,324 |
| 4 | 348 | smeltery/block/entity/module/MultitankFuelModule | 145,0,6,19,178 |
| 4 | 342 | library/tools/capability/inventory/ToolInventoryCapability | 42,2,2,0,296 |
| 4 | 320 | library/client/model/tools/MaterialModel | 12,260,41,0,7 |
| 4 | 301 | smeltery/block/entity/FaucetBlockEntity | 120,0,15,38,128 |
| 4 | 300 | fluids/TinkerFluids | 236,38,10,0,16 |
| 4 | 289 | tables/client/inventory/TinkerStationScreen | 200,0,8,37,44 |
| 5 | 279 | smeltery/block/entity/tank/CastingFluidHandler | 25,2,11,24,217 |
| 5 | 274 | library/tools/nbt/ToolStack | 59,8,77,9,121 |
| 4 | 257 | smeltery/block/entity/ChannelBlockEntity | 171,0,4,34,48 |
| 5 | 256 | TConstruct | 164,59,3,3,27 |
| 4 | 252 | library/client/model/TinkerItemProperties | 22,219,2,0,9 |
| 5 | 248 | library/client/GuiUtil | 26,13,75,128,6 |
| 4 | 242 | tools/TinkerTools | 63,151,21,0,7 |
| 4 | 228 | smeltery/client/render/ChannelBlockEntityRenderer | 4,0,3,6,215 |

| n | lines | file (Mantle) | vector |
|---|---|---|---|
| 5 | 663 | client/model/RetexturedModel | 16,292,343,8,4 |
| 5 | 388 | client/model/NBTKeyModel | 18,141,217,8,4 |
| 4 | 291 | client/model/util/MantleItemLayerModel | 66,150,71,4,0 |
| 4 | 240 | client/render/FluidRenderer | 70,8,42,0,120 |
| 5 | 239 | client/ClientEvents | 98,37,21,60,23 |
| 4 | 234 | inventory/SingleItemHandler | 27,0,3,21,183 |
| 5 | 231 | client/screen/book/BookScreen | 77,38,7,82,27 |
| 4 | 189 | block/entity/InventoryBlockEntity | 61,0,11,63,54 |
| 4 | 158 | client/screen/MultiModuleScreen | 44,4,0,56,54 |
| 5 | 157 | client/render/MantleShaders | 10,35,95,7,10 |

Other files touched in all 5 transitions, though smaller: entity classes (ThrownShuriken, CombatFishingHook, CustomFireball, FluidEffectProjectile, ArmoredSlimeEntity), event hubs (ModifierEvents, ToolEvents, InteractionHandler, *ClientEvents), BER renderers (HeatingStructure, Faucet), screens (ToolTable, ModifierWorktable), ranged tool items (ModifiableBow/Crossbow) and MantleBlockEntity. Full list: `tables.txt` and `hotspots_by_lines.txt`.

## 4. Platform coupling at 1.21.10 (main Java, excluding package-info and test packages)

| Repo | files | import net.minecraft | import net.neoforged | import com.mojang | no MC/NF/Mojang reference at all |
|---|---|---|---|---|---|
| Mantle | 522 | 457 (88%) | 151 (29%) | 122 | **49 (9.4%)** |
| Tinkers | 1592 | 1393 (87%) | 470 (30%) | 160 | **153 (9.6%)**, of which 69 import Mantle |

- Tinkers files importing `slimeknights.mantle`: 943 / 1592 (59%).
- Only 86 Tinkers files (31 in Mantle) limit their MC imports to "value types" (ResourceLocation/ResourceKey, chat, nbt, Mth, RandomSource, Holder, TagKey).
- Most-imported MC classes in Tinkers: ResourceLocation 475, ItemStack 442, Component 297, Level 297, LivingEntity 288, Player 248, BlockPos 243, BlockState 213, FluidStack (NF) 170.

Platform-free share per subsystem:

| Tinkers group | n | free% | | Mantle group | n | free% |
|---|---|---|---|---|---|---|
| library.module | 4 | 75% | | util | 29 | 51.7% |
| library.fluid | 7 | 57% | | client | 129 | 14.0% |
| library.json | 109 | 22.0% | | datagen | 7 | 14.3% |
| library.modifiers | 206 | 18.9% | | data | 147 | 8.2% |
| library.tools | 153 | 17.6% | | registration | 36 | 5.6% |
| library.utils | 22 | 13.6% | | recipe | 49 | 2.0% |
| tools | 282 | 8.9% | | fluid, command, block, loot, item, network, inventory, transfer | 117 total | 0% |
| library.materials | 25 | 8.0% | | | | |
| common | 63 | 6.3% | | | | |
| plugin | 52 | 5.8% | | | | |
| library.client | 110 | 5.5% | | | | |
| library.recipe | 136 | 3.7% | | | | |
| tables / smeltery / world | 78 / 127 / 61 | 2.6 / 2.4 / 1.6% | | | | |
| shared, gadgets, fluids, library.events, library.transfer | 126 total | 0% | | | | |

"Free" here is import-based: a file can still depend on MC types through a supertype or a Mantle class without importing them.

## 5. Mechanical vs substantive (heuristic estimate)

I classified every changed main file automatically with `-U0` hunks and checked a few by hand. Changed lines are bucketed as imports, identifier-only swaps (same token shape, only identifiers differ), "similar" modified lines (≥50% token overlap), or unpaired/structural lines. A file counts as mechanical when ≥90% of its lines are imports, identifier swaps or trivia. It counts as redesign when it was added or deleted with ≥40 lines, or when ≥50% of its lines (and at least 40) are structural. Everything else is signature-adaptation. Seeded random 40-file samples are shown below.

| Transition | files | mech / sig-adapt / redesign (all) | random 40 | lines: import / id-only / similar / structural / comments-braces |
|---|---|---|---|---|
| Tinkers 1.21.5→1.21.8 | 121 | 11 / 109 / 1 | 3 / 37 / 0 | 17% / 9% / 36% / 18% / 20% |
| Tinkers 1.21.8→1.21.10 | 371 | 61 / 286 / 24 (3,562 L = 37% of lines) | 7 / 31 / 2 | 12% / 6% / 16% / 37% / 30% |
| Mantle 1.21.5→1.21.8 | 53 | 8 / 43 / 2 | 6 / 33 / 1 | 14% / 11% / 16% / 30% / 29% |
| Mantle 1.21.8→1.21.10 | 62 | 7 / 49 / 6 (804 L = 33%) | 5 / 32 / 3 | 9% / 6% / 11% / 33% / 41% |

Estimate: purely mechanical lines (imports + identifier-only) are about 15–26% of changed lines. Most files fall in signature-adaptation, i.e. local call-site and signature rewrites; for example, GuiUtil at 1.21.8 went from PoseStack to GuiGraphics and from RenderType to RenderPipelines. True redesign concentrates in a few files per transition: 1–2 at 1.21.8, and 24 Tinkers + 6 Mantle at 1.21.10, holding about a third of that transition's lines.

Redesign examples:
1. `tconstruct/library/transfer/ModifiableItemHandlerSlot` (new, 359 L): menu slots rebuilt on the transactional NeoForge transfer API, followed by 7 follow-up fix commits.
2. `tconstruct/smeltery/block/entity/tank/SmelteryTank` (324 L, 179 structural): the multi-fluid tank moved to native transactional handlers, including duct mappings that must survive aborted transactions.
3. `tconstruct/smeltery/client/render/HeatingStructureBlockEntityRenderer` (154 L): ported to 1.21.10 two-phase rendering (extract a render state, then submit).
4. `mantle/inventory/SingleItemHandler` (183 L) and the new `mantle/transfer/SingleFluidTank` (211 L): Mantle's item/fluid handler base classes reimplemented natively, and the legacy compat surface dropped.
5. `mantle/client/screen/book/element/pip/StructurePiPRenderer` (new, 122 L, 1.21.8): book structure rendering moved to a picture-in-picture GUI renderer for the 1.21.6+ deferred GUI.

Some "redesign" files are not port-driven: ToolDataVerifier (lazy tool-data verification, an M5 fix), SpawnEggTextureProvider and DatagenCacheKeeper (datagen fixes).

## 6. Hotfix backports

Main-Java lines per hotfix (tests in brackets):

| Hotfix | main lines | main files | tests |
|---|---|---|---|
| 3.11.4 / 3.12.2 / 3.13.2 | 3 / 3 / 3 | InventoryModule | [68–69] |
| 3.11.5 | 76 | 7 files | [676] |
| 3.12.3 | 105 | 10 files | [846] |
| 3.13.3 | 111 | 10 files | [829] |
| 3.14.1 | 58 | 4 files | [402] |
| 3.14.2 | 15 | TinkerTools | [69] |
| 3.12.1 (staged-tags) | 44 | 2 files | [396] |
| 3.13.1 (head model + legacy models) | 109 main | 4 files | +5,230 generated deleted |
| Mantle 1.12.1 / 1.13.1 / 1.14.1 | 27 each | MapLoadable, SetLoadable, FluidIngredient, MantleCommand | |
| Mantle 1.15.1 | 8 | SetLoadable | |

Logical fixes applied to several lines. J is the Jaccard overlap of the changed-line set against the first application.

| Fix | lines applied | main lines per line | J |
|---|---|---|---|
| persist modifier-inventory items | 3 (1.21.1/4/5) | 3 | 1.00 everywhere |
| armor/arrow/shuriken copies own tool data | 4 (+1.21.10 origin) | 46, 46, 52, 52 | 1.00 / 0.89 |
| register tool item capabilities | 4 (+1.21.10 origin) | 15 each | 1.00 |
| JEI sort copy of melting fuels | 3 | 6 | 0.71 on 1.21.4+ |
| thrown tool reload | 3 | 4 | 0.33 on 1.21.5 |
| castless casting lookup | 3 | 5 → 9 | **0.00**: 1.21.1 patches CastingBlockEntity, 1.21.4+ patches RecipeLookupPopulator/TinkerRecipeAccess (server-side recipe API) |
| positive max_damage, sync alloying recipes, JEI patternless molding | 2 each (1.21.4/5) | 9 / 10 / 6 | 1.00 |
| Mantle set-loadable parse order | 4 (+1.21.10 via same commit lineage) | 8 | 1.00 |
| Mantle map-loadable order, flowing-fluid ingredient, recipe-source folder | 3 each | 12 / 4 / 3 | 1.00 |

Fixes that originated on 1.21.10 were implemented differently there. Tool-copy ownership was ~330 main lines (ToolDataOwnership + ItemStackMixin + ToolDataVerifier, in 2 commits), against 46–52 lines in the backports; these are related but not identical changes. Tool capabilities were 30 lines against the native transfer API, against 15 lines in the backports.

Summary: 10 logical fixes reached 3–5 lines each. Most backports are byte-identical (J = 1.00) and small (3–15 lines). Divergence appears exactly where the platform API changed between lines: the recipe API between 1.21.1 and 1.21.4, and the transfer API and tool-data model at 1.21.10. Test code dominates the hotfix diffs, at about 8–9× the main-Java lines.
