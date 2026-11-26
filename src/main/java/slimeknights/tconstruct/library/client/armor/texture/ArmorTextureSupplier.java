package slimeknights.tconstruct.library.client.armor.texture;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import java.util.Objects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import slimeknights.mantle.data.listener.ResourceValidator;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.registry.GenericLoaderRegistry;
import slimeknights.mantle.data.registry.GenericLoaderRegistry.IHaveLoader;

/** Logic to get an armor texture given a stack */
public interface ArmorTextureSupplier extends IHaveLoader {
  /** Folder for tinkers armor, intentionally not the normal armor folder to make the texture scan low risk */
  String FOLDER = "textures/tinker_armor";
  /** Validator checking if armor textures exist */
  ResourceValidator TEXTURE_VALIDATOR = new ResourceValidator(FOLDER, FOLDER, ".png");

  /** Empty module instance. Used as fallback for {@link ConditionalArmorTextureSupplier} modules. Not registered. */
  ArmorTextureSupplier EMPTY = new ArmorTextureSupplier() {
    @Override
    public ArmorTexture getArmorTexture(ItemStack stack, TextureType leggings, RegistryAccess access) {
      return ArmorTexture.EMPTY;
    }

    @Override
    public RecordLoadable<? extends ArmorTextureSupplier> getLoader() {
      throw new UnsupportedOperationException("Cannot datagen an empty texture supplier.");
    }
  };
  /** Registry for resource packs */
  GenericLoaderRegistry<ArmorTextureSupplier> LOADER = new GenericLoaderRegistry<>("Armor texture type", EMPTY, true);

  /** Gets the texture and color to display for the given stack. Use {@link ArmorTexture#EMPTY} to indicates this texture will not render */
  ArmorTexture getArmorTexture(ItemStack stack, TextureType leggings, RegistryAccess access);

  /** Pair of texture and color */
  interface ArmorTexture {
    /** Empty instance since caches don't support caching null. */
    ArmorTexture EMPTY = new ArmorTexture() {
      @Override
      public <S> int submit(Model<? super S> model, S state, PoseStack matrices, SubmitNodeCollector collector, int order, int packedLight, int outlineColor, boolean hasGlint) {
        return order;
      }
    };

    /**
     * Submits this texture on the given model.
     * 1.21.9: replaces rendering into a buffer source, entity layers only submit render nodes (two-phase rendering).
     * @param model         Model to draw, posed from the state when the node renders
     * @param state         Render state to pose the model
     * @param matrices      Pose to draw at
     * @param collector     Collector receiving the nodes
     * @param order         First order to submit at, as a layer drawn later must have a higher order to draw on top of the earlier ones
     * @param packedLight   Packed light at the entity
     * @param outlineColor  Outline color of the entity, for the glowing effect
     * @param hasGlint      If true, also submits the enchantment glint over this texture
     * @return  Next free order
     */
    <S> int submit(Model<? super S> model, S state, PoseStack matrices, SubmitNodeCollector collector, int order, int packedLight, int outlineColor, boolean hasGlint);
  }

  /** Texture variants, armor is used for helmet, chestplate, and boots, while leggings is leggings and wings is on chest for elytra */
  enum TextureType {
    ARMOR, LEGGINGS, WINGS;

    /** Gets the type for the given slot */
    public static TextureType fromSlot(EquipmentSlot slot) {
      return slot == EquipmentSlot.LEGS ? LEGGINGS : ARMOR;
    }
  }

  /**
   * Gets a texture using the named format
   */
  static Identifier getTexturePath(Identifier name) {
    return Objects.requireNonNull(Identifier.tryBuild(name.getNamespace(), FOLDER + '/' + name.getPath() + ".png"));
  }
}
