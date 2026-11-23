package slimeknights.tconstruct.library.fluid;

import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Fluid handler wrapper that only allows filling.
 * PORT 1.21.10 (transfer step 1): native {@link ResourceHandler} over a native parent, it only backs registered capabilities.
 */
public class FillOnlyFluidHandler extends DelegatingResourceHandler<FluidResource> {
	public FillOnlyFluidHandler(ResourceHandler<FluidResource> parent) {
		super(parent);
	}

	@Override
	public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
		TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
		return 0;
	}

	@Override
	public int extract(FluidResource resource, int amount, TransactionContext transaction) {
		TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
		return 0;
	}
}
