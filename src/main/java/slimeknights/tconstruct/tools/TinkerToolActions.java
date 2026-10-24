package slimeknights.tconstruct.tools;

import net.neoforged.neoforge.common.ItemAbility;
import slimeknights.tconstruct.TConstruct;

/** Custom item abilities defined by the mod (formerly tool actions) */
public class TinkerToolActions {
  /**
   * Tools that can block like a shield. 1.21.5: NeoForge removed {@code ItemAbilities.SHIELD_BLOCK} (blocking is now the
   * {@link net.minecraft.core.component.DataComponents#BLOCKS_ATTACKS} component), so Tinkers keeps the ability under the same name
   * and derives the component from it, see {@link slimeknights.tconstruct.library.tools.nbt.ToolStack#updateComponents(net.minecraft.world.item.ItemStack)}.
   */
  public static final ItemAbility SHIELD_BLOCK = ItemAbility.get("shield_block");
  /** Tinker tools that can disable shields on attack */
  public static final ItemAbility SHIELD_DISABLE = ItemAbility.get(TConstruct.MOD_ID + ":shield_disable");
  /** Fishing rods that can act as a grappling hook */
  public static final ItemAbility GRAPPLE_HOOK = ItemAbility.get(TConstruct.MOD_ID + ":grapple_hook");
  /** Makes the tool use the drill attack during its dash action */
  public static final ItemAbility DRILL_ATTACK = ItemAbility.get(TConstruct.MOD_ID + ":drill_attack");
  /** Fishing rods that can collect items */
  public static final ItemAbility ITEM_HOOK = ItemAbility.get(TConstruct.MOD_ID + ":item_hook");
}
