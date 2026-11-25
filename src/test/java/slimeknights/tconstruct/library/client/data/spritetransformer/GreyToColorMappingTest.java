package slimeknights.tconstruct.library.client.data.spritetransformer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GreyToColorMappingTest {
  /** Swaps the red and blue bytes, converting between ARGB and ABGR */
  private static int swapRedBlue(int color) {
    return (color & 0xFF00FF00) | ((color & 0x00FF0000) >>> 16) | ((color & 0x000000FF) << 16);
  }

  /** Verbatim 1.20.1 (upstream) scaleColor, working in ABGR, which generated the committed textures */
  private static int upstreamScaleColorABGR(int original, int newColor, int grey) {
    int alpha = (original >>> 24);
    if (alpha < 255) newColor = (newColor & 0x00FFFFFF) | ((alpha * (newColor >>> 24) / 255) << 24);
    int red = original & 0xFF;
    if (red   < grey) newColor = (newColor & 0xFFFFFF00) | (((newColor & 0x000000FF) * red   / grey) & 0x000000FF);
    int green = (original >> 8) & 0xFF;
    if (green < grey) newColor = (newColor & 0xFFFF00FF) | (((newColor & 0x0000FF00) * green / grey) & 0x0000FF00);
    int blue = (original >> 16) & 0xFF;
    if (blue  < grey) newColor = (newColor & 0xFF00FFFF) | (((newColor & 0x00FF0000) * blue  / grey) & 0x00FF0000);
    return newColor;
  }

  /** Port result must equal the upstream ABGR result translated to ARGB */
  private static int upstream(int originalARGB, int newColorARGB, int grey) {
    return swapRedBlue(upstreamScaleColorABGR(swapRedBlue(originalARGB), swapRedBlue(newColorARGB), grey));
  }

  @Test
  void blueKeepsUpstreamOverflow() {
    // maille_armor_cloth pixel (214,212,212) recolored by jeweled hide (palette 0xFF9CFAE7 at grey 214):
    // upstream's committed maille_armor_tconstruct_jeweled_hide.png has blue 178 (int overflow), not 228
    assertThat(GreyToColorMapping.scaleColor(0xFFD6D4D4, 0xFF9CFAE7, 214)).isEqualTo(0xFF9CF7B2);
  }

  @Test
  void redDoesNotOverflow() {
    // upstream computed red in the low byte, so 255 * 200 / 255 is exactly 200 (the shifted product would give 199)
    assertThat(GreyToColorMapping.scaleColor(0xFFC8FFFF, 0xFFFFFFFF, 255)).isEqualTo(0xFFC8FFFF);
  }

  @Test
  void matchesUpstreamForEveryChannelValue() {
    int[] others = {0, 1, 127, 254, 255};
    for (int grey = 1; grey < 256; grey++) {
      for (int value = 0; value < grey; value++) {
        for (int target = 0; target < 256; target++) {
          for (int other : others) {
            int newColor = 0xFF000000 | (target << 16) | (other << 8) | target;
            // red channel scaled
            int original = 0xFF000000 | (value << 16) | (grey << 8) | grey;
            assertChannel(original, newColor, grey);
            // blue channel scaled
            original = 0xFF000000 | (grey << 16) | (grey << 8) | value;
            assertChannel(original, newColor, grey);
            // green channel scaled
            original = 0xFF000000 | (grey << 16) | (value << 8) | grey;
            assertChannel(original, 0xFF000000 | (other << 16) | (target << 8) | other, grey);
          }
        }
      }
    }
  }

  @Test
  void matchesUpstreamWithPartialAlpha() {
    for (int alpha = 0; alpha < 256; alpha += 17) {
      int original = (alpha << 24) | 0x00D6D4D4;
      assertChannel(original, 0xFF9CFAE7, 214);
      assertChannel(original, 0x80FFFFFF, 214);
    }
  }

  private static void assertChannel(int original, int newColor, int grey) {
    int expected = upstream(original, newColor, grey);
    int actual = GreyToColorMapping.scaleColor(original, newColor, grey);
    if (actual != expected) {
      throw new AssertionError(String.format("scaleColor(%08X, %08X, %d) = %08X, upstream %08X", original, newColor, grey, actual, expected));
    }
  }
}
