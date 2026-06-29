package slimeknights.tconstruct.library.utils;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.function.BiFunction;

/**
 * Helper for use with our extensions of resource location for some type safety in IDs.
 *
 * <p>PORT M3: {@link ResourceLocation} became {@code final} in 1.20.5/1.21, so this class can no longer extend it.
 * Instead, {@code ResourceId} now <em>wraps</em> a {@link ResourceLocation} and delegates the methods subclasses and
 * callers actually use ({@link #getNamespace()}, {@link #getPath()}, {@link #toString()}, equality, ordering). Use
 * {@link #getLocation()} to obtain the wrapped {@link ResourceLocation} where a bare one is required (e.g. when writing
 * to a buffer or building a translation key).
 * @see IdParser
 */
public abstract class ResourceId implements Comparable<ResourceId> {
  /** Wrapped resource location backing this ID */
  private final ResourceLocation location;

  protected ResourceId(String namespace, String path) {
    this.location = ResourceLocation.fromNamespaceAndPath(namespace, path);
  }

  public ResourceId(ResourceLocation location) {
    this.location = location;
  }

  public ResourceId(String location) {
    this(splitNamespace(location), splitPath(location));
  }

  /** {@return the wrapped resource location} */
  public ResourceLocation getLocation() {
    return location;
  }

  /** {@return the namespace of this ID} */
  public String getNamespace() {
    return location.getNamespace();
  }

  /** {@return the path of this ID} */
  public String getPath() {
    return location.getPath();
  }

  @Override
  public String toString() {
    return location.toString();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ResourceId resourceId)) {
      return false;
    }
    return location.equals(resourceId.location);
  }

  @Override
  public int hashCode() {
    return location.hashCode();
  }

  @Override
  public int compareTo(ResourceId other) {
    return location.compareTo(other.location);
  }


  /* Parsing helpers, mirroring the removed ResourceLocation static helpers */

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
  protected static <T extends ResourceId> T tryParse(String string, BiFunction<String,String,T> constructor) {
    return tryBuild(splitNamespace(string), splitPath(string), constructor);
  }

  /**
   * Creates a new ID from the given namespace and path
   * @param namespace  Namespace
   * @param path       Path
   * @return  ID, or null if invalid
   */
  @Nullable
  protected static <T extends ResourceId> T tryBuild(String namespace, String path, BiFunction<String,String,T> constructor) {
    if (ResourceLocation.isValidNamespace(namespace) && ResourceLocation.isValidPath(path)) {
      return constructor.apply(namespace, path);
    }
    return null;
  }
}
