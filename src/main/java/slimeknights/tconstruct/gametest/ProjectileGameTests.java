package slimeknights.tconstruct.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.modifiers.hook.build.ConditionalStatModifierHook;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.data.ModifierIds;
import slimeknights.tconstruct.tools.data.material.MaterialIds;

import java.util.List;

/**
 * Tinkers projectiles that write vanilla {@link AbstractArrow} state opened by the access transformer. 1.21.11 moved the arrow classes to
 * {@code entity.projectile.arrow}, so the AT entries ({@code baseDamage}, {@code setPierceLevel}, {@code piercingIgnoreEntityIds}, ...) were
 * retargeted; these tests shoot a real Tinkers bow and read the values back from the spawned entity, so the field and method the code writes
 * must be the ones the arrow uses.
 */
public class ProjectileGameTests {
  /**
   * A longbow with arrow pierce II and power fires a vanilla arrow: {@code ModifiableBowItem#releaseUsing} replaces the arrow's base damage
   * (the {@code baseDamage} field) with the bow's projectile damage, and {@code ArrowPierceModule} sets the pierce level through
   * {@code setPierceLevel}.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void longbow_arrow_damage_and_pierce(GameTestHelper helper) {
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    player.setPos(helper.absoluteVec(new Vec3(2.5, 1, 2.5)));
    ToolStack built = ToolStack.createTool(TinkerTools.longbow.get(), TinkerTools.longbow.get().getToolDefinition(), MaterialNBT.of(
      MaterialVariant.of(MaterialIds.wood, ""), MaterialVariant.of(MaterialIds.wood, ""), MaterialVariant.of(MaterialIds.wood, ""), MaterialVariant.of(MaterialIds.string, "")));
    built.addModifier(ModifierIds.arrowPierce, 2);
    built.addModifier(ModifierIds.power, 3);
    ItemStack bow = built.createStack();
    player.setItemInHand(InteractionHand.MAIN_HAND, bow);
    player.getInventory().setItem(9, new ItemStack(Items.ARROW, 16));

    // draw, then release fully charged
    helper.assertTrue(bow.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).consumesAction(), Component.literal("longbow did not start drawing"));
    helper.assertTrue(bow.getItem().releaseUsing(bow, helper.getLevel(), player, 0), Component.literal("longbow did not fire"));

    List<AbstractArrow> arrows = helper.getLevel().getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(3));
    try {
      helper.assertTrue(arrows.size() == 1, Component.literal("expected one arrow, found " + arrows));
      AbstractArrow arrow = arrows.getFirst();
      ToolStack tool = ToolStack.from(player.getMainHandItem());
      float expectedDamage = ConditionalStatModifierHook.getModifiedStat(tool, player, ToolStats.PROJECTILE_DAMAGE);
      helper.assertTrue(Math.abs(expectedDamage - 2.0) > 0.1, Component.literal("test setup: the bow's projectile damage " + expectedDamage + " matches the vanilla arrow's"));
      helper.assertTrue(Math.abs(arrow.baseDamage - expectedDamage) < 1e-4, Component.literal("arrow base damage " + arrow.baseDamage + ", expected the bow's projectile damage " + expectedDamage));
      helper.assertTrue(arrow.getPierceLevel() == 2, Component.literal("arrow pierce level " + arrow.getPierceLevel() + ", expected 2"));
    } finally {
      arrows.forEach(Entity::discard);
    }
    helper.succeed();
  }
}
