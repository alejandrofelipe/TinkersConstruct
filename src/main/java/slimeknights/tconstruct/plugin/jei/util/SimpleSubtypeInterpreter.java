package slimeknights.tconstruct.plugin.jei.util;

import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;

/**
 * Named functional alias for {@link ISubtypeInterpreter}. JEI 20 dropped the legacy {@code getLegacyStringSubtypeInfo}
 * method, leaving {@link #getSubtypeData(Object, mezz.jei.api.ingredients.subtypes.UidContext)} as the single abstract
 * method, so this can be used directly as a lambda target.
 */
@FunctionalInterface
public interface SimpleSubtypeInterpreter<T> extends ISubtypeInterpreter<T> {}
