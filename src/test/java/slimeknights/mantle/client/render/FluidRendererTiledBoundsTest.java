package slimeknights.mantle.client.render;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Per-block split of {@link FluidRenderer#submitTiledBox} (generalized from Tinkers' old smeltery tank bounds) */
class FluidRendererTiledBoundsTest {
  private static void assertBounds(float start, float end, float... expected) {
    float[] bounds = FluidRenderer.tiledBounds(start, end);
    assertThat(bounds).hasSize(expected.length);
    for (int i = 0; i < expected.length; i++) {
      assertThat(bounds[i]).as("bound %d of %s..%s", i, start, end).isCloseTo(expected[i], within(1e-6f));
    }
  }

  @Test
  void withinOneBlock_keepsBothEnds() {
    assertBounds(0.005f, 0.995f, 0.005f, 0.995f);
  }

  @Test
  void acrossBlocks_splitsOnEveryBlockEdge() {
    // smeltery interior 3 wide, inset like the tank renderer does
    assertBounds(0.005f, 2.995f, 0.005f, 1, 2, 2.995f);
    // liquid height: whole blocks plus a partial top
    assertBounds(0f, 2.3f, 0, 1, 2, 2.3f);
  }

  @Test
  void fractionalStart_countsTheBlocksItCrosses() {
    assertBounds(2.3f, 5.1f, 2.3f, 3, 4, 5, 5.1f);
  }

  @Test
  void endOnABlockEdge_doesNotRepeatTheLastFace() {
    assertBounds(1f, 3f, 1, 2, 3);
    assertBounds(0f, 1f, 0, 1);
  }
}
