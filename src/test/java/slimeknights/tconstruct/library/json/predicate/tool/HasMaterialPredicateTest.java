package slimeknights.tconstruct.library.json.predicate.tool;

import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests {@link HasMaterialPredicate}: material-on-tool check, either at a specific part index or anywhere on the tool. */
class HasMaterialPredicateTest extends BaseMcTest {
  // typed as the MaterialVariantId interface, not the MaterialId implementation: MaterialVariant.of(MaterialId) resolves
  // to the unrelated inherited LazyMaterial.of(MaterialId) overload (more specific parameter type wins), not MaterialVariant.of(MaterialVariantId)
  private static final MaterialVariantId WOOD = new MaterialId("test", "wood");
  private static final MaterialVariantId STONE = new MaterialId("test", "stone");

  @Test
  void matches_withIndex_trueWhenThatIndexHasTheMaterial() {
    HasMaterialPredicate predicate = new HasMaterialPredicate(WOOD, 0);
    IToolContext tool = mock(IToolContext.class);
    when(tool.getMaterial(0)).thenReturn(MaterialVariant.of(WOOD));
    assertThat(predicate.matches(tool)).isTrue();
  }

  @Test
  void matches_withIndex_falseWhenThatIndexHasAnotherMaterial() {
    HasMaterialPredicate predicate = new HasMaterialPredicate(WOOD, 0);
    IToolContext tool = mock(IToolContext.class);
    when(tool.getMaterial(0)).thenReturn(MaterialVariant.of(STONE));
    assertThat(predicate.matches(tool)).isFalse();
  }

  @Test
  void matches_withIndex_ignoresTheMaterialAtOtherIndices() {
    HasMaterialPredicate predicate = new HasMaterialPredicate(WOOD, 1);
    IToolContext tool = mock(IToolContext.class);
    when(tool.getMaterial(0)).thenReturn(MaterialVariant.of(WOOD));
    when(tool.getMaterial(1)).thenReturn(MaterialVariant.of(STONE));
    // wood is on the tool, but not at index 1
    assertThat(predicate.matches(tool)).isFalse();
  }

  @Test
  void matches_defaultIndex_searchesEveryMaterial() {
    HasMaterialPredicate predicate = new HasMaterialPredicate(WOOD);
    IToolContext tool = mock(IToolContext.class);
    when(tool.getMaterials()).thenReturn(MaterialNBT.of(MaterialVariant.of(STONE), MaterialVariant.of(WOOD)));
    assertThat(predicate.matches(tool)).isTrue();
  }

  @Test
  void matches_defaultIndex_falseWhenNoMaterialMatches() {
    HasMaterialPredicate predicate = new HasMaterialPredicate(WOOD);
    IToolContext tool = mock(IToolContext.class);
    when(tool.getMaterials()).thenReturn(MaterialNBT.of(MaterialVariant.of(STONE)));
    assertThat(predicate.matches(tool)).isFalse();
  }

  @Test
  void oneArgConstructor_defaultsIndexToSearchAll() {
    assertThat(new HasMaterialPredicate(WOOD).index()).isEqualTo(-1);
  }
}
