package slimeknights.tconstruct.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.ToolDataOwnership;

/**
 * Releases the ownership of a tool's custom data whenever a stack is built around an existing component map: {@code copy()}, {@code split},
 * {@code transmuteCopy}, {@code ItemResource}, decoding. Replaces what {@code Item#verifyComponentsAfterLoad} (removed in 1.21.9, called from this
 * constructor) gave tools by accident: a tag of their own per stack. See {@link ToolDataOwnership}.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
  @Inject(method = "<init>(Lnet/minecraft/world/level/ItemLike;ILnet/minecraft/core/component/PatchedDataComponentMap;)V", at = @At("RETURN"))
  private void tconstruct$releaseToolData(ItemLike item, int count, PatchedDataComponentMap components, CallbackInfo ci) {
    if (item.asItem() instanceof IModifiable) {
      ToolDataOwnership.release(components.get(DataComponents.CUSTOM_DATA));
    }
  }
}
