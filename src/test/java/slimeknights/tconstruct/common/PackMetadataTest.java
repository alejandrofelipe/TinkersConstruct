package slimeknights.tconstruct.common;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.InclusiveRange;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.test.BaseMcTest;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/** Tinkers' {@code pack.mcmeta} must parse without errors for every reader of mod pack metadata */
class PackMetadataTest extends BaseMcTest {
  /** 1.21.10 data pack format, the newest of the two pack types */
  private static final PackFormat DATA_FORMAT = PackFormat.of(88);

  /** Finds the {@code pack} section of Tinkers' {@code pack.mcmeta} (Mantle's is on the classpath too) */
  private static JsonObject packSection() throws IOException {
    for (URL url : Collections.list(PackMetadataTest.class.getClassLoader().getResources("pack.mcmeta"))) {
      try (Reader reader = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)) {
        JsonObject pack = GsonHelper.getAsJsonObject(JsonParser.parseReader(reader).getAsJsonObject(), "pack");
        if ("Tinkers Construct Resources".equals(GsonHelper.getAsString(pack, "description", ""))) {
          return pack;
        }
      }
    }
    throw new AssertionError("Tinkers' pack.mcmeta is not on the test classpath");
  }

  private static void assertParses(DataResult<?> result, String reader) {
    assertThat(result.error()).as(reader).isEmpty();
  }

  private static void assertVanillaParses(MetadataSectionType<PackMetadataSection> type, String reader) throws IOException {
    DataResult<PackMetadataSection> result = type.codec().parse(JsonOps.INSTANCE, packSection());
    assertParses(result, reader);
    InclusiveRange<PackFormat> formats = result.getOrThrow().supportedFormats();
    assertThat(formats.isValueInRange(DATA_FORMAT)).as(reader + " formats " + formats).isTrue();
  }

  /** The client requires min_format/max_format above format 64 and forbids supported_formats there */
  @Test
  void vanillaClient_parses() throws IOException {
    assertVanillaParses(PackMetadataSection.CLIENT_TYPE, "client resources");
  }

  /** The server requires min_format/max_format above format 81 and supported_formats up to it */
  @Test
  void vanillaServer_parses() throws IOException {
    assertVanillaParses(PackMetadataSection.SERVER_TYPE, "server data");
  }

  /** JSON Things reads every mod's pack.mcmeta in its thingpack repository, where formats after 11 need min/max_format */
  @Test
  void jsonThingsThingpackRepository_parses() throws IOException {
    DataResult<InclusiveRange<PackFormat>> result = PackFormat.IntermediaryFormat.PACK_CODEC.codec().parse(JsonOps.INSTANCE, packSection())
      .flatMap(format -> format.validate(11, true, false, "Pack", "supported_formats"));
    assertParses(result, "thingpack repository (last old format 11)");
  }
}
