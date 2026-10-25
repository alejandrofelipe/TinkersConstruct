package slimeknights.tconstruct.library.data;

import net.minecraft.world.item.equipment.ArmorType;

import com.google.common.collect.Streams;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.PackOutput.PathProvider;
import net.minecraft.data.PackOutput.Target;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import slimeknights.tconstruct.library.tools.definition.ModifiableArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.ApiStatus.NonExtendable;
import slimeknights.mantle.data.GenericDataProvider;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.registration.object.EnumObject;
import slimeknights.mantle.registration.object.IdAwareObject;
import slimeknights.tconstruct.library.tools.item.ranged.ModifiableCrossbowItem;
import slimeknights.tconstruct.library.tools.item.ranged.ModifiableLauncherItem;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Helper for generating tool item models.
 * PORT 1.21.4: the {@code tconstruct:tool} block-model loader no longer exists, tools render through the inline
 * {@code tconstruct:tool} item model in {@code items/<id>.json}. The legacy source models are read from
 * {@code tool_models/<id>.json} (see {@link #readToolJson}) so the model manager does not try to load them, and the
 * derived legacy models (broken, pulling, charging, blocking...) are only kept in {@link #models} instead of being written.
 */
public abstract class AbstractToolItemModelProvider extends GenericDataProvider {
  /** Derived legacy models by path, not written; feeds the broken variant of the item definitions */
  protected final Map<String,JsonObject> models = new HashMap<>();
  /** PORT 1.21.4: item-model definitions (assets/<ns>/items/<id>.json), keyed by item id */
  protected final Map<ResourceLocation,JsonObject> itemModels = new HashMap<>();
  private final PathProvider itemPath;
  protected final ResourceManager resourceManager;
  protected final String modId;
  public AbstractToolItemModelProvider(PackOutput packOutput, ResourceManager resourceManager, String modId) {
    super(packOutput, Target.RESOURCE_PACK, "models/item");
    this.itemPath = packOutput.createPathProvider(Target.RESOURCE_PACK, "items");
    this.resourceManager = resourceManager;
    this.modId = modId;
  }

  /** Add all relevant models */
  protected abstract void addModels() throws IOException;

  @Override
  public CompletableFuture<?> run(CachedOutput cache) {
    try {
      addModels();
    } catch (IOException e) {
      return CompletableFuture.failedFuture(e);
    }
    return allOf(itemModels.entrySet().stream().map((entry) -> DataProvider.saveStable(cache, entry.getValue(), itemPath.json(entry.getKey()))));
  }

  /**
   * Converts a legacy {@code tconstruct:tool} loader model (parent/textures/parts/modifier_maps/overrides) into the 1.21.4
   * {@code tconstruct:tool} {@link net.minecraft.client.renderer.item.ItemModel.Unbaked} object, matching
   * {@code ToolModel.Unbaked.MAP_CODEC}. The legacy {@code modifier_roots} has no codec field (modifier models now resolve
   * via {@code modifier_maps}) and {@code overrides} become the dispatch tree, so both are dropped here.
   */
  protected JsonObject convertToolDef(JsonObject legacy) {
    JsonObject def = new JsonObject();
    def.addProperty("type", "tconstruct:tool");
    if (legacy.has("parent")) {
      def.addProperty("transform_model", GsonHelper.getAsString(legacy, "parent"));
    }
    for (String key : new String[]{"textures", "parts", "modifier_maps", "large", "large_offset", "show_traits"}) {
      if (legacy.has(key)) {
        def.add(key, legacy.get(key));
      }
    }
    // PORT 1.21.4: the legacy loader took first_modifiers as a plain string array, but ToolModel.Unbaked's
    // FirstModifier.CODEC expects objects ({"name": <id>, "forced": <bool>}). Convert each bare string to that form;
    // a string array left as-is fails to parse and the whole item model falls back to the missing (magenta) model.
    if (legacy.has("first_modifiers")) {
      JsonArray converted = new JsonArray();
      for (JsonElement element : legacy.getAsJsonArray("first_modifiers")) {
        if (element.isJsonPrimitive()) {
          JsonObject modifier = new JsonObject();
          modifier.addProperty("name", element.getAsString());
          converted.add(modifier);
        } else {
          converted.add(element);
        }
      }
      def.add("first_modifiers", converted);
    }
    return def;
  }

  /**
   * Emits the {@code items/<id>.json} item-model definition for a tool, dispatching the broken variant via a
   * {@code minecraft:condition} on {@code tconstruct:broken}.
   * PORT 1.21.4: the charging/blocking display override and the ranged (pulling/ammo/charge) dispatches are not yet
   * reconstructed here; ranged tools (bows, staves) + armor/shields still need their items/ definitions.
   */
  protected void toolItem(ResourceLocation id, JsonObject source, @Nullable JsonObject broken) {
    JsonObject model = convertToolDef(source);
    if (broken != null) {
      JsonObject condition = new JsonObject();
      condition.addProperty("type", "minecraft:condition");
      condition.addProperty("property", "tconstruct:broken");
      condition.add("on_true", convertToolDef(broken));
      condition.add("on_false", model);
      model = condition;
    }
    JsonObject def = new JsonObject();
    def.add("model", model);
    itemModels.put(id, def);
  }


  /* Model types */

  /** Creates models for blocking and broken for the given tool */
  protected void tool(IdAwareObject<ResourceLocation> tool, @Nullable JsonObject blocking, String... brokenParts) throws IOException {
    ResourceLocation id = tool.getId();
    String name = id.getPath();
    JsonObject source = readToolJson(id);
    if (blocking != null) {
      withDisplay("tool/" + name + "/blocking", id, blocking);
    }
    JsonObject broken = transformTool("tool/" + name + "/broken", source, "", false, '_', "broken", brokenParts);
    toolItem(id, source, broken);
  }

  /**
   * Emits the base {@code items/<id>.json} definition for a tool that has no broken/charging/ammo states (e.g. thrown
   * weapons like shuriken, throwing axe, arrow). The legacy source model carries no {@code overrides}.
   */
  protected void basicTool(IdAwareObject<ResourceLocation> tool) throws IOException {
    ResourceLocation id = tool.getId();
    toolItem(id, readToolJson(id), null);
  }

  /** Logic for creating models for the given ammo type */
  @NonExtendable
  protected interface AmmoHandler {
    default void apply(AbstractToolItemModelProvider self, String name, JsonObject base, JsonObject properties, int pullingCount, String[] pullingParts) {
      for (int i = 1; i <= pullingCount; i++) {
        String pulling = "tool/" + name + "/pulling_" + i;
        self.transformTool(pulling, base, "", false, '/', Integer.toString(i), pullingParts);
        self.withDisplay("tool/" + name + "/blocking_" + i, self.resource(pulling), properties);
      }
    }
  }

  /** Default staticly display ammo for bows or crossbows */
  public enum AmmoType implements AmmoHandler {
    CROSSBOW {
      @Override
      public void apply(AbstractToolItemModelProvider self, String name, JsonObject base, JsonObject properties, int pullingCount, String[] pullingParts) {
        // crossbows have two ammo states
        String arrowName = "tool/" + name + "/arrow";
        String fireworkName = "tool/" + name + "/firework";
        JsonObject ammoBase = suffixTextures(base.deepCopy(), "3", pullingParts);
        self.models.put(arrowName, self.addPart(ammoBase.deepCopy(), "ammo", name, "arrow"));
        self.models.put(fireworkName, self.addPart(ammoBase.deepCopy(), "ammo", name, "firework"));
        self.withDisplay("tool/" + name + "/arrow_blocking", self.resource(arrowName), properties);
        self.withDisplay("tool/" + name + "/firework_blocking", self.resource(fireworkName), properties);
        // apply default blocking and pulling
        super.apply(self, name, base, properties, pullingCount, pullingParts);
      }
    },
    BOW {
      @Override
      public void apply(AbstractToolItemModelProvider self, String name, JsonObject base, JsonObject properties, int pullingCount, String[] pullingParts) {
        // create charging tools before adding in the arrow
        String[] pullingWithArrow = Streams.concat(Stream.of("arrow"), Arrays.stream(pullingParts)).toArray(String[]::new);
        // bows have an arrow part that pulls back
        JsonObject withArrow = self.addPart(base.deepCopy(), "arrow", name, "arrow");
        for (int i = 1; i <= pullingCount; i++) {
          String pulling = "tool/" + name + "/pulling_arrow_" + i;
          self.transformTool(pulling, withArrow, "", false, '/', Integer.toString(i), pullingWithArrow);
          self.withDisplay("tool/" + name + "/blocking_arrow_" + i, self.resource(pulling), properties);
        }
        // apply default blocking and pulling
        super.apply(self, name, base, properties, pullingCount, pullingParts);
      }
    },
    NONE;
  }

  /** Serializes the vector to an JSON array */
  private static JsonElement serializeVec2(Vec2 vec2) {
    JsonArray array = new JsonArray();
    array.add(vec2.x);
    array.add(vec2.y);
    return array;
  }

  /** Crossbow displaying ammo as an item stack instead of statically */
  public record CrossbowAmmo(Vec2 ammoOffset, boolean flipAmmo, boolean leftAmmo) implements AmmoHandler {
    @Override
    public void apply(AbstractToolItemModelProvider self, String name, JsonObject base, JsonObject properties, int pullingCount, String[] pullingParts) {
      // crossbows have two ammo states
      String fireworkName = "tool/" + name + "/firework";
      JsonObject ammoBase = suffixTextures(base.deepCopy(), "3", pullingParts);
      self.models.put(fireworkName, self.addPart(ammoBase.deepCopy(), "ammo", name, "firework"));
      self.withDisplay("tool/" + name + "/firework_blocking", self.resource(fireworkName), properties);

      // apply default blocking and pulling
      for (int i = 1; i < pullingCount; i++) {
        String pulling = "tool/" + name + "/pulling_" + i;
        self.transformTool(pulling, base, "", false, '/', Integer.toString(i), pullingParts);
        self.withDisplay("tool/" + name + "/blocking_" + i, self.resource(pulling), properties);
      }
      String pulling = "tool/" + name + "/pulling_" + pullingCount;
      JsonObject arrow = self.transformTool(pulling, base, "", false, '/', Integer.toString(pullingCount), pullingParts);
      self.withDisplay("tool/" + name + "/blocking_" + pullingCount, self.resource(pulling), properties);
      // add the arrow to pulling 3, ToolModel handles not showing it when it has no ammo
      {
        JsonObject ammo = new JsonObject();
        ammo.addProperty("key", ModifiableCrossbowItem.KEY_CROSSBOW_AMMO.toString());
        ammo.addProperty("flip", flipAmmo);
        ammo.addProperty("left", leftAmmo);
        ammo.add("offset", serializeVec2(ammoOffset));
        arrow.add("ammo", ammo);
      }
    }
  }

  /** Longbow displaying ammo on each pulling and blocking variant */
  public record LongbowAmmo(Vec2[] smallOffsets, Vec2[] largeOffsets, boolean flipAmmo, boolean leftAmmo) implements AmmoHandler {
    @Override
    public void apply(AbstractToolItemModelProvider self, String name, JsonObject base, JsonObject properties, int pullingCount, String[] pullingParts) {
      // bows have an arrow part that pulls back
      for (int i = 1; i <= pullingCount; i++) {
        String pulling = "tool/" + name + "/pulling_" + i;
        JsonObject arrow = self.transformTool(pulling, base, "", false, '/', Integer.toString(i), pullingParts);
        {
          JsonObject ammo = new JsonObject();
          ammo.addProperty("key", ModifiableLauncherItem.KEY_DRAWBACK_AMMO.toString());
          ammo.addProperty("flip", flipAmmo);
          ammo.addProperty("left", leftAmmo);
          ammo.add("small_offset", serializeVec2(smallOffsets[i-1]));
          ammo.add("large_offset", serializeVec2(largeOffsets[i-1]));
          arrow.add("ammo", ammo);
        }
        self.withDisplay("tool/" + name + "/blocking_" + i, self.resource(pulling), properties);
      }
    }
  }

  /** Creates a model in the blocking folder with the given copied display */
  protected void bow(IdAwareObject<ResourceLocation> bow, JsonObject properties, boolean crossbow, String... pullingParts) throws IOException {
    bow(bow, properties, crossbow ? AmmoType.CROSSBOW : AmmoType.BOW, pullingParts);
  }

  /** Creates a model in the blocking folder with the given copied display */
  protected void bow(IdAwareObject<ResourceLocation> bow, JsonObject properties, AmmoHandler ammo, String... pullingParts) throws IOException {
    pulling(bow, properties, ammo, "bowstring", 3, pullingParts);
  }

  /** Creates a model in the blocking folder with the given copied display */
  protected void pulling(IdAwareObject<ResourceLocation> bow, JsonObject blocking, AmmoHandler ammo, String brokenPart, int pullingCount, String... pullingParts) throws IOException {
    ResourceLocation id = bow.getId();
    String name = id.getPath();
    JsonObject base = readToolJson(id);
    base.remove("overrides"); // don't need them anywhere, notably ditching for the sake of ammo models
    JsonObject broken = transformTool("tool/" + name + "/broken", base, "", false, '/', "broken", brokenPart);
    withDisplay("tool/" + name + "/blocking", id, blocking);

    // apply ammo specific code
    ammo.apply(this, name, base, blocking, pullingCount, pullingParts);
    // PORT 1.21.4: emit the base + broken items/ definition so the resting (GUI) item renders
    toolItem(id, base, broken);
  }

  /** Creates models for blocking, broken and fully charged for the given tool */
  protected void charged(IdAwareObject<ResourceLocation> bow, JsonObject properties, String... brokenParts) throws IOException {
    ResourceLocation id = bow.getId();
    String name = id.getPath();
    JsonObject base = readToolJson(id);
    base.remove("overrides");
    withDisplay("tool/" + name + "/blocking", id, properties);
    JsonObject broken = transformTool("tool/" + name + "/broken", base, "", false, '/', "broken", brokenParts);
    // PORT 1.21.4: emit the base + broken items/ definition before addPart mutates base with the charged overlay
    toolItem(id, base.deepCopy(), broken);

    addPart(base, "overlay", name, "overlay");

    String charged = "tool/" + name + "/charged";
    transformTool(charged, base, "", false, '/', "charged", "overlay");
    withDisplay("tool/" + name + "/blocking_charged", resource(charged), properties);
  }

  /** Creates a model in the blocking folder with the given copied display */
  protected void staff(IdAwareObject<ResourceLocation> staff, JsonObject properties) throws IOException {
    ResourceLocation id = staff.getId();
    String path = id.getPath();
    String name = path.substring(0, path.length() - "_staff".length());
    JsonObject base = readToolJson(id);
    withDisplay("tool/staff/" + name + "/blocking", id, properties);
    JsonObject broken = transformTool("tool/staff/" + name + "/broken", base, "", true, '/', "broken", "tool");
    for (int i = 1; i <= 5; i++) {
      String charging = "tool/staff/" + name + "/charging_" + i;
      transformTool(charging, base, "tconstruct:item/base/staff_charging", false, '/', Integer.toString(i), "tool");
      withDisplay("tool/staff/" + name + "/blocking_" + i, resource(charging), properties);
    }
    // PORT 1.21.4: emit the base + broken items/ definition so the resting (GUI) item renders
    toolItem(id, base, broken);
  }

  /** Adds broken and blocking models for the shield */
  protected void shield(String setName, IdAwareObject<ResourceLocation> shield, JsonObject properties, String... parts) throws IOException {
    ResourceLocation id = shield.getId();
    JsonObject base = readToolJson(id);
    withDisplay("armor/" + setName + "/shield_blocking", id, Objects.requireNonNull(properties));
    JsonObject broken = transformTool("armor/" + setName + "/shield_broken", base, "", false, '_', "broken", parts);
    // PORT 1.21.4: emit the base + broken items/ definition so the resting (GUI) item renders
    toolItem(id, base, broken);
  }

  /** Adds broken and blocking models for the armor item */
  protected void armor(String overrideName, ResourceLocation itemId, String... textures) throws IOException {
    JsonObject base = readToolJson(itemId);
    JsonObject broken = transformTool("armor/" + overrideName + "_broken", base, "", false, '_', "broken", textures);
    // PORT 1.21.4: emit the base + broken items/ definition so the resting (GUI) item renders
    toolItem(itemId, base, broken);
  }

  /** Adds broken and blocking models for the armor item */
  protected void armor(String overrideName, IdAwareObject<ResourceLocation> item, String... textures) throws IOException {
    armor(overrideName, item.getId(), textures);
  }

  /** Adds broken and blocking models for the armor item */
  protected void armor(String name, EnumObject<ArmorType,? extends Item> armor, ArmorType slot, String... textures) throws IOException {
    armor(name + '/' + slot.getName(), Loadables.ITEM.getKey(armor.get(slot)), textures);
  }

  /** Adds broken and blocking models for the armor set */
  protected void armor(String name, EnumObject<ArmorType,? extends Item> armor, ArmorType[] types, String... textures) throws IOException {
    for (ArmorType slot : types) {
      armor(name, armor, slot, textures);
    }
  }

  /** Adds broken and blocking models for the armor set */
  protected void armor(String name, EnumObject<ArmorType,? extends Item> armor, String... textures) throws IOException {
    armor(name, armor, ModifiableArmorMaterial.ARMOR_SLOTS, textures);
  }

  /** Creates models for fishing rods cast and broken */
  @SuppressWarnings("SameParameterValue") // API
  protected void fishingRod(IdAwareObject<ResourceLocation> tool, @Nullable JsonObject blocking, String[] castParts, String[] brokenParts) throws IOException {
    ResourceLocation id = tool.getId();
    String name = id.getPath();
    JsonObject base = readToolJson(id);
    String cast = "tool/" + name + "/cast";
    transformTool(cast,   base, "", false, '/', "cast", castParts);
    JsonObject broken = transformTool("tool/" + name + "/broken", base, "", false, '/', "broken", brokenParts);
    if (blocking != null) {
      withDisplay("tool/" + name + "/blocking", id, blocking);
      withDisplay("tool/" + name + "/blocking_cast", resource(cast), blocking);
    }
    // PORT 1.21.4: emit the base + broken items/ definition so the resting (GUI) item renders
    toolItem(id, base, broken);
  }

  /* Helpers */

  /** Reads a JSON file */
  protected JsonObject readJson(ResourceLocation path) throws IOException {
    return readResource(ResourceLocation.fromNamespaceAndPath(path.getNamespace(), "models/item/" + path.getPath() + ".json"));
  }

  /** Reads the legacy {@code tconstruct:tool} source model for a tool, kept outside {@code models/} as nothing can load it at runtime */
  protected JsonObject readToolJson(ResourceLocation tool) throws IOException {
    return readResource(ResourceLocation.fromNamespaceAndPath(tool.getNamespace(), "tool_models/" + tool.getPath() + ".json"));
  }

  /** Reads a JSON file at the given full resource path */
  private JsonObject readResource(ResourceLocation fullPath) throws IOException {
    try (BufferedReader reader = resourceManager.getResource(fullPath).orElseThrow(() -> new IOException("Missing model " + fullPath)).openAsReader()) {
      return GsonHelper.parse(reader);
    }
  }

  /** Creates a resource location under this mod */
  @SuppressWarnings("removal")
  protected ResourceLocation resource(String name) {
    return ResourceLocation.fromNamespaceAndPath(modId, name);
  }

  /** Creates a model with display from the given target */
  protected void withDisplay(String destination, ResourceLocation parent, JsonObject properties) {
    JsonObject model = new JsonObject();
    model.addProperty("parent", parent.withPrefix("item/").toString());
    model.add("display", properties.get("display"));
    models.put(destination, model);
  }

  /** Adds a new root to the array with the given suffix */
  protected static JsonArray copyAndSuffixRoot(JsonArray array, String suffix, boolean allRoots) {
    JsonArray newArray = new JsonArray();
    boolean first = true;
    for (JsonElement element : array) {
      if (allRoots || first) {
        newArray.add(element.getAsString() + suffix);
      }
      newArray.add(element);
      first = false;
    }
    return newArray;
  }

  /** Adds a part to the given tool */
  protected JsonObject addPart(JsonObject tool, String part, String toolName, String texture) {
    JsonObject textures = tool.getAsJsonObject("textures");
    // add the texture
    boolean large = GsonHelper.getAsBoolean(tool, "large", false);
    textures.addProperty(part, resource("item/tool/" + toolName + "/" + texture).toString());
    if (large) {
      textures.addProperty("large_" + part, resource("item/tool/" + toolName + "/large/" + texture).toString());
    }
    JsonObject partObject = new JsonObject();
    partObject.addProperty("name", part);
    tool.getAsJsonArray("parts").add(partObject);
    return tool;
  }

  /** Suffixes the passed textures with the given suffix */
  protected static JsonObject suffixTextures(JsonObject tool, String suffix, String... updateTextures) {
    // update parts that we were told to update
    boolean large = GsonHelper.getAsBoolean(tool, "large", false);
    JsonObject textures = tool.getAsJsonObject("textures");
    for (String part : updateTextures) {
      textures.addProperty(part, GsonHelper.getAsString(textures, part) + '_' + suffix);
      if (large) {
        textures.addProperty("large_" + part, GsonHelper.getAsString(textures, "large_" + part) + '_' + suffix);
      }
    }
    return tool;
  }

  /** Transforms the given tool by adding suffixes to listed textures and the modifier roots */
  @Deprecated
  protected JsonObject transformTool(String destination, JsonObject tool, String parent, boolean allRoots, String suffix, String... updateTextures) {
    return transformTool(destination, tool, parent, allRoots, '_', suffix, updateTextures);
  }

  /** Transforms the given tool by adding suffixes to listed textures and the modifier roots */
  protected JsonObject transformTool(String destination, JsonObject tool, String parent, boolean allRoots, char mapSeparator, String suffix, String... updateTextures) {
    JsonObject transformed = tool.deepCopy();
    // set parent if given
    if (!parent.isEmpty()) {
      transformed.addProperty("parent", parent);
    }
    // update parts that we were told to update
    suffixTextures(transformed, suffix, updateTextures);
    // add modifier roots
    if (transformed.has("modifier_roots")) {
      if (GsonHelper.getAsBoolean(transformed, "large", false)) {
        JsonObject roots = transformed.getAsJsonObject("modifier_roots");
        roots.add("small", copyAndSuffixRoot(roots.getAsJsonArray("small"), suffix + '/', allRoots));
        roots.add("large", copyAndSuffixRoot(roots.getAsJsonArray("large"), suffix + '/', allRoots));
      } else {
        transformed.add("modifier_roots", copyAndSuffixRoot(transformed.getAsJsonArray("modifier_roots"), suffix + '/', allRoots));
      }
    }
    if (transformed.has("modifier_maps")) {
      transformed.add("modifier_maps", copyAndSuffixRoot(transformed.getAsJsonArray("modifier_maps"), mapSeparator + suffix, allRoots));
    }
    // delete overrides, no need to nest them
    transformed.remove("overrides");
    models.put(destination, transformed);
    return transformed;
  }
}
