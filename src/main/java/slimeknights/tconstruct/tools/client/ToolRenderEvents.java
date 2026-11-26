package slimeknights.tconstruct.tools.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.util.ARGB;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.tools.definition.module.ToolHooks;
import slimeknights.tconstruct.library.tools.definition.module.aoe.AreaOfEffectIterator;
import slimeknights.tconstruct.library.tools.definition.module.aoe.AreaOfEffectIterator.AOEMatchType;
import slimeknights.tconstruct.library.tools.definition.module.mining.IsEffectiveToolHook;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.utils.BlockSideHitListener;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@EventBusSubscriber(modid = TConstruct.MOD_ID, value = Dist.CLIENT)
public class ToolRenderEvents {
  /** Maximum number of blocks from the iterator to render */
  private static final int MAX_BLOCKS = 60;
  /** Outline color vanilla uses with the high contrast block outline option (light blue) */
  private static final int HIGH_CONTRAST_OUTLINE = -11010079;

  /**
   * Adds the outline on the extra blocks.
   * 1.21.9: {@code RenderHighlightEvent} is gone; the extra blocks and their shapes are found while the vanilla outline
   * state is extracted, and drawn by a custom outline renderer next to the vanilla outline.
   *
   * @param event the outline extraction event
   */
  @SubscribeEvent
  static void extractBlockHighlights(ExtractBlockOutlineRenderStateEvent event) {
    Level world = event.getLevel();
    Player player = Minecraft.getInstance().player;
    if (player == null) {
      return;
    }
    // must have the right tags
    ItemStack stack = player.getMainHandItem();
    if (stack.isEmpty() || !stack.is(TinkerTags.Items.MODIFIABLE)) {
      return;
    }
    // must not be broken, must be right interface
    ToolStack tool = ToolStack.from(stack);
    if (tool.isBroken()) {
      return;
    }
    BlockHitResult blockTrace = event.getHitResult();
    BlockPos origin = event.getBlockPos();
    BlockState state = event.getBlockState();
    AOEMatchType matchType = AOEMatchType.BREAKING;
    // if we have any modifier that has an AOE interaction, make our match type more liberal
    if (tool.getModifiers().has(TinkerTags.Modifiers.AOE_INTERACTION)) {
      matchType = AOEMatchType.DISPLAY;
    } else if (!IsEffectiveToolHook.isEffective(tool, state)) {
      return;
    }
    UseOnContext context = new UseOnContext(world, player, InteractionHand.MAIN_HAND, stack, blockTrace);
    Iterator<BlockPos> extraBlocks = tool.getHook(ToolHooks.AOE_ITERATOR).getBlocks(tool, context, state, matchType).iterator();
    if (!extraBlocks.hasNext()) {
      return;
    }

    // collect the shapes now, the renderer runs later in the frame; iterators may reuse a mutable position
    CollisionContext collisionContext = event.getCollisionContext();
    List<ExtraOutline> outlines = new ArrayList<>();
    int rendered = 0;
    do {
      BlockPos pos = extraBlocks.next();
      if (world.getWorldBorder().isWithinBounds(pos)) {
        rendered++;
        outlines.add(new ExtraOutline(pos.immutable(), world.getBlockState(pos).getShape(world, pos, collisionContext)));
      }
    } while(rendered < MAX_BLOCKS && extraBlocks.hasNext());
    if (outlines.isEmpty()) {
      return;
    }

    // draw in the same pass as the vanilla outline, without replacing it
    event.addCustomRenderer((outlineState, buffers, matrices, translucent, levelState) -> {
      if (outlineState.isTranslucent() == translucent) {
        Vec3 camera = levelState.cameraRenderState.pos;
        // same as the vanilla outline: with the high contrast option, a black secondary outline under a light blue one, else semi-transparent black
        boolean highContrast = outlineState.highContrast();
        if (highContrast) {
          renderOutlines(outlines, matrices, buffers.getBuffer(RenderType.secondaryBlockOutline()), camera, 0xFF000000);
        }
        renderOutlines(outlines, matrices, buffers.getBuffer(RenderType.lines()), camera, highContrast ? HIGH_CONTRAST_OUTLINE : ARGB.color(102, 0xFF000000));
      }
      return false;
    });
  }

