package slimeknights.mantle.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.fluid.FluidTransferHelper.FluidInteractionResult;
import slimeknights.mantle.fluid.transfer.EmptyFluidContainerTransfer;
import slimeknights.mantle.fluid.transfer.FluidContainerTransferManager;
import slimeknights.mantle.fluid.transfer.IFluidContainerTransfer;
import slimeknights.mantle.fluid.transfer.IFluidContainerTransfer.TransferDirection;
import slimeknights.mantle.fluid.transfer.IFluidContainerTransfer.TransferResult;
import slimeknights.mantle.recipe.helper.FluidOutput;
import slimeknights.mantle.recipe.helper.ItemOutput;
import slimeknights.mantle.transfer.SingleFluidTank;
import slimeknights.mantle.transfer.TestFluidItemCapabilities;
import slimeknights.tconstruct.test.BaseMcTest;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static slimeknights.mantle.transfer.TestFluidItemCapabilities.HONEY_WATER;

/**
 * Native {@link FluidTransferHelper} (M5 phase 6a): tank to item transfers through {@link net.neoforged.neoforge.transfer.ResourceHandler}s in one
 * transaction, per container item semantics (one item of the stack is used, the passed stack shrinks by one), no change on failure, and the JSON
 * transfers taking precedence over the item capability.
 */
class FluidTransferHelperTest extends BaseMcTest {
  @BeforeAll
  static void registerCapabilities() throws ReflectiveOperationException {
    TestFluidItemCapabilities.register();
  }

  @AfterAll
  static void unregisterCapabilities() throws ReflectiveOperationException {
    TestFluidItemCapabilities.unregister();
  }

  @AfterEach
  void clearTransfers() throws ReflectiveOperationException {
    setTransfers(Collections.emptyList());
  }

  /** Replaces the loaded JSON transfers */
  private static void setTransfers(List<IFluidContainerTransfer> transfers) throws ReflectiveOperationException {
    Field field = FluidContainerTransferManager.class.getDeclaredField("transfers");
    field.setAccessible(true);
    field.set(FluidContainerTransferManager.INSTANCE, transfers);
    FluidContainerTransferManager.INSTANCE.setContainerItems(null);
  }

  /** Native tank with one slot per given fluid */
  private static FluidStacksResourceHandler tank(int capacity, FluidStack... contents) {
    FluidStacksResourceHandler tank = new FluidStacksResourceHandler(Math.max(1, contents.length), capacity);
    for (int i = 0; i < contents.length; i++) {
      if (!contents[i].isEmpty()) {
        tank.set(i, FluidResource.of(contents[i]), contents[i].getAmount());
      }
    }
    return tank;
  }

  private static void assertSlot(FluidStacksResourceHandler tank, int slot, FluidStack expected) {
    assertThat(tank.getResource(slot)).isEqualTo(expected.isEmpty() ? FluidResource.EMPTY : FluidResource.of(expected));
    assertThat(tank.getAmountAsInt(slot)).isEqualTo(expected.getAmount());
  }

  private static void assertFluid(FluidStack actual, FluidStack expected) {
    assertThat(FluidStack.matches(actual, expected)).as("expected %s, got %s", expected, actual).isTrue();
  }


  /* Transfer */

  @Test
  void getDrainableFluid_firstExtractable_withoutChanges() {
    FluidStacksResourceHandler tank = tank(1000, FluidStack.EMPTY, new FluidStack(Fluids.LAVA, 300), new FluidStack(Fluids.WATER, 200));
    assertFluid(FluidTransferHelper.getDrainableFluid(tank), new FluidStack(Fluids.LAVA, 300));
    assertSlot(tank, 1, new FluidStack(Fluids.LAVA, 300));
    assertThat(FluidTransferHelper.getDrainableFluid(tank(1000))).matches(FluidStack::isEmpty);
  }

  @Test
  void tryTransfer_movesOnlyTheFirstFluid_upToTheMaximum() {
    FluidStacksResourceHandler input = tank(1000, new FluidStack(Fluids.WATER, 500), new FluidStack(Fluids.LAVA, 500));
    FluidStacksResourceHandler output = tank(4000);
    try (Transaction tx = Transaction.openRoot()) {
      assertFluid(FluidTransferHelper.tryTransfer(input, output, 200, tx), new FluidStack(Fluids.WATER, 200));
      assertFluid(FluidTransferHelper.tryTransfer(input, output, Integer.MAX_VALUE, tx), new FluidStack(Fluids.WATER, 300));
      tx.commit();
    }
    assertSlot(input, 0, FluidStack.EMPTY);
    assertSlot(input, 1, new FluidStack(Fluids.LAVA, 500));
    assertSlot(output, 0, new FluidStack(Fluids.WATER, 500));
  }

  @Test
  void tryTransfer_filtered_andAborted_leavesNoChange() {
    FluidStacksResourceHandler input = tank(1000, new FluidStack(Fluids.WATER, 500), new FluidStack(Fluids.LAVA, 500));
    FluidStacksResourceHandler output = tank(4000);
    try (Transaction tx = Transaction.openRoot()) {
      assertFluid(FluidTransferHelper.tryTransfer(input, output, new FluidStack(Fluids.LAVA, 300), tx), new FluidStack(Fluids.LAVA, 300));
      assertSlot(output, 0, new FluidStack(Fluids.LAVA, 300));
      // not committed
    }
    assertSlot(input, 1, new FluidStack(Fluids.LAVA, 500));
    assertSlot(output, 0, FluidStack.EMPTY);
  }


