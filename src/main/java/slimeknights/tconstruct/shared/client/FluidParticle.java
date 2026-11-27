package slimeknights.tconstruct.shared.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.client.render.FluidRenderInfo;
import slimeknights.mantle.client.render.FluidRenderer;
import slimeknights.tconstruct.shared.particle.FluidParticleData;

/** Particle type that renders a fluid still texture */
public class FluidParticle extends SingleQuadParticle {
  private final FluidStack fluid;
  private final float uCoord;
  private final float vCoord;
  /** 26.1: sprite and tint come from the fluid model */
  protected FluidParticle(ClientLevel world, double x, double y, double z, double motionX, double motionY, double motionZ, FluidStack fluid, FluidRenderInfo info) {
    super(world, x, y, z, motionX, motionY, motionZ, info.still());
    this.fluid = fluid;
    this.gravity = 1.0F;
    int color = info.color();
    this.alpha = ((color >> 24) & 0xFF) / 255f;
    this.rCol   = ((color >> 16) & 0xFF) / 255f;
    this.gCol = ((color >>  8) & 0xFF) / 255f;
    this.bCol  = ( color        & 0xFF) / 255f;
    this.quadSize /= 2.0F;
    this.uCoord = this.random.nextFloat() * 3.0F;
    this.vCoord = this.random.nextFloat() * 3.0F;
  }

  @Override
  protected Layer getLayer() {
    // 26.1: the 1.21.11 TERRAIN layer (translucent, blocks atlas) was renamed
    return Layer.TRANSLUCENT_TERRAIN;
  }

  @Override
  protected float getU0() {
    return this.sprite.getU((this.uCoord + 1.0F) / 4.0F);
  }

  @Override
  protected float getU1() {
    return this.sprite.getU(this.uCoord / 4.0F);
  }

  @Override
  protected float getV0() {
    return this.sprite.getV(this.vCoord / 4.0F);
  }

  @Override
  protected float getV1() {
    return this.sprite.getV((this.vCoord + 1.0F) / 4.0F);
  }

  @Override
  public int getLightCoords(float partialTick) {
    return FluidRenderer.withBlockLight(super.getLightCoords(partialTick), fluid.getFluid().getFluidType().getLightLevel(fluid));
  }

  /** Factory to create a fluid particle */
  public static class Factory implements ParticleProvider<FluidParticleData> {
    @Override
    public Particle createParticle(FluidParticleData data, ClientLevel worldIn, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, RandomSource random) {
      FluidStack fluid = data.getFluid();
      // 26.1: no fluid model (or an empty fluid) means no sprite: no particle (1.21.11 drew the missing sprite)
      FluidRenderInfo info = fluid.isEmpty() ? null : FluidRenderInfo.of(fluid);
      return info != null ? new FluidParticle(worldIn, x, y, z, xSpeed, ySpeed, zSpeed, fluid, info) : null;
    }
  }
}
