package slimeknights.tconstruct.tools.modifiers.loot;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount.BinomialWithBonusCount;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount.Formula;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount.FormulaType;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount.OreDrops;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount.UniformBonusCount;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.tools.TinkerModifiers;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Boosts drop rates based on modifier level */
public class ModifierBonusLootFunction extends LootItemConditionalFunction {
  /** Map of all known formula types by id, used to build the dispatch codec since the vanilla map is private */
  private static final Map<ResourceLocation, FormulaType> FORMULAS = Stream.of(BinomialWithBonusCount.TYPE, OreDrops.TYPE, UniformBonusCount.TYPE)
    .collect(Collectors.toMap(FormulaType::id, type -> type));
  /** Codec for the formula type, mirroring the private vanilla codec */
  private static final Codec<FormulaType> FORMULA_TYPE_CODEC = ResourceLocation.CODEC.comapFlatMap(
    id -> {
      FormulaType type = FORMULAS.get(id);
      return type != null
        ? com.mojang.serialization.DataResult.success(type)
        : com.mojang.serialization.DataResult.error(() -> "No formula type with id: '" + id + "'");
    },
    FormulaType::id);
  /** Codec for the formula, dispatching on the type */
  private static final MapCodec<Formula> FORMULA_CODEC = ExtraCodecs.dispatchOptionalValue(
    "formula", "parameters", FORMULA_TYPE_CODEC, Formula::getType, FormulaType::codec);

  /** Codec for the function */
  public static final MapCodec<ModifierBonusLootFunction> CODEC = RecordCodecBuilder.mapCodec(inst ->
    commonFields(inst).and(inst.group(
      ResourceLocation.CODEC.xmap(ModifierId::new, ModifierId::getLocation).fieldOf("modifier").forGetter(fn -> fn.modifier),
      FORMULA_CODEC.forGetter(fn -> fn.formula),
      Codec.BOOL.optionalFieldOf("include_base", true).forGetter(fn -> fn.includeBase)
    )).apply(inst, ModifierBonusLootFunction::new));

  /** Modifier ID to use for multiplier bonus */
  private final ModifierId modifier;
  /** Formula to apply */
  private final Formula formula;
  /** If true, considers level 1 as bonus, if false considers level 1 as no bonus */
  private final boolean includeBase;

  protected ModifierBonusLootFunction(List<LootItemCondition> conditions, ModifierId modifier, Formula formula, boolean includeBase) {
    super(conditions);
    this.modifier = modifier;
    this.formula = formula;
    this.includeBase = includeBase;
  }

  /** Creates a generic builder */
  public static Builder<?> builder(ModifierId modifier, Formula formula, boolean includeBase) {
    return simpleBuilder(conditions -> new ModifierBonusLootFunction(conditions, modifier, formula, includeBase));
  }

  /** Creates a builder for the binomial with bonus formula */
  public static Builder<?> binomialWithBonusCount(ModifierId modifier, float probability, int extra, boolean includeBase) {
    return builder(modifier, new BinomialWithBonusCount(extra, probability), includeBase);
  }

  /** Creates a builder for the ore drops formula */
  public static Builder<?> oreDrops(ModifierId modifier, boolean includeBase) {
    return builder(modifier, new OreDrops(), includeBase);
  }

  /** Creates a builder for the uniform bonus count */
  public static Builder<?> uniformBonusCount(ModifierId modifier, int bonusMultiplier, boolean includeBase) {
    return builder(modifier, new UniformBonusCount(bonusMultiplier), includeBase);
  }

  @Override
  public LootItemFunctionType<ModifierBonusLootFunction> getType() {
    return TinkerModifiers.modifierBonusFunction.get();
  }

  @Override
  public Set<LootContextParam<?>> getReferencedContextParams() {
    return ImmutableSet.of(LootContextParams.TOOL);
  }

  @Override
  protected ItemStack run(ItemStack stack, LootContext context) {
    int level = ModifierUtil.getModifierLevel(context.getParam(LootContextParams.TOOL), modifier);
    if (!includeBase) {
      level--;
    }
    if (level > 0) {
      stack.setCount(formula.calculateNewCount(context.getRandom(), stack.getCount(), level));
    }
    return stack;
  }
}
