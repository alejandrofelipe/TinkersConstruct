# Version-upgrade breaks: evidence base for an abstraction-layer decision

Sources: the five `repo/docs/migration/*.md` guides, the M6 plan (baseline), the M6+ dependency analysis, the roadmap ("Lessons from M1–M5") and `PROGRESS.md` for the hotfixes. I also measured the git history between release tags (`v1.21.1-*` up to `v1.21.10-*`) and grepped both repos on branch `1.21.11`.

Transitions: **T0** 1.20.1/Forge→1.21.1/NF · **T1** →1.21.4 (M1) · **T2** →1.21.5 (M3) · **T3** →1.21.8 (M4) · **T4** →1.21.10 (M5) · **T5** →1.21.11 (M6, Phase-0 baseline only).
Layers: R = rendering/GUI (incl. the JEI plugin UI), M = models/textures, D = data/datagen, S = recipes/codecs/serialization, BE = block entity/inventory/capabilities, I = items/components, N = networking, Reg = registries, G = gameplay, B = build/test infra, all = pervasive.
Verdict: **Y** = an abstraction layer that existed before the change would have absorbed it, **P** = partially, **N** = no. "+d" means the break also fails silently.

## 1. Catalogue (84 distinct breaks)

| # | T | Break | Cat | Layer | Size | Abs? | Why |
|---|---|---|---|---|---|---|---|
| 1 | T0 | `net.minecraftforge.*`→`net.neoforged.*` repackaging | a | all | pervasive | P | Only symbols behind a facade are absorbed. The sed was cheap anyway |
| 2 | T0 | NeoGradle, Java 21, `neoforge.mods.toml`, SRG→mojmap ATs | f | B | 2 builds | N | Lives outside the code |
| 3 | T0 | `RegistryObject`→`DeferredHolder`; `@ObjectHolder`/`MissingMappings` removed | b | Reg | — | P | Mantle registers and `*Object` types hid most of it. Removed features can't be adapted |
| 4 | T0 | Stack NBT→data components | c | I | — | P | `ToolStack`/`ModDataNBT` already wrapped tool NBT. Raw `getTag` sites leaked |
| 5 | T0 | `LazyOptional` caps→`BlockCapability` + central registration | c | BE | — | P | The handler interfaces survived. A lookup port would hide the rest |
| 6 | T0 | `SimpleChannel`→`CustomPacketPayload` + `StreamCodec` | c | N | ~30 packets | P | `NetworkWrapper` absorbed registration and send. Encoding changed in every packet |
| 7 | T0 | Serializers→`MapCodec`/`StreamCodec`; `FinishedRecipe`→`RecipeOutput` | c | S | — | **Y** | `Loadable` gained `codec()`/`streamCodec()`/`mapCodec()` (§1 "codec bridge") |
| 8 | T0 | JSON `nbt`→`components`; `MobType`→entity tags | e | D | — | N | Schema change visible in data |
| 9 | T0 | `use`→`useWithoutItem`, `appendHoverText(TooltipContext)`, pick-block | b | G | — | N | These are overrides of vanilla hooks: the mod class *is* the adapter |
| 10 | T0 | BE save/load + `ItemStack.save` take `HolderLookup.Provider` | b | BE | — | Y | `MantleBlockEntity` base |
| 11 | T0 | Food component, `ArmorMaterial` holder, `ItemAbility`, `DistExecutor` | b | I | — | P | |
| 12 | T0 | `bake` 5-arg, model packages, `forge:`→`neoforge:` ids | a | M | 6 models | P | |
| 13 | T0 | `@Mod` constructor injection, `ModConfigSpec` | b | B | 1 class | Y | Single entrypoint |
| 14 | T1 | NG 7.1.39; `data`→`clientData`+`serverData` runs | f | B | — | N | |
| 15 | T1 | Datagen API (`GatherDataEvent.Client/Server`, no `ExistingFileHelper`, vanilla `ModelProvider`, `RecipeProvider.Runner`) | c | D | — | P | Mantle provider bases helped; the contract changed |
| 16 | T1 | Datagen runs purge each other (~10.5k textures) | d | D | every run until M5 | P | Fixed once, but only in M5 (`DatagenCacheKeeper`) |
| 17 | T1 | `Properties.setId` required (compiles, crashes at load) | d | Reg | ~200 sites | **Y** | Mantle Function-form registers inject the id |
| 18 | T1 | `BlockEntityType.Builder` removed | a | Reg | — | Y | |
| 19 | T1 | `Ingredient.of(TagKey)` removed; eager tags crash | c | S | — | P | `LazyTagIngredient` |
| 20 | T1 | Registry-aware ops: holder sets, staged tags at reload, NbtOps client sync | d | S | every reload listener | P | `ContextKey.OPS` fixed loadable users. Two loaders that bypassed it silently dropped data |
| 21 | T1 | Vanilla tag renames (`c:slime_balls`, `trim_templates`) | e | D | — | N | |
| 22 | T1 | Recipes become a datapack registry (`display()`, `placementInfo()`, `ResourceKey` ids) | c | S | 116/401 Tinkers loadable files | **N** | Every recipe *is* a vanilla `Recipe` |
| 23 | T1 | No client `RecipeManager`; only opted-in types synced; empty input returns nothing | d | S | — | P | `TinkerRecipeAccess`/`ClientRecipes` localized the fix, but the first adapter was wrong for 2–4 releases |
| 24 | T1 | `InteractionResultHolder` removed | b | G | ~73 files | N | Hook overrides |
| 25 | T1 | Damageable = `MAX_DAMAGE` presence; `POSITIVE_INT` checked on encode | d | I | — | N | Vanilla invariant; caused shipped data loss |
| 26 | T1 | `Tier`→`ToolMaterial`, `ArmorType`, `Consumable` | b | I | — | P | |
| 27 | T1 | Item-model system (`ItemModel.Unbaked`, `items/*.json`, no texture deps, bake before atlas, no `ItemColors`) | c | M | "biggest wall" | **N** | Where variant selection happens changed |
| 28 | T1 | `blit(RenderType…)`, `EntityRenderState`, `ShaderProgram` | c | R | — | P | |
| 29 | T1 | JEI 19→20 | g | R | — | P | |
| 30 | T2 | `onRemove`→`preRemoveSideEffects`/`affectNeighborsAfterRemoval`; default drops cause double drops | c+d | BE | — | **Y** | `MantleBlockEntity` routes to the `InventoryBlock` hooks; callers unchanged |
| 31 | T2 | NBT getters→`Optional` | b+d | S | 4 silent bugs | P | `IModDataView`/`ModDataNBT` kept the old shape (D1). Raw sites leaked |
| 32 | T2 | `parseOptional`/`NbtUtils` removed | b | S | — | P | |
| 33 | T2 | `ArmorItem`/`SwordItem` removed; tooltip `Consumer` | b | I | — | P | Mantle `Consumer` overloads |
| 34 | T2 | `getOrThrow(TagKey)` throws on undefined tags | d | Reg | crashed tabs | P | |
| 35 | T2 | Gametest framework rewrite | c | B | 38 tests | **Y** | `@TinkerGameTest` + reflective registrar |
| 36 | T2 | Blocking needs `BLOCKS_ATTACKS`/`WEAPON` | d | G | — | N | New vanilla semantics |
| 37 | T2 | `inventoryTick` slot semantics, misc gameplay signatures | b | G | — | N | |
| 38 | T2 | `DataComponentPredicate`, trims, `ClientAsset`, `Weighted` | b | D | — | P | |
| 39 | T2 | Hand-written blockstates need `type` | e | M | 34 files | N | |
| 40 | T2 | `RenderPipeline`; `RenderSystem` blend gone; `mat3` silently zero | c+d | R | — | P | Central `MantleRenderTypes`/`MantleShaders`; GL semantics leak |
| 41 | T2 | Async screenshot API | c | B | — | Y | `RenderTargetCapture` |
| 42 | T2 | `BakedModel` removed; dynamic models = block-state types | c | M | Mantle `client/model` +1.9k/−1.1k lines | **N** | The model concept itself split |
| 43 | T2 | `ResourceLocation.Serializer`, sealed `ClickEvent` | a | G | — | P | |
| 44 | T3 | Deferred GUI (`Matrix3x2fStack`, pipeline blits, `nextStratum`, no `setShaderColor`) | c | R | `GuiUtil` 13 callers | P | Immediate→recorded contract |
| 45 | T3 | Text without alpha invisible; `renderBackground` first; tooltips first-wins without pose | d | R | — | P | Central fixes covered subclasses; literals leaked |
| 46 | T3 | Picture-in-picture for 3D, absolute coordinates | c | R | — | N | New contract |
| 47 | T3 | GUI item atlas keyed by model identity | d | M | — | **Y** | Fixed once in `ModelHelper.addItemLayer` (the memoization rule still leaked) |
| 48 | T3 | Mob-effect atlas gone | e | M | 28 PNGs | N | |
| 49 | T3 | `ValueInput`/`ValueOutput` | c | BE | — | P | Mantle BE API changed and propagated; keys kept |
| 50 | T3 | `ItemStackHandler.deserialize` stops calling `onLoad` | d | BE | — | N | Inherited base behavior |
| 51 | T3 | UBO shaders + fog rewrite | c | R | 70 JSONs | P | |
| 52 | T3 | BER frustum uses the renderer's box | d | R | 4 renderers | P | |
| 53 | T3 | `ChunkSectionLayer`, `shouldRenderOffScreen()`, stage sub-events | b | R | — | P | |
| 54 | T3 | GPU readback async | d | B | — | Y | `RenderTargetCapture` |
| 55 | T3 | Tag appenders | b | D | output identical | P | |
| 56 | T3 | `sendToServer` client-only | b | N | — | **Y** | `NetworkWrapper.sendToServer` unchanged |
| 57 | T3 | `@EventBusSubscriber(bus=)` removed + renames | a | B | 24 files | N | |
| 58 | T3 | JEI 24 GUI | g | R | — | P | |
| 59 | T4 | Transfer API (`ResourceHandler`, transactions; old caps removed) | c | BE | 713 refs / 123 files; done twice | P | See §3 |
| 60 | T4 | Native handlers write other NBT keys | d | BE | — | Y | `SingleFluidTank`/`ChestItemHandler` + byte fixtures |
| 61 | T4 | Item-cap totals, slot index 0, `StackCopySlot` write-back, transactional slots | d | BE | 9 `slot_safety` tests | N | |
| 62 | T4 | Two-phase rendering (extract/submit; `renderStatic` gone) | c | R | 9 BERs + 5 entity renderers | **N** | Mantle `renderItem` removed |
| 63 | T4 | `AtlasManager`; `getAtlasOrThrow` takes an id (compiles, throws) | d | M | — | Y | One helper |
| 64 | T4 | Outline event, particles, skulls | c | R | — | P | |
| 65 | T4 | Event-based input; un-`@Override`d methods never called | b+d | R | — | P | Mantle kept `(x, y, button)` |
| 66 | T4 | `getUnsafe` and `verifyComponentsAfterLoad` removed | c | I | — | **N** | New design + first mixin |
| 67 | T4 | `CustomData` shared between copies | d | I | — | **N** | Inside a vanilla constructor |
| 68 | T4 | `isClientSide()`, `getServer`, `IRON_CHAIN` | a | G | 203 sites | P | |
| 69 | T4 | Reload listener `SharedState` | b | D | — | **Y** | `IEarlyReloadListener` adapts once |
| 70 | T4 | FML 10 `getDist()`/`isProduction()` | b | B | unit-test mocks | P | |
| 71 | T4 | `ContainerUser`, respawn data, `KeyMapping.Category`, `TypedEntityData` | b | G | — | P | |
| 72 | T4 | `ValueInput#keySet` throws on the empty input | d | S | — | P | |
| 73 | T4 | `position_color_tex_lightmap` removed (cascade crash) | c | R | — | Y | Mantle ships its own shader; constant name stable |
| 74 | T4 | `min_format`/`max_format`; spawn-egg template gone | e | D | — | N | |
| 75 | T4 | JEI 26.3 renames; JSON Things 0.16 | g | R | 4 breaks | P | |
| 76 | T5 | `ResourceLocation`→`Identifier` | a | all | Mantle 849/1172 errors; ~700 files | P | Domain ids insulate 176 files |
| 77 | T5 | `package-info` annotation removed | f | B | 357 files | N | |
| 78 | T5 | Moved packages (`Util`, `criterion`, entity subpackages) | a | all | 83 files | N | |
| 79 | T5 | Game rules registry; `PermissionCheck` | b | G | 22 errors | P | Mantle int constants propagate (D-P) |
| 80 | T5 | `RenderTypes`/`RenderSetup`; `IQuadTransformer` gone | c | R | 56 errors / 10 files | P | |
| 81 | T5 | `BakedQuad` without `int[]` | b | M | 6 + 24 files | P | Quad readers are scattered |
| 82 | T5 | Item atlas split | d | M | 8 + 18 sites | P | The atlas choice is per texture |
| 83 | T5 | `Screen` final fields, `Button` abstract, `keyPressed` returns `false` | b+d | R | 17 errors | P | |
| 84 | T5 | JEI 27 `getIdentifier` | g | R | — | P | |

