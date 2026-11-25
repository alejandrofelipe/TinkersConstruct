package slimeknights.tconstruct.world.data;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.data.GenericTextureGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Generates the spawn egg textures. Until 1.21.4 every spawn egg was the vanilla two layer template ({@code item/template_spawn_egg}:
 * {@code spawn_egg} tinted with the primary color, {@code spawn_egg_overlay} tinted with the secondary color); 1.21.5 removed the template
 * for one texture per egg. This bakes the old tinted look into one texture per egg, multiplying each layer by its color the way the tint did.
 * <p>
 * The two template layers are Mojang's {@code minecraft:textures/item/spawn_egg.png} and {@code spawn_egg_overlay.png} (byte identical
 * from 1.21.1 to 1.21.4). They are not part of this repository: the {@code runData} Gradle task finds the 1.21.4 client jar on the machine
 * and passes its path in the {@value #TEMPLATE_JAR_PROPERTY} system property, and the layers are read from it.
 */
public class SpawnEggTextureProvider extends GenericTextureGenerator {
  /** System property holding the path to the 1.21.4 client jar */
  public static final String TEMPLATE_JAR_PROPERTY = "tconstruct.datagen.spawnEggTemplateJar";
  /** Base layer in the client jar, tinted with the primary color */
  public static final String BASE = "assets/minecraft/textures/item/spawn_egg.png";
  /** Overlay layer in the client jar, tinted with the secondary color */
  public static final String OVERLAY = "assets/minecraft/textures/item/spawn_egg_overlay.png";

  public SpawnEggTextureProvider(PackOutput packOutput) {
    super(packOutput, "textures");
  }

  @Override
  public CompletableFuture<?> run(CachedOutput cache) {
    String jarPath = System.getProperty(TEMPLATE_JAR_PROPERTY);
    if (jarPath == null || !Files.isRegularFile(Path.of(jarPath))) {
      return CompletableFuture.failedFuture(new IllegalStateException(
        "System property " + TEMPLATE_JAR_PROPERTY + " must point to the 1.21.4 client jar holding the spawn egg template layers, got " + jarPath
        + "; run datagen through the runData Gradle task, which finds the jar"));
    }
    NativeImage base;
    NativeImage overlay;
    try (ZipFile jar = new ZipFile(jarPath)) {
      base = readLayer(jar, BASE);
      try {
        overlay = readLayer(jar, OVERLAY);
      } catch (IOException e) {
        base.close();
        throw e;
      }
    } catch (IOException e) {
      return CompletableFuture.failedFuture(new IllegalStateException("Could not read the spawn egg template layers from " + jarPath, e));
    }
    List<CompletableFuture<?>> tasks = new ArrayList<>();
    for (SpawnEggColors egg : SpawnEggColors.values()) {
      tasks.add(egg(cache, base, overlay, egg));
    }
    return CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).whenComplete((result, error) -> {
      base.close();
      overlay.close();
    });
  }

  /** Reads an image from the jar */
  private static NativeImage readLayer(ZipFile jar, String path) throws IOException {
    ZipEntry entry = jar.getEntry(path);
    if (entry == null) {
      throw new IOException("No " + path + " in " + jar.getName());
    }
    try (InputStream stream = jar.getInputStream(entry)) {
      return NativeImage.read(stream);
    }
  }

  /** Saves the tinted template as the texture named after the egg item, {@code tconstruct:item/<egg name>} */
  private CompletableFuture<?> egg(CachedOutput cache, NativeImage base, NativeImage overlay, SpawnEggColors egg) {
    ResourceLocation item = TConstruct.getResource(egg.getEggName());
    if (!BuiltInRegistries.ITEM.containsKey(item)) {
      return CompletableFuture.failedFuture(new IllegalStateException("Spawn egg colors for " + item + ", which is not a registered item"));
    }
    NativeImage image = new NativeImage(base.getWidth(), base.getHeight(), true);
    for (int x = 0; x < base.getWidth(); x++) {
      for (int y = 0; y < base.getHeight(); y++) {
        image.setPixel(x, y, composite(base.getPixel(x, y), overlay.getPixel(x, y), egg.getPrimary(), egg.getSecondary()));
      }
    }
    return saveImage(cache, item.withPrefix("item/"), image).whenComplete((result, error) -> image.close());
  }

  /**
   * Computes a pixel of the egg texture: the overlay tinted with the secondary color drawn over the base tinted with the primary color,
   * like the two tinted layers of the old generated item model
   * @param base       Base layer pixel, ARGB
   * @param overlay    Overlay layer pixel, ARGB
   * @param primary    Primary color, RGB
   * @param secondary  Secondary color, RGB
   * @return  Egg texture pixel, ARGB
   */
  static int composite(int base, int overlay, int primary, int secondary) {
    return over(tint(overlay, secondary), tint(base, primary));
  }

  /** Multiplies the color channels by the tint, as the vertex color does in the item shader; alpha is kept */
  static int tint(int argb, int tint) {
    return ARGB.color(ARGB.alpha(argb), multiply(ARGB.red(argb), ARGB.red(tint)), multiply(ARGB.green(argb), ARGB.green(tint)), multiply(ARGB.blue(argb), ARGB.blue(tint)));
  }

  private static int multiply(int channel, int tint) {
    return Math.round(channel * tint / 255f);
  }

  /** Alpha composites the top color over the bottom one; the template layers are fully opaque or fully transparent */
  static int over(int top, int bottom) {
    int topAlpha = ARGB.alpha(top);
    if (topAlpha == 255 || ARGB.alpha(bottom) == 0) {
      return top;
    }
    if (topAlpha == 0) {
      return bottom;
    }
    float a = topAlpha / 255f;
    float b = ARGB.alpha(bottom) / 255f * (1 - a);
    float alpha = a + b;
    return ARGB.color(Math.round(alpha * 255),
                      Math.round((ARGB.red(top) * a + ARGB.red(bottom) * b) / alpha),
                      Math.round((ARGB.green(top) * a + ARGB.green(bottom) * b) / alpha),
                      Math.round((ARGB.blue(top) * a + ARGB.blue(bottom) * b) / alpha));
  }

  @Override
  public String getName() {
    return "Tinkers' Construct Spawn Egg Textures";
  }
}
