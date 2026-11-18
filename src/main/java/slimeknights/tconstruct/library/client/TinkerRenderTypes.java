package slimeknights.tconstruct.library.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import slimeknights.mantle.client.render.MantleShaders;
import slimeknights.tconstruct.TConstruct;

import java.util.OptionalDouble;

/**
 * Render types defined by Tinkers.
 * 1.21.5: no longer extends {@link RenderType}; shader, transparency, cull, depth and write mask state moved into
 * {@link RenderPipeline}s, see {@link Pipelines}.
 */
public class TinkerRenderTypes {
  private TinkerRenderTypes() {}

  /** Render type for the error block that is seen through everything, mostly based on {@link RenderType#LINES} */
  public static final RenderType ERROR_BLOCK = RenderType.create(
    TConstruct.resourceString("lines"), 256, false, false, Pipelines.ERROR_BLOCK,
    RenderType.CompositeState.builder()
                             .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
                             .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                             .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                             .createCompositeState(false));

  /**
   * Render type for fluids, like {@link slimeknights.mantle.client.render.MantleRenderTypes#FLUID}, but disables cull so both sides show.
   * Like Mantle, the config-driven shader choice is resolved once when this class initializes.
   */
  public static final RenderType SMELTERY_FLUID = RenderType.create(
    TConstruct.resourceString("smeltery_fluid"), 256, false, true,
    MantleShaders.useFluidFogFix() ? Pipelines.SMELTERY_FLUID : Pipelines.SMELTERY_FLUID_NO_FOG,
    RenderType.CompositeState.builder()
                  .setLightmapState(RenderStateShard.LIGHTMAP)
                  .setTextureState(RenderStateShard.BLOCK_SHEET_MIPPED)
                  .createCompositeState(false));


  /**
   * Render pipelines for the above render types. Separate class so pipelines can be registered without loading the
   * render types, which read the config.
   */
  @EventBusSubscriber(modid = TConstruct.MOD_ID, value = Dist.CLIENT)
  public static class Pipelines {
    private Pipelines() {}

    /** Pipeline for the error block, vanilla lines without depth testing so it shows through everything */
    public static final RenderPipeline ERROR_BLOCK = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
      .withLocation(TConstruct.getResource("pipeline/error_block"))
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .build();
    /** Pipeline for smeltery fluids with the fluid fog fix, like {@link MantleShaders#FLUID} but with cull disabled so both sides show */
    public static final RenderPipeline SMELTERY_FLUID = RenderPipeline.builder(MantleShaders.FLUID_SNIPPET)
      .withLocation(TConstruct.getResource("pipeline/smeltery_fluid"))
      .withCull(false)
      .build();
    /** Pipeline for smeltery fluids without the fluid fog fix, like {@link MantleShaders#FLUID_NO_FOG} but with cull disabled */
    public static final RenderPipeline SMELTERY_FLUID_NO_FOG = RenderPipeline.builder(MantleShaders.FLUID_NO_FOG_SNIPPET)
      .withLocation(TConstruct.getResource("pipeline/smeltery_fluid_no_fog"))
      .withCull(false)
      .build();

    @SubscribeEvent
    static void registerPipelines(RegisterRenderPipelinesEvent event) {
      event.registerPipeline(ERROR_BLOCK);
      // register both fluid variants, the config is read when the render type is created
      event.registerPipeline(SMELTERY_FLUID);
      event.registerPipeline(SMELTERY_FLUID_NO_FOG);
    }
  }
}
