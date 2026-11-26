package slimeknights.tconstruct.tools.modules.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.fluid.FluidTransferHelper;
import slimeknights.mantle.transfer.TransferHelper;
import slimeknights.tconstruct.library.json.TinkerLoadables;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.interaction.BlockInteractionModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.InteractionSource;
import slimeknights.tconstruct.library.modifiers.modules.ModifierModule;
import slimeknights.tconstruct.library.module.HookProvider;
import slimeknights.tconstruct.library.module.ModuleHook;
import slimeknights.tconstruct.library.tools.definition.module.ToolHooks;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.transfer.TransferUtil;

import org.jspecify.annotations.Nullable;
import java.util.List;

import static slimeknights.tconstruct.library.tools.capability.fluid.ToolTankHelper.TANK_HELPER;

/** Module allowing a tool to interact with a tank beyond the normal block interaction behavior. */
public record TankInteractionModule(@Nullable InteractionSource source) implements ModifierModule, BlockInteractionModifierHook {
  private static final List<ModuleHook<?>> DEFAULT_HOOKS = HookProvider.<TankInteractionModule>defaultHooks(ModifierHooks.BLOCK_INTERACT);
  public static final RecordLoadable<TankInteractionModule> LOADER = RecordLoadable.create(TinkerLoadables.INTERACTION_SOURCE.nullableField("interaction_source", TankInteractionModule::source), TankInteractionModule::new);

  @Override
  public RecordLoadable<? extends ModifierModule> getLoader() {
    return LOADER;
  }

  @Override
  public List<ModuleHook<?>> getDefaultHooks() {
    return DEFAULT_HOOKS;
  }

  @Override
  public InteractionResult beforeBlockUse(IToolStackView tool, ModifierEntry modifier, UseOnContext context, InteractionSource source) {
    // if source is not null, its a filter and only that source may use this. Used mainly for armor
    if ((this.source != null && this.source != source) || !tool.getHook(ToolHooks.INTERACTION).canInteract(tool, modifier.getId(), source)) {
      return InteractionResult.PASS;
    }

    Level world = context.getLevel();
    BlockPos target = context.getClickedPos();
    // must have a block that exposes a fluid handler capability
    Direction face = context.getClickedFace();
    // player interaction, never inside a transaction: each fill/drain below runs in its own committed transaction
    ResourceHandler<FluidResource> cap = TransferHelper.getFluidResourceHandler(world, target, face);
    if (cap == null) {
      return InteractionResult.PASS;
    }

    // only the server needs to deal with actually handling stuff
    if (!world.isClientSide()) {
      Player player = context.getPlayer();
      boolean sneaking = player != null && player.isShiftKeyDown();
      FluidStack fluidStack = TANK_HELPER.getFluid(tool);
      // sneaking fills, not sneak drains
      SoundEvent sound = null;
      if (sneaking) {
        // must have something to fill
        if (!fluidStack.isEmpty()) {
          int added = TransferUtil.fill(cap, fluidStack, false);
          if (added > 0) {
            sound = FluidTransferHelper.getEmptySound(fluidStack);
            fluidStack.shrink(added);
            TANK_HELPER.setFluid(tool, fluidStack);
          }
        }
        // if nothing currently, will drain whatever
      } else if (fluidStack.isEmpty()) {
        // like the legacy drain(amount): the first fluid the block can give
        FluidStack drainable = FluidTransferHelper.getDrainableFluid(cap);
        FluidStack drained = drainable.isEmpty() ? FluidStack.EMPTY : TransferUtil.drain(cap, drainable.copyWithAmount(TANK_HELPER.getCapacity(tool)), false);
        if (!drained.isEmpty()) {
          TANK_HELPER.setFluid(tool, drained);
          sound = FluidTransferHelper.getFillSound(fluidStack);
        }
      } else {
        // filter drained to be the same as the current fluid
        FluidStack drained = TransferUtil.drain(cap, fluidStack.copyWithAmount(TANK_HELPER.getCapacity(tool) - fluidStack.getAmount()), false);
        if (!drained.isEmpty() && FluidStack.isSameFluidSameComponents(drained, fluidStack)) {
          fluidStack.grow(drained.getAmount());
          TANK_HELPER.setFluid(tool, fluidStack);
          sound = FluidTransferHelper.getFillSound(fluidStack);
        }
      }
      if (sound != null) {
        world.playSound(null, target, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
      }
    }
    return InteractionResult.SUCCESS;
  }
}
