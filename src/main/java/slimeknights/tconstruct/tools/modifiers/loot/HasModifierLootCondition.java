package slimeknights.tconstruct.tools.modifiers.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;

/** Condition to check if a held tool has the given modifier */
public record HasModifierLootCondition(ModifierId modifier) implements LootItemCondition {
  /** Codec for the condition */
  public static final MapCodec<HasModifierLootCondition> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
    Identifier.CODEC.xmap(ModifierId::new, ModifierId::getLocation).fieldOf("modifier").forGetter(HasModifierLootCondition::modifier)
  ).apply(inst, HasModifierLootCondition::new));

  @Override
  public MapCodec<HasModifierLootCondition> codec() {
    return CODEC;
  }

  @Override
  public boolean test(LootContext context) {
    // 26.1: the tool parameter is an ItemInstance; tools are always stacks
    return context.getOptionalParameter(LootContextParams.TOOL) instanceof ItemStack tool && tool.is(TinkerTags.Items.MODIFIABLE) && ModifierUtil.getModifierLevel(tool, modifier) > 0;
  }
}
