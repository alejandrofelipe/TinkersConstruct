package slimeknights.tconstruct.common.data.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.BlockStateGenerator;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import slimeknights.mantle.registration.object.BuildingBlockObject;
import slimeknights.mantle.registration.object.FenceBuildingBlockObject;
import slimeknights.mantle.registration.object.WoodBlockObject;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.registration.GeodeItemObject;
import slimeknights.tconstruct.common.registration.GeodeItemObject.BudSize;
import slimeknights.tconstruct.shared.TinkerCommons;
import slimeknights.tconstruct.shared.TinkerMaterials;
import slimeknights.tconstruct.shared.block.ClearStainedGlassBlock.GlassColor;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.world.TinkerWorld;

import javax.annotation.Nullable;
import java.util.function.Function;

import static slimeknights.tconstruct.TConstruct.getResource;

/**
 * Generates blockstates, block models and block-item models for Tinkers blocks.
 * <p>
 * PORT 1.21.4: the removed NeoForge {@code BlockStateProvider}/{@code ModelBuilder}/{@code ModelFile} DSL is replaced by
 * vanilla {@link ModelProvider}. Standard shapes use the public static {@link BlockModelGenerators} generators (which take
 * explicit model {@link ResourceLocation}s so Tinkers' custom model paths are preserved and the variant logic is vanilla-
 * correct); block/item model JSON is hand-emitted through {@code blockModels.modelOutput}. Connected/coloured glass and
 * panes keep the {@code mantle:connected}/{@code mantle:colored_block} custom-loader JSON (see
 * {@code slimeknights.mantle.client.model.builder}); the item-side colour tint (formerly {@code MantleItemLayerBuilder})
 * is a client {@code ItemTintSource} concern handled in that batch, so tinted pane items emit a plain flat item here.
 */
@SuppressWarnings({"SameParameterValue", "UnusedReturnValue"})
public class TinkerBlockStateProvider extends ModelProvider {
  private BlockModelGenerators blocks;
  private final ResourceManager resourceManager;

  public TinkerBlockStateProvider(PackOutput output, ResourceManager resourceManager) {
    super(output, TConstruct.MOD_ID);
    this.resourceManager = resourceManager;
  }

