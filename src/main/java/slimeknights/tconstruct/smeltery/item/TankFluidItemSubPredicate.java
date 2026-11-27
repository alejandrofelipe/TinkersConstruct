package slimeknights.tconstruct.smeltery.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.predicates.DataComponentPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import slimeknights.tconstruct.library.json.predicate.tool.TinkerItemPredicates;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;

/**
 * Item predicate ({@code tconstruct:tank_fluid}, see {@link TinkerItemPredicates#TANK_FLUID}) matching a tank item that holds exactly the given
 * fluid, like an exact {@link TinkerSmeltery#TANK_FLUID} component predicate. Same JSON as a fluid stack: {@code id}, {@code amount} and optional
 * {@code components}.
 * <p>
 * 26.1: an exact component predicate decodes the component value, and {@link SimpleFluidContent} decodes a fluid stack, which fails while the
 * fluid's default components are not bound yet ("does not have components yet"); so does {@link net.neoforged.neoforge.fluids.FluidStackTemplate}.
 * They are bound only after every reload listener ran, so on the first load the advancements using it were dropped. This predicate only decodes
 * the fluid, amount and component changes, and creates the stack when it matches, at runtime.
 */
public record TankFluidItemSubPredicate(Holder<Fluid> fluid, int amount, DataComponentPatch components) implements DataComponentPredicate {
  public static final Codec<TankFluidItemSubPredicate> CODEC = RecordCodecBuilder.create(inst -> inst.group(
    BuiltInRegistries.FLUID.holderByNameCodec().fieldOf("id").forGetter(TankFluidItemSubPredicate::fluid),
    ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(TankFluidItemSubPredicate::amount),
    DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(TankFluidItemSubPredicate::components)
  ).apply(inst, TankFluidItemSubPredicate::new));

  public TankFluidItemSubPredicate(Fluid fluid, int amount) {
    this(fluid.builtInRegistryHolder(), amount, DataComponentPatch.EMPTY);
  }

  @Override
  public boolean matches(DataComponentGetter getter) {
    SimpleFluidContent content = getter.get(TinkerSmeltery.TANK_FLUID.get());
    return content != null && content.getAmount() == amount && content.is(fluid)
      && content.equals(SimpleFluidContent.copyOf(new FluidStack(fluid, amount, components)));
  }
}
