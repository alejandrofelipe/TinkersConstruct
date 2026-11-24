package slimeknights.tconstruct.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tools.TinkerTools;

import java.util.List;

/**
 * Tool stacks are edited in place ({@link ToolStack#from(ItemStack)} does not copy the custom data tag), so every copy of a
 * tool stack must own its tag. Copies get a fresh tag from {@code Item#verifyComponentsAfterLoad}, which runs in the
 * {@code ItemStack} constructor used by {@code copy()}; armor, arrows and shurikens did not override it, so a copy shared the
 * tag with the original. The equipment snapshot vanilla compares against ({@code LivingEntity#lastEquipmentItems}) then
 * changed together with the worn armor, so breaking armor in place never fired an equipment change and broken armor kept
 * its protection.
 */
public class ToolCopyGameTests {
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void tool_copies_own_their_data(GameTestHelper helper) {
    List<IModifiable> items = List.of(
      TinkerTools.pickaxe.get(), TinkerTools.plateArmor.get(ArmorType.CHESTPLATE), TinkerTools.travelersGear.get(ArmorType.BOOTS),
      TinkerTools.slimesuit.get(ArmorType.HELMET), TinkerTools.arrow.get(), TinkerTools.shuriken.get());
    for (IModifiable item : items) {
      ItemStack original = ToolBuildHandler.buildItemRandomMaterials(item, helper.getLevel().getRandom());
      ItemStack copy = original.copy();
      // break the original in place, as damaging a worn tool does
      ToolStack.from(original).setDamage(Integer.MAX_VALUE);
      String name = item.asItem().toString();
      helper.assertTrue(ToolDamageUtil.isBroken(original), Component.literal(name + " did not break"));
      helper.assertFalse(ToolDamageUtil.isBroken(copy), Component.literal("breaking " + name + " in place also broke its copy"));
      helper.assertFalse(ItemStack.matches(original, copy), Component.literal("broken " + name + " still matches its unbroken copy"));
    }
    helper.succeed();
  }
}
