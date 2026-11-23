package slimeknights.tconstruct.shared.inventory;

import net.minecraft.world.Container;
import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Item capability of a container that may forbid insertion and/or extraction, typically to expose a station's contents read only.
 * <p>
 * PORT 1.21.10 (transfer step 1): native, over NeoForge's {@link VanillaContainerWrapper} (was a legacy {@code InvWrapper}). Note the
 * wrapper calls {@code Container#setItem} inside transactions, so only allow insertion or extraction for containers whose {@code setItem}
 * has no side effects; all current users (the stations) forbid both.
 */
public class ConfigurableInvWrapperCapability extends DelegatingResourceHandler<ItemResource> {
  private final boolean canInsert;
  private final boolean canExtract;

  public ConfigurableInvWrapperCapability(Container inv, boolean canInsert, boolean canExtract) {
    // NeoForge caches the wrapper per container and refreshes its size on each lookup
    super(() -> VanillaContainerWrapper.of(inv));
    this.canInsert = canInsert;
    this.canExtract = canExtract;
  }

  @Override
  public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
    TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
    return canInsert ? super.insert(index, resource, amount, transaction) : 0;
  }

  @Override
  public int insert(ItemResource resource, int amount, TransactionContext transaction) {
    TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
    return canInsert ? super.insert(resource, amount, transaction) : 0;
  }

  @Override
  public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
    TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
    return canExtract ? super.extract(index, resource, amount, transaction) : 0;
  }

  @Override
  public int extract(ItemResource resource, int amount, TransactionContext transaction) {
    TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
    return canExtract ? super.extract(resource, amount, transaction) : 0;
  }
}