  /* Item interaction */

  @Test
  void interactWithStack_emptiesBucket() {
    FluidStacksResourceHandler tank = tank(4000);
    ItemStack stack = new ItemStack(Items.WATER_BUCKET);
    TransferResult result = FluidTransferHelper.interactWithStack(tank, stack, TransferDirection.AUTO);
    assertThat(result).isNotNull();
    assertThat(result.stack().is(Items.BUCKET)).isTrue();
    assertThat(result.didFill()).isFalse();
    assertFluid(result.fluid(), new FluidStack(Fluids.WATER, 1000));
    assertThat(stack.isEmpty()).isTrue();
    assertSlot(tank, 0, new FluidStack(Fluids.WATER, 1000));
  }

  @Test
  void interactWithStack_fillsBucket() {
    FluidStacksResourceHandler tank = tank(4000, new FluidStack(Fluids.WATER, 1500));
    ItemStack stack = new ItemStack(Items.BUCKET);
    TransferResult result = FluidTransferHelper.interactWithStack(tank, stack, TransferDirection.AUTO);
    assertThat(result).isNotNull();
    assertThat(result.stack().is(Items.WATER_BUCKET)).isTrue();
    assertThat(result.didFill()).isTrue();
    assertSlot(tank, 0, new FluidStack(Fluids.WATER, 500));
  }

  @Test
  void interactWithStack_usesOneContainerOfTheStack() {
    FluidStacksResourceHandler tank = tank(4000, new FluidStack(Fluids.WATER, 100));
    ItemStack stack = new ItemStack(Items.HONEY_BOTTLE, 3);
    // honey cannot be filled, so reverse falls back to emptying one bottle
    TransferResult result = FluidTransferHelper.interactWithStack(tank, stack, TransferDirection.REVERSE);
    assertThat(result).isNotNull();
    assertThat(result.didFill()).isFalse();
    assertThat(result.stack().is(Items.GLASS_BOTTLE)).isTrue();
    assertThat(result.stack().getCount()).isEqualTo(1);
    assertThat(stack.getCount()).isEqualTo(2);
    assertSlot(tank, 0, new FluidStack(Fluids.WATER, 100 + HONEY_WATER));
  }

  @Test
  void interactWithStack_failure_leavesNoChange() {
    // wrong fluid
    FluidStacksResourceHandler lava = tank(4000, new FluidStack(Fluids.LAVA, 500));
    ItemStack bucket = new ItemStack(Items.WATER_BUCKET);
    assertThat(FluidTransferHelper.interactWithStack(lava, bucket, TransferDirection.AUTO)).isNull();
    assertThat(bucket.is(Items.WATER_BUCKET)).isTrue();
    assertThat(bucket.getCount()).isEqualTo(1);
    assertSlot(lava, 0, new FluidStack(Fluids.LAVA, 500));
    // direction does not allow filling the empty bucket
    FluidStacksResourceHandler water = tank(4000, new FluidStack(Fluids.WATER, 1500));
    ItemStack empty = new ItemStack(Items.BUCKET);
    assertThat(FluidTransferHelper.interactWithStack(water, empty, TransferDirection.EMPTY_ITEM)).isNull();
    assertThat(empty.getCount()).isEqualTo(1);
    assertSlot(water, 0, new FluidStack(Fluids.WATER, 1500));
    // not enough for a whole bucket
    FluidStacksResourceHandler little = tank(4000, new FluidStack(Fluids.WATER, 600));
    assertThat(FluidTransferHelper.interactWithStack(little, empty, TransferDirection.FILL_ITEM)).isNull();
    assertSlot(little, 0, new FluidStack(Fluids.WATER, 600));
  }

  @Test
  void fillStack_drainsOnlyTheRequestedFluid() {
    FluidStacksResourceHandler tank = tank(4000, new FluidStack(Fluids.LAVA, 1000), new FluidStack(Fluids.WATER, 1000));
    ItemStack stack = new ItemStack(Items.BUCKET);
    TransferResult result = FluidTransferHelper.fillStack(tank, stack, new FluidStack(Fluids.WATER, 1000));
    assertThat(result).isNotNull();
    assertThat(result.stack().is(Items.WATER_BUCKET)).isTrue();
    assertThat(stack.isEmpty()).isTrue();
    assertSlot(tank, 0, new FluidStack(Fluids.LAVA, 1000));
    assertSlot(tank, 1, FluidStack.EMPTY);
  }

