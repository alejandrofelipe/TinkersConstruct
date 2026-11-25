# M6+ dependency & breaking-change analysis — New Tinkers (Mantle + TinkersConstruct, NeoForge)

> Pesquisa antecipada, somente leitura (web + metadados Maven + grep nos repos). Feita em **2026-11-25**.
> Nenhum arquivo dos repos foi alterado e o Gradle não foi executado. Fontes: `[Sx]`, listadas no fim.
> Base de comparação: M5 = MC 1.21.10 / NF 21.10.64, JEI 26.3.0.31, CT 21.10.1+1.21.10, Balm 21.10.11+1.21.10,
> Parchment 1.21.10/2025.10.12, JSON Things 0.16.0, IE excluído, NeoGradle 7.1.39 + Gradle 9.2.1 + Java 21.

## 0. Versões do Minecraft que existem hoje (manifest da Mojang [S1])

| MC | Tipo | Lançamento | Java (`javaVersion`) | `client_mappings` no JSON da versão |
|----|------|-----------|----------------------|-------------------------------|
| 1.21.10 | release | 2025-10-07 | 21 (`java-runtime-delta`) | sim (ofuscado) |
| **1.21.11** | release | 2025-12-09 | **21** | sim (ofuscado, o último) |
| **26.1** | release | 2026-03-24 | **25** (`java-runtime-epsilon`) | **NÃO** (sem ofuscação) |
| **26.1.1** | release | 2026-04-01 | 25 | NÃO |
| **26.1.2** | release | 2026-04-09 | 25 | NÃO |
| **26.2** | release | 2026-06-16 | 25 | NÃO |
| 26.3 | release | 2026-09-15 | 25 | NÃO |
| 26.4 | **ainda não saiu** (`26.4-pre-1` em 2026-11-24) | — | — | — |

- O alvo **26.2 já saiu** (há 5 meses). `latest.release` = **26.3**. O 26.4 está em pre-release.
- Do 26.1 em diante o jar vem **sem ofuscação**: o JSON da versão não tem mais `client_mappings`. Os nomes oficiais da
  Mojang, incluindo nomes de parâmetros, vêm no próprio jar ([S1], [S5], [S6]).

## (a) Matriz de disponibilidade

Legenda: ✅ = existe build estável/utilizável; ⚠️ = só beta/parcial; ❌ = não existe.

