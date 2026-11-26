# Making MC-version ports cheaper: what the modding ecosystem actually does (research, 2026-11-25)

Legend: **[V]** verified against the cited page; **[S]** comes only from a search-result snippet (the page itself wasn't readable); **[U]** unverified or my own inference.

## TL;DR
- No big content mod (JEI, AE2, Mekanism, Sodium, Fabric API, SlimeKnights) uses a single-source preprocessor or a domain/adapter layer to absorb MC-version changes. They all keep **one branch per MC version**, with multi-loader modules *inside* each branch, a stable public **API module**, and backports cherry-picked by hand.
- Loader abstractions (Xplat, MultiLoader-Template, Architectury, Balm) abstract the **loader**, not Minecraft. Common code compiles against vanilla, so vanilla renames and paradigm shifts still land in it. Balm itself broke its own API heavily at 1.21.11.
- Single-source tooling (Stonecutter, ReplayMod preprocessor, Manifold) is real and maintained. It is used mostly by small or medium, often Fabric-first or client-side mods, and it targets *multi-branch maintenance* rather than reducing port effort.
- Nothing found automates `ResourceLocation→Identifier`-style renames across MC versions for NeoForge. The generic tools (IntelliJ Migrate, OpenRewrite `ChangeType`, Stonecutter replacements) can do the class-level part.
- The cheapest documented insulation is **testing**: gametests, plus committed datagen output diffed as golden files. These catch the silent semantic changes that no architecture prevents.

## 1. Single-source multi-version tooling

**Stonecutter.** It moved from GitHub (kikugie) to Codeberg, current branch 0.10 [V] ([Codeberg](https://codeberg.org/stonecutter/stonecutter); [GitHub mirror notice](https://github.com/kikugie/stonecutter)).
- **How it works:** it is a Gradle pre-processor that "parses marked comments… disabling segments or replacing snippets" and "needs no compiler or IDE extensions". It handles Java, Kotlin and resource files [V].
- **Syntax:**
  - Conditions: `//? if >=1.21 <26.1`, with semantic version comparison and user constants such as the loader [V].
  - Replacements: `//~ if` rules, applied globally or per segment [V].
  - Swaps: `/*$ … */` [V].
- **Workflow:** `src/` holds only the *active* version, which is tracked in `.sc_active_version`. You must run `resetActiveProject` before committing, and `chiseledBuild` builds every target [V] ([Modstitch docs](https://isxander.github.io/modstitch-docs/guide/getting-started/setup), [rotgruengelb template](https://github.com/rotgruengelb/stonecutter-mod-template)).
- **Real use across the 1.21→26.x boundary:**
  - One template keeps a single tree for **1.21 to 26.3**. It switches to `loom-no-remap` at 26.1. Conditions don't work in `.json`/`.toml` files. First builds download every version and JDK [V] ([OddHero template](https://github.com/OddHero/minecraft-mod-template)).
  - MidnightLib handles the Identifier rename with a version-gated Stonecutter replacement, `direction = eval(current.version, ">=1.21.11-rc2")` [S] ([MidnightLib compare](https://git.midnightdust.eu/Motschen/MidnightLib/compare/312096d9899d3fd73a8b83018af85081a108d1bf..multiversion)).
- **Adoption:** isXander's Zoomify (Controlify is a property-gated dependency there) [S] ([gist](https://gist.github.com/isXander/e8f4bf54942663ea05f652a7ad6a09cd)), KikuGie's Elytra Trims [S], many templates, and Stonecraft (Stonecutter plus Architectury) [V] ([Stonecraft](https://github.com/meza/Stonecraft)). No large content mod was found using it [U].
- **Docs access:** the official docs site (stonecutter.kikugie.dev) serves anti-scraper filler to automated fetchers, so it was not read.

**ReplayMod preprocessor** [V] ([repo](https://github.com/ReplayMod/preprocessor)).
- **Syntax:** `//#if MC>=11200` blocks, with `//$$` marking commented-out alternative code.
- **Layout:** `src/main` is the "core" version, and other versions are derived from it. Whole files can be overridden per version under `versions/<mc>/src`. `@Pattern`-annotated methods give type-aware search and replace.
- **Limits:** patterns must be single-line, and switching the core version can leave noise in the working tree.

**Essential toolkit** (architectury-loom plus the preprocessor).
- **Model:** versions form a chain of nodes, and the toolkit notes links are "only possible between versions that share… intermediary mappings" [V] ([toolkit](https://github.com/EssentialGG/essential-gradle-toolkit)).
- **Essential itself:** builds from main version 1.12.2 out to 1.21.11. Building any version requires all intermediate versions to be set up [V] ([Essential](https://github.com/EssentialGG/Essential)).
- **Caveat:** Fabric is ending Yarn and Intermediary after 1.21.11 [S] ([Fabric 1.21.11 mappings docs](https://docs.fabricmc.net/1.21.11/develop/porting/mappings/)). Mapping-linked chains may therefore get harder past 26.1 [U].

**Manifold preprocessor.**
- **Use:** Joban Client Mod uses `#if MC_VERSION == "11904"` and `LOADER` [V] ([JCM docs](https://jcm.joban.org/v2/contributing/project/multiversion_development/)).
- **Counter-evidence:** the same page says that from MTR 4.1 the mod "no longer target multiple Minecraft versions", so the tooling would "likely be deprecated" [V]. That is a real project walking away from single-source.

**What it solves:** keeping N MC versions alive from one tree, so a hotfix is written once.
- **Adoption cost for ~2,000+ files:** high. Every divergent line becomes a conditional, and paradigm shifts (transfer API, extract/submit rendering, ValueInput/Output) produce large dual blocks or whole-file overrides [U].
- **Runtime cost:** zero, since it is compile-time only.

## 2. Loader-abstraction patterns: they don't address MC versions

- **Botania Xplat:**
  - Shared code lives in `Xplat`, whose sources are copied into the Forge and Fabric projects at build time [S] ([Botania docs](https://docsearch.algolia.com/mcp/docs/repo/vazkiimods/botania)).
  - Loader services go behind `IXplatAbstractions`, which was later renamed `XplatAbstractions` [S] ([gist](https://gist.github.com/artemisSystem/c8b390958e0a439b24484c561ca28cc4)).
  - The Botania NeoForge 1.21.1 port that exists is unofficial ("Botania Revived") [S].
- **MultiLoader-Template:** `common` "compiles against vanilla" and "has no knowledge or access to ModLoader specific code" [V] ([template](https://github.com/jaredlll08/MultiLoader-Template)). The common code is therefore exactly as exposed to vanilla churn as single-loader code.
- **Architectury `@ExpectPlatform`:** works only on static methods, and is replaced per platform at build time [S] ([docs](https://docs.architectury.dev/plugin/platform_specific/), [turou guide](https://turou.gitbook.io/arch/architectury-plugin/expectplatform)).
- **Balm** is the strongest counter-evidence. Its own 1.21.11 migration includes:
  - package moves and renamed accessors;
  - its event bus removed;
  - `BalmBlockEntity` and `DeferredObject` removed;
  - a feature that "has not yet been updated to Minecraft's rendering changes" [V] ([Balm 1.21.11 migration](https://balm.twelveiterations.com/migration/1.21.11)).

  The abstraction layer passes vanilla churn on to its users and adds its own.
- **SlimeKnights** (upstream Tinkers): supporting multiple loaders "requires significant additional work which we lack time for" [V] ([FAQ](https://slimeknights.github.io/faq)).
- **Maintainer quotes:** I found **no** maintainer statement that a multi-loader structure reduced MC-version port cost [U: absence of evidence, not proof].

## 3. How large long-lived mods survive updates

| Project | Structure | Evidence |
|---|---|---|
| **JEI** | Default branch named `26.3`. Supports 26.3, 26.1.2, 1.21.11, 1.21.1, 1.20.1, 1.19.2 and 1.12.2. Multi-loader modules (Common/Gui/Library/Fabric/NeoForge) live inside each branch. | [V] [repo](https://github.com/mezz/JustEnoughItems); [S] CommonApi has no deps ([DeepWiki](https://deepwiki.com/mezz/JustEnoughItems/2-build-system)) |
| **Fabric API** | "Strong backwards compatibility guarantees". Module names suffixed `-vN`. Breaks only for "code directly affected by the Minecraft version update". Maintainers cherry-pick backports. Supports latest stable plus snapshot. | [V] [CONTRIBUTING](https://github.com/FabricMC/fabric-api/blob/HEAD/CONTRIBUTING.md) |
| **Sodium** | `common/fabric/neoforge`, `dev` branch, separate 0.8 backport line for 1.21.1 | [V] [repo](https://github.com/CaffeineMC/sodium); [S] [CurseForge](https://www.curseforge.com/minecraft/mc-mods/sodium/files/8382328) |
| **AE2** | Single `main` branch per active version. The API ships as a separate semver artifact. The Forge 1.20.1 line is effectively EOL (fork's claim). | [V] [repo](https://github.com/AppliedEnergistics/Applied-Energistics-2); [S] [AE2 UELM](https://www.curseforge.com/minecraft/mc-mods/ae2-uelm) |
| **Mekanism** | Branch per version ("renamed the 1.20.5 branch to 1.20.6") | [S] [Modrinth 10.6.0](https://modrinth.com/mod/mekanism/version/10.6.0.44) |
| **SlimeKnights** | 1.20.1 is "long-term support" with bugfixes only. No feature backports. Mantle is ported before Tinkers. Publishes an addon "update primer" per version. | [V] [FAQ](https://slimeknights.github.io/faq), [primer](https://slimeknights.github.io/posts/2024/12/31/1.20-update-primer) |
| **Create, Iris, Lithium** | Lithium/Sodium are multi-loader. Nothing found on Create's porting process. | [U] |

The ecosystem's answer is **branch per version, a small stable API surface, and limited backport scope**.

NeoForge's own transfer rework shows the limits of an adapter across a paradigm change [V] ([post](https://neoforged.net/news/21.9-transfer-rework/)):
- `IItemHandler.of(handler)` wraps new→old, but "it is not possible to provide a wrapper that turns any IItemHandler into an ResourceHandler".
- The old types are deprecated for removal "in… 21.10 or later".

## 4. Architecture literature vs Minecraft reality

**The literature:**
- **ACL (Evans):** a translation layer so that "dependencies on outside subsystems don't limit an application's design" [V] ([Azure ACL](https://learn.microsoft.com/en-us/azure/architecture/patterns/anti-corruption-layer)).
  - Listed costs: extra latency, plus "an extra service that you must manage and maintain".
  - Listed as possibly unsuitable when the systems have "no significant semantic differences".
- **Ports & adapters (Cockburn):** an app "equally… driven by users, programs, automated test", testable apart from devices [V] ([Cockburn](https://alistair.cockburn.us/hexagonal-architecture/)).
- **Clean Architecture:** "the Web is a detail… the database is a detail" [S] ([2012 post](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)). One critique names the cost: "No idiomatic framework usage" [S] ([DZone](https://dzone.com/articles/clean-architecture-is-screaming)).
- **Spolsky:** "All non-trivial abstractions, to some degree, are leaky" [V] ([Joel](https://www.joelonsoftware.com/2002/11/11/the-law-of-leaky-abstractions/)).

**Minecraft-specific obstacles.** The churning surface *is* the integration surface:
- **Rendering:** `GuiGraphics`→`GuiGraphicsExtractor` and `Screen#render`→`extractRenderState` in 26.1 [V] ([NF 26.1](https://neoforged.net/news/26.1release/)). Render types moved to `RenderTypes` and the `BakedQuad` layout changed in 1.21.11 [V] ([NF 21.11](https://neoforged.net/news/21.11release/)).
- **Data:** `ItemStackTemplate`/`FluidStackTemplate` in data files in 26.1 [V]. Codecs, StreamCodecs, data components and registry `Holder`s are vanilla types that serialization must use directly [U].
- **Mixins and datagen:** mixins target vanilla internals by name, and datagen providers subclass vanilla providers. Neither can sit behind an adapter [U].
- **Binary compatibility:** "no existing mods for 1.21.11 or older… will work on 26.1, even as a compile-only dependency" [S] ([Fabric porting](https://docs.fabricmc.net/develop/porting/)).
- **Hot paths:** Lithium ships a whole `alloc.*` mixin category (for example `alloc.blockstate`, `alloc.enum_values`) to remove allocations in vanilla code [S] ([Lithium changelog](https://www.modrinth.com/mod/lithium/changelog)). That suggests wrapper objects per tick or per frame have a real cost. JIT escape analysis may remove some of it [U].

**Where an ACL *does* pay off** [U, inference]: domain logic that is already mostly pure. For Tinkers that means modifier math, tool stat computation, material/recipe rules and JSON loaders behind Mantle's own types. The rule: wrap only what you own semantically, never rendering, registries or networking.

## 5. Testing as insulation

- **NeoForge GameTest:**
  - Test instances, environments and registered functions, all generatable through datagen [V] ([NF docs](https://docs.neoforged.net/docs/misc/gametest)).
  - The system was "completely overhauled into a registry based system" in 1.21.5 [V] ([primer 1.21.5](https://docs.neoforged.net/primer/docs/1.21.5)), so the test harness itself churns.
- **Fabric:** documents three layers [V] ([Fabric testing](https://docs.fabricmc.net/1.21.11/develop/automatic-testing)):
  - JUnit with `Bootstrap.bootStrap()`;
  - server gametests;
  - client gametests (`runClientGameTest`, `takeScreenshot`). No golden-image comparison is documented.
- **Datagen golden files:** Balm's template runs a CI "verify" job that errors if `runData` wasn't re-run [S] ([Balm datagen](https://balm.twelveiterations.com/getting-started/datagen)). The standard pattern is regenerate, then `git diff --exit-code`.
- **Applied to a port** [U]: diffing committed `src/generated` before and after a port is a cheap semantic-regression oracle for recipes, loot, tags and models. It catches silent codec or default changes.

## 6. Mechanical-rename tooling

- **NeoForge primers** (ChampionAsh5357, CC-BY 4.0): a "high level, non-exhaustive overview", with no automation [V] ([index](https://docs.neoforged.net/primer/docs/)). NeoForge calls the Identifier rename "fairly mechanical", with "find-and-replace" for most of it [V] ([NF 21.11](https://neoforged.net/news/21.11release/)).
- **Loom `migrateMappings` / Ravel** (IntelliJ plugin):
  - They remap sources between *mapping sets* (Yarn→Mojang), including Mixins, Class Tweakers and Access Wideners, and flag problems with `TODO(Ravel)` [V] ([Ravel docs](https://docs.fabricmc.net/1.21.11/develop/porting/mappings/ravel)).
  - Neither is documented for porting across MC versions. Pivoting 1.21.10-mojmap → intermediary (`class_2960` is stable) → 1.21.11-mojmap might work for ≤1.21.11 [U]. Intermediary ends after 1.21.11.
- **IntelliJ "Migrate Packages and Classes":** custom class/package migration maps, scoped runs, shareable XML [V] ([JetBrains](https://jetbrains.com/help/idea/migrate.html)). It handles class renames, not method renames.
- **OpenRewrite:** no Minecraft recipe found [S] ([search](https://central.sonatype.com/artifact/org.mockbukkit.rewrite/openrewrite-recipes) found only MockBukkit). The generic `ChangeType`/`ChangeMethodName` recipes could encode a primer's rename list as YAML [U].
- **Linkie / mapping-io:** both are mapping lookup or manipulation tools [S] ([linkie-core](https://github.com/linkie/linkie-core), [mapping-io](https://github.com/FabricMC/mapping-io)). No version-to-version diff feature was found. With unobfuscated jars from 26.1 onward, a jar-to-jar API diff (classes and members) would be the natural generator for a rename map [U].

## Comparison

| Approach | Solves | Adoption cost (~2k files) | Runtime cost | Who uses it | Key caveat |
|---|---|---|---|---|---|
| Branch per MC version + cherry-pick | multi-branch maintenance (manually) | none (status quo) | none | JEI, Fabric API, Mekanism, AE2, SlimeKnights | hotfix ×N; mitigate with small backport scope |
| Stonecutter | multi-branch maintenance | high: conditionals everywhere, 1 active version in IDE | none | small/medium mods, templates | paradigm shifts → huge dual blocks; no JSON conditions |
| ReplayMod preproc / Essential / Manifold | same | high | none | ReplayMod, Essential, JCM (dropping it) | mapping-linked chains; core-version switching noise |
| Xplat / MultiLoader / Architectury / Balm | **loader** only | medium–high | ~none (static dispatch / ServiceLoader) | Botania, many multiloader mods | common still compiles against vanilla; layer itself churns (Balm 1.21.11) |
| Stable public API module | downstream breakage | low–medium | none | JEI, AE2, Fabric API | doesn't reduce *your* port cost, only addons' |
| ACL / hexagonal for domain logic | MC-version churn in pure logic | high to retrofit | allocation/indirection in hot paths | no MC mod found [U] | can't wrap rendering, codecs, registries, mixins, datagen |
| Gametests + datagen golden diff | silent semantic changes | low–medium, incremental | none (test-time) | NeoForge/Fabric devs, Balm template | harness itself churns (1.21.5 overhaul) |
| Rename tooling (IntelliJ Migrate, OpenRewrite, Ravel) | mechanical renames | low per port | none | Fabric (Ravel for mappings) | no MC cross-version recipes exist; method renames need OpenRewrite/scripting |