  @Test
  void jsonTransfer_takesPrecedenceOverTheCapability() throws ReflectiveOperationException {
    // honey bottles hold water through their capability; the JSON transfer empties lava instead, so the result shows which path ran
    setTransfers(List.of(new EmptyFluidContainerTransfer(Ingredient.of(Items.HONEY_BOTTLE), ItemOutput.fromItem(Items.GLASS_BOTTLE), FluidOutput.fromFluid(Fluids.LAVA, 250))));
    FluidStacksResourceHandler tank = tank(4000);
    ItemStack stack = new ItemStack(Items.HONEY_BOTTLE, 2);
    TransferResult result = FluidTransferHelper.interactWithStack(tank, stack, TransferDirection.AUTO);
    assertThat(result).isNotNull();
    assertThat(result.stack().is(Items.GLASS_BOTTLE)).isTrue();
    assertThat(stack.getCount()).isEqualTo(1);
    assertSlot(tank, 0, new FluidStack(Fluids.LAVA, 250));

    // a JSON transfer that fails falls back to the capability
    FluidStacksResourceHandler water = tank(4000, new FluidStack(Fluids.WATER, 100));
    result = FluidTransferHelper.interactWithStack(water, stack, TransferDirection.AUTO);
    assertThat(result).isNotNull();
    assertSlot(water, 0, new FluidStack(Fluids.WATER, 100 + HONEY_WATER));
  }

  /** Server side level and a player holding the given stack, mocked: only the failure paths below run without the fluid type registry (sounds, messages) */
  private static Player holding(ItemStack held) {
    Level level = mock(Level.class);
    when(level.isClientSide()).thenReturn(false);
    Player player = mock(Player.class);
    when(player.level()).thenReturn(level);
    when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(held);
    return player;
  }

  @Test
  void interactWithContainer_failedJsonTransfer_returnsContainerWithoutCapabilityFallback() throws ReflectiveOperationException {
    // the JSON transfer empties lava, which the water tank refuses; the honey bottle capability (water) would fit, but unlike
    // interactWithStack the player interaction does not fall back to it
    setTransfers(List.of(new EmptyFluidContainerTransfer(Ingredient.of(Items.HONEY_BOTTLE), ItemOutput.fromItem(Items.GLASS_BOTTLE), FluidOutput.fromFluid(Fluids.LAVA, 250))));
    FluidStacksResourceHandler tank = tank(4000, new FluidStack(Fluids.WATER, 100));
    ItemStack held = new ItemStack(Items.HONEY_BOTTLE, 2);
    Player player = holding(held);
    FluidInteractionResult result = FluidTransferHelper.interactWithContainer(player.level(), BlockPos.ZERO, tank, player, InteractionHand.MAIN_HAND);
    assertThat(result).isEqualTo(FluidInteractionResult.CONTAINER);
    assertSlot(tank, 0, new FluidStack(Fluids.WATER, 100));
    assertThat(held.getCount()).isEqualTo(2);
    verify(player, never()).setItemInHand(any(), any());
  }

  @Test
  void interactWithFilledBucket_noRoomForTheWholeBucket_returnsContainerWithoutChange() {
    FluidStacksResourceHandler tank = tank(500, new FluidStack(Fluids.WATER, 100));
    ItemStack held = new ItemStack(Items.WATER_BUCKET);
    Player player = holding(held);
    FluidInteractionResult result = FluidTransferHelper.interactWithFilledBucket(player.level(), BlockPos.ZERO, tank, player, InteractionHand.MAIN_HAND, Direction.UP);
    assertThat(result).isEqualTo(FluidInteractionResult.CONTAINER);
    assertSlot(tank, 0, new FluidStack(Fluids.WATER, 100));
    assertThat(held.is(Items.WATER_BUCKET)).isTrue();
    verify(player, never()).setItemInHand(any(), any());
  }

  @Test
  void interaction_insideOpenTransaction_throws() {
    FluidStacksResourceHandler tank = tank(4000);
    ItemStack stack = new ItemStack(Items.WATER_BUCKET);
    try (Transaction ignored = Transaction.openRoot()) {
      assertThatThrownBy(() -> FluidTransferHelper.interactWithStack(tank, stack, TransferDirection.AUTO)).isInstanceOf(IllegalStateException.class);
    }
    assertThat(stack.is(Items.WATER_BUCKET)).isTrue();
    assertSlot(tank, 0, FluidStack.EMPTY);
  }

  /** Mantle's native single tank (the replacement of NeoForge's legacy {@code FluidTank}, which the removed legacy overload test used) */
  @Test
  void singleFluidTank_interactAndFill() {
    SingleFluidTank tank = new SingleFluidTank(4000);
    ItemStack stack = new ItemStack(Items.WATER_BUCKET);
    TransferResult result = FluidTransferHelper.interactWithStack(tank, stack, TransferDirection.AUTO);
    assertThat(result).isNotNull();
    assertThat(result.stack().is(Items.BUCKET)).isTrue();
    assertThat(result.didFill()).isFalse();
    assertThat(stack.isEmpty()).isTrue();
    assertThat(tank.getFluidAmount()).isEqualTo(1000);

    result = FluidTransferHelper.fillStack(tank, new ItemStack(Items.BUCKET), new FluidStack(Fluids.WATER, 1000));
    assertThat(result).isNotNull();
    assertThat(result.stack().is(Items.WATER_BUCKET)).isTrue();
    assertThat(tank.isEmpty()).isTrue();
  }
}
