package slimeknights.tconstruct.smeltery.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.recipe.FluidValues;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;

import java.util.List;
import java.util.function.Consumer;

/**
 * Fluid container holding 1 ingot of fluid
 */
public class CopperCanItem extends Item {
  public CopperCanItem(Properties properties) {
    super(properties);
  }

  /** Creates the fluid handler for the given stack; registered centrally on {@code RegisterCapabilitiesEvent}. */
  public CopperCanFluidHandler getFluidHandler(ItemStack stack) {
    return new CopperCanFluidHandler(stack);
  }

  @Override
  public boolean hasCraftingRemainingItem(ItemStack stack) {
    return getFluid(stack) != Fluids.EMPTY;
  }

  @Override
  public ItemStack getCraftingRemainingItem(ItemStack stack) {
    if (hasCraftingRemainingItem(stack)) {
      return new ItemStack(this);
    }
    return ItemStack.EMPTY;
  }

  @Override
  public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
    FluidStack contained = getFluidStack(stack);
    if (!contained.isEmpty()) {
      MutableComponent text = contained.getHoverName().plainCopy();
      tooltip.add(Component.translatable(this.getDescriptionId() + ".contents", text).withStyle(ChatFormatting.GRAY));
      if (flag.isAdvanced()) {
        tooltip.add(Component.translatable(TankItem.FLUID_ID, Loadables.FLUID.getKey(contained.getFluid())).withStyle(ChatFormatting.DARK_GRAY));
      }
    } else {
      tooltip.add(Component.translatable(this.getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
  }

  /** Removes the fluid from the given stack */
  public static void removeFluid(ItemStack stack) {
    // PORT M3: TinkerSmeltery.COPPER_CAN_FLUID is a DataComponentType<SimpleFluidContent> registered centrally
    stack.remove(TinkerSmeltery.COPPER_CAN_FLUID.get());
  }

  /** Sets the fluid on the given stack */
  public static ItemStack setFluid(ItemStack stack, FluidStack fluid) {
    if (fluid.isEmpty()) {
      removeFluid(stack);
    } else {
      // PORT M3: TinkerSmeltery.COPPER_CAN_FLUID is a DataComponentType<SimpleFluidContent> registered centrally
      // store a single ingot's worth, the handler scales by stack size
      stack.set(TinkerSmeltery.COPPER_CAN_FLUID.get(), SimpleFluidContent.copyOf(fluid.copyWithAmount(FluidValues.INGOT)));
    }
    return stack;
  }

  /** Gets the contained fluid stack (1 ingot) from the given stack */
  public static FluidStack getFluidStack(ItemStack stack) {
    SimpleFluidContent content = stack.get(TinkerSmeltery.COPPER_CAN_FLUID.get());
    return content == null ? FluidStack.EMPTY : content.copy();
  }

  /** Gets the fluid from the given stack */
  public static Fluid getFluid(ItemStack stack) {
    return getFluidStack(stack).getFluid();
  }

  /** Adds filled variants of the copper can to the given consumer */
  @SuppressWarnings("deprecation")
  public static void addFilledVariants(Consumer<ItemStack> output) {
    BuiltInRegistries.FLUID.holders().filter(holder -> {
      Fluid fluid = holder.value();
      return fluid.isSource(fluid.defaultFluidState()) && !holder.is(TinkerTags.Fluids.HIDE_IN_CREATIVE_TANKS);
    }).forEachOrdered(holder -> {
      output.accept(CopperCanItem.setFluid(new ItemStack(TinkerSmeltery.copperCan), new FluidStack(holder, FluidValues.INGOT)));
    });
  }

  /**
   * Gets a string variant name for the given stack
   * @param stack  Stack instance to check
   * @return  String variant name
   */
  public static String getSubtype(ItemStack stack) {
    Fluid fluid = getFluid(stack);
    if (fluid != Fluids.EMPTY) {
      return BuiltInRegistries.FLUID.getKey(fluid).toString();
    }
    return "";
  }
}
