package slimeknights.tconstruct.common.data;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import net.minecraft.data.PackOutput.Target;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import slimeknights.mantle.data.GenericDataProvider;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.json.predicate.tool.TinkerItemPredicates;
import slimeknights.tconstruct.library.json.predicate.tool.ToolItemSubPredicate;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.shared.TinkerCommons;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
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
    builder(TinkerCommons.materialsAndYou, resource("tools/materials_and_you"), resource("textures/gui/advancement_background.png"), AdvancementType.TASK, builder ->
      builder.addCriterion("crafted_book", hasItem(TinkerCommons.materialsAndYou)));
  }

  @Override
  public CompletableFuture<?> run(CachedOutput cache) {
    return this.registries.thenCompose(provider -> {
      this.advancements.clear();
      generate();
      // NeoForge's datagen provider overrides createSerializationContext to recognise built-in (mod) item
      // holders; RegistryOps.create(..) would not, failing HolderSet serialization ("not valid in registry set").
      RegistryOps<JsonElement> ops = provider.createSerializationContext(JsonOps.INSTANCE);
      return allOf(this.advancements.stream().map(holder ->
        saveJson(cache, holder.id(), Advancement.CODEC.encodeStart(ops, holder.value()).getOrThrow())));
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
