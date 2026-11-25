package slimeknights.tconstruct.smeltery.block.entity.tank;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import slimeknights.mantle.transfer.SingleFluidTank;
import slimeknights.tconstruct.smeltery.block.entity.ChannelBlockEntity;

/**
 * Tank for channel contents.
 * PORT 1.21.10 (transfer): a native {@link ResourceHandler} on Mantle's {@link SingleFluidTank}; changes to the fluid and the lock are
 * journaled and the client sync runs on root commit only. The channel's own ticking moves fluid through transactions as well.
 */
public class ChannelTank extends SingleFluidTank {
	private static final String TAG_LOCKED = "locked";

	/**
	 * Amount of fluid that may not be extracted this tick
	 * Essentially, since we cannot guarantee tick order, this prevents us from having a net 0 fluid for the renderer
	 * if draining and filling at the same time
	 */
	private int locked;

	/** Tank owner */
	private final ChannelBlockEntity parent;

	/** Journal of the lock for native transfers, the fluid is journaled by the parent class */
	private final SnapshotJournal<Integer> lockJournal = new SnapshotJournal<>() {
		@Override
		protected Integer createSnapshot() {
			return locked;
		}

		@Override
		protected void revertToSnapshot(Integer snapshot) {
			locked = snapshot;
		}
	};

	public ChannelTank(int capacity, ChannelBlockEntity parent) {
		super(capacity);
		this.parent = parent;
	}

	@Override
	protected void onRootCommit(FluidStack original) {
		// sync to client if we went from empty to filled or back
		if (original.isEmpty() != fluid.isEmpty()) {
			parent.sendFluidUpdate();
		}
	}

	@Override
	public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
		int inserted = super.insert(index, resource, amount, transaction);
		if (inserted > 0) {
			// newly added fluid may not leave this tick
			lockJournal.updateSnapshots(transaction);
			locked += inserted;
		}
		return inserted;
	}

	/**
	 * Called on channel update to clear the lock, allowing this fluid to be drained
	 */
	public void freeFluid() {
		this.locked = 0;
	}

	/**
	 * Returns the maximum fluid that can be extracted from this tank
	 * @return  Max fluid that can be pulled
	 */
	public int getMaxUsable() {
		return Math.max(fluid.getAmount() - locked, 0);
	}

	@Override
	public void deserialize(ValueInput input) {
		this.locked = input.getIntOr(TAG_LOCKED, 0);
		super.deserialize(input);
	}

	@Override
	public void serialize(ValueOutput output) {
		super.serialize(output);
		output.putInt(TAG_LOCKED, locked);
	}
}
