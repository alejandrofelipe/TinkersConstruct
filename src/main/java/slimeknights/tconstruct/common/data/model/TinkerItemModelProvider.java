package slimeknights.tconstruct.common.data.model;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.PackOutput.PathProvider;
import net.minecraft.data.PackOutput.Target;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.ItemLike;
import slimeknights.mantle.registration.object.ItemObject;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.registration.CastItemObject;
import slimeknights.tconstruct.library.tools.part.MaterialItem;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.tools.TinkerToolParts;
import slimeknights.tconstruct.world.TinkerHeadType;
import slimeknights.tconstruct.world.TinkerWorld;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static slimeknights.tconstruct.TConstruct.getResource;

/**
 * Generates the item-model definitions ({@code assets/tconstruct/items/<id>.json}) and their backing item models
 * ({@code assets/tconstruct/models/item/<id>.json}) for tool parts, casts and misc generated items.
 * <p>
 * PORT 1.21.4: rewritten off the removed NeoForge {@code ItemModelProvider}/{@code CustomLoaderBuilder} DSL. Each item now
 * has a {@link net.minecraft.client.renderer.item.ClientItem} definition ({@code {"model": <unbaked>}}); material parts use
 * the {@code tconstruct:material} {@link net.minecraft.client.renderer.item.ItemModel.Unbaked} and everything else uses the
 * vanilla {@code minecraft:model} wrapper. The item's {@code minecraft:item_model} component defaults to the item's
 * registry id, which matches the emitted {@code items/<id>.json}, so no explicit component is set here.
 */
@SuppressWarnings("UnusedReturnValue")
public class TinkerItemModelProvider implements DataProvider {
  private final PathProvider itemPath;
  private final PathProvider modelPath;
  /** Item-model definitions, keyed by item id -> {@code {"model": ...}} */
  private final Map<ResourceLocation,JsonObject> items = new LinkedHashMap<>();
  /** Backing item models, keyed by model id (item/<id>) -> model json */
  private final Map<ResourceLocation,JsonObject> models = new LinkedHashMap<>();

  @SuppressWarnings("unused") // resourceManager kept for ctor parity with the client GatherDataEvent wiring
  public TinkerItemModelProvider(PackOutput output, ResourceManager resourceManager) {
    this.itemPath = output.createPathProvider(Target.RESOURCE_PACK, "items");
    // PORT 1.21.4 fix: model keys from modelId() already carry the "item/" prefix (e.g. tconstruct:item/pick_head),
    // which is also how the item definitions reference them. The path root must be "models" so a key of item/<id>
    // writes to models/item/<id>.json (the location the model loader resolves). Using "models/item" here produced a
    // double prefix (models/item/item/<id>.json), so every backing part/cast model failed to load -> missing texture.
    this.modelPath = output.createPathProvider(Target.RESOURCE_PACK, "models");
  }

