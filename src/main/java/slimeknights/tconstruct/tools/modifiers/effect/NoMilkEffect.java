package slimeknights.tconstruct.tools.modifiers.effect;

import net.minecraft.world.effect.MobEffectCategory;
import slimeknights.tconstruct.common.TinkerEffect;

/**
 * Effect that cannot be cured with milk.
 * PORT 1.21.4: the NeoForge cure system (EffectCure / fillEffectCures) was removed, so the uncurable behaviour is no
 * longer expressible here and this effect is now milk-curable. Re-wire in Phase 4 if an equivalent gate returns.
 * TODO 1.21: move to {@link slimeknights.tconstruct.shared.effect}
 */
public class NoMilkEffect extends TinkerEffect {
  public NoMilkEffect(MobEffectCategory typeIn, int color, boolean show) {
    super(typeIn, color, show);
  }
}
