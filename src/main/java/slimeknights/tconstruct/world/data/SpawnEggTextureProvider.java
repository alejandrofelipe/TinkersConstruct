package slimeknights.tconstruct.world.data;

import com.google.common.hash.Hashing;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.data.GenericTextureGenerator;
import slimeknights.tconstruct.library.client.data.util.DataGenSpriteReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Generates the spawn egg textures. Until 1.21.4 every spawn egg was the vanilla two layer template ({@code item/template_spawn_egg}:
 * {@code spawn_egg} tinted with the primary color, {@code spawn_egg_overlay} tinted with the secondary color); 1.21.5 removed the template
 * for one texture per egg. This bakes the old tinted look into one texture per egg, multiplying each layer by its color the way the tint did.
 * <p>
 * The two template layers are Mojang's 1.21.4 {@code minecraft:textures/item/spawn_egg.png} and {@code spawn_egg_overlay.png}, kept as
 * datagen-only inputs under {@code src/datagen/resources/assets/tconstruct/textures/item/template_spawn_egg/} (not shipped).
 */
public class SpawnEggTextureProvider extends GenericTextureGenerator {
  private static final ResourceLocation BASE = TConstruct.getResource("item/template_spawn_egg/spawn_egg");
  private static final ResourceLocation OVERLAY = TConstruct.getResource("item/template_spawn_egg/spawn_egg_overlay");

  public SpawnEggTextureProvider(PackOutput packOutput, ResourceManager resourceManager) {
    super(packOutput, resourceManager, "textures");
  }

  @Override
  public CompletableFuture<?> run(CachedOutput cache) {
    DataGenSpriteReader spriteReader = new DataGenSpriteReader(Objects.requireNonNull(resourceManager), "textures");
    List<CompletableFuture<?>> tasks = new ArrayList<>();
    try {
      NativeImage base = spriteReader.read(BASE);
      NativeImage overlay = spriteReader.read(OVERLAY);
      for (SpawnEggColors egg : SpawnEggColors.values()) {
        tasks.add(egg(cache, spriteReader, base, overlay, egg));
      }
    } catch (IOException e) {
      tasks.add(CompletableFuture.failedFuture(e));
    }
    return CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).whenComplete((result, error) -> spriteReader.closeAll());
  }

  /** Saves the tinted template as the texture named after the egg item, {@code tconstruct:item/<egg name>} */
  private CompletableFuture<?> egg(CachedOutput cache, DataGenSpriteReader spriteReader, NativeImage base, NativeImage overlay, SpawnEggColors egg) {
    ResourceLocation item = TConstruct.getResource(egg.getEggName());
    if (!BuiltInRegistries.ITEM.containsKey(item)) {
      return CompletableFuture.failedFuture(new IllegalStateException("Spawn egg colors for " + item + ", which is not a registered item"));
    }
    NativeImage image = spriteReader.create(base.getWidth(), base.getHeight());
    for (int x = 0; x < base.getWidth(); x++) {
      for (int y = 0; y < base.getHeight(); y++) {
        // overlay drawn over the base, like the second layer of a generated item model
        image.setPixel(x, y, over(tint(overlay.getPixel(x, y), egg.getSecondary()), tint(base.getPixel(x, y), egg.getPrimary())));
      }
    }
    return saveCached(cache, item.withPrefix("item/"), image);
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

  /**
   * Saves the image through the cache, unlike {@link #saveImage(CachedOutput, ResourceLocation, NativeImage)}: files written past the cache
   * are deleted by the data run's stale file purge. {@link NativeImage} only encodes PNGs to files, so encode through a temporary file.
   */
  @SuppressWarnings("deprecation") // sha1 is what the data cache hashes with
  private CompletableFuture<?> saveCached(CachedOutput cache, ResourceLocation location, NativeImage image) {
    return CompletableFuture.runAsync(() -> {
      try {
        Path temp = Files.createTempFile("tconstruct_spawn_egg", ".png");
        try {
          image.writeToFile(temp);
          byte[] bytes = Files.readAllBytes(temp);
          cache.writeIfNeeded(pathProvider.file(location, "png"), bytes, Hashing.sha1().hashBytes(bytes));
        } finally {
          Files.deleteIfExists(temp);
        }
      } catch (IOException e) {
        TConstruct.LOG.error("Couldn't write image for {}", location, e);
        throw new CompletionException(e);
      }
    }, Util.backgroundExecutor());
  }

  @Override
  public String getName() {
    return "Tinkers' Construct Spawn Egg Textures";
  }
}
