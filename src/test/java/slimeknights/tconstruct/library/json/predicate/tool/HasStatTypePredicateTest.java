package slimeknights.tconstruct.library.json.predicate.tool;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.fixture.MaterialItemFixture;
import slimeknights.tconstruct.fixture.ToolDefinitionFixture;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.test.BaseMcTest;
import slimeknights.tconstruct.tools.stats.HandleMaterialStats;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link HasStatTypePredicate} against {@link ToolDefinitionFixture#getStandardToolDefinition()}, which has
 * a head part (index 0, {@link HeadMaterialStats#ID}) and a handle part (index 1, {@link HandleMaterialStats#ID}).
 */
class HasStatTypePredicateTest extends BaseMcTest {
  // typed as the MaterialVariantId interface, not the MaterialId implementation: MaterialVariant.of(MaterialId) resolves
  // to the unrelated inherited LazyMaterial.of(MaterialId) overload (more specific parameter type wins), not MaterialVariant.of(MaterialVariantId)
  private static final MaterialVariantId WOOD = new MaterialId("test", "wood");
  private static final MaterialVariantId STONE = new MaterialId("test", "stone");
  private static final MaterialStatsId UNKNOWN_STAT_TYPE = new MaterialStatsId("test", "unknown_part");

  @BeforeAll
  static void beforeAll() {
    MaterialItemFixture.init();
  }

  private static IToolContext mockTool() {
    IToolContext tool = mock(IToolContext.class);
    when(tool.getDefinition()).thenReturn(ToolDefinitionFixture.getStandardToolDefinition());
    return tool;
  }

  @Test
  void matches_noMaterialFilter_trueWhenStatTypePresent() {
    HasStatTypePredicate predicate = new HasStatTypePredicate(HeadMaterialStats.ID);
    assertThat(predicate.matches(mockTool())).isTrue();
  }

  @Test
  void matches_noMaterialFilter_trueForAnyPartIndex() {
    HasStatTypePredicate predicate = new HasStatTypePredicate(HandleMaterialStats.ID);
    assertThat(predicate.matches(mockTool())).isTrue();
  }

  @Test
  void matches_noMaterialFilter_falseWhenStatTypeAbsent() {
    HasStatTypePredicate predicate = new HasStatTypePredicate(UNKNOWN_STAT_TYPE);
    assertThat(predicate.matches(mockTool())).isFalse();
  }

  @Test
  void matches_withMaterialFilter_trueWhenStatTypeAndMaterialMatch() {
    HasStatTypePredicate predicate = new HasStatTypePredicate(HeadMaterialStats.ID, WOOD);
    IToolContext tool = mockTool();
    when(tool.getMaterial(0)).thenReturn(MaterialVariant.of(WOOD));
    assertThat(predicate.matches(tool)).isTrue();
  }

  @Test
  void matches_withMaterialFilter_falseWhenMaterialDiffers() {
    HasStatTypePredicate predicate = new HasStatTypePredicate(HeadMaterialStats.ID, WOOD);
    IToolContext tool = mockTool();
    when(tool.getMaterial(0)).thenReturn(MaterialVariant.of(STONE));
    assertThat(predicate.matches(tool)).isFalse();
  }

  @Test
  void matches_withMaterialFilter_falseWhenStatTypeAbsentRegardlessOfMaterial() {
    HasStatTypePredicate predicate = new HasStatTypePredicate(UNKNOWN_STAT_TYPE, WOOD);
    IToolContext tool = mockTool();
    assertThat(predicate.matches(tool)).isFalse();
  }

  @Test
  void oneArgConstructor_defaultsMaterialToNull() {
    assertThat(new HasStatTypePredicate(HeadMaterialStats.ID).material()).isNull();
  }

  @Test
  void loader_serializeThenDeserialize_roundTrips() {
    HasStatTypePredicate original = new HasStatTypePredicate(HeadMaterialStats.ID, WOOD);
    JsonObject json = new JsonObject();
    HasStatTypePredicate.LOADER.serialize(original, json);
    HasStatTypePredicate roundTripped = HasStatTypePredicate.LOADER.deserialize(json);

    IToolContext tool = mockTool();
    when(tool.getMaterial(0)).thenReturn(MaterialVariant.of(WOOD));
    assertThat(roundTripped.matches(tool)).isTrue();
    when(tool.getMaterial(0)).thenReturn(MaterialVariant.of(STONE));
    assertThat(roundTripped.matches(tool)).isFalse();
  }
}