  protected void registerModels() {
    // tool parts //
    // rock
    part(TinkerToolParts.pickHead, "pickaxe/head").offset(-2, 1);
    part(TinkerToolParts.hammerHead, "sledge_hammer/head").offset(-3, 3);
    // axe
    part(TinkerToolParts.smallAxeHead, "hand_axe/head").offset(-2, 3);
    part(TinkerToolParts.broadAxeHead, "broad_axe/blade").offset(0, 3);
    // blades
    part(TinkerToolParts.smallBlade);
    part(TinkerToolParts.broadBlade, "cleaver/head").offset(-1, 1);
    // plates
    part(TinkerToolParts.adzeHead, "pickadze/adze").offset(-5, 1);
    part(TinkerToolParts.largePlate);
    // bows
    part(TinkerToolParts.bowLimb, "longbow/limb_bottom").offset(5, -2);
    part(TinkerToolParts.bowGrip, "crossbow/body").offset(-2, -2);
    part(TinkerToolParts.bowstring);
    part(TinkerToolParts.arrowHead, "ammo/arrow_head").offset(-4, 3);
    part(TinkerToolParts.arrowShaft, "ammo/arrow_shaft").offset(1, -1);
    part(TinkerToolParts.fletching, "ammo/arrow_feather").offset(4, -5);
    // other
    part(TinkerToolParts.toolBinding);
    part(TinkerToolParts.toolHandle);
    part(TinkerToolParts.toughHandle);
    part(TinkerToolParts.toughBinding);
    part(TinkerToolParts.repairKit);
    part(TinkerToolParts.fakeIngot, "parts/ingot");
    // armor
    TinkerToolParts.plating.forEach((slot, item) -> {
      MaterialModelBuilder b = this.part(item, "armor/plate/" + slot.getName() + "/plating");
      if (slot == ArmorType.HELMET) {
        b.offset(0, 2);
      } else if (slot == ArmorType.LEGGINGS) {
        b.offset(0, 1);
      }
    });
    part(TinkerToolParts.maille);
    part(TinkerToolParts.shieldCore, "armor/plate/shield/core");
    // slimesuit
    part(TinkerToolParts.ribcage, "armor/slime/chestplate/ribcage").offset(0, 3);
    part(TinkerToolParts.shell, "armor/slime/leggings/shell").offset(0, -1);
    part(TinkerToolParts.laces, "armor/slime/boots/laces").offset(0, 1);

    // gauges
    generated(TinkerSmeltery.copperGauge, "block/smeltery/io/gauge");
    generated(TinkerSmeltery.obsidianGauge, "block/foundry/io/gauge");

    // casts //
    // basic
    basicItem(TinkerSmeltery.blankSandCast, "sand_cast/blank");
    basicItem(TinkerSmeltery.blankRedSandCast, "red_sand_cast/blank");
    cast(TinkerSmeltery.ingotCast);
    cast(TinkerSmeltery.nuggetCast);
    cast(TinkerSmeltery.gemCast);
    cast(TinkerSmeltery.rodCast);
    cast(TinkerSmeltery.repairKitCast);
    // compat
    cast(TinkerSmeltery.plateCast);
    cast(TinkerSmeltery.gearCast);
    cast(TinkerSmeltery.coinCast);
    cast(TinkerSmeltery.wireCast);
    // small heads
    cast(TinkerSmeltery.pickHeadCast);
    cast(TinkerSmeltery.smallAxeHeadCast);
    cast(TinkerSmeltery.smallBladeCast);
    cast(TinkerSmeltery.adzeHeadCast);
    // large heads
    cast(TinkerSmeltery.hammerHeadCast);
    cast(TinkerSmeltery.broadBladeCast);
    cast(TinkerSmeltery.broadAxeHeadCast);
    cast(TinkerSmeltery.largePlateCast);
    // bindings
    cast(TinkerSmeltery.toolBindingCast);
    cast(TinkerSmeltery.toughBindingCast);
    // tool rods
    cast(TinkerSmeltery.toolHandleCast);
    cast(TinkerSmeltery.toughHandleCast);
    // bow
    cast(TinkerSmeltery.bowLimbCast);
    cast(TinkerSmeltery.bowGripCast);
    basicItem(TinkerSmeltery.arrowCast.getId(), "cast/arrow");
    // armor
    cast(TinkerSmeltery.helmetPlatingCast);
    cast(TinkerSmeltery.chestplatePlatingCast);
    cast(TinkerSmeltery.leggingsPlatingCast);
    cast(TinkerSmeltery.bootsPlatingCast);
    cast(TinkerSmeltery.mailleCast);
    // dummy parts
    TinkerSmeltery.dummyPlating.forEach((type, item) -> basicItem(item, "tool/parts/plating_" + type.getName()));

    // world //
    // shards
    basicItem(TinkerWorld.steelShard, "materials/steel_shard");
    basicItem(TinkerWorld.cobaltShard, "materials/cobalt_shard");
    basicItem(TinkerWorld.knightmetalShard, "materials/knightmetal_shard");
    generated(TinkerWorld.steelCluster, "block/geode/steel_cluster");
    generated(TinkerWorld.cobaltCluster, "block/geode/cobalt_cluster");
    generated(TinkerWorld.knightmetalCluster, "block/geode/knightmetal_cluster");
    // heads
    TinkerWorld.headItems.forEach((type, head) -> head(id(head), type));
  }

  @Override
  public CompletableFuture<?> run(CachedOutput cache) {
    registerModels();
    CompletableFuture<?>[] tasks = new CompletableFuture<?>[items.size() + models.size()];
    int i = 0;
    for (Map.Entry<ResourceLocation,JsonObject> entry : items.entrySet()) {
      tasks[i++] = DataProvider.saveStable(cache, entry.getValue(), itemPath.json(entry.getKey()));
    }
    for (Map.Entry<ResourceLocation,JsonObject> entry : models.entrySet()) {
      tasks[i++] = DataProvider.saveStable(cache, entry.getValue(), modelPath.json(entry.getKey()));
    }
    return CompletableFuture.allOf(tasks);
  }

