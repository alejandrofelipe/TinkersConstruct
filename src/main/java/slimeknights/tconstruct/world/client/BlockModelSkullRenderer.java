package slimeknights.tconstruct.world.client;

import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Skull model instance for the sake of making a Slimeskull with a block item
 * Requires {@link net.minecraft.world.inventory.InventoryMenu#BLOCK_ATLAS} as the texture for the skull.
 *
 * <p>PORT 1.21.4: this used to render a baked item model inside the skull slot via the removed
 * {@code ItemRenderer#getModel}/{@code renderModelLists}/{@code BakedModel} path, and overrode the now-final
 * {@link SkullModelBase#renderToBuffer}. The whole item-model render pipeline moved to {@code ItemStackRenderState}
 * (see {@code ItemModelUtil}/{@code ItemModelResolver}). Since the slimeskull render path that consumed this model
 * ({@code SlimeskullArmorModel}) is itself vestigial in 1.21.4 (NeoForge no longer invokes the custom armor-model
 * hook), this is reduced to an empty-root placeholder that compiles and registers; the item-as-skull render must be
 * rebuilt on {@code ItemStackRenderState} in Phase-4 (uitest).
 **/
public class BlockModelSkullRenderer extends SkullModelBase {
  private final ItemStack stack;

  public BlockModelSkullRenderer(ItemRenderer itemRenderer, ItemStack stack) {
    // empty root: this model no longer renders through the vanilla skull/model path (see class breadcrumb)
    super(new ModelPart(List.of(), Map.of()));
    this.stack = stack;
  }

  @Override
  public void setupAnim(float pMouthAnimation, float yRot, float xRot) {
    // PORT 1.21.4: no-op; item-as-skull pose/render deferred to Phase-4 ItemStackRenderState reimplementation
  }

  /** @return the block item stack this placeholder would render as a skull */
  public ItemStack getStack() {
    return stack;
  }
}
