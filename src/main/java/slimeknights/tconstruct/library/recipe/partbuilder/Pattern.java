package slimeknights.tconstruct.library.recipe.partbuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.utils.IdParser;
import slimeknights.tconstruct.library.utils.ResourceId;
import slimeknights.tconstruct.library.utils.Util;

import java.util.Objects;

import javax.annotation.Nullable;

/**
 * Resource location variant for pattern buttons in the part builder. Also used in a few spots for convenient icon loading.
 */
public class Pattern extends ResourceId {
  public static final IdParser<Pattern> PARSER = new IdParser<>(Pattern::new, "Pattern");

  public Pattern(String resourceName) {
    super(resourceName);
  }

  public Pattern(String namespaceIn, String pathIn) {
    super(namespaceIn, pathIn);
  }

  public Pattern(ResourceLocation location) {
    super(location);
  }

  // PORT M3: ResourceLocation's (String,String,Dummy) constructor was removed in 1.21; delegate to the
  // (namespace, path) form. Resolved fully as part of the ResourceId-wrapping refactor (see ResourceId).
  private Pattern(String namespace, String path) {
    super(namespace, path);
  }

  /**
   * Gets the translation key for this pattern
   * @return  Translation key
   */
  public String getTranslationKey() {
    return Util.makeTranslationKey("pattern", this);
  }

  /**
   * Gets the display name for this pattern
   * @return  Display name
   */
  public Component getDisplayName() {
    return Component.translatable(getTranslationKey());
  }

  /**
   * Gets the texture for this pattern for rendering
   * @return  Pattern texture
   */
  public ResourceLocation getTexture() {
    return Objects.requireNonNull(ResourceLocation.tryBuild(getNamespace(), "gui/tinker_pattern/" + getPath()));
  }


  /** {@return Pattern ID, or null if invalid} */
  @Nullable
  public static Pattern tryParse(String string) {
    return tryParse(string, Pattern::new);
  }

  /** {@return Pattern ID, or null if invalid} */
  @Nullable
  public static Pattern tryBuild(String namespace, String path) {
    return tryBuild(namespace, path, Pattern::new);
  }
}
