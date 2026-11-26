package slimeknights.tconstruct.library.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import slimeknights.mantle.client.render.MantleShaders;
import slimeknights.tconstruct.TConstruct;

import java.util.function.Supplier;

/**
 * Render types defined by Tinkers.
 * 1.21.5: no longer extends {@link RenderType}; shader, transparency, cull, depth and write mask state moved into
 * {@link RenderPipeline}s, see {@link Pipelines}.
 * 1.21.11: {@code RenderStateShard} and {@code CompositeState} are gone; each type is a {@link RenderSetup} on the same pipeline.
 */
public class TinkerRenderTypes {
  private TinkerRenderTypes() {}

  /**
   * Sampler matching the 1.21.10 {@code RenderStateShard.BLOCK_SHEET_MIPPED} filtering, same as Mantle's fluid render type:
   * {@code NEAREST} filtering with mipmaps on, clamped like the 1.21.11 atlas sampler.
   */
  private static final Supplier<GpuSampler> BLOCK_SHEET_MIPPED_SAMPLER = () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST, true);

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
   * Render type for fluids, like {@link slimeknights.mantle.client.render.MantleRenderTypes#FLUID}, but disables cull so both sides show.
   * Like Mantle, the config-driven shader choice is resolved once when this class initializes.
   */
  public static final RenderType SMELTERY_FLUID = RenderType.create(
    TConstruct.resourceString("smeltery_fluid"),
    RenderSetup.builder(MantleShaders.useFluidFogFix() ? Pipelines.SMELTERY_FLUID : Pipelines.SMELTERY_FLUID_NO_FOG)
               .useLightmap()
               .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS, BLOCK_SHEET_MIPPED_SAMPLER)
               .sortOnUpload()
               .bufferSize(256)
               .createRenderSetup());


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
