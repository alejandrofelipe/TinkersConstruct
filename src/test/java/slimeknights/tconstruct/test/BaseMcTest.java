package slimeknights.tconstruct.test;

import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.IConfigSpec.ILoadedConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import slimeknights.tconstruct.common.config.Config;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
  //
  // PORT 1.21.10 (FML 10): LoadingModList.get() is gone, FeatureFlagLoader now reads
  // FMLLoader.getCurrent().getLoadingModList(), and SharedConstants.<clinit> calls FMLEnvironment.isProduction(), which also
  // needs a running FMLLoader. Mock both around the version setup and bootstrap: FMLLoader.getCurrent() returns a mock whose
  // getLoadingModList() is a mock (empty mod files), FMLEnvironment is a dev server.
  @SuppressWarnings({"ResultOfMethodCallIgnored", "unused"})
  @BeforeAll
  static void setUpRegistries() {
    try (MockedStatic<FMLEnvironment> environment = Mockito.mockStatic(FMLEnvironment.class);
         MockedStatic<FMLLoader> loader = Mockito.mockStatic(FMLLoader.class)) {
      environment.when(FMLEnvironment::isProduction).thenReturn(false);
      environment.when(FMLEnvironment::getDist).thenReturn(Dist.DEDICATED_SERVER);
      FMLLoader current = Mockito.mock(FMLLoader.class);
      Mockito.when(current.getLoadingModList()).thenReturn(Mockito.mock(LoadingModList.class));
      loader.when(FMLLoader::getCurrent).thenReturn(current);
      SharedConstants.setVersion(TestWorldVersion.INSTANCE);
      Bootstrap.bootStrap();
    }
    ModLoadingContext.get().setActiveContainer(new TestModContainer(TestModInfo.INSTANCE));
    loadConfig(Config.commonSpec);
    bindDefaultComponents();
  }

  /**
   * D-TESTBOOT (26.1): items get their default data components only when the server (or client) binds them from the loaded
   * registries; until then {@code new ItemStack(item)} fails with "Components not bound yet". Binds them like
   * {@code ReloadableServerResources} does: runs every component initializer against a lookup of the built-in registries plus
   * the vanilla datapack registries ({@link VanillaRegistries#createLookup()}). Tags are not loaded in unit tests, so a tag lookup
   * an initializer makes (e.g. {@code DamageTypeTags.IS_FIRE}) resolves to an empty tag. Call it again after registering test
   * items (their initializers are added by the {@code Item} constructor); rebinding is allowed.
   */
  public static void bindDefaultComponents() {
    HolderLookup.Provider datapack = VanillaRegistries.createLookup();
    Set<ResourceKey<? extends Registry<?>>> datapackKeys = datapack.listRegistryKeys().collect(Collectors.toSet());
    Stream<HolderLookup.RegistryLookup<?>> builtIn = BuiltInRegistries.REGISTRY.stream()
      .filter(registry -> !datapackKeys.contains(registry.key()))
      .map(registry -> (HolderLookup.RegistryLookup<?>) registry);
    HolderLookup.Provider provider = HolderLookup.Provider.create(
      Stream.concat(builtIn, datapack.listRegistries()).map(lookup -> (HolderLookup.RegistryLookup<?>) withEmptyTags(lookup)));
    BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(provider).forEach(DataComponentInitializers.PendingComponents::apply);
  }

  /**
   * Wraps the lookup so missing or unbound tags resolve to an empty tag.
   * <p>
   * Consequence for tests: <b>every tag-based default component is empty in unit tests.</b> A missing or unbound tag becomes a detached,
   * empty {@link HolderSet.Named} (not bound to any registry, never filled), so components built from tags (e.g. a tool's mineable blocks,
   * {@code DamageResistant} fire types, repair items) match nothing. Behaviour that depends on tag contents must be verified in gametests,
   * where the server loads the real tags.
   */
  private static <T> HolderLookup.RegistryLookup<T> withEmptyTags(HolderLookup.RegistryLookup<T> lookup) {
    return new HolderLookup.RegistryLookup.Delegate<>() {
      @Override
      public HolderLookup.RegistryLookup<T> parent() {
        return lookup;
      }

      @Override
      public Optional<HolderSet.Named<T>> get(TagKey<T> tag) {
        Optional<HolderSet.Named<T>> found;
        try {
          found = lookup.get(tag);
        } catch (IllegalStateException e) {
          // built-in registry whose tags were never bound
          found = Optional.empty();
        }
        // the datapack lookup answers with HolderSet#emptyNamed placeholders (not dereferenceable, rejected as components)
        return found.isPresent() && found.get().getClass() == HolderSet.Named.class ? found : Optional.of(emptyTag(lookup, tag));
      }
    };
  }

  /**
   * Creates a bound, empty {@link HolderSet.Named}. Not {@link HolderSet#emptyNamed}: that anonymous subclass fails NeoForge's
   * data component validation (components must have value equality; the plain {@code Named} class is accepted as a tag) and
   * cannot be dereferenced. The constructor and {@code bind} are package-private, hence reflection (tests only).
   */
  @SuppressWarnings("unchecked")
  private static <T> HolderSet.Named<T> emptyTag(HolderOwner<T> owner, TagKey<T> tag) {
    try {
      Constructor<?> constructor = HolderSet.Named.class.getDeclaredConstructor(HolderOwner.class, TagKey.class);
      constructor.setAccessible(true);
      HolderSet.Named<T> named = (HolderSet.Named<T>) constructor.newInstance(owner, tag);
      Method bind = HolderSet.Named.class.getDeclaredMethod("bind", List.class);
      bind.setAccessible(true);
      bind.invoke(named, List.of());
      return named;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to create an empty tag for tests", e);
    }
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