| MC | NeoForge (estável? / último) | JEI (`jei-<mc>-neoforge`) | Crafting Tweaks / Balm | JSON Things | Immersive Eng. | Parchment | Toolchain (NG / MDG / Gradle) | Java |
|----|------------------------------|---------------------------|------------------------|-------------|----------------|-----------|-------------------------------|------|
| **1.21.11** | ✅ estável desde **21.11.42** (2026-03-28); estáveis: 21.11.42/44/45/48; **último 21.11.48** (2026-10-15); 46 builds, 1ª beta 2025-12-09 [S2] | ✅ 102 builds, `27.0.0.1` → **`27.55.0.122`** (2026-11-22) [S3] | ✅ CT **21.11.5** (2026-03-13; POM exige Balm ≥ 21.11.2) / Balm **21.11.9.1**. Os sufixos `+1.21.x` saíram do esquema de versão [S4] | ✅ **0.17.0** [S7] | ❌ (último `1.21.1-12.4.3-pre.15`; metadata parada desde 2025-08-18) [S8] | ✅ **2025.12.20** (o último Parchment que existe) [S9] | NG **7.1.39** + Gradle **9.2.1** (MDK oficial `MDK-1.21.11-NeoGradle`) [S10] | 21 |
| 26.1 | ⚠️ **só beta/alpha** (`26.1.0.x-beta`) [S2] | ✅ 5 builds `29.2.0.17`→`29.2.0.21` [S3] | CT 26.1.0.1 / Balm 26.1.0.1–.7 [S4] | ✅ 0.18.0 [S7] | ❌ | ❌ (desnecessário) [S9][S5] | NG 7.1.21+ / MDG 2.0.141+ (post do release) [S5]; Gradle ≥ **9.1.0** | **25** |
| 26.1.1 | ⚠️ **só beta** (`26.1.1.x-beta`) [S2] | ✅ 2 builds (`29.3.0.22`, `29.4.0.23`) [S3] | Balm 26.1.1.1–.2; nenhum CT específico [S4] | ❌ (sem diretório) [S7] | ❌ | ❌ | idem | 25 |
| **26.1.2** | ✅ estável desde **26.1.2.71** (2026-06-02); **último 26.1.2.130** (2026-11-24, ainda ativo) [S2] | ✅ 87 builds `29.5.0.24` → **`29.54.0.123`** (2026-11-22) [S3] | ✅ CT **26.1.2.4** (2026-11-03; POM → Balm 26.1.1.2) / Balm **26.1.2.15** (POM → **`kuma-api-neoforge:26.1.2.2`**, dependência transitiva nova) [S4] | ✅ **0.18.5** (0.18.1–0.18.5) [S7] | ❌ | ❌ | NG **7.1.39** + Gradle 9.2.1 + toolchain 25 (MDK oficial `MDK-26.1.2-NeoGradle`) [S10] | **25** |
| **26.2** | ✅ estável desde **26.2.0.57** (2026-08-09); **último 26.2.0.95** (2026-11-17); 1ª beta 2026-06-16 [S2] | ✅ 192 builds `30.0.0.1` (2026-06-19) → **`30.48.0.244`** (2026-10-28; sem build nova há ~4 semanas) [S3] | ✅ CT **26.2.0.4** (POM → Balm 26.2.0.1) / Balm **26.2.0.9** (POM → `kuma-api-neoforge:26.2.0.1`) [S4] | ✅ **0.19.0** (2026-08-06) [S7] | ❌ | ❌ | NG **7.1.39** + Gradle 9.2.1 (MDK oficial `MDK-26.2-NeoGradle`); MDG 2.0.148 [S10][S11] | **25** |
| 26.3 *(além do alvo)* | ✅ estável desde **26.3.0.76** (2026-10-11); último 26.3.0.118 (2026-11-24) [S2] | ✅ 104 builds → `31.23.0.130` [S3] | ✅ CT 26.3.0.2 / Balm 26.3.0.3 [S4] | ❌ (sem diretório) [S7] | ❌ | ❌ | NG 7.1.39 (MDK `MDK-26.3-NeoGradle`) [S10] | 25 |

Notas da matriz:
- **Esquema de versão do NeoForge 26.x:** são quatro componentes, `<mc major>.<mc minor>.<mc patch>.<build>`
  (ex.: `26.2.0.95`, `26.1.2.130`). O sufixo `-beta` indica que ainda pode haver breaking changes. As versões NeoForm
  passam a ser `<mc>-<build>` [S5]. Quanto ao FancyModLoader: FML **10.x** no 1.21.10/1.21.11, **11.x** no 26.1/26.2
  e **12.x** no 26.3 (POMs [S2]). Nosso `loader_range=[4,)` continua válido.
- **JEI não pulou nenhuma versão** de 1.21.11 a 26.3 (no 1.21.6 e no 1.21.9 tinha pulado). As linhas 1.21.11 e 26.1.2
  continuam recebendo builds (2026-11-22), já a de 26.2 parou em 2026-10-28.
- **Crafting Tweaks / Balm:** o artifactId continua o mesmo (`*-neoforge`). Do 21.11 em diante a versão vem sem o
  sufixo `+mc`. O Balm 26.x puxa uma lib nova, **`kuma-api-neoforge`**, do mesmo repo twelveiterations, que precisa
  resolver em runtime.
- **Parchment não existe para nenhuma versão 26.x** (só `parchment-1.21.x/` no Maven). O NeoForge manda remover:
  *"Parchment is no longer needed"*. Só vale manter para quem quiser os javadocs extras [S5][S6].

## (b) Agrupamento de milestones proposto (regra de folding do roadmap)

