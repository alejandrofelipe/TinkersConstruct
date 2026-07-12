package slimeknights.tconstruct.library.materials.definition;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.common.conditions.FalseCondition;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.conditions.TrueCondition;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.test.BaseMcTest;
import slimeknights.tconstruct.test.JsonFileLoader;

import java.util.Collection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MaterialManagerTest extends BaseMcTest {

  private static MaterialManager materialManager;
  private final JsonFileLoader fileLoader = new JsonFileLoader(MaterialManager.GSON, MaterialManager.FOLDER);

  /** Ensures the given condition codec is registered */
  private static void ensureSerializerRegistered(ResourceLocation id, MapCodec<? extends ICondition> codec) {
    try {
      Registry.register(NeoForgeRegistries.CONDITION_SERIALIZERS, id, codec);
    } catch (Exception e) {
      // NO-OP
    }
  }

  // PORT M6: IConditionSerializer was removed; NeoForge conditions now register a MapCodec directly
  // into NeoForgeRegistries.CONDITION_SERIALIZERS. In a real run NeoForgeMod registers "true"/"false"
  // under its own mod id via a DeferredRegister, but that only fires through FML's RegisterEvent, which
  // a bare JUnit run never triggers - so this test registers them directly, same as the old
  // CraftingHelper.register defensive call it replaces. Registered under "forge" to match this repo's
  // existing fixtures (conditional_pass.json / conditional_fail.json use "forge:true" / "forge:false");
  // flagged for Task 6 in case those fixtures get migrated to "neoforge:" instead.
  @BeforeAll
  static void setUp() {
    materialManager = new MaterialManager();
    ensureSerializerRegistered(ResourceLocation.fromNamespaceAndPath("forge", "false"), FalseCondition.CODEC);
    ensureSerializerRegistered(ResourceLocation.fromNamespaceAndPath("forge", "true"), TrueCondition.CODEC);
  }

  @Test
  void loadFullMaterial_allStatsPresent() {
    Map<ResourceLocation,JsonElement> splashList = fileLoader.loadFilesAsSplashlist("full");

    materialManager.apply(splashList, mock(ResourceManager.class), mock(ProfilerFiller.class));

    Collection<IMaterial> allMaterials = materialManager.getAllMaterials();
    assertThat(allMaterials).hasSize(1);
    IMaterial testMaterial = allMaterials.iterator().next();
    assertThat(testMaterial.getIdentifier()).isEqualByComparingTo(new MaterialId("tconstruct", "full"));
    assertThat(testMaterial.isCraftable()).isTrue();
    assertThat(testMaterial.getTier()).isEqualTo(15);
    assertThat(testMaterial.getSortOrder()).isEqualTo(4);
    assertThat(testMaterial.isHidden()).isTrue();
  }

  @Test
  void loadMinimalMaterial_succeedWithDefaults() {
    Map<ResourceLocation, JsonElement> splashList = fileLoader.loadFilesAsSplashlist("minimal");

    materialManager.apply(splashList, mock(ResourceManager.class), mock(ProfilerFiller.class));

    Collection<IMaterial> allMaterials = materialManager.getAllMaterials();
    assertThat(allMaterials).hasSize(1);
    IMaterial testMaterial = allMaterials.iterator().next();
    assertThat(testMaterial.getIdentifier()).isEqualByComparingTo(new MaterialId("tconstruct", "minimal"));
    assertThat(testMaterial.isCraftable()).isFalse();
    assertThat(testMaterial.getTier()).isEqualTo(0);
    assertThat(testMaterial.getSortOrder()).isEqualTo(100);
    assertThat(testMaterial.isHidden()).isFalse();
  }

  @Test
  void invalid_skipped() {
    Map<ResourceLocation, JsonElement> splashList = fileLoader.loadFilesAsSplashlist("invalid");

    materialManager.apply(splashList, mock(ResourceManager.class), mock(ProfilerFiller.class));

    Collection<IMaterial> allMaterials = materialManager.getAllMaterials();
    assertThat(allMaterials).hasSize(1);
    IMaterial testMaterial = allMaterials.iterator().next();
    assertThat(testMaterial.isCraftable()).isFalse();
    assertThat(testMaterial.isHidden()).isFalse();
  }

  @Test
  void failOnMissing() {
    ResourceLocation materialId = TConstruct.getResource("nonexistant");
    Map<ResourceLocation, JsonElement> splashList = ImmutableMap.of(materialId, new JsonObject());

    materialManager.apply(splashList, mock(ResourceManager.class), mock(ProfilerFiller.class));

    Collection<IMaterial> allMaterials = materialManager.getAllMaterials();
    assertThat(allMaterials).isEmpty();
  }

  @Test
  void conditional_conditionPass() {
    Map<ResourceLocation, JsonElement> splashList = fileLoader.loadFilesAsSplashlist("conditional_pass");
    materialManager.apply(splashList, mock(ResourceManager.class), mock(ProfilerFiller.class));

    Collection<IMaterial> allMaterials = materialManager.getAllMaterials();
    assertThat(allMaterials).hasSize(1);
  }

  @Test
  void conditional_conditionFail() {
    Map<ResourceLocation, JsonElement> splashList = fileLoader.loadFilesAsSplashlist("conditional_fail");
    materialManager.apply(splashList, mock(ResourceManager.class), mock(ProfilerFiller.class));

    Collection<IMaterial> allMaterials = materialManager.getAllMaterials();
    assertThat(allMaterials).isEmpty();
  }

  @Test
  void redirect_toExisting() {
    Map<ResourceLocation, JsonElement> splashList = fileLoader.loadFilesAsSplashlist("redirect_always", "full");
    materialManager.apply(splashList, mock(ResourceManager.class), mock(ProfilerFiller.class));

    Collection<IMaterial> allMaterials = materialManager.getAllMaterials();
    assertThat(allMaterials).hasSize(1);
    assertThat(materialManager.resolveRedirect(new MaterialId("tconstruct:redirect_always"))).isEqualTo(new MaterialId("tconstruct:full"));
  }

  @Test
  void redirect_toNonexisting() {
    Map<ResourceLocation, JsonElement> splashList = fileLoader.loadFilesAsSplashlist("redirect_always");
    materialManager.apply(splashList, mock(ResourceManager.class), mock(ProfilerFiller.class));

    Collection<IMaterial> allMaterials = materialManager.getAllMaterials();
    assertThat(allMaterials).isEmpty();
    assertThat(materialManager.resolveRedirect(new MaterialId("tconstruct:redirect_always"))).isEqualTo(new MaterialId("tconstruct:redirect_always"));
  }

  @Test
  void redirect_withMultipleConditions() {
    Map<ResourceLocation, JsonElement> splashList = fileLoader.loadFilesAsSplashlist("full", "minimal", "redirect_conditional");
    materialManager.apply(splashList, mock(ResourceManager.class), mock(ProfilerFiller.class));

    Collection<IMaterial> allMaterials = materialManager.getAllMaterials();
    assertThat(allMaterials).hasSize(2);
    assertThat(materialManager.resolveRedirect(new MaterialId("tconstruct:redirect_conditional"))).isEqualTo(new MaterialId("tconstruct:minimal"));
  }
}
