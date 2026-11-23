package slimeknights.tconstruct.smeltery.block.entity.tank;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import slimeknights.tconstruct.smeltery.block.entity.ChannelBlockEntity;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Tank for channel contents.
 * PORT 1.21.10 (transfer step 1): also a native {@link ResourceHandler}; native changes to the fluid and the lock are journaled and the
 * client sync runs on root commit only. The legacy methods stay for the channel's own ticking.
 */
@SuppressWarnings("removal")  // FluidTank is the legacy storage until phase 6
public class ChannelTank extends FluidTank implements ResourceHandler<FluidResource> {
	private static final String TAG_LOCKED = "locked";

	/**
	 * Amount of fluid that may not be extracted this tick
	 * Essentially, since we cannot guarantee tick order, this prevents us from having a net 0 fluid for the renderer
	 * if draining and filling at the same time
	 */
	private int locked;

	/** Tank owner */
	private final ChannelBlockEntity parent;

	public ChannelTank(int capacity, ChannelBlockEntity parent) {
		super(capacity);
		this.parent = parent;
	}

	/* Native transfer */

	/** Snapshot of the native state */
	private record State(FluidStack fluid, int locked) {}

	/** Journal of the fluid and lock for native transfers */
	private final SnapshotJournal<State> journal = new SnapshotJournal<>() {
		@Override
		protected State createSnapshot() {
			return new State(fluid.copy(), locked);
		}

		@Override
		protected void revertToSnapshot(State snapshot) {
			fluid = snapshot.fluid();
			locked = snapshot.locked();
		}

		@Override
		protected void onRootCommit(State original) {
			// sync to client if we went from empty to filled or back
			if (original.fluid().isEmpty() != fluid.isEmpty()) {
				parent.sendFluidUpdate();
			}
		}
	};

	@Override
	public int size() {
		return 1;
	}

	@Override
	public FluidResource getResource(int index) {
		Objects.checkIndex(index, 1);
		return FluidResource.of(fluid);
	}

	@Override
	public long getAmountAsLong(int index) {
		Objects.checkIndex(index, 1);
		return fluid.getAmount();
	}

	@Override
	public long getCapacityAsLong(int index, FluidResource resource) {
		Objects.checkIndex(index, 1);
		return capacity;
	}

	@Override
	public boolean isValid(int index, FluidResource resource) {
		Objects.checkIndex(index, 1);
		return !resource.isEmpty();
	}

	@Override
	public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
		Objects.checkIndex(index, 1);
		TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
		if (!fluid.isEmpty() && !resource.matches(fluid)) {
			return 0;
		}
		int inserted = Math.min(amount, capacity - fluid.getAmount());
		if (inserted <= 0) {
			return 0;
		}
		journal.updateSnapshots(transaction);
		fluid = resource.toStack(fluid.getAmount() + inserted);
		// like the legacy fill, newly added fluid may not leave this tick
		locked += inserted;
		return inserted;
	}

	@Override
	public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
		Objects.checkIndex(index, 1);
		TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
		if (fluid.isEmpty() || !resource.matches(fluid)) {
			return 0;
		}
		int extracted = Math.min(amount, fluid.getAmount());
		if (extracted <= 0) {
			return 0;
		}
		journal.updateSnapshots(transaction);
		fluid = fluid.getAmount() == extracted ? FluidStack.EMPTY : fluid.copyWithAmount(fluid.getAmount() - extracted);
		return extracted;
	}

	/* Legacy transfer */

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
	public int fill(FluidStack resource, FluidAction action) {
		boolean wasEmpty = isEmpty();
		int amount = super.fill(resource, action);
		if(action.execute()) {
			locked += amount;
			// if we added something, sync to client
			if (wasEmpty && !isEmpty()) {
				parent.sendFluidUpdate();
			}
		}
		return amount;
	}

	@Nonnull
	@Override
	public FluidStack drain(int maxDrain, FluidAction action) {
		boolean wasEmpty = isEmpty();
		FluidStack stack = super.drain(maxDrain, action);
		// if we removed something, sync to client
		if (action.execute() && !wasEmpty && isEmpty()) {
			parent.sendFluidUpdate();
		}
		return stack;
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
