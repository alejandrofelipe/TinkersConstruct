package slimeknights.tconstruct.world.worldgen.islands;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.test.JsonFileLoader;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates that every NBT template referenced by a {@code tconstruct:island} structure exists — and
 * carries blocks — at the path MC 1.21's {@code StructureTemplateManager} loads from:
 * {@code data/<ns>/structure/<path>.nbt} (singular).
 *
 * <p>Regression guard for the 1.20.5/1.21 datapack directory rename {@code structures/} → {@code structure/}.
 * The island templates were left in the pre-1.21 plural folder, so {@code StructureTemplateManager.getOrCreate}
 * silently returned an empty template and {@code /locate}-able islands generated with zero blocks.
 */
class IslandTemplateResourceTest {
  /** All six data-driven {@code tconstruct:island} structures (worldgen/structure/*.json). */
  private static final List<String> ISLAND_STRUCTURES = List.of(
    "earth_slime_island", "sky_slime_island", "ocean_skyslime_island",
    "clay_island", "blood_island", "end_slime_island");

  private final JsonFileLoader loader = new JsonFileLoader(new Gson(), "worldgen/structure");

  @Test
  void everyIslandTemplateResolvesToNonEmptyNbtAtSingularStructurePath() {
    List<String> missing = new ArrayList<>();
    List<String> empty = new ArrayList<>();
    int checked = 0;

    for (String structure : ISLAND_STRUCTURES) {
      JsonObject json = loader.loadJson("tconstruct", structure);
      assertThat(json.get("type").getAsString())
        .as("%s should be a tconstruct:island structure", structure)
        .isEqualTo("tconstruct:island");

      JsonArray templates = json.getAsJsonArray("templates");
      assertThat(templates).as("%s should declare templates", structure).isNotNull();

      for (JsonElement element : templates) {
        String data = element.getAsJsonObject().get("data").getAsString();
        ResourceLocation id = ResourceLocation.parse(data);
        // MC 1.21 StructureTemplateManager resolves templates under the singular structure/ directory
        String nbtPath = "data/" + id.getNamespace() + "/structure/" + id.getPath() + ".nbt";
        checked++;

        URL resource = getClass().getClassLoader().getResource(nbtPath);
        if (resource == null) {
          missing.add(nbtPath);
          continue;
        }
        // present -> also assert it actually carries blocks (a non-empty template)
        try (InputStream in = resource.openStream()) {
          CompoundTag nbt = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
          ListTag size = nbt.getList("size", Tag.TAG_INT);
          boolean hasVolume = size.size() == 3 && size.getInt(0) > 0 && size.getInt(1) > 0 && size.getInt(2) > 0;
          boolean hasBlocks = !nbt.getList("blocks", Tag.TAG_COMPOUND).isEmpty();
          if (!hasVolume || !hasBlocks) {
            empty.add(nbtPath);
          }
        } catch (Exception e) {
          empty.add(nbtPath + " (" + e.getMessage() + ")");
        }
      }
    }

    assertThat(checked).as("should have discovered island templates").isGreaterThan(0);
    assertThat(missing)
      .as("island templates missing at the singular structure/ path (the pre-1.21 structures/ rename was not applied)")
      .isEmpty();
    assertThat(empty).as("island templates present but empty (no blocks)").isEmpty();
  }
}
