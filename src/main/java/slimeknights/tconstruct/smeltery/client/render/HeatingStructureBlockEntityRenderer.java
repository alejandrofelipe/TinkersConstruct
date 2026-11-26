package slimeknights.tconstruct.smeltery.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Quaternionf;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.library.TinkerItemDisplays;
import slimeknights.tconstruct.library.client.TinkerRenderTypes;
import slimeknights.tconstruct.smeltery.block.controller.ControllerBlock;
import slimeknights.tconstruct.smeltery.block.entity.controller.HeatingStructureBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.module.MeltingModuleInventory;
import slimeknights.tconstruct.smeltery.block.entity.multiblock.HeatingStructureMultiblock.StructureData;
import slimeknights.tconstruct.smeltery.client.render.HeatingStructureBlockEntityRenderer.HeatingStructureRenderState;

import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

public class HeatingStructureBlockEntityRenderer implements BlockEntityRenderer<HeatingStructureBlockEntity, HeatingStructureRenderState> {
  private static final float ITEM_SCALE = 15f/16f;

  private final ItemModelResolver itemModelResolver;
  public HeatingStructureBlockEntityRenderer(Context context) {
    this.itemModelResolver = context.itemModelResolver();
  }

  @Override
  public HeatingStructureRenderState createRenderState() {
    return new HeatingStructureRenderState();
  }

  @Override
  public void extractRenderState(HeatingStructureBlockEntity smeltery, HeatingStructureRenderState renderState, float partialTicks, Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
    BlockEntityRenderer.super.extractRenderState(smeltery, renderState, partialTicks, cameraPos, breakProgress);
    renderState.errorPos = null;
    renderState.structureValid = false;
    renderState.fluids = List.of();
    renderState.items = List.of();
    Level world = smeltery.getLevel();
    if (world == null) return;
    StructureData structure = smeltery.getStructure();
    boolean structureValid = renderState.blockState.getValue(ControllerBlock.IN_STRUCTURE) && structure != null;
    renderState.structureValid = structureValid;

    // render erroring block, done whether in the structure or not
    BlockPos errorPos = smeltery.getErrorPos();
    Minecraft mc = Minecraft.getInstance();
    if (errorPos != null && mc.player != null) {
      // either we must be holding the book, or the structure must be erroring and it be within 10 seconds of last update
      boolean highlightError = smeltery.isHighlightError();
      if ((!structureValid && highlightError) || smeltery.showDebugBlockBorder(mc.player)) {
        // distance check, 512 is the squared length of the diagonal of a max size structure
        BlockPos pos = smeltery.getBlockPos();
        BlockPos playerPos = mc.player.blockPosition();
        int dx = playerPos.getX() - pos.getX();
        int dz = playerPos.getZ() - pos.getZ();
        if ((dx * dx + dz * dz) < 512) {
          renderState.errorPos = errorPos;
          renderState.highlightError = highlightError;
        }
      }
    }

    // if no structure, nothing else to do
    if (!structureValid) {
      return;
    }

    // relevant positions
    BlockPos minPos = structure.getMinInside();
    BlockPos maxPos = structure.getMaxInside();
    renderState.minPos = minPos;
    // tank fluids, use minPos for brightness
    renderState.fluids = SmelteryTankRenderer.extractLayers(smeltery.getTank(), minPos, maxPos);
    renderState.fluidLight = LevelRenderer.getLightColor(world, minPos);

    // items
    int xd = 1 + maxPos.getX() - minPos.getX();
    int zd = 1 + maxPos.getZ() - minPos.getZ();
    int layer = xd * zd;
    renderState.facing = renderState.blockState.getValue(ControllerBlock.FACING);
    MeltingModuleInventory inventory = smeltery.getMeltingInventory();
    int max = Config.CLIENT.maxSmelteryItemQuads.get();
    if (max != 0) {
      List<SmelteryItem> items = new ArrayList<>();
      int quadsRendered = 0;
      for (int i = 0; i < inventory.getSlots(); i++) {
        ItemStack stack = inventory.getStackInSlot(i);
        if (!stack.isEmpty()) {
          // calculate position inside the smeltery from slot index
          int height = i / layer;
          int layerIndex = i % layer;
          int offsetX = layerIndex % xd;
          int offsetZ = layerIndex / xd;
          BlockPos itemPos = minPos.offset(offsetX, height, offsetZ);

          ItemStackRenderState itemRenderState = new ItemStackRenderState();
          itemModelResolver.updateForTopItem(itemRenderState, stack, TinkerItemDisplays.MELTER, world, null, 0);
          items.add(new SmelteryItem(itemRenderState, offsetX, height, offsetZ, LevelRenderer.getLightColor(world, itemPos)));

          // done as quads rather than items as its not that expensive to draw blocks, items are the problem
          if (max != -1) {
            // PORT 1.21.4: BakedModel#isCustomRenderer and #getQuads were removed with the item-model redesign and
            // ItemStackRenderState exposes no quad count, so the exact per-model quad estimate is gone. Charge a flat
            // conservative cost per rendered stack to keep the performance cap meaningful; a Phase-4 pass can refine
            // this by inspecting the populated ItemStackRenderState layers.
            quadsRendered += 100;
            if (quadsRendered > max) {
              break;
            }
          }
        }
      }
      renderState.items = items;
    }
  }