## 2. Aggregates

**By category (primary):**

| Category | Breaks | % | Y/P/N |
|---|---|---|---|
| a mechanical rename/move | 8 | 10% | 1/5/2 |
| b signature/shape change | 22 | 26% | 4/15/3 |
| c redesign / new paradigm | 24 | 29% | 5/13/6 |
| d silent behavioral change | 18 | 21% | 5/8/5 |
| e data/datagen schema | 5 | 6% | 0/0/5 |
| f build/toolchain | 3 | 4% | 0/0/3 |
| g third-party API | 4 | 5% | 0/4/0 |

**Verdict:** Y 15 (18%) · P 45 (54%) · N 24 (29%).

**Per transition (Y/P/N):** T0 3/7/3 · T1 2/8/6 · T2 3/7/4 · T3 3/8/4 · T4 4/8/5 · T5 0/7/2.

**By layer:** R 18 (21%), M 9, B 9, D 8, S 8, BE 8, G 8, I 7, Reg 4, all 3, N 2. Client-side (R+M) = 27 rows (32%). It is the largest block in every transition from T3 on.

**Effort vs count.** Category (a) dominates the *error count* but not the effort:
- T4: half of the 594 Tinkers errors were `isClientSide`.
- T5: ≈90% of the errors are scripted (849 rename + 156 `package-info` out of 1172 in Mantle).

