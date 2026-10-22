package slimeknights.tconstruct.library.client.data;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.PackOutput.Target;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import slimeknights.mantle.data.GenericDataProvider;
import slimeknights.tconstruct.TConstruct;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Data generator to create png image files */
public abstract class GenericTextureGenerator extends GenericDataProvider {
  /**
   * Resource source for reading existing textures. Was an {@code ExistingFileHelper} in 1.20/1.21.1; that class was
   * removed in 1.21.4, so datagen now reads through the {@link ResourceManager} exposed by {@code GatherDataEvent#getResourceManager}.
   */
  @Nullable
  protected final ResourceManager resourceManager;

  /** Constructor which reads existing textures through the given resource manager */
  public GenericTextureGenerator(PackOutput packOutput, @Nullable ResourceManager resourceManager, String folder) {
    super(packOutput, Target.RESOURCE_PACK, folder);
    this.resourceManager = resourceManager;
  }

  /** Constructor which does not read existing files */
  public GenericTextureGenerator(PackOutput packOutput, String folder) {
    this(packOutput, null, folder);
  }

  /** Saves the given image to the given location */
  protected CompletableFuture<?> saveImage(CachedOutput cache, ResourceLocation location, NativeImage image) {
    return CompletableFuture.runAsync(() -> {
      try {
        Path path = this.pathProvider.file(location, "png");
        // PORT 1.21.4: NativeImage#asByteArray was removed with no public replacement, so write straight to the target
        // path instead of handing raw PNG bytes to CachedOutput#writeIfNeeded. Mirrors Mantle's GenericTextureGenerator.
        image.writeToFile(path);
      } catch (IOException e) {
        TConstruct.LOG.error("Couldn't write image for {}", location, e);
        throw new CompletionException(e);
      }
    }, Util.backgroundExecutor());
  }

  /** Saves metadata for the given image */
  protected CompletableFuture<?> saveMetadata(CachedOutput cache, ResourceLocation location, JsonObject metadata) {
    return DataProvider.saveStable(cache, metadata, this.pathProvider.file(location, "png.mcmeta"));
  }
}