Regra do roadmap: um milestone cai numa versão MC que tenha **NeoForge estável + JEI**. Versões em que o NeoForge
nunca saiu do beta, ou que o JEI pulou, são *folded* no milestone seguinte.

| MC | NF estável? | JEI? | Resultado da regra |
|----|-------------|------|--------------------|
| 1.21.11 | ✅ | ✅ | **milestone próprio (M6)** |
| 26.1 | ❌ beta | ✅ | fold → 26.1.2 |
| 26.1.1 | ❌ beta | ✅ | fold → 26.1.2 |
| 26.1.2 | ✅ | ✅ | **pela regra, é milestone próprio (M7)** |
| 26.2 | ✅ | ✅ | **milestone próprio (M8) 🏁 alvo** |

**Proposta (recomendada): M6 = 1.21.11 · M7 = 26.1.2 (26.1 + 26.1.1 folded) · M8 = 26.2 🏁**

A linha M7 do roadmap hoje diz *"26.2 / folds 26.1"*. Aplicada à risca, a regra **não** permite foldar o 26.1.2, porque
ele tem NF estável (26.1.2.71+) e JEI (87 builds). Também há um argumento prático para o split: o 26.1 é o maior salto
de toda a série. Ele traz Java 25, o fim da ofuscação, `ItemStackTemplate`, o `FluidStack` que passa a exigir
registries e o `GuiGraphicsExtractor`, e o primer tem ~307 mil caracteres. O 26.2 vem por cima disso com o rewrite de
renderização (`MultiBufferSource` removido) e a reorganização de advancements. Com um ponto de parada verde no 26.1.2
dá para ter uma bateria verde (e tags) entre os dois saltos. A alternativa é **M7 = 26.2 direto**, como está no
roadmap: um milestone a menos, mas o salto 1.21.11 → 26.2 acumula todas as quebras do 26.1 e do 26.2 de uma vez.
Fica como decisão do usuário.

### Pins sugeridos por milestone

| Pin | **M6 — 1.21.11** | **M7 — 26.1.2** | **M8 — 26.2 🏁** |
|-----|------------------|-----------------|------------------|
| `minecraft_version` / `minecraft_range` | `1.21.11` / `[1.21.11,1.21.12)` | `26.1.2` / `[26.1.2,26.2)` | `26.2` / `[26.2,26.3)` |
| `neoforge_version` | **21.11.48** | **26.1.2.130** | **26.2.0.95** |
| `jei_version` | **27.55.0.122** | **29.54.0.123** | **30.48.0.244** |
| `craftingtweaks_version` | 21.11.5 | 26.1.2.4 | 26.2.0.4 |
| `balm_version` | 21.11.9.1 | 26.1.2.15 (+ `kuma-api-neoforge` transitiva) | 26.2.0.9 (+ `kuma-api-neoforge` 26.2.x) |
| `jsonthings_version` | 0.17.0 | 0.18.5 | 0.19.0 |
| Immersive Engineering | excluído | excluído | excluído |
| Parchment | `1.21.11` / `2025.12.20` | **remover o bloco `parchment {}`** (Mantle + Tinkers) | — |
| NeoGradle / Gradle | 7.1.39 / 9.2.1 (sem mudança) | 7.1.39 / 9.2.1 (sem mudança) | 7.1.39 / 9.2.1 |
| Java (`JAVA_HOME` + `languageVersion`) | 21 | **25** (instalar `temurin25-jdk` via scoop; hoje só existem 17 e 21) | 25 |
| FML (`loader_range`) | `[4,)` ok (FML 10) | ok (FML 11) | ok (FML 11) |

## (c) Principais breaking changes por milestone, com contagem estimada no nosso código

Contagens obtidas por regex sobre `repo/src` (Mantle, 606 `.java`) e `tinkers/src` (1991 `.java`), no formato
**arquivos/ocorrências**. São estimativas: os regex pegam algumas coisas a mais (ex.: `.location()`).

