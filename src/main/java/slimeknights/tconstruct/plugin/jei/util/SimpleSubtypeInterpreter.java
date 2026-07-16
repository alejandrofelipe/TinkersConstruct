package slimeknights.tconstruct.plugin.jei.util;

import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;

/**
 * Functional bridge for {@link ISubtypeInterpreter}: the JEI interface has a second abstract (legacy) method,
 * which prevents using it directly as a lambda. This sub-interface provides that legacy method in terms of
 * {@link #getSubtypeData(Object, UidContext)}, leaving a single abstract method so lambdas work again.
 */
@FunctionalInterface
public interface SimpleSubtypeInterpreter<T> extends ISubtypeInterpreter<T> {
  @Deprecated
  @Override
  default String getLegacyStringSubtypeInfo(T ingredient, UidContext context) {
    Object data = getSubtypeData(ingredient, context);
    return data != null ? data.toString() : "";
  }
}
