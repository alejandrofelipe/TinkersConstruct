package slimeknights.tconstruct.library.json.predicate.tool;

import net.minecraft.core.component.predicates.DataComponentPredicate;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.smeltery.item.TankFluidItemSubPredicate;

/** Registers Tinkers item sub-predicates (advancement item matching). Mirrors {@code TinkerIngredients}. 1.21.5: registered as data component predicates. */
public class TinkerItemPredicates {
  private TinkerItemPredicates() {}

  private static final DeferredRegister<DataComponentPredicate.Type<?>> ITEM_SUB_PREDICATES =
    DeferredRegister.create(Registries.DATA_COMPONENT_PREDICATE_TYPE, TConstruct.MOD_ID);

  /** Matches a Tinker tool against a tool predicate. */
  public static final DeferredHolder<DataComponentPredicate.Type<?>, DataComponentPredicate.Type<ToolItemSubPredicate>> TOOL =
    ITEM_SUB_PREDICATES.register("tool", () -> new DataComponentPredicate.ConcreteType<>(ToolItemSubPredicate.CODEC));
  /** Matches a tank item holding exactly the given fluid; 26.1 replacement for an exact tank fluid component predicate, see {@link TankFluidItemSubPredicate}. */
  public static final DeferredHolder<DataComponentPredicate.Type<?>, DataComponentPredicate.Type<TankFluidItemSubPredicate>> TANK_FLUID =
    ITEM_SUB_PREDICATES.register("tank_fluid", () -> new DataComponentPredicate.ConcreteType<>(TankFluidItemSubPredicate.CODEC));

  /** Call from the mod constructor with the mod bus. */
  public static void init(IEventBus bus) {
    ITEM_SUB_PREDICATES.register(bus);
  }
}
