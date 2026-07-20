package slimeknights.tconstruct.common.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.PlayerTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import net.minecraft.data.PackOutput.Target;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.common.conditions.ICondition;
import slimeknights.mantle.data.GenericDataProvider;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.common.json.ConfigEnabledCondition;
import slimeknights.tconstruct.library.json.predicate.tool.HasMaterialPredicate;
import slimeknights.tconstruct.library.json.predicate.tool.HasModifierPredicate;
import slimeknights.tconstruct.library.json.predicate.tool.StatInRangePredicate;
import slimeknights.tconstruct.library.json.predicate.tool.StatInSetPredicate;
import slimeknights.tconstruct.library.json.predicate.tool.TinkerItemPredicates;
import slimeknights.tconstruct.library.json.predicate.tool.ToolContextPredicate;
import slimeknights.tconstruct.library.json.predicate.tool.ToolItemSubPredicate;
import slimeknights.tconstruct.library.json.predicate.tool.ToolStackPredicate;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.shared.TinkerCommons;
import slimeknights.tconstruct.shared.TinkerMaterials;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tools.TinkerToolParts;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.data.ModifierIds;
import slimeknights.tconstruct.tools.data.material.MaterialIds;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Generates Tinkers' advancements on the 1.21 vanilla API ({@link Advancement.Builder} + {@link Advancement#CODEC}),
 * emitting at the singular {@code data/tconstruct/advancement/} path (1.20.5/1.21 datapack dir rename).
 * <p>
 * Tool matching in advancements goes through the {@code tconstruct:tool} {@link net.minecraft.advancements.critereon.ItemSubPredicate}
 * bridge ({@link TinkerItemPredicates#TOOL}); see {@link #toolCriterion}. Only the {@code tools/} tinkering path is
 * generated here — the other categories and config-gated advancements are a separate sub-project.
 */
public class AdvancementsProvider extends GenericDataProvider {
  private final CompletableFuture<HolderLookup.Provider> registries;
  private final List<AdvancementHolder> advancements = new ArrayList<>();
  /** Config-gating: conditions to attach to an advancement id via {@code neoforge:conditions} (replaces ConditionalAdvancement). */
  private final Map<ResourceLocation, List<ICondition>> conditions = new HashMap<>();

  public AdvancementsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
    super(output, Target.DATA_PACK, "advancement");
    this.registries = registries;
  }

  @Override
  public String getName() {
    return "Tinkers' Construct Advancements";
  }

  /** Generates all advancements into {@link #advancements} */
  protected void generate() {
    // tinkering path
    AdvancementHolder materialsAndYou = builder(TinkerCommons.materialsAndYou, resource("tools/materials_and_you"), resource("textures/gui/advancement_background.png"), AdvancementType.TASK, builder ->
      builder.addCriterion("crafted_book", hasItem(TinkerCommons.materialsAndYou)));
    AdvancementHolder partBuilder = builder(TinkerTables.partBuilder, resource("tools/part_builder"), materialsAndYou, AdvancementType.TASK, builder ->
      builder.addCriterion("crafted_block", hasItem(TinkerTables.partBuilder)));
    builder(TinkerToolParts.pickHead.get().withMaterialForDisplay(MaterialIds.wood), resource("tools/make_part"), partBuilder, AdvancementType.TASK, builder ->
      builder.addCriterion("crafted_part", hasTag(TinkerTags.Items.TOOL_PARTS)));
    AdvancementHolder tinkerStation = builder(TinkerTables.tinkerStation, resource("tools/tinker_station"), partBuilder, AdvancementType.TASK, builder ->
      builder.addCriterion("crafted_block", hasItem(TinkerTables.tinkerStation)));
    AdvancementHolder tinkerTool = builder(TinkerTools.pickaxe.get().getRenderTool(), resource("tools/tinker_tool"), tinkerStation, AdvancementType.TASK, builder ->
      builder.addCriterion("crafted_tool", hasTag(TinkerTags.Items.MULTIPART_TOOL)));
    AdvancementHolder harvestLevel = builder(Items.NETHERITE_INGOT, resource("tools/netherite_tier"), tinkerTool, AdvancementType.GOAL, builder ->
      builder.addCriterion("harvest_level", toolCriterion(new StatInSetPredicate<>(ToolStats.HARVEST_TIER, Tiers.NETHERITE))));
    builder(Items.TARGET, resource("tools/perfect_aim"), tinkerTool, AdvancementType.GOAL, builder ->
      builder.addCriterion("accuracy", toolCriterion(ToolStackPredicate.and(
        ToolStackPredicate.tag(TinkerTags.Items.BOWS),
        StatInRangePredicate.match(ToolStats.ACCURACY, 1)))));
    // note that attack damage gets +1 from player attributes, so 20 is actually 21 damage with the tool
    builder(Items.ZOMBIE_HEAD, resource("tools/one_shot"), tinkerTool, AdvancementType.GOAL, builder ->
      builder.addCriterion("damage", toolCriterion(StatInRangePredicate.min(ToolStats.ATTACK_DAMAGE, 20))));
    builder(TinkerMaterials.manyullyn.getIngot(), resource("tools/material_master"), harvestLevel, AdvancementType.CHALLENGE, builder -> {
      Consumer<MaterialId> with = id -> builder.addCriterion(id.getPath(), toolContextCriterion(new HasMaterialPredicate(id)));
      // tier 1
      with.accept(MaterialIds.wood);
      with.accept(MaterialIds.flint);
      with.accept(MaterialIds.rock);
      with.accept(MaterialIds.bone);
      with.accept(MaterialIds.necroticBone);
      with.accept(MaterialIds.leather);
      with.accept(MaterialIds.string);
      with.accept(MaterialIds.vine);
      with.accept(MaterialIds.bamboo);
      with.accept(MaterialIds.chorus);
      // tier 2
      with.accept(MaterialIds.iron);
      with.accept(MaterialIds.searedStone);
      with.accept(MaterialIds.scorchedStone);
      with.accept(MaterialIds.copper);
      with.accept(MaterialIds.slimewood);
      with.accept(MaterialIds.slimeskin);
      with.accept(MaterialIds.skyslimeVine);
      with.accept(MaterialIds.weepingVine);
      with.accept(MaterialIds.twistingVine);
      with.accept(MaterialIds.whitestone);
      // tier 3
      with.accept(MaterialIds.roseGold);
      with.accept(MaterialIds.slimesteel);
      with.accept(MaterialIds.nahuatl);
      with.accept(MaterialIds.amethystBronze);
      with.accept(MaterialIds.pigIron);
      with.accept(MaterialIds.cobalt);
      with.accept(MaterialIds.darkthread);
      with.accept(MaterialIds.ichorskin);
      // tier 4
      with.accept(MaterialIds.manyullyn);
      with.accept(MaterialIds.hepatizon);
      with.accept(MaterialIds.cinderslime);
      with.accept(MaterialIds.queensSlime);
      with.accept(MaterialIds.blazingBone);
      with.accept(MaterialIds.blazewood);
      with.accept(MaterialIds.jeweledHide);
      with.accept(MaterialIds.knightmetal);
      with.accept(MaterialIds.knightslime);
      with.accept(MaterialIds.enderslimeVine);
    });
    builder(TinkerTools.travelersGear.get(ArmorItem.Type.HELMET).getRenderTool(), resource("tools/travelers_gear"), tinkerStation, AdvancementType.TASK, builder ->
      TinkerTools.travelersGear.forEach((type, armor) -> builder.addCriterion("crafted_" + type.getName(), hasItem(armor))));
    builder(TinkerTools.pickaxe.get().getRenderTool(), resource("tools/tool_smith"), tinkerTool, AdvancementType.CHALLENGE, builder -> {
      Consumer<Item> with = item -> builder.addCriterion(BuiltInRegistries.ITEM.getKey(item).getPath(), hasItem(item));
      with.accept(TinkerTools.pickaxe.get());
      with.accept(TinkerTools.mattock.get());
      with.accept(TinkerTools.pickadze.get());
      with.accept(TinkerTools.handAxe.get());
      with.accept(TinkerTools.kama.get());
      with.accept(TinkerTools.dagger.get());
      with.accept(TinkerTools.sword.get());
    });
    AdvancementHolder modified = builder(Items.REDSTONE, resource("tools/modified"), tinkerTool, AdvancementType.TASK, builder ->
      builder.addCriterion("crafted_tool", toolContextCriterion(ToolContextPredicate.HAS_UPGRADES)));
    builder(Items.WRITABLE_BOOK, resource("tools/upgrade_slots"), modified, AdvancementType.CHALLENGE, builder ->
      builder.addCriterion("has_modified", toolContextCriterion(
        ToolContextPredicate.and(
          HasModifierPredicate.hasUpgrade(ModifierIds.writable, 1),
          HasModifierPredicate.hasUpgrade(ModifierIds.recapitated, 1),
          HasModifierPredicate.hasUpgrade(ModifierIds.harmonious, 1),
          HasModifierPredicate.hasUpgrade(ModifierIds.forecast, 1),
          HasModifierPredicate.hasUpgrade(ModifierIds.gilded, 1)))));

    // internal advancements
    hiddenBuilder(resource("internal/starting_book"), ConfigEnabledCondition.SPAWN_WITH_BOOK, builder -> {
      builder.addCriterion("tick", tickCriterion());
      builder.rewards(AdvancementRewards.Builder.loot(ResourceKey.create(Registries.LOOT_TABLE, resource("gameplay/starting_book"))));
    });
  }

  @Override
  public CompletableFuture<?> run(CachedOutput cache) {
    return this.registries.thenCompose(provider -> {
      this.advancements.clear();
      this.conditions.clear();
      generate();
      // NeoForge's datagen provider overrides createSerializationContext to recognise built-in (mod) item
      // holders; RegistryOps.create(..) would not, failing HolderSet serialization ("not valid in registry set").
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


  /* Builder helpers */

  /** Root builder: has a background image and no parent */
  protected AdvancementHolder builder(ItemLike display, ResourceLocation name, ResourceLocation background, AdvancementType frame, Consumer<Advancement.Builder> consumer) {
    return builder(new ItemStack(display), name, null, background, frame, consumer);
  }

  /** Child builder from an item display, parented, no background */
  protected AdvancementHolder builder(ItemLike display, ResourceLocation name, AdvancementHolder parent, AdvancementType frame, Consumer<Advancement.Builder> consumer) {
    return builder(new ItemStack(display), name, parent, null, frame, consumer);
  }

  /** Child builder from a stack display, parented, no background */
  protected AdvancementHolder builder(ItemStack display, ResourceLocation name, AdvancementHolder parent, AdvancementType frame, Consumer<Advancement.Builder> consumer) {
    return builder(display, name, parent, null, frame, consumer);
  }

  /** Core builder: builds, registers into {@link #advancements}, and returns the holder for use as a parent */
  protected AdvancementHolder builder(ItemStack display, ResourceLocation name, @Nullable AdvancementHolder parent, @Nullable ResourceLocation background, AdvancementType frame, Consumer<Advancement.Builder> consumer) {
    Advancement.Builder builder = Advancement.Builder.advancement();
    if (parent != null) {
      builder.parent(parent);
    }
    builder.display(new DisplayInfo(display,
      Component.translatable(makeTranslationKey(name) + ".title"),
      Component.translatable(makeTranslationKey(name) + ".description"),
      Optional.ofNullable(background), frame, true, frame != AdvancementType.TASK, false));
    consumer.accept(builder);
    builder.requirements(AdvancementRequirements.Strategy.AND);
    AdvancementHolder holder = builder.build(name);
    this.advancements.add(holder);
    return holder;
  }

  /** Hidden, config-gated advancement (replaces the removed ConditionalAdvancement): no display, no parent. */
  protected AdvancementHolder hiddenBuilder(ResourceLocation name, ICondition condition, Consumer<Advancement.Builder> consumer) {
    Advancement.Builder builder = Advancement.Builder.advancement();
    builder.requirements(AdvancementRequirements.Strategy.AND);
    consumer.accept(builder);
    AdvancementHolder holder = builder.build(name);
    this.advancements.add(holder);
    this.conditions.put(name, List.of(condition));
    return holder;
  }


  /* Criterion helpers */

  /** Inventory-change criterion from an item predicate builder */
  protected static Criterion<?> inventoryTrigger(ItemPredicate.Builder item) {
    return CriteriaTriggers.INVENTORY_CHANGED.createCriterion(new InventoryChangeTrigger.TriggerInstance(
      Optional.empty(), InventoryChangeTrigger.TriggerInstance.Slots.ANY, List.of(item.build())));
  }

  /** Criterion matching an item in the inventory */
  protected static Criterion<?> hasItem(ItemLike item) {
    return inventoryTrigger(ItemPredicate.Builder.item().of(item));
  }

  /** Criterion matching any item in the given tag */
  protected static Criterion<?> hasTag(TagKey<Item> tag) {
    return inventoryTrigger(ItemPredicate.Builder.item().of(tag));
  }

  /** Criterion matching a Tinker tool against a tool-stack predicate (via the {@code tconstruct:tool} sub-predicate) */
  protected static Criterion<?> toolCriterion(IJsonPredicate<IToolStackView> predicate) {
    return inventoryTrigger(ItemPredicate.Builder.item().withSubPredicate(TinkerItemPredicates.TOOL.get(), ToolItemSubPredicate.ofTool(predicate)));
  }

  /** Criterion matching a Tinker tool against a tool-context predicate */
  protected static Criterion<?> toolContextCriterion(IJsonPredicate<IToolContext> predicate) {
    return inventoryTrigger(ItemPredicate.Builder.item().withSubPredicate(TinkerItemPredicates.TOOL.get(), ToolItemSubPredicate.ofContext(predicate)));
  }

  /** Criterion firing every player tick (hidden reward advancements). */
  protected static Criterion<?> tickCriterion() {
    return CriteriaTriggers.TICK.createCriterion(new PlayerTrigger.TriggerInstance(Optional.empty()));
  }


  /* Misc helpers */

  /** Gets a tinkers resource location */
  protected static ResourceLocation resource(String name) {
    return TConstruct.getResource(name);
  }

  /** Makes an advancement translation key from the given ID */
  private static String makeTranslationKey(ResourceLocation advancement) {
    return "advancements." + advancement.getNamespace() + "." + advancement.getPath().replace('/', '.');
  }
}
