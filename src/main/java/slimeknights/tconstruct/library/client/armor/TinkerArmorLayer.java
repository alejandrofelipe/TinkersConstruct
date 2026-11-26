package slimeknights.tconstruct.library.client.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.equipment.ElytraModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import slimeknights.tconstruct.library.client.armor.ArmorModelManager.ArmorModelDispatcher;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Draws worn armor whose client extensions are an {@link ArmorModelDispatcher}, from the texture layers of its armor model.
 * Mirrors vanilla's {@link HumanoidArmorLayer}, and is added next to it on every renderer that has one, using the same armor models.
 * <p>
 * 1.21.4: replaces returning a custom model from the armor model hook, which can no longer composite dynamic layers. Tinkers armor
 * sets an equipment asset without layers (see {@code ModifiableArmorItem#EQUIPMENT_ASSET}), so vanilla's layer draws nothing for it.
 */
public class TinkerArmorLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>, A extends HumanoidModel<S>> extends RenderLayer<S, M> {
  private final ArmorModelSet<A> modelSet;
  private final ArmorModelSet<A> babyModelSet;
  private final ElytraModel elytraModel;
  private final ElytraModel elytraBabyModel;

  public TinkerArmorLayer(RenderLayerParent<S, M> renderer, ArmorModelSet<A> modelSet, ArmorModelSet<A> babyModelSet, EntityModelSet models) {
    super(renderer);
    this.modelSet = modelSet;
    this.babyModelSet = babyModelSet;
    this.elytraModel = new ElytraModel(models.bakeLayer(ModelLayers.ELYTRA));
    this.elytraBabyModel = new ElytraModel(models.bakeLayer(ModelLayers.ELYTRA_BABY));
  }

  /**
   * Adds this layer right after the vanilla armor layer of the renderer, reusing its armor models. Does nothing if the renderer has no armor layer.
   * @param renderer  Renderer to add the layer to
   * @param models    Model set to bake the wing models
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static void addTo(@Nullable EntityRenderer<?,?> renderer, EntityModelSet models) {
    if (renderer instanceof LivingEntityRenderer<?,?,?> living) {
      List<RenderLayer> layers = (List<RenderLayer>)(List<?>)living.layers;
      for (int i = 0; i < layers.size(); i++) {
        if (layers.get(i) instanceof HumanoidArmorLayer armorLayer) {
          layers.add(i + 1, new TinkerArmorLayer(living, armorLayer.modelSet, armorLayer.babyModelSet, models));
          return;
        }
      }
    }
  }

  @Override
  public void submit(PoseStack matrices, SubmitNodeCollector collector, int packedLight, S state, float yRot, float xRot) {
    ClientLevel level = Minecraft.getInstance().level;
    if (level == null) {
      return;
    }
    Context<S> context = new Context<>(state, getParentModel(), state.isBaby ? elytraBabyModel : elytraModel, matrices, collector, packedLight, level.registryAccess());
    // same order as vanilla
    submitPiece(state.chestEquipment, EquipmentSlot.CHEST, context);
    submitPiece(state.legsEquipment, EquipmentSlot.LEGS, context);
    submitPiece(state.feetEquipment, EquipmentSlot.FEET, context);
    submitPiece(state.headEquipment, EquipmentSlot.HEAD, context);
  }

  /** Submits a single armor piece if it renders through an armor model */
  private void submitPiece(ItemStack stack, EquipmentSlot slot, Context<S> context) {
    if (!stack.isEmpty() && HumanoidArmorLayer.shouldRender(stack, slot) && IClientItemExtensions.of(stack) instanceof ArmorModelDispatcher dispatcher) {
      dispatcher.submitArmor(stack, slot, (context.state().isBaby ? babyModelSet : modelSet).get(slot), context);
    }
  }

  /**
   * Entity being rendered, passed to the armor renderers.
   * @param state           Render state of the entity
   * @param parent          Model of the entity, already posed for this state
   * @param wings           Wings model for armor with the elytra modifier
   * @param matrices        Pose of the entity
   * @param collector       Collector receiving the render nodes
   * @param packedLight     Packed light at the entity
   * @param registryAccess  Client registries, for trim lookups
   */
  public record Context<S extends HumanoidRenderState>(S state, HumanoidModel<S> parent, Model<? super S> wings, PoseStack matrices, SubmitNodeCollector collector, int packedLight, RegistryAccess registryAccess) {}
}
