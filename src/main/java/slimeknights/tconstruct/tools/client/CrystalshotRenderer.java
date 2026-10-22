package slimeknights.tconstruct.tools.client;

import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.tools.client.CrystalshotRenderer.CrystalshotRenderState;
import slimeknights.tconstruct.tools.item.CrystalshotItem.CrystalshotEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class CrystalshotRenderer extends ArrowRenderer<CrystalshotEntity, CrystalshotRenderState> {
  private static final Map<String,ResourceLocation> TEXTURES = new HashMap<>();
  private static final Function<String,ResourceLocation> TEXTURE_GETTER = variant -> TConstruct.getResource("textures/entity/arrow/" + variant + ".png");
  public CrystalshotRenderer(Context context) {
    super(context);
  }

  @Override
  public CrystalshotRenderState createRenderState() {
    return new CrystalshotRenderState();
  }

  @Override
  public void extractRenderState(CrystalshotEntity arrow, CrystalshotRenderState state, float partialTicks) {
    super.extractRenderState(arrow, state, partialTicks);
    state.variant = arrow.getVariant();
  }

  @Override
  protected ResourceLocation getTextureLocation(CrystalshotRenderState state) {
    return TEXTURES.computeIfAbsent(state.variant, TEXTURE_GETTER);
  }

  /** Render state carrying the crystalshot variant */
  public static class CrystalshotRenderState extends ArrowRenderState {
    public String variant = "";
  }
}
