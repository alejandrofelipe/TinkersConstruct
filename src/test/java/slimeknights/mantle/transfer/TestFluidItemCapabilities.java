package slimeknights.mantle.transfer;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import slimeknights.mantle.item.ConstantFluidContainerWrapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;

/**
 * Registers fluid item capabilities for unit tests, where no mod loading fires {@link RegisterCapabilitiesEvent}: vanilla water/lava buckets
 * and the empty bucket get a {@link TestBucketHandler}, honey bottles a constant 250 water that leaves a glass bottle. Call {@link #unregister()}
 * after the test class.
 */
public final class TestFluidItemCapabilities {
  /** Amount of water in a honey bottle */
  public static final int HONEY_WATER = 250;
  /** Amount in a bucket */
  public static final int BUCKET = 1000;
  private static final Item[] BUCKETS = { Items.BUCKET, Items.WATER_BUCKET, Items.LAVA_BUCKET };

  private TestFluidItemCapabilities() {}

  /** Registers the capabilities */
  public static void register() throws ReflectiveOperationException {
    Constructor<RegisterCapabilitiesEvent> constructor = RegisterCapabilitiesEvent.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    RegisterCapabilitiesEvent event = constructor.newInstance();
    event.registerItem(Capabilities.Fluid.ITEM, (stack, access) -> new TestBucketHandler(access), BUCKETS);
    event.registerItem(Capabilities.Fluid.ITEM, (stack, access) -> new ConstantFluidContainerWrapper(new FluidStack(Fluids.WATER, HONEY_WATER), access, ItemResource.of(Items.GLASS_BOTTLE)), Items.HONEY_BOTTLE);
  }

  /** Removes the capabilities again */
  @SuppressWarnings("unchecked")
  public static void unregister() throws ReflectiveOperationException {
    Field providers = ItemCapability.class.getDeclaredField("providers");
    providers.setAccessible(true);
    Map<Item, ?> map = (Map<Item, ?>) providers.get(Capabilities.Fluid.ITEM);
    for (Item bucket : BUCKETS) {
      map.remove(bucket);
    }
    map.remove(Items.HONEY_BOTTLE);
  }

  /**
   * Water and lava buckets that fill and empty whole buckets by exchanging the accessed item, like NeoForge's {@code BucketResourceHandler}.
   * That one reads the fluid type registry, which unit tests do not have.
   */
  private record TestBucketHandler(ItemAccess access) implements ResourceHandler<FluidResource> {
    private static Fluid contained(Item item) {
      if (item == Items.WATER_BUCKET) {
        return Fluids.WATER;
      }
      if (item == Items.LAVA_BUCKET) {
        return Fluids.LAVA;
      }
      return Fluids.EMPTY;
    }

    private static Item filled(Fluid fluid) {
      if (fluid == Fluids.WATER) {
        return Items.WATER_BUCKET;
      }
      if (fluid == Fluids.LAVA) {
        return Items.LAVA_BUCKET;
      }
      return Items.AIR;
    }

    @Override
    public int size() {
      return 1;
    }

    @Override
    public FluidResource getResource(int index) {
      Fluid fluid = contained(access.getResource().getItem());
      return fluid == Fluids.EMPTY ? FluidResource.EMPTY : FluidResource.of(fluid);
    }

    @Override
    public long getAmountAsLong(int index) {
      return getResource(index).isEmpty() ? 0 : (long) BUCKET * access.getAmount();
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
      return (long) BUCKET * access.getAmount();
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
      return resource.isComponentsPatchEmpty() && filled(resource.getFluid()) != Items.AIR;
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
      if (!access.getResource().is(Items.BUCKET) || !isValid(index, resource) || amount < BUCKET) {
        return 0;
      }
      return access.exchange(ItemResource.of(filled(resource.getFluid())), amount / BUCKET, transaction) * BUCKET;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
      if (resource.isEmpty() || !resource.equals(getResource(index)) || amount < BUCKET) {
        return 0;
      }
      return access.exchange(ItemResource.of(Items.BUCKET), amount / BUCKET, transaction) * BUCKET;
    }
  }
}