  @Override
  public String getName() {
    return "Tinkers' Construct Item Model Definitions";
  }

  @SuppressWarnings("deprecation") // registry lookup for the item id
  private static ResourceLocation id(ItemLike item) {
    return BuiltInRegistries.ITEM.getKey(item.asItem());
  }

  /** Backing model id for an item id: {@code <ns>:item/<path>} */
  private static ResourceLocation modelId(ResourceLocation item) {
    return ResourceLocation.fromNamespaceAndPath(item.getNamespace(), "item/" + item.getPath());
  }

  /** Emits a {@code minecraft:item/generated} backing model with the given layer0 texture, returns its id */
  private ResourceLocation generatedModel(ResourceLocation item, ResourceLocation texture) {
    ResourceLocation model = modelId(item);
    JsonObject json = new JsonObject();
    json.addProperty("parent", "minecraft:item/generated");
    JsonObject textures = new JsonObject();
    textures.addProperty("layer0", texture.toString());
    json.add("textures", textures);
    models.put(model, json);
    return model;
  }

  /** Wraps an unbaked item model object in a {@link net.minecraft.client.renderer.item.ClientItem} definition */
  private void itemDefinition(ResourceLocation item, JsonObject model) {
    JsonObject json = new JsonObject();
    json.add("model", model);
    items.put(item, json);
  }

  /** Creates a material part model with the given texture (under {@code item/tool/}) */
  private MaterialModelBuilder part(ResourceLocation part, String texture) {
    ResourceLocation base = generatedModel(part, getResource("item/tool/" + texture));
    JsonObject material = new JsonObject();
    itemDefinition(part, material);
    return new MaterialModelBuilder(material, base);
  }

  /** Creates a material part model in the parts folder */
  private MaterialModelBuilder part(Item item, String texture) {
    return part(id(item), texture);
  }

  /** Creates a material part model with the given texture */
  private MaterialModelBuilder part(ItemObject<? extends MaterialItem> part, String texture) {
    return part(part.getId(), texture);
  }

  /** Creates a material part model in the parts folder */
  private void part(ItemObject<? extends MaterialItem> part) {
    part(part, "parts/" + part.getId().getPath());
  }

  /** Simple generated item with the given full texture path */
  private void generated(ResourceLocation item, ResourceLocation texture) {
    ResourceLocation base = generatedModel(item, texture);
    JsonObject model = new JsonObject();
    model.addProperty("type", "minecraft:model");
    model.addProperty("model", base.toString());
    itemDefinition(item, model);
  }

  /** Simple generated item with the given texture path relative to the item namespace */
  private void generated(ResourceLocation item, String texture) {
    generated(item, ResourceLocation.fromNamespaceAndPath(item.getNamespace(), texture));
  }

  /** Simple generated item with the given texture path relative to the item namespace */
  private void generated(ItemLike item, String texture) {
    generated(id(item), texture);
  }

  /** Simple generated item with a texture under {@code item/} */
  private void basicItem(ResourceLocation item, String texture) {
    generated(item, "item/" + texture);
  }

  /** Simple generated item with a texture under {@code item/} */
  private void basicItem(ItemLike item, String texture) {
    basicItem(id(item), texture);
  }

  /**
   * Head item rendered by the {@code tconstruct:head} special model, mirroring vanilla's head items. A plain
   * {@code template_skull} model has no geometry, so the item was invisible.
   */
  private void head(ResourceLocation item, TinkerHeadType type) {
    JsonObject special = new JsonObject();
    special.addProperty("type", TConstruct.resourceString("head"));
    special.addProperty("kind", type.getSerializedName());
    JsonObject def = new JsonObject();
    def.addProperty("type", "minecraft:special");
    def.addProperty("base", "minecraft:item/template_skull");
    def.add("model", special);
    itemDefinition(item, def);
  }

  /** Creates models for the given cast object (cast, sand cast, red sand cast) */
  private void cast(CastItemObject cast) {
    String name = cast.getName().getPath();
    basicItem(cast.getId(), "cast/" + name);
    basicItem(cast.getSand(), "sand_cast/" + name);
    basicItem(cast.getRedSand(), "red_sand_cast/" + name);
  }
}
