package slimeknights.tconstruct.plugin.jei.entity;

import lombok.Getter;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import slimeknights.mantle.plugin.jei.MantleJEIConstants;
import slimeknights.mantle.plugin.jei.entity.EntityIngredientRenderer;
import slimeknights.mantle.recipe.ingredient.EntityIngredient;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.recipe.modifiers.severing.SeveringRecipe;
import slimeknights.tconstruct.plugin.jei.TConstructJEIConstants;
import slimeknights.tconstruct.tools.TinkerTools;

public class SeveringCategory implements IRecipeCategory<SeveringRecipe> {
  public static final Identifier BACKGROUND_LOC = TConstruct.getResource("textures/gui/jei/tinker_station.png");
  private static final Component TITLE = TConstruct.makeTranslation("jei", "severing.title");

  /** Renderer instance to use in this category */
  private final EntityIngredientRenderer entityRenderer = new EntityIngredientRenderer(32);

  private final IDrawable background;

  @Override
  public int getWidth() {
    return background.getWidth();
  }

  @Override
  public int getHeight() {
    return background.getHeight();
  }

  @Getter
  private final IDrawable icon;
  public SeveringCategory(IGuiHelper helper) {
    this.background = helper.createDrawable(BACKGROUND_LOC, 0, 78, 100, 38);
    this.icon = helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, TinkerTools.cleaver.get().getRenderTool());
  }

  @Override
  public IRecipeType<SeveringRecipe> getRecipeType() {
    return TConstructJEIConstants.SEVERING;
  }

  @Override
  public Component getTitle() {
    return TITLE;
  }

  @Override
  public void draw(SeveringRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
    // the background, where JEI drew getBackground() (deprecated for removal): same pose, right before draw
    background.draw(graphics);
  }

  @Override
  public void setRecipe(IRecipeLayoutBuilder builder, SeveringRecipe recipe, IFocusGroup focuses) {
    EntityIngredient input = recipe.getIngredient();
    IIngredientAcceptor<?> entities = builder.addSlot(RecipeIngredientRole.INPUT, 3, 3)
           .setCustomRenderer(MantleJEIConstants.ENTITY_TYPE, entityRenderer)
           .addIngredients(MantleJEIConstants.ENTITY_TYPE, input.getDisplay());
    // addIngredients keeps the null entries of entities without egg, so both slots stay the same size
    IIngredientAcceptor<?> eggs = builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addIngredients(VanillaTypes.ITEM_STACK, input.getEggs());
    builder.createFocusLink(entities, eggs);

    // output
    builder.addSlot(RecipeIngredientRole.OUTPUT, 76, 11).add(recipe.getOutput());
  }

  @Override
  public Identifier getIdentifier(SeveringRecipe recipe) {
    return recipe.getId();
  }
}
