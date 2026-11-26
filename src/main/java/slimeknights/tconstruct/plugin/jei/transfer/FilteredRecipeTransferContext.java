package slimeknights.tconstruct.plugin.jei.transfer;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
import mezz.jei.api.recipe.transfer.RecipeTransferResult;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.ApiStatus.Internal;

/**
 * Recipe transfer context showing a filtered set of recipe slots, everything else (including the transfer result) going to the original context.
 * The API equivalent of JEI's internal {@code RecipeTransferContext.copyWithRecipeSlots}, used by its player inventory handler.
 */
@Internal
record FilteredRecipeTransferContext<R, C extends AbstractContainerMenu>(IRecipeTransferContext<R, C> parent, IRecipeSlotsView recipeSlots) implements IRecipeTransferContext<R, C> {
  @Override
  public int getTransferId() {
    return parent.getTransferId();
  }

  @Override
  public void completeRecipeTransfer(RecipeTransferResult result) {
    parent.completeRecipeTransfer(result);
  }

  @Override
  public R getRecipe() {
    return parent.getRecipe();
  }

  @Override
  public IRecipeType<R> getRecipeType() {
    return parent.getRecipeType();
  }

  @Override
  public C getContainer() {
    return parent.getContainer();
  }

  @Override
  public AbstractContainerScreen<C> getScreen() {
    return parent.getScreen();
  }

  @Override
  public IRecipeSlotsView getRecipeSlots() {
    return recipeSlots;
  }

  @Override
  public Player getPlayer() {
    return parent.getPlayer();
  }

  @Override
  public boolean isMaxTransfer() {
    return parent.isMaxTransfer();
  }
}
