package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import slimeknights.tconstruct.library.tools.part.IToolPart;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.tables.TinkerTables;
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