### M6 — 1.21.11 (NF 21.11) — fontes: post do release [S12], primer 1.21.11 [S13]

| Mudança | Mantle | Tinkers | Observação |
|---------|--------|---------|------------|
| **`ResourceLocation` → `Identifier`** (confirmado: *"`ResourceLocation` was renamed to `Identifier`"* [S12]). Junto vêm `ResourceLocationException`→`IdentifierException`, `ResourceLocationArgument`→`IdentifierArgument` etc. | **174 / 766** | **511 / 2078** | Mecânico e pervasivo. Dá para fazer com find/replace de token + imports. JSON/data não muda |
| `ResourceKey#location()` → `#identifier()` | ~30 / 47 | ~78 / 129 | o regex pega alguns falsos positivos |
| `FriendlyByteBuf#read/writeResourceLocation` → `read/writeIdentifier` | 3 / 5 | 16 / 42 | pacotes de rede |
| **`net.minecraft.MethodsReturnNonnullByDefault` / `FieldsAreNonnullByDefault` removidos** (→ JSpecify `@NullMarked`) | **78** package-info | **278** package-info | **quebra de compilação garantida**, mas mecânica (79 + 279 `package-info.java`). O MC 1.21.11 passa a trazer `org.jspecify:jspecify:1.0.0` [S1] |
| `javax.annotation.Nullable/Nonnull` → `org.jspecify.annotations.Nullable` (recomendado; é anotação type-use, então `Map.@Nullable Entry`) | 237 / 243 | 909 / 934 | **Risco:** confirmar na Fase 0 se o jsr305 continua no classpath do NF 21.11. Ele não aparece nas libs do MC nem no POM do NF. Se sumiu, são ~1150 arquivos (mecânico) |
| `Util`/`FileUtil`/`BlockUtil` → pacote `net.minecraft.util` | 7 | 12 | imports |
| `advancements.critereon` → `advancements.criterion` | 2 / 3 | 9 / 27 | o pacote muda de novo no 26.2 |
| `RenderType.xxx()` vanilla → `RenderTypes`; `RenderStateShard` → `RenderSetup` (não é 1:1) | RenderType 7 / 46 (shard 2) | 15 / 69 (shard 1) | render types custom de fluido/tanque. **Médio** |
| `BakedQuad` sem `int[]` (campos `position*`/`packedUV*`, `bakedNormals`/`bakedColors` do NF) | 6 / 32 | 24 / 64 | modelos de ferramenta e de fluido. **Médio-alto** |
| Game rules viram registry (`GameRules#getBoolean` → `get`, `RULE_*` renomeados) | 3 / 11 | 2 / 4 | baixo |
| Permissões: `hasPermissions(int)` → `PermissionCheck` | 0 | 2 | baixo |
| GUI: `Button` vira abstrato (`Button$Plain`), `AbstractButton#renderWidget` vira final (→ `renderContents`), `Screen#init(Minecraft,int,int)` → `init(int,int)` | extends Button 1, renderWidget 1, init 2 | 4, 2, 0 | baixo |
| `FMLLoader.isProduction()` removido (segundo o roadmap; o post do release não fala disso) | 0 | 2 | baixo |
| Transforms aceitam `Vector3fc`/`*fc` read-only; linhas `LINE_STRIP` removidas | — | — | pontual em renderização |
| Modelos JSON: `neoforge_data.block_light/sky_light` → `light_emission` | conferir em assets | conferir em assets | grep nos JSON de modelo |

**Estimativa M6:** 🟠 volume grande (≈700 arquivos de rename), mas quase tudo mecânico. A parte que exige julgamento
fica em BakedQuad + RenderTypes (≈30–40 arquivos).

### M7 — 26.1.2 (26.1 + 26.1.1 folded) — fontes: post 26.1 [S5], post de snapshots [S6], primer 26.1 [S14]

