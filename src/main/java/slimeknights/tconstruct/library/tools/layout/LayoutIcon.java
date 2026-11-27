package slimeknights.tconstruct.library.tools.layout;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;
import io.netty.handler.codec.DecoderException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.tconstruct.library.recipe.partbuilder.Pattern;

import org.jspecify.annotations.Nullable;

/** Data holder for a button icon, currently supports item stack icons and pattern icons */
public abstract class LayoutIcon {
  /** JSON serializer for a layout button icon */
  public static final Serializer SERIALIZER = new Serializer();

  /** Empty icon, used primarily as a fallback */
  public static final LayoutIcon EMPTY = new LayoutIcon() {
    @Nullable
    @Override
    public <T> T getValue(Class<T> clazz) {
      return null;
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
      buffer.writeEnum(Type.EMPTY);
    }

    @Override
    public JsonObject toJson() {
      return new JsonObject();
    }
  };

  /** Creates a stack icon */
  public static LayoutIcon ofItem(ItemStack stack) {
    return stack.isEmpty() ? EMPTY : new ItemStackIcon(ItemStackTemplate.fromNonEmptyStack(stack), stack);
  }

  /** Creates a stack icon from a template; the stack is created on first use */
  public static LayoutIcon ofItem(ItemStackTemplate template) {
    return new ItemStackIcon(template, null);
  }

  /** Creates an icon from a pattern */
  public static LayoutIcon ofPattern(Pattern pattern) {
    return new PatternIcon(pattern);
  }

  /** Gets the value of this icon, done this way to separate the drawing logic out */
  @Nullable
  public abstract <T> T getValue(Class<T> clazz);

  /** Reads the button icon from the buffer */
  public static LayoutIcon read(FriendlyByteBuf buffer) {
    Type type = buffer.readEnum(Type.class);
    switch (type) {
      case EMPTY: return EMPTY;
      case ITEM: {
        // 26.1: the icon holds a template (D-TPL), sent through ItemStackTemplate.STREAM_CODEC on a RegistryFriendlyByteBuf
        return ofItem(ItemStackTemplate.STREAM_CODEC.decode((RegistryFriendlyByteBuf) buffer));
      }
      case PATTERN: {
        Pattern pattern = new Pattern(buffer.readIdentifier());
        return new PatternIcon(pattern);
      }
    }
    throw new DecoderException("Invalid LayoutButtonIcon " + type);
  }

  /** Writes this to the packet buffer */
  public abstract void write(FriendlyByteBuf buffer);

  /** Writes this object to json */
  public abstract JsonObject toJson();

  /**
   * Icon drawing an item stack. 26.1: holds an {@link ItemStackTemplate} and creates the stack on first use, as layouts load during
   * the data reload, before the default components are bound (a stack codec fails there, D-TPL).
   */
  @AllArgsConstructor(access = AccessLevel.PRIVATE) @VisibleForTesting
  protected static class ItemStackIcon extends LayoutIcon {
    private final ItemStackTemplate template;
    @Nullable
    private ItemStack stack;

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getValue(Class<T> clazz) {
      if (clazz == ItemStack.class) {
        if (stack == null) {
          stack = template.create();
        }
        return (T) stack;
      }
      return null;
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
      buffer.writeEnum(Type.ITEM);
      ItemStackTemplate.STREAM_CODEC.encode((RegistryFriendlyByteBuf) buffer, template);
    }

    @Override
    public JsonObject toJson() {
      // same JSON as the 1.21.11 ItemStack.CODEC (id + count + components): the template codec omits a count of 1
      JsonObject json = ItemStackTemplate.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, template).getOrThrow(JsonParseException::new).getAsJsonObject();
      if (!json.has("count")) {
        json.addProperty("count", template.count());
      }
      return json;
    }
  }

  /** Icon drawing a static patttern sprite */
  @RequiredArgsConstructor @VisibleForTesting
  protected static class PatternIcon extends LayoutIcon {
    private final Pattern pattern;

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getValue(Class<T> clazz) {
      if (clazz == Pattern.class) {
        return (T) pattern;
      }
      return null;
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
      buffer.writeEnum(Type.PATTERN);
      buffer.writeIdentifier(pattern.getLocation());
    }

    @Override
    public JsonObject toJson() {
      JsonObject json = new JsonObject();
      json.addProperty("pattern", pattern.toString());
      return json;
    }
  }

  /** enum of icon types for serialization */
  private enum Type {
    EMPTY,
    ITEM,
    PATTERN
  }

  /** Serializer class */
  protected static class Serializer implements JsonSerializer<LayoutIcon>, JsonDeserializer<LayoutIcon> {
    @Override
    public LayoutIcon deserialize(JsonElement json, java.lang.reflect.Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      JsonObject object = GsonHelper.convertToJsonObject(json, "button_icon");
      if (object.has("pattern")) {
        Pattern pattern = new Pattern(JsonHelper.getResourceLocation(object, "pattern"));
        return new PatternIcon(pattern);
      }
      if (object.has("item") || object.has("id")) {
        // 26.1: parse a template (same id/count/components JSON as ItemStack.CODEC); ItemStack.CODEC needs bound components
        return ofItem(ItemStackTemplate.MAP_CODEC.codec().parse(JsonOps.INSTANCE, object).getOrThrow(JsonSyntaxException::new));
      }
      // not sure why this would be needed, but might as well
      if (object.entrySet().isEmpty()) {
        return EMPTY;
      }
      throw new JsonSyntaxException("LayoutButtonIcon must have either pattern or item");
    }

    @Override
    public JsonElement serialize(LayoutIcon icon, java.lang.reflect.Type typeOfSrc, JsonSerializationContext context) {
      return icon.toJson();
    }
  }
}
