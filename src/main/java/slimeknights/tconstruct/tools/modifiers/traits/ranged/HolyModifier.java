package slimeknights.tconstruct.tools.modifiers.traits.ranged;

import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.modules.combat.ConditionalPowerModule;
import slimeknights.tconstruct.library.module.ModuleHookMap.Builder;

/** @deprecated use {@link ConditionalPowerModule} */
@Deprecated(forRemoval = true)
public class HolyModifier extends Modifier {
  @Override
  protected void registerHooks(Builder hookBuilder) {
    super.registerHooks(hookBuilder);
    // Superseded by the data-driven ConditionalPowerModule; this @Deprecated modifier is kept only for data
    // back-compat and intentionally adds no hooks. (MobType was removed in 1.21 — undead is now
    // EntityTypeTags.UNDEAD — and Mantle's MobTypePredicate is registered and tag-based, no longer a stub.)
  }
}