| Mudança | Mantle | Tinkers | Observação |
|---------|--------|---------|------------|
| **Toolchain:** Java 25, Gradle ≥ 9.1 (já estamos no 9.2.1), sem ofuscação, **sem Parchment**, NF com 4 componentes | 2 `build.gradle` + `gradle.properties` | idem | o NG 7.1.39 já suporta (MDK oficial). ATs (9 + 111 entradas) e mixins (3, sem refmap) continuam com nomes Mojang. Revisar os membros que foram renomeados |
| **`ItemStack` → `ItemStackTemplate`** em recipes, advancements, loot e `DisplayInfo`; `ItemStack` exige registries carregados; removidos `ItemStack.SINGLE_ITEM_CODEC/STRICT_CODEC/SIMPLE_ITEM_CODEC` | codecs de ItemStack 4 / 9; `new ItemStack(` 12 / 41 | 10 / 24; 102 / 232 | **Alto.** O `ItemStack` estático/de datagen quebra em runtime, não na compilação |
| **`FluidStack`/`FluidResource` exigem registries** + `FluidStackTemplate` nos data files | FluidStack **20 / 146** | **188 / 942** | **O maior risco do Tinkers** (recipes de fundição/casting, codecs, constantes) |
| **`RecipeSerializer` vira record (`MapCodec` + `StreamCodec`)**; classes `$Serializer` removidas; `RecipeSerializer#register` removido; `Recipe#assemble` sem `HolderLookup.Provider` | 11 / 44; assemble 7 / 8 | **68 / 231**; assemble 30 / 46 | **Alto.** Toca todos os loaders de receita do Mantle e do Tinkers |
| Loot: `*Type` removidos (`LootItemFunctionType`, `LootItemConditionType`…), `CODEC` → `MAP_CODEC`, `getType` → `codec`; `IntProvider`/`FloatProvider` viram interfaces | 8 / 27 (GLM 4 / 24) | 16 / 45 (GLM 5 / 22; providers 2 / 5) | médio |
| **`GuiGraphics` → `GuiGraphicsExtractor`**; `render` → `extractRenderState`; `renderBg`/`renderBackground` → `extractBackground`; `renderLabels` → `extractLabels`; `drawString` → `text`; `renderItem` → `item` | **29 / 108**; overrides 4; bg/labels 5 / 10; drawString 5 / 12 | **52 / 177**; 13; 15 / 32; 12 / 13 | **Alto** no Mantle (book/screens) e nas estações do Tinkers. Uitest obrigatório |
| Renderização: `ItemBlockRenderTypes` removido (layer por quad), `BlockRenderDispatcher`/`ItemRenderer` removidos, `Material` (sprite) → `SpriteId`, `LightTexture` → `Lightmap`, `entityCutoutNoCull` → `entityCutout`, `LiquidBlockRenderer` → `FluidRenderer` | ItemBlockRenderTypes 1; Material 9; LightTexture 1 | 1; 34; 2 | médio-alto |
| Menus: `ClickType` → `ContainerInput` | 1 / 3 | 1 / 3 | baixo |
| `ChunkPos(BlockPos/long)` → `containing`/`unpack`, `asLong` → `pack` | 3 / 10 | 4 / 15 | baixo |
| `Level#random` vira protected (→ `getRandom()`) | 2 / 3 | 21 / 35 | baixo, mecânico |
| Tempo do mundo → clocks (`getDayTime` → `getOverworldClockTime`); `DimensionDataStorage` → `SavedDataStorage`; `SavedDataType` recebe `Identifier` | 1; 1 | 0; 2 | baixo |
| DataComponents passam a ser holder-wrapped (`DAMAGE_TYPE`, `PROVIDES_TRIM_MATERIAL`, `INSTRUMENT`…); `DyeItem` sem `DyeColor` | 0 | 5 / 6; Dye* 10 / 66 | médio no Tinkers (modifiers de dye/trim) |
| Entidades: `EntityType#is` → `TypedInstance#is`; `Entity#getTags` → `entityTags`; `interactAt` removido | 1 | — | baixo |
| **Transfer legado** (`IItemHandler`, `IFluidHandler`, `ItemStackHandler`, `FluidTank`, `FluidUtil`): **continuam existindo no 26.1 e no 26.2** (só marcados deprecated/forRemoval) | IItemHandler 6 / 18; IFluidHandler 11 / 62; FluidUtil 1 | 29 / 86; 79 / 179; FluidTank 11 / 41; ItemStackHandler 5 / 13; FluidUtil 3 | Só warnings até o 26.2. **Removidos no branch 26.3.x** (conferido nos arquivos do GitHub do NeoForge [S15]). `ResourceHandler` já aparece em 4 / 26 (Mantle) e 38 / 121 (Tinkers), migração parcial do M5 |