The docs place the effort on (c) and (d):
- T1: "Item models (the biggest wall)", "Serialization (the long tail)".
- T2: models, render pipeline, `Optional` getters, gametests.
- T3: "most of the real cost was **silent visual breakage**".
- T4: "the transfer API rework (done twice), two-phase rendering and event-based input, both of which break silently".

The battery grew 674→793 unit and 58→138 gametests in M5 alone, plus 12 new uitest scenarios. M5 had review rounds after every batch, and they caught 7 regressions that were never shipped.

Churn between release tags (Tinkers `src/main/java`, ~1890 files): T1 566 files, T2 261, T3 127, T4 385. Mantle (605 files): 158 / 56 / 56 / 63.

## 3. Would hexagonal / ports-and-adapters have held? (c) and (d) in detail

**Where the old port shape was right and survived:**
- **Serialization schema (`Loadable`).** Across all four transitions, the lines touching `RecordLoadable` field definitions in Tinkers' 401 loadable files changed **15 times, all in T1**, and 0 in T2–T4. Codec bridges (T0), registry-aware ops threading and NbtOps re-parenting (T1, `ContextKey.OPS`; Mantle `data/loadable` +155 lines) and the network order fixes all landed inside Mantle. JSON shapes even survived the transfer rework ("JSON shapes unchanged").
- **Input (T4).** `ModuleScreen.handleMouse*(x, y, button)` and `BookElement.mouse*` kept their signatures. `MultiModuleScreen`/`BookScreen` unpack the events at the vanilla boundary. Input changed in value, not in paradigm, so a port holds.
- **`ValueOutput` (T3).** Mantle's `saveSynced(CompoundTag, Provider)` propagated, but the shape that replaced it, `store(key, codec, value)`, could have been written in 1.21.1 over `CompoundTag` + `RegistryOps`. NBT parity holds precisely because "`store(key, CODEC, v)` uses the same codec and registry ops". The right abstraction was knowable in advance; Mantle exposed the vanilla type instead.
- **Lifecycle hooks:** `preRemoveSideEffects` (T2), `setId` (T1), `SharedState` (T4) and `sendToServer` (T3) were absorbed by Mantle bases.

