package slimeknights.tconstruct.library.client.armor.texture;

import com.mojang.blaze3d.vertex.PoseStack;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import slimeknights.tconstruct.library.client.armor.texture.ArmorTextureSupplier.ArmorTexture;

/** Armor texture which tints the texture */
@AllArgsConstructor
@Accessors(fluent = true)
@RequiredArgsConstructor
@Setter
public class TintedArmorTexture implements ArmorTexture {
  private static final int MAX_LIGHT = LightTexture.pack(15, 15);

  @Getter
  private final Identifier texture;
  @Getter
  private int color = -1;
  @Getter
  private int luminosity = 0;

  public TintedArmorTexture(Identifier texture, int color) {
    this(texture, color, 0);
  }

  /** Applies luminosity to the given lightmap color. Assumes that {@code luminosity} is between 1 and 15. */
  public static int applyLuminosity(int packedLight, int luminosity) {
    // if full bright, skip some math
    if (luminosity >= 15) {
      return TintedArmorTexture.MAX_LIGHT;
    }
    // inlined version of methods from LightTexture
    return Math.max(luminosity, (packedLight & 0xFFFF) >> 4) << 4
      | Math.max(luminosity, packedLight >> 20 & 0xFFFF) << 20;
  }

  @Override
  public <S> int submit(Model<? super S> model, S state, PoseStack matrices, SubmitNodeCollector collector, int order, int packedLight, int outlineColor, boolean hasGlint) {
    if (luminosity > 0) {
      packedLight = applyLuminosity(packedLight, luminosity);
    }
    // same nodes as vanilla's EquipmentLayerRenderer submits for a dyed layer
    collector.order(order++).submitModel(model, state, matrices, RenderTypes.armorCutoutNoCull(texture), packedLight, OverlayTexture.NO_OVERLAY, color, null, outlineColor, null);
    if (hasGlint) {
      collector.order(order++).submitModel(model, state, matrices, RenderTypes.armorEntityGlint(), packedLight, OverlayTexture.NO_OVERLAY, color, null, outlineColor, null);
    }
    return order;
  }
}
