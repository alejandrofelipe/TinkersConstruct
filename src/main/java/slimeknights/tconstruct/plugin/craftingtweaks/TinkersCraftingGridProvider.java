package slimeknights.tconstruct.plugin.craftingtweaks;

import net.blay09.mods.craftingtweaks.api.ButtonAlignment;
import net.blay09.mods.craftingtweaks.api.CraftingGridBuilder;
import net.blay09.mods.craftingtweaks.api.CraftingGridProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.tables.menu.CraftingStationContainerMenu;

public class TinkersCraftingGridProvider implements CraftingGridProvider {

  @Override
  public String getModId() {
    return TConstruct.MOD_ID;
  }

  @Override
  public boolean handles(AbstractContainerMenu menu) {
    return menu instanceof CraftingStationContainerMenu;
  }

  @Override
  public void buildCraftingGrids(CraftingGridBuilder builder, AbstractContainerMenu menu) {
    // 26.1: addGrid(start, size) became addGrid(start, width, height); the old size 9 meant a 3x3 grid
    builder.addGrid(0, 3, 3).setButtonAlignment(ButtonAlignment.LEFT);
  }

  @Override
  public boolean requiresServerSide() {
    return true;
  }

}