  @Override
  public void submit(HeatingStructureRenderState state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera) {
    BlockPos pos = state.blockPos;
    // render erroring block
    BlockPos errorPos = state.errorPos;
    if (errorPos != null) {
      // color will be yellow if the structure is valid (expanding), red if invalid
      int color = ARGB.colorFromFloat(0.5f, 1f, state.structureValid ? 1f : 0f, 0f);
      double x = errorPos.getX() - pos.getX();
      double y = errorPos.getY() - pos.getY();
      double z = errorPos.getZ() - pos.getZ();
      // 1.21.11: line width moved from the render type to the vertices; both types used the window scaled width
      float lineWidth = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
      collector.submitCustomGeometry(matrices, state.highlightError ? TinkerRenderTypes.ERROR_BLOCK : RenderTypes.lines(), (pose, buffer) -> {
        PoseStack poseStack = new PoseStack();
        poseStack.last().set(pose);
        ShapeRenderer.renderShape(poseStack, buffer, Shapes.block(), x, y, z, color, lineWidth);
      });
    }

    // if no structure, nothing else to do
    if (!state.structureValid) {
      return;
    }

    // offset to make rendering min pos relative
    BlockPos minPos = state.minPos;
    matrices.pushPose();
    matrices.translate(minPos.getX() - pos.getX(), minPos.getY() - pos.getY(), minPos.getZ() - pos.getZ());
    // render tank fluids
    SmelteryTankRenderer.submitFluids(matrices, collector, state.fluids, state.fluidLight);

    // render items
    if (!state.items.isEmpty()) {
      Quaternionf itemRotation = Axis.YP.rotationDegrees(-90.0F * (float)state.facing.get2DDataValue());
      for (SmelteryItem item : state.items) {
        // offset to the slot position in the structure, scale, and rotate the item
        matrices.pushPose();
        matrices.translate(item.x + 0.5f, item.y + 0.5f, item.z + 0.5f);
        matrices.mulPose(itemRotation);
        matrices.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        item.state.submit(matrices, collector, item.light, OverlayTexture.NO_OVERLAY, 0);
        matrices.popPose();
      }
    }

    matrices.popPose();
  }

  /**
   * 1.21.8: shouldRenderOffScreen lost its block entity parameter, so the old per-tile condition
   * (IN_STRUCTURE && structure != null) can no longer be restored: the flag is read once, when the block entity is added
   * to the level, before the structure state is known, and would go stale after formation anyway. The fluid is drawn
   * across the whole multiblock, far beyond the controller's chunk section, so always register as globally rendered.
   * Global renderers get no distance limit, only the frustum test against {@link #getRenderBoundingBox}.
   */
  @Override
  public boolean shouldRenderOffScreen() {
    return true;
  }

  /** The frustum test uses the renderer's box (default is one block), so use the multiblock bounds to keep the fluid visible when the controller is off screen */
  @Override
  public AABB getRenderBoundingBox(HeatingStructureBlockEntity be) {
    return be.getRenderBoundingBox();
  }

  /** Item resolved for display inside the structure, positioned relative to the min inside position */
  private record SmelteryItem(ItemStackRenderState state, int x, int y, int z, int light) {}

  /** Render state for smelteries and foundries */
  public static class HeatingStructureRenderState extends BlockEntityRenderState {
    /** Error block to highlight, null for none */
    @Nullable
    public BlockPos errorPos;
    public boolean highlightError = false;
    public boolean structureValid = false;
    public BlockPos minPos = BlockPos.ZERO;
    public Direction facing = Direction.NORTH;
    /** Tank fluid layers, from the bottom up */
    public List<SmelteryTankRenderer.FluidLayer> fluids = List.of();
    public int fluidLight = 0;
    private List<SmelteryItem> items = List.of();
  }
}
