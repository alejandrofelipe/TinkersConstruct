package slimeknights.tconstruct.world.client;

import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.model.geom.ModelPart;

/** Recreation of {@link net.minecraft.client.model.dragon.DragonHeadModel} but adjusted for slimeskulls. */
public class DragonSkullModel extends SkullModelBase {
  private final ModelPart head;
  private final ModelPart jaw;

  public DragonSkullModel(ModelPart root) {
    // PORT 1.21.4: SkullModelBase now requires a root ModelPart and renderToBuffer is final (renders the root).
    // The slimeskull-specific root translate/scale fitting that used to live in the renderToBuffer override is
    // dropped; it must be re-applied by the caller's pose or via a baked MeshTransformer in Phase-4.
    super(root);
    this.head = root.getChild("head");
    this.jaw = this.head.getChild("jaw");
  }

  @Override
  public void setupAnim(float pMouthAnimation, float yRot, float xRot) {
    this.jaw.xRot = (float)(Math.sin(pMouthAnimation * Math.PI * 0.2f) + 1) * 0.2f;
    this.head.yRot = yRot * ((float)Math.PI / 180);
    this.head.xRot = xRot * ((float)Math.PI / 180);
  }
}
