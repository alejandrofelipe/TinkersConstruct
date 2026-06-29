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
    // PORT M3: net.minecraft.world.entity.MobType was removed in 1.21 (undead is now EntityTypeTags.UNDEAD);
    // Mantle's MobTypePredicate is a deferred stub (loader registration commented out in Mantle.java).
    // Re-enable once ConditionalPowerModule.Builder#target and a tag-based entity predicate are available.
    // hookBuilder.addModule(ConditionalPowerModule.builder().target(new MobTypePredicate(MobType.UNDEAD)).eachLevel(0.75f));
  }
}
