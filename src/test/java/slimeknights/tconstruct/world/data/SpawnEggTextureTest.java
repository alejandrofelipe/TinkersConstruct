package slimeknights.tconstruct.world.data;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Checks the spawn egg textures from {@link SpawnEggTextureProvider} against the pre 1.21.5 look: the vanilla template base layer multiplied by
 * the primary color, with the overlay layer multiplied by the secondary color on top (the layers are fully opaque or fully transparent).
 * Expected pixels are computed independently of the provider.
 */
class SpawnEggTextureTest {
  /** Set by the Gradle test task when it finds the 1.21.4 client jar holding the template layers */
  private static final String TEMPLATE_JAR_PROPERTY = "tconstruct.test.spawnEggTemplateJar";
  private static final String GENERATED = "src/generated/resources/assets/tconstruct/textures/item/";

  /** Multiplies a channel by the tint channel, rounding like the GPU stores the shader output */
  private static int multiply(int argb, int tint, int shift) {
    return Math.round(((argb >> shift) & 0xFF) * ((tint >> shift) & 0xFF) / 255f) << shift;
  }

  private static int tint(int argb, int tint) {
    return (argb & 0xFF000000) | multiply(argb, tint, 16) | multiply(argb, tint, 8) | multiply(argb, tint, 0);
  }

  /** Expected egg pixel: the tinted overlay where it is opaque, else the tinted base */
  private static int expected(int base, int overlay, int primary, int secondary) {
    return (overlay >>> 24) == 0xFF ? tint(overlay, secondary) : tint(base, primary);
  }

  /** Asserts the actual pixel matches the expected one; fully transparent pixels only need to stay transparent */
  private static void assertPixel(int expected, int actual, String description) {
    if ((expected >>> 24) == 0) {
      assertThat(actual >>> 24).as("%s alpha", description).isZero();
    } else {
      assertThat(Integer.toHexString(actual)).as(description).isEqualTo(Integer.toHexString(expected));
    }
  }

  /** Runs the provider's pixel math over a synthetic template: every combination of transparent/opaque layers and a spread of grays and colors */
  @Test
  void composite_tintsLayersOfSyntheticTemplate() {
    int[] colors = {0x000000, 0xFFFFFF, 0x808080, 0x3A5F7C, 0xC81E64, 0x7F7F7F, 0x010203};
    int[] alphas = {0x00, 0xFF};
    for (SpawnEggColors egg : SpawnEggColors.values()) {
      for (int baseAlpha : alphas) {
        for (int overlayAlpha : alphas) {
          for (int baseColor : colors) {
            for (int overlayColor : colors) {
              int base = baseAlpha << 24 | baseColor;
              int overlay = overlayAlpha << 24 | overlayColor;
              assertPixel(expected(base, overlay, egg.getPrimary(), egg.getSecondary()),
                          SpawnEggTextureProvider.composite(base, overlay, egg.getPrimary(), egg.getSecondary()),
                          String.format("%s base %08x overlay %08x", egg, base, overlay));
            }
          }
        }
      }
    }
  }

  /** Checks the committed egg textures pixel by pixel against the real template layers, read from the 1.21.4 client jar */
  @Test
  void committedEggs_matchTintedTemplateFromClientJar() throws IOException {
    String jarPath = System.getProperty(TEMPLATE_JAR_PROPERTY);
    assumeTrue(jarPath != null && new File(jarPath).isFile(),
               "the Gradle test task found no 1.21.4 client jar (it passes -D" + TEMPLATE_JAR_PROPERTY + " when it does), so the committed egg textures are not checked against the template layers");
    BufferedImage base;
    BufferedImage overlay;
    try (ZipFile jar = new ZipFile(jarPath)) {
      base = readLayer(jar, SpawnEggTextureProvider.BASE);
      overlay = readLayer(jar, SpawnEggTextureProvider.OVERLAY);
    }
    for (SpawnEggColors egg : SpawnEggColors.values()) {
      String name = egg.getEggName();
      File file = new File(GENERATED + name + ".png");
      assertThat(file).as(name).exists();
      BufferedImage texture = ImageIO.read(file);
      assertThat(texture.getWidth()).as("%s width", name).isEqualTo(base.getWidth());
      assertThat(texture.getHeight()).as("%s height", name).isEqualTo(base.getHeight());
      for (int x = 0; x < base.getWidth(); x++) {
        for (int y = 0; y < base.getHeight(); y++) {
          assertPixel(expected(base.getRGB(x, y), overlay.getRGB(x, y), egg.getPrimary(), egg.getSecondary()), texture.getRGB(x, y), String.format("%s at %d,%d", name, x, y));
        }
      }
    }
  }

  private static BufferedImage readLayer(ZipFile jar, String path) throws IOException {
    ZipEntry entry = jar.getEntry(path);
    assertThat(entry).as("%s in %s", path, jar.getName()).isNotNull();
    try (InputStream stream = jar.getInputStream(entry)) {
      return ImageIO.read(stream);
    }
  }
}