**Estimativa M7:** 🔴 **o maior milestone da série.** O volume é menor que o rename do M6, mas a semântica é
profunda: templates vs stacks, registries obrigatórios, serializers de receita e a renomeação inteira da GUI.

### M8 — 26.2 🏁 — fonte: primer 26.2 [S16]

| Mudança | Mantle | Tinkers | Observação |
|---------|--------|---------|------------|
| **`MultiBufferSource` removido** (o substituto mais próximo é `PreparedRenderType`); `Tesselator` removido; `Font#drawInBatch` removido (→ `Font#prepareText`); `RenderType#draw` → `PreparedRenderType#drawFromBuffer`; `VertexFormat$Mode` → `PrimitiveTopology`; **depth test invertido** (`LESS_THAN_OR_EQUAL` → `GREATER_THAN_OR_EQUAL`) | MultiBufferSource 3 / 8; VertexFormat.Mode 1 / 2; RenderSystem 4 / 8 | 7 / 14; 0; 3 / 6 | **Alto** nos BERs (smeltery/tanque/casting): BlockEntityRenderer 2 / 6 (Mantle), 8 / 24 (Tinkers). Uitest visual obrigatório |
| `Minecraft#screen`/`setScreen` → `Gui#screen()`/`setScreen`; `Minecraft#getMainRenderTarget` → `GameRenderer#mainRenderTarget` | 3 / 3 | 5 / 10 | baixo |
| `ChatFormatting#getColor`/`CODEC`/`getByName` → `TextColor` | ChatFormatting 15 / 40 | 41 / 133 | a maioria dos usos (`ChatFormatting.RED` em estilos) deve continuar funcionando. Só os métodos removidos quebram. Baixo-médio |
| **Advancements:** `CriteriaTriggers`/`CriterionTrigger` → `advancements.triggers.*`; predicados → `advancements.predicates.*`; campos de `EntityPredicate` viram mapa de `EntitySubPredicate`; `EntitySubPredicate#CODEC` → `EntityPredicate#MAP_CODEC`; `ENTITY_SUB_PREDICATE_TYPE` vira `Codec` | EntityPredicate 1; triggers 1 | 6 / 19; 11 / 26 | **Médio.** O provider dos 42 advancements do Tinkers (datagen) e o bridge `tconstruct:tool` precisam ser reescritos |
| Registries: constantes de `EntityType` → `EntityTypes` (`byString` removido); constantes de `BlockEntityType` → `BlockEntityTypes`; `ResourceKey`s de fluido → `FluidIds` | EntityType.X 5 / 8; BET 2 / 2 | **25 / 141**; 11 / 14 | mecânico |
| Dano/combate: `LivingEntity#knockback`/`blockUsingItem`/`causeExtraKnockback` recebem `DamageSource` + `float` | 0 | 7 / 11 | modifiers de knockback/escudo |
| `StructureProcessor` vira interface com `MapCodec`; `CODEC` dos templates → `MAP_CODEC` | 0 | 0 | sem impacto direto (worldgen do Tinkers é data-driven, mas conferir) |
| Coleções: dyes em `Items#DYE` (`ColorCollection`); `DyeColor` recebe `MapColor` | 0 | 0 (`Items.*_DYE`) | baixo |
| Menus: `AbstractFurnaceMenu` sem `RecipeType`; `OptionInstance` com `ValueUpdateListener` | — | — | provavelmente sem uso |

