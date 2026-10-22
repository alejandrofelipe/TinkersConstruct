package slimeknights.tconstruct.library.client.data.util;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;

/**
 * Logic to read sprites from existing images and return native images which can later be modified.
 * PORT 1.21.4: {@code ExistingFileHelper} was removed, so this reads through the {@link ResourceManager} exposed by
 * {@code GatherDataEvent#getResourceManager} (same backing as {@link ResourceManagerSpriteReader}).
 */
@Log4j2
@RequiredArgsConstructor
public class DataGenSpriteReader extends AbstractSpriteReader {
  private final ResourceManager resourceManager;
  private final String folder;

  /** Gets a location with the given extension under the folder */
  private ResourceLocation getLocation(ResourceLocation base, String extension) {
    return Objects.requireNonNull(ResourceLocation.tryBuild(base.getNamespace(), folder + "/" + base.getPath() + extension));
  }

  @Override
  public boolean exists(ResourceLocation path) {
    return resourceManager.getResource(getLocation(path, ".png")).isPresent();
  }

  @Override
  public boolean metadataExists(ResourceLocation path) {
    return resourceManager.getResource(getLocation(path, ".png.mcmeta")).isPresent();
  }

  @Override
  public NativeImage read(ResourceLocation path) throws IOException {
    try {
      Resource resource = resourceManager.getResource(getLocation(path, ".png")).orElseThrow(FileNotFoundException::new);
      NativeImage image = NativeImage.read(resource.open());
      openedImages.add(image);
      return image;
    } catch (IOException e) {
      log.error("Failed to read image at {}", path);
      throw e;
    }
  }

  @Override
  public JsonObject readMetadata(ResourceLocation path) throws IOException {
    try (BufferedReader reader = resourceManager.getResource(getLocation(path, ".png.mcmeta")).orElseThrow(FileNotFoundException::new).openAsReader()) {
      return GsonHelper.parse(reader);
    }
  }
}