  /** Draws the extra block outlines camera-relative in the given color */
  private static void renderOutlines(List<ExtraOutline> outlines, PoseStack matrices, VertexConsumer vertexBuilder, Vec3 camera, int color) {
    for (ExtraOutline outline : outlines) {
      BlockPos pos = outline.pos;
      ShapeRenderer.renderShape(matrices, vertexBuilder, outline.shape, pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z, color);
    }
  }

  /** Extra block outline extracted for {@link #extractBlockHighlights} */
  private record ExtraOutline(BlockPos pos, VoxelShape shape) {}
  /** Renders the block damage process on the extra blocks */
  @SubscribeEvent
  static void renderBlockDamageProgress(RenderLevelStageEvent.AfterTripwireBlocks event) {
    // TODO: AfterTripwireBlocks: confirm this is the right stage for crumbling, maybe earlier
    // validate required variables are set
    MultiPlayerGameMode controller = Minecraft.getInstance().gameMode;
    if (controller == null || !controller.isDestroying()) {
      return;
    }
    Level world = Minecraft.getInstance().level;
    Player player = Minecraft.getInstance().player;
    if (world == null || player == null || Minecraft.getInstance().getCameraEntity() == null) {
      return;
    }
    // must have the right tags
    ItemStack stack = player.getMainHandItem();
    if (stack.isEmpty() || !stack.is(TinkerTags.Items.HARVEST)) {
      return;
    }
    // must be targeting a block
    HitResult result = Minecraft.getInstance().hitResult;
    if (result == null || result.getType() != Type.BLOCK) {
      return;
    }
    // must not be broken, must be right interface
    ToolStack tool = ToolStack.from(stack);
    if (tool.isBroken()) {
      return;
    }
    // find breaking progress
    BlockHitResult blockTrace = (BlockHitResult)result;
    BlockPos target = blockTrace.getBlockPos();
    BlockDestructionProgress progress = null;
    for (Int2ObjectMap.Entry<BlockDestructionProgress> entry : Minecraft.getInstance().levelRenderer.destroyingBlocks.int2ObjectEntrySet()) {
      if (entry.getValue().getPos().equals(target)) {
        progress = entry.getValue();
        break;
      }
    }
    if (progress == null) {
      return;
    }
    // determine extra blocks to highlight
    BlockState state = world.getBlockState(target);
    // must not be broken, and the tool definition must be effective
    if (!IsEffectiveToolHook.isEffective(tool, state)) {
      return;
    }
    UseOnContext context = new UseOnContext(world, player, InteractionHand.MAIN_HAND, stack, blockTrace.withDirection(BlockSideHitListener.getClientSideHit()));
    Iterator<BlockPos> extraBlocks = tool.getHook(ToolHooks.AOE_ITERATOR).getBlocks(tool, context, state, AreaOfEffectIterator.AOEMatchType.BREAKING).iterator();
    if (!extraBlocks.hasNext()) {
      return;
    }

    // set up buffers
    PoseStack matrices = event.getPoseStack();
    matrices.pushPose();
    MultiBufferSource.BufferSource vertices = event.getLevelRenderer().renderBuffers.crumblingBufferSource();
    VertexConsumer vertexBuilder = vertices.getBuffer(ModelBakery.DESTROY_TYPES.get(progress.getProgress()));

    // finally, render the blocks
    Camera renderInfo = Minecraft.getInstance().gameRenderer.getMainCamera();
    double x = renderInfo.getPosition().x;
    double y = renderInfo.getPosition().y;
    double z = renderInfo.getPosition().z;
    BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
    int rendered = 0;
    do {
      BlockPos pos = extraBlocks.next();
      matrices.pushPose();
      matrices.translate(pos.getX() - x, pos.getY() - y, pos.getZ() - z);
      PoseStack.Pose entry = matrices.last();
      VertexConsumer blockBuilder = new SheetedDecalTextureGenerator(vertexBuilder, entry, 1f);
      // TODO: is it practical to fetch model data here?
      dispatcher.renderBreakingTexture(world.getBlockState(pos), pos, world, matrices, blockBuilder);
      matrices.popPose();
      rendered++;
    } while (rendered < MAX_BLOCKS && extraBlocks.hasNext());
    // finish rendering
    matrices.popPose();
    vertices.endBatch();
  }
}
