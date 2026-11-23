package slimeknights.tconstruct.tables.block.entity.inventory;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.ValueIOSerializable;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import slimeknights.mantle.block.entity.MantleBlockEntity;

/** Interface for tinker chest TEs */
public interface IChestItemHandler extends IItemHandlerModifiable, ValueIOSerializable, IScalingContainer {
  /** Sets the parent of this block */
  void setParent(MantleBlockEntity parent);

  /**
   * Sets the stack in the slot without any side effect (no {@code onContentsChanged}, no visual size update).
   * Used by the native item capability inside transactions, which replays the final stack through {@link #setStackInSlot(int, ItemStack)}.
   */
  void setStackSilently(int slot, ItemStack stack);
}
