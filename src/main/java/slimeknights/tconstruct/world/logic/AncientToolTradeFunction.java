package slimeknights.tconstruct.world.logic;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.materials.RandomMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Trade item modifier for the wandering trader ancient tool trade ({@code data/tconstruct/villager_trade/ancient_tool.json}): replaces
 * the given item with a random tool from {@link TinkerTags.Items#TRADER_TOOLS} built from random ancient materials, and sets its
 * emerald cost through {@link DataComponents#ADDITIONAL_TRADE_COST}, which the trade adds to its base cost (0) and removes from
 * the result. Replaces the 1.21.11 {@code AncientToolItemListing} (26.1 removed {@code VillagerTrades.ItemListing}; trades are data).
 */
public class AncientToolTradeFunction extends LootItemConditionalFunction {
  public static final MapCodec<AncientToolTradeFunction> CODEC = RecordCodecBuilder.mapCodec(inst -> commonFields(inst).apply(inst, AncientToolTradeFunction::new));

  protected AncientToolTradeFunction(List<LootItemCondition> conditions) {
    super(conditions);
  }

  /** Creates a builder for datagen */
  public static LootItemConditionalFunction.Builder<?> builder() {
    return simpleBuilder(AncientToolTradeFunction::new);
  }

  @Override
  public MapCodec<AncientToolTradeFunction> codec() {
    return CODEC;
  }

  @Override
  public Set<ContextKey<?>> getReferencedContextParams() {
    return Set.of(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED);
  }

  @SuppressWarnings("deprecation")  // builtInRegistryHolder tag lookup, as before
  @Override
  protected ItemStack run(ItemStack stack, LootContext context) {
    RandomSource random = context.getRandom();
    // step 1: select ancient tool
    Optional<Holder<Item>> selected = BuiltInRegistries.ITEM.getOrThrow(TinkerTags.Items.TRADER_TOOLS).getRandomElement(random);
    if (selected.isPresent() && selected.get().value() instanceof IModifiable toolItem) {
      // step 2: select materials
      ToolStack tool = ToolBuildHandler.buildToolRandomMaterials(toolItem, RandomMaterial.ancient(), random);
      ItemStack result = tool.createStack();
      // step 3: calculate cost based on tier; the trade adds it to its base cost
      if (context.hasParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED)) {
        result.set(DataComponents.ADDITIONAL_TRADE_COST, cost(tool.getMaterials(), random));
      }
      return result;
    }
    // no tool: an empty result drops the offer, as the old listing returned no offer
    return ItemStack.EMPTY;
  }

  /**
   * Emerald cost of an ancient tool with the given materials: 6-8 emeralds per average tier, from 6 (tier 1) to 32 (tier 4), and at least 1.
   * Ancient materials go down to tier 0: 1.21.11 then offered the tool for 0 emeralds (an empty cost), but a 26.1 trade with a total cost of 0
   * gives no offer at all, so the minimum keeps the trade.
   */
  public static int cost(MaterialNBT materials, RandomSource random) {
    float tier = 0;
    if (materials.isEmpty()) {
      // if no materials, just choose a baseline tier of 2
      tier = 2;
    } else {
      for (MaterialVariant material : materials) {
        tier += material.get().getTier();
      }
      tier /= materials.size();
    }
    return Math.max(1, Math.round(tier * 6) + random.nextInt(Math.round(2 * tier) + 1));
  }
}
