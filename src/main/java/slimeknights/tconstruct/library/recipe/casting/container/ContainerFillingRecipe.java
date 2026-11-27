package slimeknights.tconstruct.library.recipe.casting.container;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.IMultiRecipe;
import slimeknights.mantle.recipe.helper.LoadableRecipeSerializer;
import slimeknights.mantle.recipe.helper.TypeAwareRecipeSerializer;
import slimeknights.tconstruct.library.recipe.casting.DisplayCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.ICastingContainer;
import slimeknights.tconstruct.library.recipe.casting.ICastingRecipe;
import slimeknights.tconstruct.library.transfer.TransferUtil;

import java.util.Collections;
import java.util.List;

/**
 * Casting recipe that takes an arbitrary fluid for a given amount and fills a container
 */
@RequiredArgsConstructor
public class ContainerFillingRecipe implements ICastingRecipe, IMultiRecipe<DisplayCastingRecipe> {
  public static final RecordLoadable<ContainerFillingRecipe> LOADER = RecordLoadable.create(
    LoadableRecipeSerializer.TYPED_SERIALIZER.requiredField(), ContextKey.ID.nullableField(), LoadableRecipeSerializer.RECIPE_GROUP,
    IntLoadable.FROM_ONE.requiredField("fluid_amount", (ContainerFillingRecipe r) -> r.fluidAmount),
    Loadables.ITEM.requiredField("container", (ContainerFillingRecipe r) -> r.container),
    ContainerFillingRecipe::new);

  @Getter
  private final TypeAwareRecipeSerializer<?> serializer;
  @Getter
  private final Identifier id;
  @Getter
  private final String group;
  private final int fluidAmount;
  private final Item container;

  @Override
  @SuppressWarnings("unchecked")
  public RecipeType<? extends Recipe<ICastingContainer>> getType() {
    return (RecipeType<? extends Recipe<ICastingContainer>>) serializer.getType();
  }

  @Override
  @SuppressWarnings("unchecked")
  public RecipeSerializer<? extends Recipe<ICastingContainer>> getSerializer() {
    return (RecipeSerializer<? extends Recipe<ICastingContainer>>) serializer.serializer();
  }

  @Override
  public int getFluidAmount(ICastingContainer inv) {
    Fluid fluid = inv.getFluid();
    // PORT 1.21.10: transaction safe, as a casting table sizes its tank from this during a native fill
    return TransferUtil.simulateFill(inv.getStack(), new FluidStack(fluid, this.fluidAmount));
  }

  @Override
  public boolean isConsumed() {
    return true;
  }

  @Override
  public boolean switchSlots() {
    return false;
  }

  @Override
  public int getCoolingTime(ICastingContainer inv) {
    return 5;
  }

  @Override
  public boolean matches(ICastingContainer inv, Level worldIn) {
    ItemStack stack = inv.getStack();
    Fluid fluid = inv.getFluid();
    if (stack.getItem() != this.container.asItem()) {
      return false;
    }
    // PORT 1.21.10: transaction safe, as a casting table matches recipes during a native fill
    return TransferUtil.simulateFill(stack, new FluidStack(fluid, this.fluidAmount)) > 0;
  }

  /** @deprecated use {@link ICastingRecipe#assemble(Container, HolderLookup.Provider)} */
  @Override
  @Deprecated
  public ItemStack getResultItem(HolderLookup.Provider access) {
    return new ItemStack(this.container);
  }

  @Override
  public ItemStack assemble(ICastingContainer inv) {
    ItemStack stack = inv.getStack();
    if (!TransferUtil.hasFluidHandler(stack)) {
      return stack.copy();
    }
    FluidStack fluidStack = new FluidStack(inv.getFluid(), this.fluidAmount);
    DataComponentPatch components = inv.getFluidComponents();
    if (!components.isEmpty()) {
      fluidStack.applyComponents(components);
    }
    // fills a single container, like the legacy capability on the one item in the casting slot
    return TransferUtil.fill(stack, fluidStack);
  }

  /* Display */
  /** Cache of items to display for this container */
  private List<DisplayCastingRecipe> displayRecipes = null;

  @Override
  public List<DisplayCastingRecipe> getRecipes(RegistryAccess access) {
    if (displayRecipes == null) {
      List<ItemStack> casts = Collections.singletonList(new ItemStack(container));
      displayRecipes = BuiltInRegistries.FLUID.stream()
                                             .filter(fluid -> fluid.getBucket() != Items.AIR && fluid.isSource(fluid.defaultFluidState()))
                                             .map(fluid -> {
                                               FluidStack fluidStack = new FluidStack(fluid, fluidAmount);
                                               ItemStack stack = TransferUtil.fill(new ItemStack(container), fluidStack);
                                               return new DisplayCastingRecipe(getId(), getType(), casts, Collections.singletonList(fluidStack), stack, 5, true);
                                             })
                                             .toList();
    }
    return displayRecipes;
  }
}
