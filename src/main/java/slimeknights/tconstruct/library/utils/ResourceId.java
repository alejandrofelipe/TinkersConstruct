package slimeknights.tconstruct.library.utils;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.function.BiFunction;

/**
 * Helper for use with our extensions of resource location for some type safety in IDs.
 * Note we left {@link ResourceLocation#withPath(String)} and alike as returning {@link ResourceLocation} as there is not much use extending an ID.
 *
 * <p>PORT M3 (ARCHITECTURAL BLOCKER): {@link ResourceLocation} became {@code final} in 1.20.5/1.21, so this
 * class can no longer extend it. The private constructor + {@code Dummy} marker and the {@code decompose(String,char)}
 * helper were also removed from {@code ResourceLocation}. Every subclass ({@code MaterialId}, {@code MaterialStatsId},
 * {@code ModifierId}, {@code ToolStatId}, {@code Pattern}) treats itself as a {@code ResourceLocation} (returns
 * {@code this} where one is expected, calls {@code getNamespace()}/{@code getPath()}). Porting requires reworking the
 * whole {@code ResourceId} hierarchy to <em>wrap</em> a {@code ResourceLocation} rather than extend it, touching
 * {@code materials/}, {@code modifiers/}, {@code recipe/}, and {@code tools/stat/}. The {@code extends ResourceLocation}
 * below is the remaining hard error to resolve as part of that cross-package refactor.
 * @see IdParser
 */
public abstract class ResourceId extends ResourceLocation {
  protected ResourceId(String namespace, String path) {
    // PORT M3: ResourceLocation's namespace+path constructor is now package-private; the (String,String,Dummy)
    // form is gone. Resolved as part of the ResourceId-wrapping refactor described above.
    super(namespace, path, null);
  }

  public ResourceId(ResourceLocation location) {
    this(location.getNamespace(), location.getPath());
  }

  public ResourceId(String location) {
    this(splitNamespace(location), splitPath(location));
  }

  private static String splitNamespace(String location) {
    int colon = location.indexOf(':');
    return colon >= 0 ? location.substring(0, colon) : "minecraft";
  }

  private static String splitPath(String location) {
    int colon = location.indexOf(':');
    return colon >= 0 ? location.substring(colon + 1) : location;
  }


  /* Helpers for static constructors */

  /**
   * Creates a new ID from the given string
   * @param string  String
   * @return  ID, or null if invalid
   */
  @Nullable
  protected static <T extends ResourceLocation> T tryParse(String string, BiFunction<String,String,T> constructor) {
    return tryBuild(splitNamespace(string), splitPath(string), constructor);
  }

  /**
   * Creates a new ID from the given namespace and path
   * @param namespace  Namespace
   * @param path       Path
   * @return  ID, or null if invalid
   */
  @Nullable
  protected static <T extends ResourceLocation> T tryBuild(String namespace, String path, BiFunction<String,String,T> constructor) {
    if (isValidNamespace(namespace) && isValidPath(path)) {
      return constructor.apply(namespace, path);
    }
    return null;
  }
}
