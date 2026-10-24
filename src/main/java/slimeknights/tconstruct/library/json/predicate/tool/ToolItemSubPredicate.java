package slimeknights.tconstruct.library.json.predicate.tool;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.predicates.DataComponentPredicate;
import net.minecraft.world.item.ItemStack;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * Bridges a Tinkers tool predicate into the 1.21 advancement item-matching system. {@code ItemPredicate}
 * became a final record in 1.20.5, so custom item matching in advancements now goes through a registered
 * {@code ItemSubPredicate} (see {@link TinkerItemPredicates#TOOL}). 1.21.5: item sub-predicates became {@link DataComponentPredicate}s,
 * still under the {@code predicates} key of the item predicate.
 */
public record ToolItemSubPredicate(IJsonPredicate<IToolStackView> predicate) implements DataComponentPredicate {
  /** Codec built off the Mantle {@code Loadable#codec} adapter, so no hand-written predicate codec is needed. */
  public static final Codec<ToolItemSubPredicate> CODEC =
    ToolStackPredicate.LOADER.codec().xmap(ToolItemSubPredicate::new, ToolItemSubPredicate::predicate);

  public static ToolItemSubPredicate ofTool(IJsonPredicate<IToolStackView> predicate) {
    return new ToolItemSubPredicate(predicate);
  }

  public static ToolItemSubPredicate ofContext(IJsonPredicate<IToolContext> predicate) {
    return new ToolItemSubPredicate(ToolStackPredicate.context(predicate));
  }

  @Override
  public boolean matches(DataComponentGetter components) {
    // 1.21.5: item predicates pass the stack as its component getter; we need the item for tools
    // tag check prevents reading the NBT of non-tools, matching ToolStackItemPredicate
    return components instanceof ItemStack stack && stack.is(TinkerTags.Items.MODIFIABLE) && predicate.matches(ToolStack.from(stack));
  }
}
