package slimeknights.tconstruct.mixin;

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
 * Gives a stack of a modifiable item its own custom data when it is built around the components of a stack that owns them: {@code copy()},
 * {@code split}, {@code transmuteCopy}, {@code ItemResource}. Restores what {@code Item#verifyComponentsAfterLoad} (removed in 1.21.9, called from
 * this constructor) gave tools by accident: a tag of their own per stack. See {@link ToolDataOwnership}.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
  @Inject(method = "<init>(Lnet/minecraft/world/level/ItemLike;ILnet/minecraft/core/component/PatchedDataComponentMap;)V", at = @At("RETURN"))
  private void tconstruct$copyOwnedToolData(ItemLike item, int count, PatchedDataComponentMap components, CallbackInfo ci) {
    if (item.asItem() instanceof IModifiable) {
      ToolDataOwnership.onStackBuilt((ItemStack) (Object) this, components);
    }
  }
}
