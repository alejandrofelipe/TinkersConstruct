package slimeknights.tconstruct.library.json.predicate.tool;

import net.minecraft.advancements.critereon.ItemSubPredicate;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import slimeknights.tconstruct.TConstruct;

/** Registers Tinkers item sub-predicates (advancement item matching). Mirrors {@code TinkerIngredients}. */
public class TinkerItemPredicates {
  private TinkerItemPredicates() {}

  private static final DeferredRegister<ItemSubPredicate.Type<?>> ITEM_SUB_PREDICATES =
    DeferredRegister.create(Registries.ITEM_SUB_PREDICATE_TYPE, TConstruct.MOD_ID);

  /** Matches a Tinker tool against a tool predicate. */
  public static final DeferredHolder<ItemSubPredicate.Type<?>, ItemSubPredicate.Type<ToolItemSubPredicate>> TOOL =
    ITEM_SUB_PREDICATES.register("tool", () -> new ItemSubPredicate.Type<>(ToolItemSubPredicate.CODEC));

  /** Call from the mod constructor with the mod bus. */
  public static void init(IEventBus bus) {
    ITEM_SUB_PREDICATES.register(bus);
  }
}
