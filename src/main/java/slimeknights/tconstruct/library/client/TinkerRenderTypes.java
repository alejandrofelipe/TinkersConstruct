package slimeknights.tconstruct.library.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import slimeknights.tconstruct.TConstruct;

import java.util.Optional;

/**
 * Render types defined by Tinkers.
 * 1.21.5: no longer extends {@link RenderType}; shader, transparency, cull, depth and write mask state moved into
 * {@link RenderPipeline}s, see {@link Pipelines}.
 * 1.21.11: {@code RenderStateShard} and {@code CompositeState} are gone; each type is a {@link RenderSetup} on the same pipeline.
 */
public class TinkerRenderTypes {
  private TinkerRenderTypes() {}

  /**
   * Render type for the error block that is seen through everything, mostly based on {@link net.minecraft.client.renderer.rendertype.RenderTypes#LINES}.
   * 1.21.11: the line width (window scaled) is set per vertex by the caller.
   */
  public static final RenderType ERROR_BLOCK = RenderType.create(
    TConstruct.resourceString("lines"),
    RenderSetup.builder(Pipelines.ERROR_BLOCK)
               .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
               .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
               .bufferSize(256)
               .createRenderSetup());

  /**
   * Render pipelines for the above render types. Separate class so pipelines can be registered without loading the
   * render types. The smeltery fluid type moved to Mantle as {@code MantleRenderTypes.FLUID_NO_CULL}.
   */
  @EventBusSubscriber(modid = TConstruct.MOD_ID, value = Dist.CLIENT)
  public static class Pipelines {
    private Pipelines() {}

    /**
     * Pipeline for the error block, vanilla lines without depth testing so it shows through everything.
     * 26.1: {@code DepthTestFunction.NO_DEPTH_TEST} is gone; no depth stencil state = no depth test (as vanilla's GUI text snippet).
     */
    public static final RenderPipeline ERROR_BLOCK = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
      .withLocation(TConstruct.getResource("pipeline/error_block"))
      .withDepthStencilState(Optional.empty())
      .build();

    @SubscribeEvent
    static void registerPipelines(RegisterRenderPipelinesEvent event) {
      event.registerPipeline(ERROR_BLOCK);
    }
  }
}
