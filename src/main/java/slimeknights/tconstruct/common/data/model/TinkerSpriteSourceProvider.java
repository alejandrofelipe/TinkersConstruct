package slimeknights.tconstruct.common.data.model;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.renderer.texture.atlas.sources.DirectoryLister;
import net.minecraft.client.renderer.texture.atlas.sources.PalettedPermutations;
import net.minecraft.client.renderer.texture.atlas.sources.SingleFile;
import net.minecraft.client.renderer.texture.atlas.sources.SourceFilter;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.AtlasIds;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.util.IdentifierPattern;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.neoforged.neoforge.client.data.SpriteSourceProvider;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.modifiers.model.TrimModifierModel;
import slimeknights.tconstruct.library.client.modifiers.model.TrimModifierModel.Armor;
import slimeknights.tconstruct.tools.client.ShieldBannerModifierSpriteSource;
import slimeknights.tconstruct.tools.data.material.MaterialIds;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provider to stitch textures from additional folders
 */
public class TinkerSpriteSourceProvider extends SpriteSourceProvider {
  /** List of trim variants supported, must all exist in vanilla */
  private static final String[] TRIMS = {
    "coast", "sentry", "dune", "wild", "ward", "eye", "vex", "tide", "snout",
    "rib", "spire", "wayfinder", "shaper", "silence", "raiser", "host"
  };
  private static final String PALETTE_FOLDER = "trims/color_palettes/";
  /** 1.21.4: vanilla's worn trim textures, was {@code trims/models/armor/<pattern>(_leggings)} */
  private static final String TRIM_FOLDER = "trims/entity/humanoid/";
  private static final String TRIM_LEGGINGS_FOLDER = "trims/entity/humanoid_leggings/";

  public TinkerSpriteSourceProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
    super(output, lookupProvider, TConstruct.MOD_ID);
  }

  @SuppressWarnings("removal")
  @Override
  protected void gather() {
    Identifier trimPalette = Identifier.parse(PALETTE_FOLDER + "trim_palette");
    // map of material suffix to material paeltte for trims
    Map<String,Identifier> tinkerMaterials = Arrays.stream(MaterialIds.TRIM_MATERIALS).collect(Collectors.toMap(id -> id.getNamespace() + "_" + id.getPath(), id -> id.getLocation().withPrefix(PALETTE_FOLDER)));
    Map<String,Identifier> vanillaMaterials = new HashMap<>();
    addVanilla(vanillaMaterials, TrimMaterials.QUARTZ);
    addVanilla(vanillaMaterials, TrimMaterials.IRON);
    addVanilla(vanillaMaterials, TrimMaterials.NETHERITE);
    addVanilla(vanillaMaterials, TrimMaterials.REDSTONE);
    addVanilla(vanillaMaterials, TrimMaterials.COPPER);
    addVanilla(vanillaMaterials, TrimMaterials.GOLD);
    addVanilla(vanillaMaterials, TrimMaterials.EMERALD);
    addVanilla(vanillaMaterials, TrimMaterials.DIAMOND);
    addVanilla(vanillaMaterials, TrimMaterials.LAPIS);
    addVanilla(vanillaMaterials, TrimMaterials.AMETHYST);
    // custom armor "modifier" textures that use the trim materials
    List<Identifier> customItemTrims = Stream.of("item/tool/armor/travelers/goggles/trim", "item/tool/armor/slime/wings/trim").map(TConstruct::getResource).toList();

    SourceList blocks = atlas(AtlasIds.BLOCKS)
      // We load our fluid textures from here
      .addSource(directory("fluid"))
      // PORT 1.21.4: material part / tool textures are looked up dynamically by name at bake time
      // (MaterialModel / ToolModel resolve item/tool/<part>_<material> and item/tool/<tool>/<part> against the
      // block atlas). The removed IUnbakedGeometry system used to declare these as model texture dependencies so
      // the stitcher picked them up; ItemModel.Unbaked has no such hook, so without this directory source the
      // greyscale bases and the ~10k generated material permutations are absent from the atlas -> missing texture.
      .addSource(directory("item/tool"))
      // patterns load from this directory
      .addSource(directory("gui/modifiers"))
      // we typically use this directory for modifier icons that are not items nor blocks
      .addSource(directory("gui/tinker_pattern"))
      // trim armor icons
      .addSource(new PalettedPermutations(
        Stream.concat(Arrays.stream(Armor.values()).map(armor -> armor.getRoot(false)), customItemTrims.stream()).toList(),
        trimPalette, tinkerMaterials))
      // trim shield icons
      .addSource(new PalettedPermutations(customItemTrims, trimPalette, vanillaMaterials))
      // banner modifier icons
      .addSource(new ShieldBannerModifierSpriteSource(2, 2, 10, 20, TConstruct.getResource("item/tool/armor/plate/shield/banner_large/"), 11, 8, 32));
    // 1.21.11: vanilla's trim item icons in vanilla materials (trims/items/<slot>_trim_<material>) are on the items atlas, so
    // TrimModifierModel looks them up there; stitching them here too would define each sprite in two atlases.
    // add untinted trim textures, we use them as fallbacks
    for (Armor armor : TrimModifierModel.Armor.values()) {
      blocks.addSource(new SingleFile(armor.getRoot(false), Optional.empty()));
    }
    // PORT 1.21.11: vanilla's items atlas now lists every namespace's item/ folder, which would stitch item/tool/ (~11k
    // sprites, mostly generated material permutations) a second time. The blocks atlas stays their home (tool models mix
    // them with block-atlas fluid sprites in one quad list); BLOCK_OR_ITEM lookups fall back to blocks when items lacks one.
    // Pack sources run after vanilla's, so this filter removes the copy vanilla's directory source added.
    atlas(AtlasIds.ITEMS)
      .addSource(new SourceFilter(identifierPattern("^" + TConstruct.MOD_ID + "$", "^item/tool/")));
    // add armor trims in our materials
    atlas(Identifier.parse("armor_trims"))
      .addSource(new PalettedPermutations(
        Arrays.stream(TRIMS).flatMap(name -> Stream.of(Identifier.parse(TRIM_FOLDER + name), Identifier.parse(TRIM_LEGGINGS_FOLDER + name))).toList(),
        trimPalette, tinkerMaterials));
  }

  /** Creates a directory lister where the source matches the prefix. */
  private static DirectoryLister directory(String path) {
    return new DirectoryLister(path, path + '/');
  }

  /** Creates an identifier pattern (regexes use find semantics, so anchor them); the constructor is private, so go through the codec. */
  private static IdentifierPattern identifierPattern(String namespace, String path) {
    JsonObject json = new JsonObject();
    json.addProperty("namespace", namespace);
    json.addProperty("path", path);
    return IdentifierPattern.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
  }

  /** Adds a vanilla material to the map */
  private static void addVanilla(Map<String,Identifier> map, ResourceKey<TrimMaterial> material) {
    Identifier path = material.identifier();
    map.put(path.getPath(), path.withPrefix(PALETTE_FOLDER));
  }
}
