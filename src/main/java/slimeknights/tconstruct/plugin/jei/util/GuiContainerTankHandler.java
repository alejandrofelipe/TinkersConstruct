package slimeknights.tconstruct.plugin.jei.util;

import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.tconstruct.plugin.jei.JEIPlugin;
import slimeknights.tconstruct.smeltery.client.screen.IScreenWithFluidTank;
import slimeknights.tconstruct.smeltery.client.screen.IScreenWithFluidTank.FluidLocation;

import java.util.Optional;

/**
 * Class to pass {@link IScreenWithFluidTank} into JEI
 */
public class GuiContainerTankHandler<C extends AbstractContainerMenu, T extends AbstractContainerScreen<C> & IScreenWithFluidTank> implements IGuiContainerHandler<T> {
  @Override
  public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(T containerScreen, double mouseX, double mouseY) {
    FluidLocation fluid = containerScreen.getFluidUnderMouse((int)mouseX, (int)mouseY);
    // 1.21.4 JEI 20: the IClickableIngredientFactory parameter was removed, so the clickable ingredient is built from the
    // runtime ingredient manager instead (captured in JEIPlugin#onRuntimeAvailable; always set while a screen is open)
    IJeiRuntime runtime = JEIPlugin.runtime;
    if (fluid != null && runtime != null) {
      IIngredientManager manager = runtime.getIngredientManager();
      return manager.createTypedIngredient(NeoForgeTypes.FLUID_STACK, fluid.fluid())
                    .<IClickableIngredient<?>>map(typed -> new ClickableFluid(typed, fluid.location()));
    }
    return Optional.empty();
  }

  /** Minimal {@link IClickableIngredient}; JEI 20 no longer hands the handler a factory to build one */
  private record ClickableFluid(ITypedIngredient<FluidStack> typed, Rect2i area) implements IClickableIngredient<FluidStack> {
    @Override
    public ITypedIngredient<FluidStack> getTypedIngredient() {
      return typed;
    }

    @Override
    public Rect2i getArea() {
      return area;
    }
  }
}
