package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.recipe.partbuilder.Pattern;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import slimeknights.tconstruct.library.tools.part.IToolPart;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tables.block.entity.table.PartBuilderBlockEntity;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;
import slimeknights.tconstruct.tools.TinkerToolParts;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.data.material.MaterialIds;

/** Gametests for the tool crafting subsystem: assembling parts into a working tool at a tinker station. */
@PrefixGameTestTemplate(false)
@GameTestHolder(TConstruct.MOD_ID)
public class ToolGameTests {

  /**
   * A stone pick head, wood tool handle, and wood tool binding are placed in a tinker station's input
   * slots and assembled into a pickaxe through the same seam the real menu uses:
   * {@link TinkerStationBlockEntity#calcResult} looks up the tool building recipe from the level's
   * {@code RecipeManager} (matching on part items, see {@code ToolBuildingRecipe.matches}) and validates
   * the built result (see {@code ToolBuildingRecipe.getValidatedResult}). Asserts the crafted stack is a
   * real pickaxe whose {@link ToolStack} has positive durability and the 3 expected part materials.
   */
  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tool_crafting(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
    helper.getLevel().setBlockAndUpdate(pos, TinkerTables.tinkerStation.get().defaultBlockState());
    if (!(helper.getLevel().getBlockEntity(pos) instanceof TinkerStationBlockEntity station)) {
      helper.fail("no tinker station BE", helper.relativePos(pos));
      return;
    }

    ItemStack head = partStack(TinkerToolParts.pickHead.get(), MaterialIds.rock);
    ItemStack handle = partStack(TinkerToolParts.toolHandle.get(), MaterialIds.wood);
    ItemStack binding = partStack(TinkerToolParts.toolBinding.get(), MaterialIds.wood);

    // input slots follow the pickaxe's part order - head, handle, binding, per PartStatsModule.parts()
    // in ToolDefinitionDataProvider (also the ToolPartsHook consulted by ToolBuildingRecipe.matches).
    // The tool slot (index 0) stays empty since we are building a new tool, not modifying one.
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT, head);
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 1, handle);
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 2, binding);

    // no world ticking is needed for a tinker station recipe (unlike smeltery melting/casting), so the
    // result is computed and asserted directly instead of polling via succeedWhen
    ItemStack result = station.calcResult(null);
    if (result.isEmpty()) {
      throw new IllegalStateException("Tinker station found no recipe for parts [" + head + ", " + handle + ", " + binding
                                      + "]; station error=" + station.getCurrentError());
    }
    helper.assertTrue(result.is(TinkerTools.pickaxe.get()), "expected a pickaxe, got " + result);

    ToolStack tool = ToolStack.from(result);
    int durability = tool.getStats().getInt(ToolStats.DURABILITY);
    helper.assertTrue(durability > 0, "expected positive durability, got " + durability);
    helper.assertTrue(tool.getMaterials().size() == 3, "expected 3 materials, got " + tool.getMaterials().size());
    helper.assertTrue(tool.getMaterials().get(0).getVariant().equals(MaterialIds.rock), "expected rock head material, got " + tool.getMaterials().get(0).getVariant());
    helper.assertTrue(tool.getMaterials().get(1).getVariant().equals(MaterialIds.wood), "expected wood handle material, got " + tool.getMaterials().get(1).getVariant());
    helper.assertTrue(tool.getMaterials().get(2).getVariant().equals(MaterialIds.wood), "expected wood binding material, got " + tool.getMaterials().get(2).getVariant());

    helper.succeed();
  }

  /**
   * Mirrors {@link #tool_crafting} for {@link TinkerTools#handAxe}: an iron small axe head, wood tool handle,
   * and wood tool binding are assembled at a tinker station into a hand axe. Parts and order (head, handle,
   * binding) come from {@code ToolDefinitions.HAND_AXE} in {@code ToolDefinitionDataProvider} - the same shape
   * as the pickaxe, but a different tool/part family, and an iron (tier 2) head instead of rock to confirm a
   * higher-tier base material threads through as cleanly as tier 1.
   */
  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void hand_axe_crafting(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
    helper.getLevel().setBlockAndUpdate(pos, TinkerTables.tinkerStation.get().defaultBlockState());
    if (!(helper.getLevel().getBlockEntity(pos) instanceof TinkerStationBlockEntity station)) {
      helper.fail("no tinker station BE", helper.relativePos(pos));
      return;
    }

    ItemStack head = partStack(TinkerToolParts.smallAxeHead.get(), MaterialIds.iron);
    ItemStack handle = partStack(TinkerToolParts.toolHandle.get(), MaterialIds.wood);
    ItemStack binding = partStack(TinkerToolParts.toolBinding.get(), MaterialIds.wood);

    station.setItem(TinkerStationBlockEntity.INPUT_SLOT, head);
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 1, handle);
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 2, binding);

    ItemStack result = station.calcResult(null);
    if (result.isEmpty()) {
      throw new IllegalStateException("Tinker station found no recipe for parts [" + head + ", " + handle + ", " + binding
                                      + "]; station error=" + station.getCurrentError());
    }
    helper.assertTrue(result.is(TinkerTools.handAxe.get()), "expected a hand axe, got " + result);

    ToolStack tool = ToolStack.from(result);
    int durability = tool.getStats().getInt(ToolStats.DURABILITY);
    helper.assertTrue(durability > 0, "expected positive durability, got " + durability);
    helper.assertTrue(tool.getMaterials().size() == 3, "expected 3 materials, got " + tool.getMaterials().size());
    helper.assertTrue(tool.getMaterials().get(0).getVariant().equals(MaterialIds.iron), "expected iron head material, got " + tool.getMaterials().get(0).getVariant());
    helper.assertTrue(tool.getMaterials().get(1).getVariant().equals(MaterialIds.wood), "expected wood handle material, got " + tool.getMaterials().get(1).getVariant());
    helper.assertTrue(tool.getMaterials().get(2).getVariant().equals(MaterialIds.wood), "expected wood binding material, got " + tool.getMaterials().get(2).getVariant());

    helper.succeed();
  }

  /**
   * {@link TinkerTools#sword} takes 3 parts, but two of them are the very same part item: {@code smallBlade}
   * plus {@code toolHandle} twice, per {@code ToolDefinitions.SWORD} in {@code ToolDefinitionDataProvider} -
   * unlike the pickaxe's 3 distinct part types. The second handle slot gets a different material (iron instead
   * of wood) to confirm the tinker station keeps per-slot materials independent even when two slots hold the
   * same part item.
   */
  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void sword_crafting(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
    helper.getLevel().setBlockAndUpdate(pos, TinkerTables.tinkerStation.get().defaultBlockState());
    if (!(helper.getLevel().getBlockEntity(pos) instanceof TinkerStationBlockEntity station)) {
      helper.fail("no tinker station BE", helper.relativePos(pos));
      return;
    }

    ItemStack blade = partStack(TinkerToolParts.smallBlade.get(), MaterialIds.rock);
    ItemStack handle1 = partStack(TinkerToolParts.toolHandle.get(), MaterialIds.wood);
    ItemStack handle2 = partStack(TinkerToolParts.toolHandle.get(), MaterialIds.iron);

    station.setItem(TinkerStationBlockEntity.INPUT_SLOT, blade);
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 1, handle1);
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 2, handle2);

    ItemStack result = station.calcResult(null);
    if (result.isEmpty()) {
      throw new IllegalStateException("Tinker station found no recipe for parts [" + blade + ", " + handle1 + ", " + handle2
                                      + "]; station error=" + station.getCurrentError());
    }
    helper.assertTrue(result.is(TinkerTools.sword.get()), "expected a sword, got " + result);

    ToolStack tool = ToolStack.from(result);
    int durability = tool.getStats().getInt(ToolStats.DURABILITY);
    helper.assertTrue(durability > 0, "expected positive durability, got " + durability);
    helper.assertTrue(tool.getMaterials().size() == 3, "expected 3 materials, got " + tool.getMaterials().size());
    helper.assertTrue(tool.getMaterials().get(0).getVariant().equals(MaterialIds.rock), "expected rock blade material, got " + tool.getMaterials().get(0).getVariant());
    helper.assertTrue(tool.getMaterials().get(1).getVariant().equals(MaterialIds.wood), "expected wood handle material, got " + tool.getMaterials().get(1).getVariant());
    helper.assertTrue(tool.getMaterials().get(2).getVariant().equals(MaterialIds.iron), "expected iron handle material, got " + tool.getMaterials().get(2).getVariant());

    helper.succeed();
  }

  /**
   * A 4-part large tool: hammer head, tough handle, and two large plates assembled into a sledge hammer, parts
   * per {@code ToolDefinitions.SLEDGE_HAMMER} (hammerHead, toughHandle, largePlate, largePlate) in
   * {@code ToolDefinitionDataProvider}. Unlike the 3-part tools above, {@code ToolBuildingRecipe.matches}
   * rejects a 4-part recipe at the base {@link TinkerTables#tinkerStation} (only 3 input slots) -
   * {@code ToolBuildingRecipe.requiresAnvil} returns true once a tool has 4 or more parts, so this test places
   * a {@link TinkerTables#tinkersAnvil} (6 slots) instead.
   */
  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void sledge_hammer_crafting(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
    helper.getLevel().setBlockAndUpdate(pos, TinkerTables.tinkersAnvil.get().defaultBlockState());
    if (!(helper.getLevel().getBlockEntity(pos) instanceof TinkerStationBlockEntity station)) {
      helper.fail("no tinker's anvil BE", helper.relativePos(pos));
      return;
    }

    ItemStack head = partStack(TinkerToolParts.hammerHead.get(), MaterialIds.rock);
    ItemStack handle = partStack(TinkerToolParts.toughHandle.get(), MaterialIds.wood);
    ItemStack plate1 = partStack(TinkerToolParts.largePlate.get(), MaterialIds.iron);
    ItemStack plate2 = partStack(TinkerToolParts.largePlate.get(), MaterialIds.iron);

    station.setItem(TinkerStationBlockEntity.INPUT_SLOT, head);
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 1, handle);
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 2, plate1);
    station.setItem(TinkerStationBlockEntity.INPUT_SLOT + 3, plate2);

    ItemStack result = station.calcResult(null);
    if (result.isEmpty()) {
      throw new IllegalStateException("Tinker's anvil found no recipe for parts [" + head + ", " + handle + ", " + plate1 + ", " + plate2
                                      + "]; station error=" + station.getCurrentError());
    }
    helper.assertTrue(result.is(TinkerTools.sledgeHammer.get()), "expected a sledge hammer, got " + result);

    ToolStack tool = ToolStack.from(result);
    int durability = tool.getStats().getInt(ToolStats.DURABILITY);
    helper.assertTrue(durability > 0, "expected positive durability, got " + durability);
    helper.assertTrue(tool.getMaterials().size() == 4, "expected 4 materials, got " + tool.getMaterials().size());
    helper.assertTrue(tool.getMaterials().get(0).getVariant().equals(MaterialIds.rock), "expected rock head material, got " + tool.getMaterials().get(0).getVariant());
    helper.assertTrue(tool.getMaterials().get(1).getVariant().equals(MaterialIds.wood), "expected wood handle material, got " + tool.getMaterials().get(1).getVariant());
    helper.assertTrue(tool.getMaterials().get(2).getVariant().equals(MaterialIds.iron), "expected iron plate material, got " + tool.getMaterials().get(2).getVariant());
    helper.assertTrue(tool.getMaterials().get(3).getVariant().equals(MaterialIds.iron), "expected iron plate material, got " + tool.getMaterials().get(3).getVariant());

    helper.succeed();
  }

  /**
   * A wood tool handle is built at a part builder from an oak plank and a pattern, through the block entity's
   * own result seam: {@link PartBuilderBlockEntity#calcResult} looks up the currently selected recipe and, if
   * it matches the inventory, assembles it (see {@code PartRecipe.matches}/{@code PartRecipe.assemble}).
   * Unlike the tinker station, the part builder needs an explicit recipe selection - {@code selectRecipe},
   * mirroring a player clicking a pattern button - because several recipes accept the same generic pattern
   * item and a plank (pick_head, hammer_head, tool_handle, ... are all "available" per
   * {@code ToolsRecipeProvider.partRecipes}); the tool_handle {@link Pattern} (its item's resource id) is what
   * disambiguates which one actually gets crafted. No world ticking is needed, so the result is asserted
   * directly, same as {@link #tool_crafting}. Asserts the crafted stack is a tool handle whose material is wood.
   */
  @GameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void part_builder_crafting(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
    helper.getLevel().setBlockAndUpdate(pos, TinkerTables.partBuilder.get().defaultBlockState());
    if (!(helper.getLevel().getBlockEntity(pos) instanceof PartBuilderBlockEntity builder)) {
      helper.fail("no part builder BE", helper.relativePos(pos));
      return;
    }

    builder.setItem(PartBuilderBlockEntity.PATTERN_SLOT, new ItemStack(TinkerTables.pattern.get()));
    builder.setItem(PartBuilderBlockEntity.MATERIAL_SLOT, new ItemStack(Items.OAK_PLANKS));
    builder.selectRecipe(new Pattern(BuiltInRegistries.ITEM.getKey(TinkerToolParts.toolHandle.get().asItem())));

    ItemStack result = builder.calcResult(null);
    if (result.isEmpty()) {
      throw new IllegalStateException("Part builder found no tool_handle result for an oak plank + pattern");
    }
    helper.assertTrue(result.is(TinkerToolParts.toolHandle.get()), "expected a tool handle, got " + result);
    helper.assertTrue(MaterialIds.wood.equals(IMaterialItem.getMaterialFromStack(result)), "expected wood material, got " + IMaterialItem.getMaterialFromStack(result));

    helper.succeed();
  }

  /**
   * Builds a tool part item stack of the given material.
   * @throws IllegalStateException if the part rejects the material, which would otherwise silently
   *                               produce an unmaterialed part and risk a vacuous pass or a confusing
   *                               failure further down in the recipe lookup
   */
  private static ItemStack partStack(IToolPart part, MaterialId material) {
    ItemStack stack = part.withMaterial(material);
    if (!material.equals(IMaterialItem.getMaterialFromStack(stack))) {
      throw new IllegalStateException("Part " + BuiltInRegistries.ITEM.getKey(part.asItem()) + " rejected material " + material);
    }
    return stack;
  }
}
