package slimeknights.tconstruct.smeltery.block.entity.tank;

import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.RootCommitJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import slimeknights.tconstruct.library.fluid.FillOnlyFluidHandler;
import slimeknights.tconstruct.smeltery.block.entity.ChannelBlockEntity;

/**
 * Tank for each side connection, for the sake of rendering.
 * PORT 1.21.10 (transfer step 1): native; marks the side as flowing once a fill commits, never on a simulation.
 */
public class ChannelSideTank extends FillOnlyFluidHandler {
	private final ChannelBlockEntity channel;
	private final Direction side;
	/** Marks the side as flowing once a transaction that filled through this side commits */
	private final RootCommitJournal flowJournal;

	public ChannelSideTank(ChannelBlockEntity channel, ChannelTank tank, Direction side) {
		super(tank);
		// only horizontals
		assert side.getAxis() != Axis.Y;
		this.channel = channel;
		this.side = side;
		this.flowJournal = new RootCommitJournal(() -> this.channel.setFlow(this.side, true));
	}

	@Override
	public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
		int filled = super.insert(index, resource, amount, transaction);
		if (filled > 0) {
			flowJournal.updateSnapshots(transaction);
		}
		return filled;
	}

	@Override
	public int insert(FluidResource resource, int amount, TransactionContext transaction) {
		int filled = super.insert(resource, amount, transaction);
		if (filled > 0) {
			flowJournal.updateSnapshots(transaction);
		}
		return filled;
	}
}
