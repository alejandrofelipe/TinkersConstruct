package slimeknights.mantle.data;

import com.google.common.hash.Hashing;
import net.minecraft.SharedConstants;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DataProvider;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.data.event.GatherDataEvent.DataGeneratorConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import slimeknights.tconstruct.test.BaseMcTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatagenCacheKeeperTest extends BaseMcTest {
  @TempDir
  Path output;

  /** Writes an output file and the cache file of the given provider listing it, like a previous data run */
  private Path previousOutput(String provider, String file) throws IOException {
    Path path = output.resolve(file);
    Files.createDirectories(path.getParent());
    Files.writeString(path, provider);
    String hash = Hashing.sha1().hashString(provider, StandardCharsets.UTF_8).toString();
    Path cache = output.resolve(".cache").resolve(Hashing.sha1().hashString(provider, StandardCharsets.UTF_8).toString());
    Files.createDirectories(cache.getParent());
    Files.writeString(cache, "// " + SharedConstants.getCurrentVersion().id() + "\t2026-11-25T00:00:00\t" + provider + "\n" + hash + " " + file + "\n");
    return path;
  }

  private DataGenerator generator() {
    return new DataGenerator(output, SharedConstants.getCurrentVersion(), true);
  }

  @Test
  void otherRunOutputs_keptByRun() throws IOException {
    Path other = previousOutput("Other Run Provider", "data/test/other.json");
    DataGenerator generator = generator();

    DatagenCacheKeeper.keepOtherRunOutputs(generator);
    generator.run();

    assertThat(other).exists();
    // the placeholder is registered, but not run
    assertThat(generator.getProvidersView()).doesNotContainKey("Other Run Provider");
    assertThatThrownBy(() -> generator.addProvider(false, new NamedProvider("Other Run Provider"))).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void providerRegisteredWithoutRunning_noDuplicate() throws IOException {
    Path hidden = previousOutput("Not Run Provider", "data/test/hidden.json");
    Path other = previousOutput("Other Run Provider", "data/test/other.json");
    DataGenerator generator = generator();
    generator.addProvider(false, new NamedProvider("Not Run Provider"));

    assertThatCode(() -> DatagenCacheKeeper.keepOtherRunOutputs(generator)).doesNotThrowAnyException();
    generator.run();

    assertThat(hidden).exists();
    assertThat(other).exists();
  }

  @Test
  void noCacheFolder_nothingRegistered() {
    DataGenerator generator = generator();
    DatagenCacheKeeper.keepOtherRunOutputs(generator);
    assertThatCode(() -> generator.addProvider(true, new NamedProvider("Any Provider"))).doesNotThrowAnyException();
  }

  /** Creates an event whose config generates the given mods */
  private GatherDataEvent event(DataGenerator generator, boolean flat, String... mods) {
    DataGeneratorConfig config = mock(DataGeneratorConfig.class);
    when(config.getMods()).thenReturn(Set.of(mods));
    when(config.isFlat()).thenReturn(flat || mods.length == 1);
    return new GatherDataEvent.Server(mock(ModContainer.class), generator, config);
  }

  @Test
  void severalModsIntoOneFolder_leftAlone() throws IOException {
    // NeoForge merges the generators of the mods sharing the folder before running, a placeholder in one standing for a provider of the
    // other would make the merge fail as a duplicate provider
    previousOutput("Other Mod Provider", "data/other/other.json");
    DataGenerator generator = generator();
    GatherDataEvent event = event(generator, true, "first", "other");
    assertThat(DatagenCacheKeeper.sharesFolderWithOtherMods(event)).isTrue();

    DatagenCacheKeeper.keepOtherRunOutputs(event);

    DataGenerator otherMod = generator();
    otherMod.addProvider(true, new NamedProvider("Other Mod Provider"));
    assertThatCode(() -> generator.merge(otherMod)).doesNotThrowAnyException();
  }

  @Test
  void singleModOrOwnFolders_kept() {
    DataGenerator generator = generator();
    assertThat(DatagenCacheKeeper.sharesFolderWithOtherMods(event(generator, false, "single"))).isFalse();
    assertThat(DatagenCacheKeeper.sharesFolderWithOtherMods(event(generator, false, "first", "other"))).isFalse();
  }

  private record NamedProvider(String name) implements DataProvider {
    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public String getName() {
      return name;
    }
  }
}
