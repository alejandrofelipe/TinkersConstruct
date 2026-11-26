package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import slimeknights.tconstruct.plugin.jsonthings.block.FlexBurningLiquidBlock;
import slimeknights.tconstruct.plugin.jsonthings.block.FlexMobEffectLiquidBlock;
import slimeknights.tconstruct.plugin.jsonthings.item.FlexPartCastItem;
import slimeknights.tconstruct.tools.item.RepairKitItem;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;

/**
 * Gametests for the JSON Things plugin: the thingpack {@code src/gametest/thingpacks/tconstruct_gametest} (copied into the
 * gametest server's game dir by {@code prepareGameTestThingpacks}) defines one thing of each Tinkers block and material item
 * type. Only registered when JSON Things is loaded, as this class links against the plugin classes.
 */
public class JsonThingsGameTests {
  private static final String NAMESPACE = "tconstruct_gametest";

  private static Identifier id(String path) {
    return Identifier.fromNamespaceAndPath(NAMESPACE, path);
  }

  /** Gets a thing from the item registry, failing if the thingpack did not register it */
  private static Item item(GameTestHelper helper, String path) {
    Item item = BuiltInRegistries.ITEM.getValue(id(path));
    if (item == Items.AIR) {
      helper.fail(Component.literal("thingpack item " + id(path) + " is not registered"));
    }
    return item;
  }

  /** Gets the source fluid of a thingpack fluid, failing if it is missing */
  private static FlowingFluid fluid(GameTestHelper helper, String path) {
    Fluid fluid = BuiltInRegistries.FLUID.getValue(id(path));
    if (!(fluid instanceof FlowingFluid flowing) || fluid == Fluids.EMPTY) {
      helper.fail(Component.literal("thingpack fluid " + id(path) + " is not a registered flowing fluid: " + fluid));
      throw new IllegalStateException("unreachable");
    }
    return flowing;
  }

  /** Places the source block of the fluid and checks its block is the expected Tinkers type */
  private static void placeLiquid(GameTestHelper helper, BlockPos pos, FlowingFluid fluid, Class<? extends Block> blockClass) {
    Block block = BuiltInRegistries.BLOCK.getValue(id(BuiltInRegistries.FLUID.getKey(fluid).getPath()));
    helper.assertTrue(blockClass.isInstance(block), Component.literal("liquid block of " + BuiltInRegistries.FLUID.getKey(fluid) + " is " + block + ", expected a " + blockClass.getSimpleName()));
    helper.assertTrue(fluid.getSource().defaultFluidState().createLegacyBlock().is(block), Component.literal("fluid " + fluid + " places " + fluid.getSource().defaultFluidState().createLegacyBlock() + " instead of its liquid block"));
    // walls so the liquid stays on the pig's block
    for (BlockPos wall : new BlockPos[] { pos.north(), pos.south(), pos.east(), pos.west() }) {
      helper.setBlock(wall, Blocks.GLASS);
    }
    helper.setBlock(pos, fluid.getSource().defaultFluidState().createLegacyBlock());
  }

  /** The material item types build the matching Tinkers items */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 20)
  public static void jsonthings_material_items(GameTestHelper helper) {
    if (!(item(helper, "test_head") instanceof ToolPartItem head)) {
      helper.fail(Component.literal("tconstruct:tool_part thing is not a ToolPartItem: " + item(helper, "test_head")));
      return;
    }
    MaterialStatsId statType = head.getStatType();
    helper.assertTrue(HeadMaterialStats.ID.equals(statType), Component.literal("tool part stat type is " + statType + ", expected " + HeadMaterialStats.ID));
    if (!(item(helper, "test_repair_kit") instanceof RepairKitItem kit)) {
      helper.fail(Component.literal("tconstruct:repair_kit thing is not a RepairKitItem: " + item(helper, "test_repair_kit")));
      return;
    }
    helper.assertTrue(kit.getRepairAmount() == 3, Component.literal("repair kit repairs " + kit.getRepairAmount() + ", expected 3"));
    Item cast = item(helper, "test_head_cast");
    helper.assertTrue(cast instanceof FlexPartCastItem, Component.literal("tconstruct:part_cast thing is not a FlexPartCastItem: " + cast));
    helper.succeed();
  }

  /** A burning liquid sets entities inside on fire and hurts them */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void jsonthings_burning_liquid(GameTestHelper helper) {
    BlockPos pos = new BlockPos(2, 1, 2);
    placeLiquid(helper, pos, fluid(helper, "burning_liquid"), FlexBurningLiquidBlock.class);
    Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, pos);
    float health = pig.getHealth();
    helper.succeedWhen(() -> {
      helper.assertTrue(pig.isOnFire(), Component.literal("pig in the burning liquid is not on fire"));
      helper.assertTrue(pig.getHealth() < health, Component.literal("pig in the burning liquid was not hurt"));
    });
  }

  /** Places the mob effect liquid on a pig and checks it gets slowness with the given amplifier, not fire */
  private static void assertSlowingLiquid(GameTestHelper helper, String fluid, int amplifier, String source) {
    BlockPos pos = new BlockPos(2, 1, 2);
    placeLiquid(helper, pos, fluid(helper, fluid), FlexMobEffectLiquidBlock.class);
    Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, pos);
    helper.succeedWhen(() -> {
      MobEffectInstance effect = pig.getEffect(MobEffects.SLOWNESS);
      helper.assertTrue(effect != null, Component.literal("pig in " + fluid + " has no slowness"));
      helper.assertTrue(effect.getAmplifier() == amplifier, Component.literal("slowness amplifier is " + effect.getAmplifier() + ", expected " + amplifier + " (" + source + ")"));
      helper.assertTrue(!pig.isOnFire(), Component.literal("pig in " + fluid + " is on fire"));
    });
  }

  /** A legacy mob effect liquid with only {@code burn_time} still takes its effect level from it, like upstream */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void jsonthings_mob_effect_liquid(GameTestHelper helper) {
    assertSlowingLiquid(helper, "slowing_liquid", 2, "burn_time 3, no level");
  }

  /** A mob effect liquid with {@code level} uses it, ignoring {@code burn_time} */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void jsonthings_mob_effect_liquid_level(GameTestHelper helper) {
    assertSlowingLiquid(helper, "leveled_liquid", 1, "level 2, burn_time 4");
  }
}
