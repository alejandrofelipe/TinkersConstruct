package slimeknights.tconstruct.library.data.tinkering;

import net.minecraft.data.PackOutput;
import slimeknights.tconstruct.library.data.AbstractTagProvider;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialManager;

/** Tag provider for materials */
public abstract class AbstractMaterialTagProvider extends AbstractTagProvider<IMaterial> {
  protected AbstractMaterialTagProvider(PackOutput packOutput, String modId) {
    super(packOutput, modId, MaterialManager.TAG_FOLDER, material -> material.getIdentifier().getLocation(), id -> true);
  }
}
