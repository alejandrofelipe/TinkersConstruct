package slimeknights.tconstruct.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
 * 26.1: the private constructor every other one delegates to takes the item holder instead of an {@code ItemLike}.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
  @Inject(method = "<init>(Lnet/minecraft/core/Holder;ILnet/minecraft/core/component/PatchedDataComponentMap;)V", at = @At("RETURN"))
  private void tconstruct$copyOwnedToolData(Holder<Item> item, int count, PatchedDataComponentMap components, CallbackInfo ci) {
    if (item.value() instanceof IModifiable) {
      ToolDataOwnership.onStackBuilt((ItemStack) (Object) this, components);
    }
  }
}
