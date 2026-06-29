package slimeknights.tconstruct.library.json.predicate.tool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags.Items;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.utils.JsonUtils;

/**
 * Variant of ItemPredicate for matching Tinker tools using {@link ToolStackPredicate}.
 *
 * <p>PORT 1.21.1: {@code net.minecraft.advancements.critereon.ItemPredicate} became a {@code final}
 * record in 1.20.5, so this can no longer extend it, and the Forge {@code ItemPredicate.register(...)}
 * extension is gone. The advancement system now matches via {@code ItemSubPredicate}/{@code DataComponentPredicate}.
 * This class is kept as a standalone tool-matching predicate; the advancement-trigger wiring that used to
 * pass it as a vanilla {@code ItemPredicate} needs to be reworked onto the new sub-predicate API.
 */
@RequiredArgsConstructor(staticName = "ofTool")
public class ToolStackItemPredicate {
  public static final ResourceLocation ID = TConstruct.getResource("tool_stack");

  private final IJsonPredicate<IToolStackView> predicate;

  public static ToolStackItemPredicate ofContext(IJsonPredicate<IToolContext> predicate) {
    return new ToolStackItemPredicate(ToolStackPredicate.context(predicate));
  }

  public boolean matches(ItemStack stack) {
    // tag check is important to prevent accidently modifying the NBT of non-tools
    return stack.is(Items.MODIFIABLE) && predicate.matches(ToolStack.from(stack));
  }

  public JsonElement serializeToJson() {
    JsonObject json = JsonUtils.withType(ID);
    json.add("predicate", ToolStackPredicate.LOADER.serialize(predicate));
    return json;
  }

  /** Deserializes the tool predicate from JSON */
  public static ToolStackItemPredicate deserialize(JsonObject json) {
    return new ToolStackItemPredicate(ToolStackPredicate.LOADER.getIfPresent(json, "predicate"));
  }
}