  /** True if the given block/item path already has a hand-authored (non-generated) asset at the given folder */
  private boolean hasResource(ResourceLocation id, String folder) {
    return resourceManager.getResource(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), folder + "/" + id.getPath() + ".json")).isPresent();
  }

  /**
   * 1.21.4: vanilla {@link ModelProvider} validates that every known block has a generated blockstate. Tinkers only
   * datagens a subset here (wood, glass, geodes, …); the remaining blocks ship hand-authored blockstate JSON in
   * {@code src/main/resources}. Exclude those so validation only requires what this provider actually generates.
   */
  @Override
  protected java.util.stream.Stream<? extends net.minecraft.core.Holder<Block>> getKnownBlocks() {
    return BuiltInRegistries.BLOCK.listElements()
      .filter(holder -> holder.getKey().location().getNamespace().equals(TConstruct.MOD_ID))
      // fluid blocks get their blockstate from FluidBlockstateModelProvider, not this provider
      .filter(holder -> !(holder.value() instanceof net.minecraft.world.level.block.LiquidBlock))
      .filter(holder -> !hasResource(holder.getKey().location(), "blockstates"));
  }

  /**
   * Item-model validation: only the block-items for the blocks this provider generates. Regular items (tool parts, casts,
   * materials) and hand-authored block items are produced elsewhere (TinkerItemModelProvider / static resources).
   */
  @Override
  protected java.util.stream.Stream<? extends net.minecraft.core.Holder<net.minecraft.world.item.Item>> getKnownItems() {
    java.util.Set<Block> known = getKnownBlocks().map(net.minecraft.core.Holder::value).collect(java.util.stream.Collectors.toSet());
    return BuiltInRegistries.ITEM.listElements()
      .filter(holder -> holder.value() instanceof net.minecraft.world.item.BlockItem blockItem && known.contains(blockItem.getBlock()));
  }

  @Override
  protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
    this.blocks = blockModels;

    addFenceBuildingBlock(TinkerMaterials.blazewood, "block/wood/blazewood/", "planks", blockTexture("wood/blazewood"));
    addFenceBuildingBlock(TinkerMaterials.nahuatl, "block/wood/nahuatl/", "planks", blockTexture("wood/nahuatl"));
    addWood(TinkerWorld.greenheart, false, "minecraft:cutout");
    addWood(TinkerWorld.skyroot, true, "minecraft:cutout");
    addWood(TinkerWorld.bloodshroom, true, "minecraft:cutout");
    addWood(TinkerWorld.enderbark, true, "minecraft:solid");
    // enderbark roots
    simpleBlock(TinkerWorld.enderbarkRoots.get(), withParent("block/wood/enderbark/roots/empty", "minecraft:block/mangrove_roots", "minecraft:cutout",
      texture("side", blockTexture("wood/enderbark/roots")), texture("top", blockTexture("wood/enderbark/roots_top"))));
    TinkerWorld.slimyEnderbarkRoots.forEach((type, block) -> {
      String name = type.getSerializedName();
      cubeColumn(block, "block/wood/enderbark/roots/" + name, blockTexture("wood/enderbark/roots/" + name), blockTexture("wood/enderbark/roots/" + name + "_top"));
    });

    // clear glass
    glassBlock(TinkerCommons.clearGlass.get(), TinkerCommons.clearGlassPane.get(), "clear_glass/", getResource("block/clear_glass"), -1, true, null);
    ResourceLocation clearStainedGlass = getResource("block/clear_stained_glass");
    for (GlassColor color : GlassColor.values()) {
      glassBlock(TinkerCommons.clearStainedGlass.get(color), TinkerCommons.clearStainedGlassPane.get(color), "clear_glass/" + color.getSerializedName() + "/", clearStainedGlass, 0xFF000000 | color.getColor(), false, "minecraft:translucent");
    }
    glassBlock(TinkerCommons.soulGlass.get(), TinkerCommons.soulGlassPane.get(), "soul_glass/", getResource("block/soul_glass"), -1, false, "minecraft:translucent");
    // smeltery glass
    glassBlock(TinkerSmeltery.searedGlass.get(),     TinkerSmeltery.searedGlassPane.get(),     "smeltery/glass/", getResource("block/smeltery/seared_glass"), -1, true, null);
    glassBlock(TinkerSmeltery.searedSoulGlass.get(), TinkerSmeltery.searedSoulGlassPane.get(), "smeltery/soul_glass/",
               getResource("block/smeltery/soul_glass"), getResource("block/smeltery/seared_glass_top"), -1, true, "minecraft:translucent");
    glassBlock(TinkerSmeltery.scorchedGlass.get(),     TinkerSmeltery.scorchedGlassPane.get(),     "foundry/glass/", getResource("block/foundry/glass"), -1, true, null);
    glassBlock(TinkerSmeltery.scorchedSoulGlass.get(), TinkerSmeltery.scorchedSoulGlassPane.get(), "foundry/soul_glass/",
               getResource("block/foundry/soul_glass"), getResource("block/foundry/glass_top"), -1, true, "minecraft:translucent");
    // obsidian pane
    ResourceLocation obsidian = ResourceLocation.parse("block/obsidian");
    paneBlock(TinkerCommons.obsidianPane.get(), "obsidian_pane/", obsidian, obsidian, false, -1, false, "minecraft:solid");

    // shards
    bud(TinkerWorld.steelCluster.get(), "block/geode/steel_cluster", blockTexture("geode/steel_cluster"));
    bud(TinkerWorld.cobaltCluster.get(), "block/geode/cobalt_cluster", blockTexture("geode/cobalt_cluster"));
    bud(TinkerWorld.knightmetalCluster.get(), "block/geode/knightmetal_cluster", blockTexture("geode/knightmetal_cluster"));

    // geodes
    geode(TinkerWorld.earthGeode, "earth");
    geode(TinkerWorld.skyGeode,   "sky");
    geode(TinkerWorld.ichorGeode, "ichor");
    geode(TinkerWorld.enderGeode, "ender");

    // smeltery
    basicBlock(TinkerSmeltery.searedLamp.get(), "block/smeltery/seared/lamp", blockTexture("smeltery/seared/lamp"));
    basicBlock(TinkerSmeltery.scorchedLamp.get(), "block/foundry/scorched/lamp", blockTexture("foundry/scorched/lamp"));

    // heads - reuse the vanilla skull model
    ResourceLocation skull = ResourceLocation.parse("block/skull");
    TinkerWorld.heads.forEach(head -> simpleBlock(head, skull));
    TinkerWorld.wallHeads.forEach(head -> simpleBlock(head, skull));
  }


  /* Texture + model JSON helpers */

  protected ResourceLocation blockTexture(String path) {
    return getResource("block/" + path);
  }

  protected ResourceLocation itemTexture(String path) {
    return getResource("item/" + path);
  }

  /** Builds a single [key,value] texture entry for {@link #withParent} */
  private static JsonObject texture(String key, ResourceLocation value) {
    JsonObject json = new JsonObject();
    json.addProperty(key, value.toString());
    return json;
  }

  /** Emits a model JSON with the given parent + merged textures, returns its id. {@code path} is relative to the mod namespace. */
  private ResourceLocation withParent(String path, String parent, @Nullable String renderType, JsonObject... textures) {
    ResourceLocation id = getResource(path);
    JsonObject json = new JsonObject();
    json.addProperty("parent", parent);
    if (textures.length > 0) {
      JsonObject merged = new JsonObject();
      for (JsonObject t : textures) {
        t.entrySet().forEach(e -> merged.add(e.getKey(), e.getValue()));
      }
      json.add("textures", merged);
    }
    if (renderType != null) {
      json.addProperty("render_type", renderType);
    }
    blocks.modelOutput.accept(id, () -> json);
    return id;
  }

  private ResourceLocation cubeAll(String path, ResourceLocation all) {
    return withParent(path, "minecraft:block/cube_all", null, texture("all", all));
  }

  private ResourceLocation cubeColumnModel(String path, ResourceLocation side, ResourceLocation end) {
    return withParent(path, "minecraft:block/cube_column", null, texture("side", side), texture("end", end));
  }


  /* Block + item registration */

  /** Registers a blockstate generator */
  private void blockState(BlockStateGenerator generator) {
    blocks.blockStateOutput.accept(generator);
  }

  /** Simple single-model blockstate */
  private void simpleBlock(Block block, ResourceLocation model) {
    blockState(BlockModelGenerators.createSimpleBlock(block, model));
  }

  /** Simple blockstate + block-model item */
  public ResourceLocation basicBlock(Block block, ResourceLocation model) {
    simpleBlock(block, model);
    blocks.registerSimpleItemModel(block, model);
    return model;
  }

  /** Cube-all block with item */
  public ResourceLocation basicBlock(Block block, String location, ResourceLocation texture) {
    return basicBlock(block, cubeAll(location, texture));
  }

  /** Cube-column block with item */
  public ResourceLocation cubeColumn(Block block, String location, ResourceLocation side, ResourceLocation top) {
    return basicBlock(block, cubeColumnModel(location, side, top));
  }

  /** Flat (layer0) item model, returns its id */
  private ResourceLocation generatedItem(ResourceLocation item, ResourceLocation texture) {
    ResourceLocation model = ResourceLocation.fromNamespaceAndPath(item.getNamespace(), "item/" + item.getPath());
    JsonObject json = new JsonObject();
    json.addProperty("parent", "minecraft:item/generated");
    json.add("textures", texture("layer0", texture));
    blocks.modelOutput.accept(model, () -> json);
    return model;
  }

  @SuppressWarnings("deprecation")
  private ResourceLocation itemKey(ItemLike item) {
    return BuiltInRegistries.ITEM.getKey(item.asItem());
  }

  private String itemName(ItemLike item) {
    return itemKey(item).getPath();
  }

  /** Flat generated item with a texture under {@code item/<prefix><name>} */
  protected void basicItem(ItemLike item, String texturePrefix) {
    ResourceLocation id = itemKey(item);
    ResourceLocation model = generatedItem(id, itemTexture(texturePrefix + id.getPath()));
    blocks.registerSimpleItemModel(item.asItem(), model);
  }


  /* Building blocks */

  protected void addBuildingBlock(BuildingBlockObject block, String folder, String name, ResourceLocation texture) {
    ResourceLocation blockModel = basicBlock(block.get(), folder + name, texture);
    slab(block.getSlab(), folder + "slab", blockModel, texture);
    stairs(block.getStairs(), folder + "stairs", texture);
  }

  protected void addFenceBuildingBlock(FenceBuildingBlockObject block, String folder, String name, ResourceLocation texture) {
    addBuildingBlock(block, folder, name, texture);
    fence(block.getFence(), folder + "fence/", texture);
  }

  protected void addWood(WoodBlockObject wood, boolean trapdoorOrientable, String doorRenderType) {
    String plankPath = wood.getId().getPath();
    String name = plankPath.substring(0, plankPath.length() - "_planks".length());
    String folder = "block/wood/" + name + "/";
    Function<String,ResourceLocation> texture = suffix -> blockTexture("wood/" + name + "/" + suffix);
    ResourceLocation planks = texture.apply("planks");
    ResourceLocation log = texture.apply("log");
    ResourceLocation stripped = texture.apply("stripped_log");

    // planks, fences and gate
    addFenceBuildingBlock(wood, folder, "planks", planks);
    fenceGate(wood.getFenceGate(), folder + "fence/gate", planks);
    // logs
    axisBlock(wood.getLog(),          folder + "log/log",           log,      true);
    axisBlock(wood.getStrippedLog(),  folder + "log/stripped",      stripped, true);
    axisBlock(wood.getWood(),         folder + "log/wood",          log,      false);
    axisBlock(wood.getStrippedWood(), folder + "log/wood_stripped", stripped, false);
    // doors
    door(wood.getDoor(), folder, doorRenderType, texture.apply("door_bottom"), texture.apply("door_top"));
    basicItem(wood.getDoor(), "wood/");
    trapdoor(wood.getTrapdoor(), folder + "trapdoor_", texture.apply("trapdoor"), trapdoorOrientable);
    // redstone
    pressurePlate(wood.getPressurePlate(), folder + "pressure_plate", planks);
    button(wood.getButton(), folder + "button", planks);
    // signs - particle-only models, item reuses the sign texture flat
    ResourceLocation sign = signModel(folder + "sign", planks);
    simpleBlock(wood.getSign(), sign);
    simpleBlock(wood.getWallSign(), sign);
    basicItem(wood.getSign(), "wood/");
    ResourceLocation hangingSign = signModel(folder + "hanging_sign", stripped);
    simpleBlock(wood.getHangingSign(), hangingSign);
    simpleBlock(wood.getWallHangingSign(), hangingSign);
    basicItem(wood.getHangingSign(), "wood/");
  }

  /** Particle-only model used for sign blocks */
  private ResourceLocation signModel(String path, ResourceLocation particle) {
    ResourceLocation id = getResource(path);
    JsonObject json = new JsonObject();
    json.add("textures", texture("particle", particle));
    blocks.modelOutput.accept(id, () -> json);
    return id;
  }


  /* Shapes (vanilla variant generators with explicit custom model paths) */

  public void slab(Block block, String location, ResourceLocation doubleModel, ResourceLocation texture) {
    JsonObject side = texture("side", texture);
    JsonObject bottom = texture("bottom", texture);
    JsonObject top = texture("top", texture);
    ResourceLocation slab = withParent(location, "minecraft:block/slab", null, side, bottom, top);
    ResourceLocation slabTop = withParent(location + "_top", "minecraft:block/slab_top", null, side, bottom, top);
    blockState(BlockModelGenerators.createSlab(block, slab, slabTop, doubleModel));
    blocks.registerSimpleItemModel(block, slab);
  }

  public void stairs(Block block, String location, ResourceLocation texture) {
    JsonObject side = texture("side", texture);
    JsonObject bottom = texture("bottom", texture);
    JsonObject top = texture("top", texture);
    ResourceLocation stairs = withParent(location, "minecraft:block/stairs", null, side, bottom, top);
    ResourceLocation inner = withParent(location + "_inner", "minecraft:block/inner_stairs", null, side, bottom, top);
    ResourceLocation outer = withParent(location + "_outer", "minecraft:block/outer_stairs", null, side, bottom, top);
    blockState(BlockModelGenerators.createStairs(block, inner, stairs, outer));
    blocks.registerSimpleItemModel(block, stairs);
  }

  public void axisBlock(Block block, String location, ResourceLocation texture, boolean horizontal) {
    ResourceLocation endTexture = horizontal ? texture.withSuffix("_top") : texture;
    ResourceLocation model = cubeColumnModel(location, texture, endTexture);
    if (horizontal) {
      ResourceLocation horizontalModel = withParent(location + "_horizontal", "minecraft:block/cube_column_horizontal", null, texture("side", texture), texture("end", endTexture));
      blockState(BlockModelGenerators.createRotatedPillarWithHorizontalVariant(block, model, horizontalModel));
    } else {
      blockState(BlockModelGenerators.createAxisAlignedPillarBlock(block, model));
    }
    blocks.registerSimpleItemModel(block, model);
  }

  public void fence(Block block, String prefix, ResourceLocation texture) {
    ResourceLocation post = withParent(prefix + "post", "minecraft:block/fence_post", null, texture("texture", texture));
    ResourceLocation side = withParent(prefix + "side", "minecraft:block/fence_side", null, texture("texture", texture));
    blockState(BlockModelGenerators.createFence(block, post, side));
    // inventory item
    ResourceLocation inventory = withParent("item/" + itemName(block), "minecraft:block/fence_inventory", null, texture("texture", texture));
    blocks.registerSimpleItemModel(block.asItem(), inventory);
  }

  public void fenceGate(Block block, String baseName, ResourceLocation texture) {
    JsonObject tex = texture("texture", texture);
    ResourceLocation closed = withParent(baseName, "minecraft:block/template_fence_gate", null, tex);
    ResourceLocation open = withParent(baseName + "_open", "minecraft:block/template_fence_gate_open", null, tex);
    ResourceLocation wall = withParent(baseName + "_wall", "minecraft:block/template_fence_gate_wall", null, tex);
    ResourceLocation wallOpen = withParent(baseName + "_wall_open", "minecraft:block/template_fence_gate_wall_open", null, tex);
    blockState(BlockModelGenerators.createFenceGate(block, open, closed, wallOpen, wall, true));
    blocks.registerSimpleItemModel(block, closed);
  }

  public void door(Block block, String prefix, String renderType, ResourceLocation bottom, ResourceLocation top) {
    JsonObject tex = new JsonObject();
    tex.addProperty("bottom", bottom.toString());
    tex.addProperty("top", top.toString());
    ResourceLocation bottomLeft = withParent(prefix + "door/bottom_left", "minecraft:block/door_bottom_left", renderType, tex);
    ResourceLocation bottomLeftOpen = withParent(prefix + "door/bottom_left_open", "minecraft:block/door_bottom_left_open", renderType, tex);
    ResourceLocation bottomRight = withParent(prefix + "door/bottom_right", "minecraft:block/door_bottom_right", renderType, tex);
    ResourceLocation bottomRightOpen = withParent(prefix + "door/bottom_right_open", "minecraft:block/door_bottom_right_open", renderType, tex);
    ResourceLocation topLeft = withParent(prefix + "door/top_left", "minecraft:block/door_top_left", renderType, tex);
    ResourceLocation topLeftOpen = withParent(prefix + "door/top_left_open", "minecraft:block/door_top_left_open", renderType, tex);
    ResourceLocation topRight = withParent(prefix + "door/top_right", "minecraft:block/door_top_right", renderType, tex);
    ResourceLocation topRightOpen = withParent(prefix + "door/top_right_open", "minecraft:block/door_top_right_open", renderType, tex);
    blockState(BlockModelGenerators.createDoor(block, bottomLeft, bottomLeftOpen, bottomRight, bottomRightOpen, topLeft, topLeftOpen, topRight, topRightOpen));
  }

  public void trapdoor(Block block, String prefix, ResourceLocation texture, boolean orientable) {
    JsonObject tex = texture("texture", texture);
    String base = orientable ? "minecraft:block/template_orientable_trapdoor_" : "minecraft:block/template_trapdoor_";
    ResourceLocation bottom = withParent(prefix + "bottom", base + "bottom", "minecraft:cutout", tex);
    ResourceLocation top = withParent(prefix + "top", base + "top", "minecraft:cutout", tex);
    ResourceLocation open = withParent(prefix + "open", base + "open", "minecraft:cutout", tex);
    blockState(orientable ? BlockModelGenerators.createOrientableTrapdoor(block, top, bottom, open) : BlockModelGenerators.createTrapdoor(block, top, bottom, open));
    blocks.registerSimpleItemModel(block, bottom);
  }

  public void pressurePlate(Block block, String location, ResourceLocation texture) {
    JsonObject tex = texture("texture", texture);
    ResourceLocation up = withParent(location, "minecraft:block/pressure_plate_up", null, tex);
    ResourceLocation down = withParent(location + "_down", "minecraft:block/pressure_plate_down", null, tex);
    blockState(BlockModelGenerators.createPressurePlate(block, up, down));
    blocks.registerSimpleItemModel(block, up);
  }

  public void button(Block block, String location, ResourceLocation texture) {
    JsonObject tex = texture("texture", texture);
    ResourceLocation button = withParent(location, "minecraft:block/button", null, tex);
    ResourceLocation pressed = withParent(location + "_pressed", "minecraft:block/button_pressed", null, tex);
    blockState(BlockModelGenerators.createButton(block, button, pressed));
    ResourceLocation inventory = withParent("item/" + itemName(block), "minecraft:block/button_inventory", null, tex);
    blocks.registerSimpleItemModel(block.asItem(), inventory);
  }


  /* Geode buds (FACING variants, hand-built blockstate) */

  public void bud(Block block, String location, ResourceLocation texture) {
    ResourceLocation cross = withParent(location, "minecraft:block/cross", "minecraft:cutout", texture("cross", texture));
    JsonObject variants = new JsonObject();
    variants.add("facing=up", variant(cross, 0, 0));
    variants.add("facing=down", variant(cross, 180, 0));
    variants.add("facing=north", variant(cross, 90, 0));
    variants.add("facing=east", variant(cross, 90, 90));
    variants.add("facing=south", variant(cross, 90, 180));
    variants.add("facing=west", variant(cross, 90, 270));
    JsonObject blockstate = new JsonObject();
    blockstate.add("variants", variants);
    blockState(rawBlockState(block, blockstate));
  }

  /** Builds a blockstate variant object with optional x/y rotation */
  private static JsonObject variant(ResourceLocation model, int x, int y) {
    JsonObject json = new JsonObject();
    json.addProperty("model", model.toString());
    if (x != 0) json.addProperty("x", x);
    if (y != 0) json.addProperty("y", y);
    return json;
  }

  /** Wraps a raw blockstate JSON object in a {@link BlockStateGenerator} */
  private static BlockStateGenerator rawBlockState(Block block, JsonObject json) {
    return new BlockStateGenerator() {
      @Override
      public Block getBlock() {
        return block;
      }
      @Override
      public JsonElement get() {
        return json;
      }
    };
  }

  public void geode(GeodeItemObject geode, String type) {
    basicItem(geode, "materials/");
    for (BudSize size : BudSize.values()) {
      String name = size.getName();
      if (size != BudSize.CLUSTER) {
        name += "_bud";
      }
      Block budBlock = geode.getBud(size);
      ResourceLocation budTexture = blockTexture("geode/" + type + '/' + name);
      bud(budBlock, "block/geode/" + type + '/' + name, budTexture);
      // bud item uses a parented base-geode item model with layer0 override
      ResourceLocation item = ResourceLocation.fromNamespaceAndPath(itemKey(budBlock).getNamespace(), "item/" + itemName(budBlock));
      JsonObject itemJson = new JsonObject();
      itemJson.addProperty("parent", getResource("item/base/geode/" + name).toString());
      itemJson.add("textures", texture("layer0", budTexture));
      blocks.modelOutput.accept(item, () -> itemJson);
      blocks.registerSimpleItemModel(budBlock.asItem(), item);
    }
    basicBlock(geode.getBlock(), "block/geode/" + type + "/block", blockTexture("geode/" + type + "/block"));
    basicBlock(geode.getBudding(), "block/geode/" + type + "/budding", blockTexture("geode/" + type + "/budding"));
  }


  /* Glass + panes (mantle custom-loader JSON) */

  /** Builds the mantle colored/connected custom-loader fields onto a model json */
  private void applyGlassLoader(JsonObject json, String connectName, int tint, boolean connected, @Nullable String predicate) {
    // PORT 1.21.4: emit the mantle:connected / mantle:colored_block custom-loader JSON (see Mantle client/model/builder).
    // Colour serialization (ColoredBlockModel.ColorData) and runtime registration are verified at runData.
    if (connected) {
      json.addProperty("loader", "mantle:connected");
      JsonObject connection = new JsonObject();
      // PORT 1.21.4 fix: the connection "type" is a bare registry name, not a ResourceLocation. ResourceLocation.parse
      // added the default "minecraft:" namespace ("minecraft:cornerless_full"), which Mantle's ConnectedModelRegistry
      // does not recognise ("Unknown connection type") -> the whole connected glass model failed to load (missing texture).
      JsonObject connectionTextures = new JsonObject();
      connectionTextures.addProperty(connectName, "cornerless_full");
      connection.add("textures", connectionTextures);
      if (predicate != null) {
        connection.addProperty("predicate", predicate);
      }
      json.add("connection", connection);
    } else if (tint != -1) {
      json.addProperty("loader", "mantle:colored_block");
    }
    if (tint != -1) {
      JsonArray colors = new JsonArray();
      JsonObject color = new JsonObject();
      color.addProperty("color", String.format("%08X", tint));
      colors.add(color);
      json.add("colors", colors);
    }
  }

  /** Creates a pane model using the TConstruct templates */
  private ResourceLocation paneModel(String baseName, String variant, ResourceLocation pane, @Nullable ResourceLocation edge, @Nullable String renderType, boolean connected, int tint) {
    ResourceLocation id = getResource("block/" + baseName + variant);
    JsonObject json = new JsonObject();
    json.addProperty("parent", getResource("block/template/pane/" + variant).toString());
    JsonObject textures = new JsonObject();
    textures.addProperty("pane", pane.toString());
    if (edge != null) {
      textures.addProperty("edge", edge.toString());
    }
    json.add("textures", textures);
    if (renderType != null) {
      json.addProperty("render_type", renderType);
    }
    applyGlassLoader(json, "pane", tint, connected, connected ? "pane" : null);
    blocks.modelOutput.accept(id, () -> json);
    return id;
  }

  /** Creates a pane block multipart blockstate */
  public void paneBlock(Block block, String baseName, ResourceLocation pane, ResourceLocation edge, boolean connected, int tint, boolean solidEdge, @Nullable String renderType) {
    ResourceLocation post      = paneModel(baseName, "post",       pane, edge, renderType, connected, tint);
    ResourceLocation side      = paneModel(baseName, "side",       pane, edge, renderType, connected, tint);
    ResourceLocation sideAlt   = paneModel(baseName, "side_alt",   pane, edge, renderType, connected, tint);
    ResourceLocation noSide    = paneModel(baseName, "noside",     pane, null, renderType, connected, tint);
    ResourceLocation noSideAlt = paneModel(baseName, "noside_alt", pane, null, renderType, connected, tint);
    JsonArray parts = new JsonArray();
    parts.add(multipartPart(null, post, 0, false));
    // PipeBlock properties map directions to the connection booleans
    PipeBlock.PROPERTY_BY_DIRECTION.forEach((dir, value) -> {
      if (dir.getAxis().isHorizontal()) {
        boolean alt = dir == Direction.SOUTH;
        int sideRot = dir.getAxis() == Axis.X ? 90 : 0;
        parts.add(multipartWhen(value.getName(), "true", (alt || dir == Direction.WEST) ? sideAlt : side, sideRot));
        int noSideRot = dir == Direction.WEST ? 270 : dir == Direction.SOUTH ? 90 : 0;
        parts.add(multipartWhen(value.getName(), "false", (alt || dir == Direction.EAST) ? noSideAlt : noSide, noSideRot));
      }
    });
    JsonObject blockstate = new JsonObject();
    blockstate.add("multipart", parts);
    blockState(rawBlockState(block, blockstate));
    // item: plain flat (tint handled by the ItemTintSource batch)
    ResourceLocation item = generatedItem(itemKey(block), pane);
    blocks.registerSimpleItemModel(block.asItem(), item);
  }

  /** A multipart entry with an optional {@code when} condition on a single property */
  private static JsonObject multipartWhen(String property, String value, ResourceLocation model, int yRot) {
    JsonObject part = multipartPart(null, model, yRot, false);
    JsonObject when = new JsonObject();
    when.addProperty(property, value);
    part.add("when", when);
    return part;
  }

  private static JsonObject multipartPart(@Nullable JsonObject when, ResourceLocation model, int yRot, boolean uvlock) {
    JsonObject part = new JsonObject();
    if (when != null) {
      part.add("when", when);
    }
    JsonObject apply = variant(model, 0, yRot);
    if (uvlock) {
      apply.addProperty("uvlock", true);
    }
    part.add("apply", apply);
    return part;
  }

  /** Adds models for a glass block with a glass pane */
  public void glassBlock(Block glass, Block pane, String baseName, ResourceLocation front, int tint, boolean solidEdge, @Nullable String renderType) {
    glassBlock(glass, pane, baseName, front, front.withSuffix("_top"), tint, solidEdge, renderType);
  }

  public void glassBlock(Block glass, Block pane, String baseName, ResourceLocation front, ResourceLocation edge, int tint, boolean solidEdge, @Nullable String renderType) {
    // block model: cube_all + connected loader
    ResourceLocation id = getResource("block/" + baseName + "block");
    JsonObject json = new JsonObject();
    json.addProperty("parent", "minecraft:block/cube_all");
    json.add("textures", texture("all", front));
    json.addProperty("render_type", renderType != null ? renderType : "minecraft:cutout");
    applyGlassLoader(json, "all", tint, true, null);
    blocks.modelOutput.accept(id, () -> json);
    basicBlock(glass, id);
    // pane models
    paneBlock(pane, baseName + "pane_", front, edge, true, tint, solidEdge, renderType);
  }
}