**Where the paradigm leaked through any port:**
- **Transfer API (T4).** A port shaped like `IFluidHandler` (fill/drain + simulate flag) would have survived *for consumers*: Step 1 kept consumers on the legacy shape through NeoForge's new→legacy bridges. It could not survive for *providers*. The team explicitly refused a "legacy → new adapter with approximate transaction semantics", and the old shape is actively unsafe: the bridges open a root transaction, so code reachable from inside a native `insert` crashes ("Never open a root transaction inside a transaction" — `FluidContainerIngredient`). The contract itself gained a parameter: `IFluidContainerTransfer.transfer(…, TransactionContext)`. Semantics (side effects only on root commit, `CLOSING`, `VanillaContainerWrapper#setItem` firing during simulation, cached views after abort, `StackCopySlot` write-back) are invisible at any interface boundary. **Verdict: the old abstraction was the wrong shape (non-transactional).** The new one (transaction-threaded) also covers the NF 26.3 removal, which is already absorbed.
- **Two-phase rendering (T4).** Mantle `RenderingHelper.renderItem(PoseStack, MultiBufferSource, …)` *was* a port. It was deleted and split into `extractItem` + `submitItem`. `InventoryBlockEntityRenderer`→`InventoryRenderState`; `renderFaucetFluids` split the same way. The contract changed in *when* state is read and requires copies ("Render states hold copies only"). The casting fade had to edit resolved layers because wrapping a `MultiBufferSource` "is impossible now". 26.2 removes `MultiBufferSource` entirely. A port defined as extract/submit now would probably hold; the 1.21.1 shape could not.
- **Models (T1 and T2).** The Mantle loaders kept the *JSON* contract ("Model JSONs can stay as they are"), but the code contract was rewritten twice: item models in T1, `BakedModel` removed in T2 (dynamic models become block-state types). Then `BakedQuad` dropped `int[]` in T5. Model JSON is a stable port; Java model classes are not.
- **Recipes registry (T1).** Tinkers recipes must implement vanilla `Recipe` because `RecipeManager` consumes them, so `display()`/`placementInfo()` touched every recipe class (116 of 401 loadable files changed that release). Hiding this would mean a domain recipe plus a vanilla adapter per type, roughly doubling around 100 classes.
- **Hook removal and shared state (T4).** `verifyComponentsAfterLoad` gave every `ItemStack` copy its own tag as a side effect of a private constructor. No port can express a dependency the code didn't know it had. Only the copy-comparison gametests found it.
- **Deferred GUI and PiP (T3).** A draw port with an explicit color would have survived the `setShaderColor` removal (in 1.21.5 `blit` already ignored it). It would not have survived depth→`nextStratum`, first-wins tooltips drawn without the pose, or PiP absolute bounds.

