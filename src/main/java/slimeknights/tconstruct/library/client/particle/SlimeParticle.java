package slimeknights.tconstruct.library.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.BreakingItemParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import slimeknights.tconstruct.shared.TinkerCommons;
import slimeknights.tconstruct.shared.block.SlimeType;

import javax.annotation.Nullable;

// not part of the tic particle system since it uses vanilla particles
public class SlimeParticle extends BreakingItemParticle {

  public SlimeParticle(ClientLevel worldIn, double posXIn, double posYIn, double posZIn, ItemStackRenderState renderState) {
    super(worldIn, posXIn, posYIn, posZIn, renderState);
  }

  public SlimeParticle(ClientLevel worldIn, double posXIn, double posYIn, double posZIn, double xSpeedIn, double ySpeedIn, double zSpeedIn, ItemStackRenderState renderState) {
    super(worldIn, posXIn, posYIn, posZIn, xSpeedIn, ySpeedIn, zSpeedIn, renderState);
  }

  public static class Factory extends BreakingItemParticle.ItemParticleProvider<SimpleParticleType> {
    private final ItemLike slime;

    public Factory(ItemLike slime) {
      this.slime = slime;
    }

    public Factory(SlimeType type) {
      this.slime = TinkerCommons.slimeball.get(type);
    }

    @Nullable
    @Override
    public Particle createParticle(SimpleParticleType typeIn, ClientLevel worldIn, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
      return new SlimeParticle(worldIn, x, y, z, xSpeed, ySpeed, zSpeed, this.calculateState(new ItemStack(slime), worldIn));
    }
  }
}
