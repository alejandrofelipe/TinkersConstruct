package slimeknights.tconstruct.tools.modifiers.loot;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
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
import slimeknights.tconstruct.tools.TinkerModifiers;
import slimeknights.tconstruct.tools.modifiers.traits.skull.ChrysophiliteModifier;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Loot modifier to boost drops based on teh chrysophilite amount */
public class ChrysophiliteBonusFunction extends LootItemConditionalFunction {
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
  public static final MapCodec<ChrysophiliteBonusFunction> CODEC = RecordCodecBuilder.mapCodec(inst ->
    commonFields(inst).and(inst.group(
      FORMULA_CODEC.forGetter(fn -> fn.formula),
      Codec.BOOL.optionalFieldOf("include_base", true).forGetter(fn -> fn.includeBase)
    )).apply(inst, ChrysophiliteBonusFunction::new));

  /** Formula to apply */
  private final Formula formula;
  /** If true, the includes the helmet in the level, if false level is just gold pieces */
  private final boolean includeBase;
  protected ChrysophiliteBonusFunction(List<LootItemCondition> conditions, Formula formula, boolean includeBase) {
    super(conditions);
    this.formula = formula;
    this.includeBase = includeBase;
  }

  /** Creates a generic builder */
  public static Builder<?> builder(Formula formula, boolean includeBase) {
    return simpleBuilder(conditions -> new ChrysophiliteBonusFunction(conditions, formula, includeBase));
  }

  /** Creates a builder for the binomial with bonus formula */
  public static Builder<?> binomialWithBonusCount(float probability, int extra, boolean includeBase) {
    return builder(new BinomialWithBonusCount(extra, probability), includeBase);
  }

  /** Creates a builder for the ore drops formula */
  public static Builder<?> oreDrops(boolean includeBase) {
    return builder(new OreDrops(), includeBase);
  }

  /** Creates a builder for the uniform bonus count */
  public static Builder<?> uniformBonusCount(int bonusMultiplier, boolean includeBase) {
    return builder(new UniformBonusCount(bonusMultiplier), includeBase);
  }

  @Override
  protected ItemStack run(ItemStack stack, LootContext context) {
    int level = ChrysophiliteModifier.getTotalGold(context.getParamOrNull(LootContextParams.THIS_ENTITY));
    if (!includeBase) {
      level--;
    }
    if (level > 0) {
      stack.setCount(formula.calculateNewCount(context.getRandom(), stack.getCount(), level));
    }
    return stack;
  }

  @Override
  public Set<LootContextParam<?>> getReferencedContextParams() {
    return ImmutableSet.of(LootContextParams.THIS_ENTITY);
  }

  @Override
  public LootItemFunctionType<ChrysophiliteBonusFunction> getType() {
    return TinkerModifiers.chrysophiliteBonusFunction.get();
  }
}