**Estimativa M8:** 🟠 médio-alto, concentrado na renderização (BER/fluido) e nos advancements.

### Para referência: 26.3, além do alvo [S17]
A troca de input para SDL (`KeyEvent#scancode` → `keycode`), o rename de pacote `com.mojang.blaze3d.*` →
`com.mojang.renderpearl.*` (Mantle 16 arquivos / Tinkers 48 com imports blaze3d), a **remoção de
`IItemHandler`/`IFluidHandler`/`FluidUtil`/`ItemStackHandler`** e Recipe/LootTable/Advancement virando *reloadable
datapack registries* fazem do 26.3 um salto pesado. Isso reforça manter o alvo no 26.2.

## (d) Riscos e incógnitas

1. **O roadmap está desatualizado sobre o 26.3.** Ele diz *"26.3 is NeoForge-beta-only"*, mas o NF 26.3 está
   **estável desde 26.3.0.76 (2026-10-11)**, e JEI, CT/Balm e o MDK oficial já existem. Faltam só JSON Things e IE. O
   alvo 26.2 continua defensável (mais ecossistema, transfer legado ainda presente), mas a premissa precisa ser
   corrigida no roadmap e a decisão revisitada.
2. **Folding do 26.1.2:** a regra do roadmap indica milestone próprio, e o roadmap atual folda. Decisão do usuário
   (seção b).
3. **JSR-305 (`javax.annotation`) no 1.21.11:** o MC passou a trazer JSpecify e o NF migrou. Se o jsr305 deixar de vir
   transitivamente, ~1150 arquivos quebram (mecânico). Verificar na Fase 0 do M6 com uma compilação de baseline.
4. **`FluidStack` com registries obrigatórios (26.1):** risco de NPE/IllegalState em runtime (inicialização estática,
   datagen, testes unitários sem registries). A suíte de 674 testes unitários pode precisar de bootstrap de registries
   em mais casos.
5. **Ofuscação e ferramentas:** NG 7.1.39 + Gradle 9.2.1 servem todas as versões até o 26.3 (MDKs oficiais), então
   **não é obrigatório trocar para ModDevGradle**. Mesmo assim, o NG não tem releases no GitHub nem changelog público,
   e o post de snapshots dizia que só o MDG suportava o 26.1 de início. É possível que o NG fique para trás em versões
   futuras (26.4+). O MDG 2.0.148 é a alternativa mantida.
6. **JDK 25 não está instalado** (scoop só tem `temurin17-jdk` e `temurin21-jdk`). O hotswap com JBR/DCEVM configurado
   no `tinkers/build.gradle` vai precisar de um JBR 25.
7. **Balm 26.x puxa `kuma-api-neoforge`:** a resolução via `includeGroupByRegex` ou filtros de repo no build pode
   bloquear o grupo/artefato novo, como aconteceu na pegadinha da dependência transitiva do JSON Things.
8. **JEI 26.2 sem build nova desde 2026-10-28:** o 30.48.0.244 provavelmente fica como final. As linhas 1.21.11 e
   26.1.2 seguem ativas (parecem as "LTS" do ecossistema).
9. **Immersive Engineering:** sem nenhum build depois do 1.21.1 (metadata parada em 2025-08). Continua excluído em
   todos os milestones.
10. **JSON Things:** sem build para 26.1.1 (irrelevante, fica folded) nem para 26.3. Para o alvo 26.2 existe o 0.19.0.
11. **Primers lidos por resumo:** os primers têm 180–350 mil caracteres e foram lidos em blocos de 100 mil, via resumo
    automático. O 1.21.11 e o 26.2 foram lidos inteiros, o 26.1 quase inteiro (faltaram ~7 mil caracteres finais) e o
    26.3 só nos primeiros 100 mil de 348 mil. As listas acima são **os destaques, não exaustivas**. Os primers também
    **não cobrem mudanças específicas do loader** (eventos/caps do NeoForge). Antes de cada milestone, reler o primer
    de cada versão folded e os posts do NF (não há post de release para 26.2/26.3 no blog [S18]).
