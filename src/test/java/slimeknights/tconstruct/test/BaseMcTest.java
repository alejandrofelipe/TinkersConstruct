package slimeknights.tconstruct.test;

import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.IConfigSpec.ILoadedConfig;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import slimeknights.tconstruct.common.config.Config;

import java.lang.reflect.Constructor;
import java.nio.file.Path;

public class BaseMcTest {

  // PORT M6: the old Forge NetworkHooks/NetworkRegistry mocks guarded against Forge's SimpleChannel
  // static init running during vanilla Bootstrap.bootStrap(); NeoForge's payload-based networking has
  // no equivalent static hook here, so that mocking is no longer needed.
  //
  // PORT M6 (Task 6): a NEW static-init hazard takes its place. NeoForge 21's FeatureFlags.<clinit> now
  // unconditionally calls FeatureFlagLoader.loadModdedFlags(), which reads LoadingModList.get().getModFiles()
  // to discover mod-declared feature flags. LoadingModList.get() is only populated by a real FML launch, so
  // in a bare JUnit run it returns null and NPEs the instant Bootstrap.bootStrap() touches Blocks/Items
  // (both build BlockBehaviour.Properties, which forces FeatureFlags to load) - and since a failed <clinit>
  // is cached by the JVM, every later test in the same fork then sees a NoClassDefFoundError instead.
  // Same mockStatic-around-bootstrap shape as the old NetworkHooks guard: stub LoadingModList.get() to a
  // Mockito mock whose getModFiles() returns Mockito's default empty list, so the modded-flags scan is a no-op.
  @SuppressWarnings({"ResultOfMethodCallIgnored", "unused"})
  @BeforeAll
  static void setUpRegistries() {
    SharedConstants.setVersion(TestWorldVersion.INSTANCE);
    try (MockedStatic<LoadingModList> mockLoadingModList = Mockito.mockStatic(LoadingModList.class)) {
      mockLoadingModList.when(LoadingModList::get).thenReturn(Mockito.mock(LoadingModList.class));
      Bootstrap.bootStrap();
    }
    ModLoadingContext.get().setActiveContainer(new TestModContainer(TestModInfo.INSTANCE));
    loadConfig(Config.commonSpec);
  }

  /**
   * PORT M6 (Task 6): {@code ModConfigSpec.ConfigValue#get()} throws {@code IllegalStateException} until
   * the spec is marked loaded, which normally happens via NeoForge's config lifecycle during a real FML
   * launch (e.g. {@code ToolStack.from} reads {@code Config.COMMON.logInvalidToolStack} for a non-modifiable
   * item). Mirrors what NeoForge itself does when reading a config off disk: correct an in-memory config to
   * fill in defaults, then hand it to the spec via acceptConfig - leaving every value at its declared default.
   * {@code ILoadedConfig} is sealed and its only permitted implementation, the record {@code LoadedConfig},
   * is package-private in {@code net.neoforged.fml.config} - same "reach into forge/neoforge internals via
   * reflection" idiom the old setupTierSorting() used, since no public constructor exists for test code to call.
   */
  private static void loadConfig(ModConfigSpec spec) {
    if (spec.isLoaded()) {
      return;
    }
    CommentedConfig values = CommentedConfig.inMemory();
    spec.correct(values);
    try {
      Class<?> loadedConfigClass = Class.forName("net.neoforged.fml.config.LoadedConfig");
      Constructor<?> constructor = loadedConfigClass.getDeclaredConstructor(CommentedConfig.class, Path.class, ModConfig.class);
      constructor.setAccessible(true);
      spec.acceptConfig((ILoadedConfig) constructor.newInstance(values, null, null));
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to mark config spec as loaded for tests", e);
    }
  }

  /**
   * PORT M6: no-op. NeoForge removed TierSortingRegistry with no replacement registry/bootstrap step;
   * tier ordering now goes through tconstruct's own HarvestTiers (library.utils.HarvestTiers), which
   * needs no setup call. Kept so existing @BeforeAll call sites don't need touching.
   */
  public static void setupTierSorting() {
  }
}
