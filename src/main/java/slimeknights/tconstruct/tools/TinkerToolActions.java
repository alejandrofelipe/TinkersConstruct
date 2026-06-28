package slimeknights.tconstruct.tools;

import net.neoforged.neoforge.common.ItemAbility;
import slimeknights.tconstruct.TConstruct;

/** Custom item abilities defined by the mod (formerly tool actions) */
public class TinkerToolActions {
  /** Tinker tools that can disable shields on attack */
  public static final ItemAbility SHIELD_DISABLE = ItemAbility.get(TConstruct.MOD_ID + ":shield_disable");
  /** Fishing rods that can act as a grappling hook */
  public static final ItemAbility GRAPPLE_HOOK = ItemAbility.get(TConstruct.MOD_ID + ":grapple_hook");
  /** Makes the tool use the drill attack during its dash action */
  public static final ItemAbility DRILL_ATTACK = ItemAbility.get(TConstruct.MOD_ID + ":drill_attack");
  /** Fishing rods that can collect items */
  public static final ItemAbility ITEM_HOOK = ItemAbility.get(TConstruct.MOD_ID + ":item_hook");
}