12. **Um item da lista de renames menores do 26.1 precisa de verificação:** *"`Blocks` → `BlockIds`; `Items` →
    `ItemIds`"*. Provavelmente só as constantes `ResourceKey` mudaram de lugar (como `FluidIds` no 26.2), não as
    classes `Blocks`/`Items`. Conferir no decompilado.

## Fontes

- [S1] Mojang version manifest — https://piston-meta.mojang.com/mc/game/version_manifest_v2.json (+ JSON de cada versão: `javaVersion`, `downloads.client_mappings`, `libraries`)
- [S2] NeoForge Maven — https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml (datas = `Last-Modified` dos POMs; deps de FML nos POMs)
- [S3] JEI Maven — https://maven.blamejared.com/mezz/jei/ (`jei-<mc>-neoforge/maven-metadata.xml`; também existem `-common-api` e `-neoforge-api` para todas as versões listadas)
- [S4] Twelveiterations Maven — https://maven.twelveiterations.com/repository/maven-public/net/blay09/mods/craftingtweaks-neoforge/maven-metadata.xml · …/balm-neoforge/maven-metadata.xml · …/kuma-api-neoforge/maven-metadata.xml (deps nos POMs)
- [S5] NeoForge for Minecraft 26.1 — https://neoforged.net/news/26.1release/
- [S6] NeoForge for 26.1 Snapshots — https://www.neoforged.net/news/26.1snapshots/ ; 2025 retrospective — https://www.neoforged.net/news/2025-retrospection/
- [S7] JSON Things Maven — https://dogforce-games.com/maven/dev/gigaherz/jsonthings/
- [S8] Immersive Engineering Maven — https://maven.blamejared.com/blusunrize/immersiveengineering/ImmersiveEngineering/maven-metadata.xml
- [S9] Parchment Maven — https://maven.parchmentmc.org/org/parchmentmc/data/
- [S10] MDKs oficiais — https://github.com/NeoForgeMDKs (`MDK-1.21.11-NeoGradle`, `MDK-26.1.2-NeoGradle`, `MDK-26.2-NeoGradle`, `MDK-26.2-ModDevGradle`, `MDK-26.3-NeoGradle`: `build.gradle`, `gradle.properties`, `gradle-wrapper.properties`)
- [S11] NeoGradle userdev — https://maven.neoforged.net/releases/net/neoforged/gradle/userdev/maven-metadata.xml (último 7.1.39, 2026-09-15) · ModDevGradle — https://maven.neoforged.net/releases/net/neoforged/moddev/net.neoforged.moddev.gradle.plugin/maven-metadata.xml (2.0.148)
- [S12] NeoForge 21.11 for Minecraft 1.21.11 — https://www.neoforged.net/news/21.11release/
- [S13] Primer 1.21.11 — https://github.com/neoforged/.github/blob/main/primers/1.21.11/index.md
- [S14] Primer 26.1 — https://github.com/neoforged/.github/blob/main/primers/26.1/index.md
- [S15] Branches do NeoForge (`1.21.11`, `26.1.x`, `26.2.x`, `26.3.x`) — https://github.com/neoforged/NeoForge/branches/all ; presença de `items/IItemHandler.java`, `fluids/capability/IFluidHandler.java`, `items/ItemStackHandler.java` e `fluids/FluidUtil.java` conferida via raw.githubusercontent.com (200 até 26.2.x, 404 no 26.3.x). Rework de transfer: https://neoforged.net/news/21.9-transfer-rework/
- [S16] Primer 26.2 — https://github.com/neoforged/.github/blob/main/primers/26.2/index.md
- [S17] Primer 26.3 — https://github.com/neoforged/.github/blob/main/primers/26.3/index.md
- [S18] Blog do NeoForge — https://neoforged.net/news/ (último post de release: 26.1; nenhum post de release para 26.2/26.3)