**Pattern in the data:** absorption happened wherever Mantle's signatures used **its own vocabulary** (`Loadable`, `handleMouse(x, y, button)`, `NetworkWrapper`, `IEarlyReloadListener` callbacks, Tinkers' new `library.fluid.FluidAction` enum, `ResourceId`-based `MaterialId`/`ModifierId`). It failed wherever the signatures **exposed a vanilla type** (`ValueOutput`, `PoseStack`/`MultiBufferSource`, `IFluidHandler`, `GuiGraphics`, `RenderType`). Mantle classes mostly *extend* vanilla (`MantleBlockEntity`, `MultiModuleScreen`, the model loaders), so they are a shared base layer, not ports.

## 4. Mantle as an existing anti-corruption layer

**Reach.** 980 of 2011 Tinkers source files import Mantle (2517 imports). By package: `data.loadable` 988, `data.predicate` 215, `util` 144, `recipe.helper` 123, `client.screen` 96.

**Absorbed (Mantle changed internally, Tinkers callers unchanged):**
- the Loadable codec/ops work (T0/T1) and the `MapLoadable`/`SetLoadable` order fixes;
- `setId` overloads (T1);
- `preRemoveSideEffects` routing (T2);
- `Consumer` tooltip overloads (T2);
- `withCorrectedBounds`, `addItemLayer` identity and `NetworkWrapper.sendToServer` (T3);
- `IEarlyReloadListener` `SharedState`, the module/book input signatures, `FLUID_NO_FOG` on its own shader, `DatagenCacheKeeper` and `FluidContainerIngredient` made transaction-safe (T4);
- planned for T5: keep every `*ResourceLocation*` Mantle name (D-R2).

Mantle `data/loadable` churn over T1–T4 was only 9 files (+172/−19); `data/predicate` 2 files, `network` 3, `data/listener` 2. The churn concentrated in `client/model` (21 files, +1883/−1070), `client/screen` (20), `client/render` (10) and `recipe` (27).

