package slimeknights.mantle.client.render;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link FluidRenderInfo#of} needs the client (fluid type extensions and the block atlas), so only the value logic is tested here; sprites stay null */
class FluidRenderInfoTest {
  private static FluidRenderInfo info(int color, int lightLevel) {
    return new FluidRenderInfo(null, null, color, lightLevel, false);
  }

  @Test
  void withOpacity_full_returnsSameInstance() {
    FluidRenderInfo info = info(0xFF123456, 0);
    assertThat(info.withOpacity(0xFF)).isSameAs(info);
    assertThat(info.withOpacity(300)).isSameAs(info);
  }

  @Test
  void withOpacity_scalesAlphaOnly() {
    FluidRenderInfo info = new FluidRenderInfo(null, null, 0xFF123456, 7, true);
    FluidRenderInfo half = info.withOpacity(0x80);
    assertThat(half.color()).isEqualTo(0x80123456);
    assertThat(half.lightLevel()).isEqualTo(7);
    assertThat(half.isGas()).isTrue();
    // multiplies an existing alpha, rounding down like the old casting fade
    assertThat(info(0x80FFFFFF, 0).withOpacity(0x80).color()).isEqualTo(0x40FFFFFF);
    assertThat(info(0xFF123456, 0).withOpacity(0).color()).isEqualTo(0x00123456);
  }

  @Test
  void light_keepsSkyLightAndTakesBrighterBlockLight() {
    // sky 15, block 3 packed as (sky << 20) | (block << 4)
    int packed = (15 << 20) | (3 << 4);
    assertThat(info(-1, 10).light(packed)).isEqualTo((15 << 20) | (10 << 4));
    assertThat(info(-1, 2).light(packed)).isEqualTo(packed);
    assertThat(info(-1, 0).light(packed)).isEqualTo(packed);
  }
}
