package slimeknights.tconstruct.world.data;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the committed spawn egg textures from {@link SpawnEggTextureProvider} pixel by pixel against the pre 1.21.5 look: the vanilla template
 * base layer multiplied by the primary color, with the overlay layer multiplied by the secondary color on top (the layers are fully opaque or
 * fully transparent). Computed independently of the provider, straight from the files.
 */
class SpawnEggTextureTest {
  private static final String TEMPLATE = "src/datagen/resources/assets/tconstruct/textures/item/template_spawn_egg/";
  private static final String GENERATED = "src/generated/resources/assets/tconstruct/textures/item/";

  private static BufferedImage read(String path) throws IOException {
    File file = new File(path);
    assertThat(file).as(path).exists();
    return ImageIO.read(file);
  }

  /** Multiplies a channel by the tint channel, rounding like the GPU stores the shader output */
  private static int multiply(int argb, int tint, int shift) {
    return Math.round(((argb >> shift) & 0xFF) * ((tint >> shift) & 0xFF) / 255f) << shift;
  }

  private static int tint(int argb, int tint) {
    return (argb & 0xFF000000) | multiply(argb, tint, 16) | multiply(argb, tint, 8) | multiply(argb, tint, 0);
  }

  @Test
  void skySlime_matchesTintedTemplate() throws IOException {
    assertTintedTemplate("sky_slime_spawn_egg", 0x47eff5, 0xacfff4);
  }

  @Test
  void enderSlime_matchesTintedTemplate() throws IOException {
    assertTintedTemplate("ender_slime_spawn_egg", 0x6300B0, 0xD37CFF);
  }

  @Test
  void terracube_matchesTintedTemplate() throws IOException {
    assertTintedTemplate("terracube_spawn_egg", 0xAFB9D6, 0xA1A7B1);
  }

  /** Asserts the generated egg texture is the base layer tinted with the primary color under the overlay tinted with the secondary color */
  private static void assertTintedTemplate(String name, int primary, int secondary) throws IOException {
    BufferedImage base = read(TEMPLATE + "spawn_egg.png");
    BufferedImage overlay = read(TEMPLATE + "spawn_egg_overlay.png");
    BufferedImage egg = read(GENERATED + name + ".png");
    assertThat(egg.getWidth()).isEqualTo(base.getWidth());
    assertThat(egg.getHeight()).isEqualTo(base.getHeight());
    for (int x = 0; x < base.getWidth(); x++) {
      for (int y = 0; y < base.getHeight(); y++) {
        int top = overlay.getRGB(x, y);
        int expected = (top >>> 24) == 0xFF ? tint(top, secondary) : tint(base.getRGB(x, y), primary);
        if ((expected >>> 24) == 0) {
          assertThat(egg.getRGB(x, y) >>> 24).as("%s alpha at %d,%d", name, x, y).isZero();
        } else {
          assertThat(Integer.toHexString(egg.getRGB(x, y))).as("%s at %d,%d", name, x, y).isEqualTo(Integer.toHexString(expected));
        }
      }
    }
  }
}
