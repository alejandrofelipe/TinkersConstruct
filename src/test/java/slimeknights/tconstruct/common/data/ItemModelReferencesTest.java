package slimeknights.tconstruct.common.data;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every {@code tconstruct:} model referenced by an item definition ({@code assets/tconstruct/items/*.json}) must exist.
 * Two client datagen providers can write the same {@code items/<id>.json}; vanilla's {@code ModelProvider} then emits a
 * default {@code block/<id>} reference that wins over the real definition, and the item renders as the missing model
 * (geode clusters, mob heads and enderbark roots did this since 1.21.4 without any error but a log warning).
 */
class ItemModelReferencesTest {
  private static final Path GENERATED = Path.of("src/generated/resources/assets/tconstruct");
  private static final Path MAIN = Path.of("src/main/resources/assets/tconstruct");
  /** Matches {@code "model": "<id>"} string values; nested unbaked models are objects and are skipped by the quote */
  private static final Pattern MODEL_REFERENCE = Pattern.compile("\"model\"\\s*:\\s*\"tconstruct:([^\"]+)\"");

  @Test
  void itemDefinitionsReferenceExistingModels() throws IOException {
    List<String> missing = new ArrayList<>();
    for (Path root : List.of(GENERATED, MAIN)) {
      Path items = root.resolve("items");
      if (!Files.isDirectory(items)) {
        continue;
      }
      try (Stream<Path> files = Files.walk(items)) {
        for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
          Matcher matcher = MODEL_REFERENCE.matcher(Files.readString(file));
          while (matcher.find()) {
            String model = matcher.group(1) + ".json";
            if (!Files.exists(GENERATED.resolve("models").resolve(model)) && !Files.exists(MAIN.resolve("models").resolve(model))) {
              missing.add(items.relativize(file) + " -> tconstruct:" + matcher.group(1));
            }
          }
        }
      }
    }
    assertThat(missing).as("item definitions referencing missing models").isEmpty();
  }
}
