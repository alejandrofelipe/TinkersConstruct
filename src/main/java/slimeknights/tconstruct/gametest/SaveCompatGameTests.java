package slimeknights.tconstruct.gametest;

import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
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
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;
import slimeknights.tconstruct.tools.TinkerToolParts;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.data.material.MaterialIds;
import slimeknights.tconstruct.tools.entity.ThrownTool;

/**
 * Save/load and recipe-lookup regressions found by the 1.21.8 save-compat gametests and backported to the 1.21.4 line.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(TConstruct.MOD_ID)
public class SaveCompatGameTests {

  /**
   * Casting into an empty basin or table must find the castless recipe through the real fill path. The casting input has
   * no items (the fluid is the input), and vanilla's {@code RecipeMap#getRecipesFor} returns nothing for an item-empty
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

    Entity loaded = EntityType.create(tag, helper.getLevel(), EntitySpawnReason.LOAD).orElse(null);
    if (!(loaded instanceof ThrownTool loadedTool)) {
      throw new IllegalStateException("Expected a loaded ThrownTool, got " + loaded);
    }
    checkStack(helper, stack, loadedTool.getPickupItemStackOrigin(), "thrown tool stack");
    checkStack(helper, stack, loadedTool.getDisplayTool(), "thrown tool display stack");
    helper.succeed();
  }

  /**
   * A tool built at a tinker station (and one built through {@code ToolStack#createStack} directly) must survive
   * {@link ItemStack#CODEC}, the codec every inventory, container and entity save goes through. {@code createStack} marks
   * tools damageable with a {@code minecraft:max_damage} marker; that component is {@code POSITIVE_INT}, so the old
   * {@code -1} marker failed the encode and every built tool vanished from saves. The marker must keep the stack damageable,
   * with the real max damage still coming from the durability stat.
   */
  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void save_compat_tool_stack_codec_round_trip(GameTestHelper helper) {
    TinkerStationBlockEntity station = place(helper, new BlockPos(2, 1, 2), TinkerTables.tinkerStation.get(), TinkerStationBlockEntity.class);
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT, TinkerToolParts.pickHead.get().withMaterial(MaterialIds.rock));
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 1, TinkerToolParts.toolHandle.get().withMaterial(MaterialIds.wood));
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 2, TinkerToolParts.toolBinding.get().withMaterial(MaterialIds.wood));
    ItemStack crafted = station.calcResult(null);
    check(helper, crafted.is(TinkerTools.pickaxe.get()), "tinker station built no pickaxe, got " + describe(crafted) + "; error=" + station.getCurrentError());
    checkToolCodec(helper, crafted, "crafted pickaxe");
    checkToolCodec(helper, damagedTool(pickaxe()), "damaged iron pickaxe");
    helper.succeed();
  }

  /**
   * The dagger is the one tool stackable to 2; its built stack must round-trip {@link ItemStack#CODEC} like any other tool
   * (no {@code max_damage} the codec rejects), and keep its durability.
   */
  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void save_compat_dagger_codec_round_trip(GameTestHelper helper) {
    ItemStack dagger = ToolBuildHandler.createSingleMaterial(TinkerTools.dagger.get(), MaterialVariant.of((MaterialVariantId) MaterialIds.iron));
    check(helper, !dagger.isEmpty(), "could not build an iron dagger");
    checkToolCodec(helper, dagger, "iron dagger");
    checkToolCodec(helper, dagger.copyWithCount(2), "stack of 2 iron daggers");
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

  /**
   * Asserts the tool stack encodes and decodes through {@link ItemStack#CODEC} with equal components, any {@code max_damage}
   * in its patch is positive, and it stays damageable with the durability stat as max damage.
   */
  private static void checkToolCodec(GameTestHelper helper, ItemStack stack, String name) {
    RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
    DataResult<Tag> encoded = ItemStack.CODEC.encodeStart(ops, stack);
    check(helper, encoded.isSuccess(), name + " " + describe(stack) + " failed to encode: " + encoded.error().map(DataResult.Error::message).orElse(""));
    DataResult<ItemStack> decoded = ItemStack.CODEC.parse(ops, encoded.getOrThrow());
    check(helper, decoded.isSuccess(), name + " failed to decode: " + decoded.error().map(DataResult.Error::message).orElse(""));
    ItemStack read = decoded.getOrThrow();
    check(helper, ItemStack.isSameItemSameComponents(stack, read) && read.getCount() == stack.getCount(), name + " changed through the codec: " + describe(stack) + " became " + describe(read));
    Integer marker = stack.get(DataComponents.MAX_DAMAGE);
    check(helper, marker == null || marker > 0, name + " has a non-positive max_damage " + marker);
    check(helper, stack.isDamageableItem() && read.isDamageableItem(), name + " is no longer damageable: " + describe(read));
    int durability = ToolStack.from(read).getStats().getInt(ToolStats.DURABILITY);
    check(helper, read.getMaxDamage() == durability, name + " max damage " + read.getMaxDamage() + " should be the durability stat " + durability);
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