**Propagated ("Mantle API changes for downstream" tables):**
- **T3:** 17 rows. 10 break downstream (BE save/load, inventory read/write, `FluidTexture`, `modifyFogRender`, `ElementModelData`, `shouldRenderOffScreen`, `addItemLayer`, `drawerTransform`, a removed field, `AppearanceData` color), 2 are deliberately unchanged, 5 are additive or behavioral fixes. The breaking ones are all rendering/GUI or serialization.
- **T4:** 27 rows. 16 break downstream (9 transfer/inventory, 4 two-phase rendering/`ItemOwner`, 2 input on `MultiModuleScreen`/`BookScreen`, `startOpen(ContainerUser)`), 2 change behavior, 5 are absorbed or internal, 4 are additive or deprecations. The `TransferHelper.getLegacy*` bridges were added and removed within M5.
- **T5 (planned):** the D-P permission constants become `PermissionCheck`, so 9 Tinkers subcommands follow.

**Domain-id insulation (T5).** 327 Tinkers files use `MaterialId`/`ModifierId`/`MaterialVariantId`; 176 of them never name `ResourceLocation`. `ResourceId` wraps the vanilla id by composition, so the rename skips those files.

## 5. Post-release regressions and hotfixes

| Hotfix | Bug | Root cause | Caught by | Abstraction or contract test? |
|---|---|---|---|---|
| 3.12.1 | First-boot staged tags: 2 modifiers + 3 station layouts dropped | d (T1 ops) | Reading the gametest log; guard `tag_ingredient_runtime_data_loads_on_first_boot` | Abstraction **yes**: these loaders bypassed Mantle's ops threading |
| 3.13.1 | Mob heads invisible since 1.21.4; legacy model log spam | c (T1 item models) | uitest `head_items` | Visual test only |
| 3.11.4 / 3.12.2 / 3.13.2 | `InventoryModule.writeStack` dropped `save`'s returned copy, losing modifier inventories | d (T0: save returns new) | `SaveCompatGameTests` | Round-trip contract test |
| 3.12.3 / 3.13.3 | `max_damage = -1`: built tools vanished from saves | d (T1) | Codec round-trip | Contract test |
| 3.11.5–3.13.3 | Castless casting found no recipe (empty-input short-circuit) | d (T1) | M4 runtime | Fix localized in `TinkerRecipeAccess` (port helped); the test caught it |
| 3.11.5–3.13.3 | `ThrownTool` read `"Trident"` instead of `"item"` | d/e (1.20.5) | Save round-trip | Contract test |
| 3.12.3 / 3.13.3 | Alloying/melting/severing types not synced; molding NPE | d (T1) | JEI uitest per category | Contract: "client-read types ⊆ synced types" |
| 3.12.3 / 3.13.3 | JEI fuel sort on an immutable list aborted categories | g | Reading the log | Plugin smoke test |
| 3.11.5–3.13.3 + 3.14.2 | Tool item/fluid/energy capabilities never registered | c (T0 caps) | Gametest `tool_item_capabilities` | Registration contract test |
| all four lines | Armor/arrow/shuriken copies shared the tag | d (hidden since T0) | Copy-comparison gametests | **No** abstraction; behavioral test only |
| 3.14.1 | Empty tank: `ValueInput#keySet` disconnects the client | d (T3) | BE safety net | Contract test |
| Mantle 1.12.1–1.15.1 | `MapLoadable`/`SetLoadable` order, compound `FluidIngredient`, `recipes/` folder | latent / e | Stream-codec round trip | Bugs lived *inside* the abstraction; the boundary test found them |

**Conclusion on regressions.** 9 of the 12 rows are category (d). None were caught by the compiler, unit tests or singleplayer. Every one was caught by a **boundary contract test** added later: save round-trip, stream-codec byte equality, the BE sync safety net, copy comparison, per-category JEI scenarios. Several bugs sat inside existing abstractions (Mantle loadables). The value came from the tests at the boundary, not from having a boundary. A port only helped where it localized the fix (`TinkerRecipeAccess`, Mantle ops threading).

## Bottom line (evidence only)

- **18% Y, 54% P, 29% N** across 84 breaks.
- Clear wins came where Mantle owned the vocabulary: serialization schema, input, lifecycle hooks, networking send, and domain ids.
- Clear losses came from paradigm shifts (transfer transactions, extract/submit rendering, model system rewrites, the vanilla `Recipe` contract) and from silent vanilla semantics (copy sharing, component invariants, atlas contents). Those were caught only by contract and visual tests.
- The most expensive categories (c, d, client-side) are the least absorbable.
- The cheapest category (a) dominates error counts but is already handled by scripts.
