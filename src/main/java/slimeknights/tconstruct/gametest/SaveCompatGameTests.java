package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.recipe.FluidValues;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.data.material.MaterialIds;
import slimeknights.tconstruct.tools.entity.ThrownTool;

/**
 * Save/load and recipe-lookup regressions found by the 1.21.8 save-compat gametests and backported to the 1.21.1 line.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(TConstruct.MOD_ID)
public class SaveCompatGameTests {

  /**
   * Casting into an empty basin or table must find the castless recipe through the real fill path. The casting input has
   * no items (the fluid is the input), and 1.21.1's {@code RecipeManager#getRecipeFor} returns nothing for an item-empty
   * {@code RecipeInput}, so castless casts (iron blocks, iron bars...) could not be made.
   */
  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void save_compat_castless_casting(GameTestHelper helper) {
    // basin without a cast: molten iron casts an iron block
    CastingBlockEntity basin = place(helper, new BlockPos(1, 1, 2), TinkerSmeltery.searedBasin.get(), CastingBlockEntity.class);
    FluidStack basinFluid = new FluidStack(TinkerFluids.moltenIron.get(), FluidValues.INGOT * 2);
    int basinFilled = basin.getTank().fill(basinFluid.copy(), FluidAction.EXECUTE);
    check(helper, basinFilled == basinFluid.getAmount(), "empty seared basin accepted " + basinFilled + "mb of " + basinFluid.getAmount() + "mb molten iron; no castless iron block recipe found");
    int basinCapacity = basin.getTank().getCapacity();
    check(helper, basinCapacity == FluidValues.METAL_BLOCK, "empty basin + molten iron should size the tank to the iron block recipe (" + FluidValues.METAL_BLOCK + "), got " + basinCapacity);

    // table without a cast: molten iron casts iron bars (smeltery/casting/metal/iron/bars.json, 30mb)
    CastingBlockEntity table = place(helper, new BlockPos(3, 1, 2), TinkerSmeltery.searedTable.get(), CastingBlockEntity.class);
    int tableFilled = table.getTank().fill(new FluidStack(TinkerFluids.moltenIron.get(), 20), FluidAction.EXECUTE);
    check(helper, tableFilled == 20, "empty casting table accepted " + tableFilled + "mb of 20mb molten iron; no castless iron bars recipe found");
    check(helper, table.getTank().getCapacity() == 30, "empty table + molten iron should size the tank to the iron bars recipe (30), got " + table.getTank().getCapacity());
    helper.succeed();
  }

  /**
   * A thrown tool keeps its tool stack and its synced display stack through save → load. Since 1.20.5 vanilla stores the
   * arrow pickup stack under {@code item} ({@code AbstractArrow}); {@code ThrownTool} refreshed its display stack (plus
   * loyalty, shiny, indestructible and magnet) only if it saw the pre-1.20.5 {@code Trident} key, so a reloaded thrown tool
   * was invisible and lost loyalty.
   */
  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void save_compat_thrown_tool_round_trip(GameTestHelper helper) {
    ItemStack stack = damagedTool(pickaxe());
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    ThrownTool thrown = new ThrownTool(helper.getLevel(), player, stack, 1f, 1.5f, 0.6f);
    CompoundTag tag = new CompoundTag();
    check(helper, thrown.save(tag), "thrown tool refused to save");
    check(helper, "tconstruct:pickaxe".equals(tag.getCompound("item").getString("id")), "thrown tool stack must be saved under item, got " + tag);

    Entity loaded = EntityType.create(tag, helper.getLevel()).orElse(null);
    if (!(loaded instanceof ThrownTool loadedTool)) {
      throw new IllegalStateException("Expected a loaded ThrownTool, got " + loaded);
    }
    checkStack(helper, stack, loadedTool.getPickupItemStackOrigin(), "thrown tool stack");
    checkStack(helper, stack, loadedTool.getDisplayTool(), "thrown tool display stack");
    helper.succeed();
  }


  /* Helpers */

  /** Places a block at the relative position and returns its block entity */
  private static <T extends BlockEntity> T place(GameTestHelper helper, BlockPos relative, Block block, Class<T> clazz) {
    BlockPos pos = helper.absolutePos(relative);
    helper.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
    BlockEntity be = helper.getLevel().getBlockEntity(pos);
    if (!clazz.isInstance(be)) {
      throw new IllegalStateException("Expected a " + clazz.getSimpleName() + " at " + relative + ", got " + be);
    }
    return clazz.cast(be);
  }

  /** Builds an iron pickaxe */
  private static ItemStack pickaxe() {
    ItemStack stack = ToolBuildHandler.createSingleMaterial(TinkerTools.pickaxe.get(), MaterialVariant.of((MaterialVariantId) MaterialIds.iron));
    if (stack.isEmpty()) {
      throw new IllegalStateException("Could not build an iron pickaxe");
    }
    return stack;
  }

  /** Damages the tool and adds persistent data, so the round-trip compares more than the default components */
  private static ItemStack damagedTool(ItemStack stack) {
    ToolStack tool = ToolStack.from(stack);
    tool.setDamage(3);
    tool.getPersistentData().putInt(TConstruct.getResource("save_compat"), 42);
    return tool.createStack(stack.getCount());
  }

  private static void checkStack(GameTestHelper helper, ItemStack expected, ItemStack actual, String name) {
    check(helper, ItemStack.matches(expected, actual), name + ": expected " + describe(expected) + ", got " + describe(actual));
  }

  private static String describe(ItemStack stack) {
    return stack.isEmpty() ? "empty" : stack.getCount() + "x " + stack.getItem() + " " + stack.getComponentsPatch();
  }

  private static void check(GameTestHelper helper, boolean condition, String message) {
    helper.assertTrue(condition, message);
  }
}
