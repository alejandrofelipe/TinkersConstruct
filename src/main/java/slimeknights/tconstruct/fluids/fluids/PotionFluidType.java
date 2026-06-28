package slimeknights.tconstruct.fluids.fluids;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import slimeknights.mantle.fluid.texture.ClientTextureFluidType;
import slimeknights.mantle.recipe.helper.FluidOutput;
import slimeknights.tconstruct.fluids.TinkerFluids;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public class PotionFluidType extends FluidType {
  public PotionFluidType(Properties properties) {
    super(properties);
  }

  /** Gets the potion contents stored on the given fluid stack */
  public static PotionContents getContents(FluidStack stack) {
    return stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
  }

  /** Checks if the contents represent an empty (water/none) potion */
  private static boolean isEmptyContents(PotionContents contents) {
    return contents.potion().isEmpty() && contents.customEffects().isEmpty() && contents.customColor().isEmpty();
  }

  @Override
  public String getDescriptionId(FluidStack stack) {
    return getContents(stack).potion().orElse(Potions.WATER).value().getName("item.minecraft.potion.effect.");
  }

  @Override
  public ItemStack getBucket(FluidStack fluidStack) {
    ItemStack itemStack = new ItemStack(fluidStack.getFluid().getBucket());
    PotionContents contents = getContents(fluidStack);
    if (!isEmptyContents(contents)) {
      itemStack.set(DataComponents.POTION_CONTENTS, contents);
    }
    return itemStack;
  }

  @Override
  public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
    consumer.accept(new ClientTextureFluidType(this) {
      /**
       * Gets the color, based on the stored {@link PotionContents}
       * @param stack  Fluid stack instance
       * @return  Color for the fluid
       */
      @Override
      public int getTintColor(FluidStack stack) {
        PotionContents contents = getContents(stack);
        if (isEmptyContents(contents)) {
          return getTintColor();
        }
        return contents.getColor() | 0xFF000000;
      }
    });
  }

  /** Creates a fluid stack for the given potion */
  public static FluidStack potionFluid(@Nullable Holder<Potion> potion, int size) {
    FluidStack stack = new FluidStack(TinkerFluids.potion.get(), size);
    if (potion != null && !potion.is(Potions.WATER)) {
      stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
    }
    return stack;
  }

  /** Creates a fluid output for the given potion */
  public static FluidOutput potionResult(@Nullable Holder<Potion> potion, int size) {
    return FluidOutput.fromStack(potionFluid(potion, size));
  }

  /** Creates a potion bucket for the given potion */
  public static ItemStack potionBucket(@Nullable Holder<Potion> potion) {
    ItemStack stack = new ItemStack(TinkerFluids.potion);
    if (potion != null && !potion.is(Potions.WATER)) {
      stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
    }
    return stack;
  }
}
