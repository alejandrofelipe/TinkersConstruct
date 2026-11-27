package slimeknights.tconstruct.library.client.modifiers.model;

import com.mojang.math.Transformation;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.client.model.util.MantleItemLayerModel;
import slimeknights.mantle.client.render.FluidRenderInfo;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.util.ItemLayerPixels;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.model.FluidContainerModel;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.capability.fluid.ToolTankHelper;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import org.jspecify.annotations.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Model for a fluid in a tool. */
public record FluidModifierModel(SpriteId small, @Nullable SpriteId large, ToolTankHelper tankHelper) implements SimpleModifierModel {
  public static final RecordLoadable<FluidModifierModel> LOADER = RecordLoadable.create(
    ModifierModel.MATERIAL_LOADABLE.requiredField("mask", FluidModifierModel::small),
    ModifierModel.MATERIAL_LOADABLE.nullableField("mask_large", FluidModifierModel::large),
    ToolTankHelper.LOADABLE.defaultField("tank_helper", ToolTankHelper.TANK_HELPER, false, FluidModifierModel::tankHelper),
    FluidModifierModel::new);

  /** Location used for baking dynamic models, name does not matter so just using a constant */
  private static final Identifier BAKE_LOCATION = TConstruct.getResource("dynamic_fluid_model");

  /** Instance with default tank helper */
  public FluidModifierModel(SpriteId small, @Nullable SpriteId large) {
    this(small, large, ToolTankHelper.TANK_HELPER);
  }

  /** Cache key for {@link #getCacheKey(IToolStackView, ModifierEntry)} */
  private record CacheKey(Fluid fluid, DataComponentPatch components) {}

  @Nullable
  @Override
  public Object getCacheKey(IToolStackView tool, ModifierEntry modifier) {
    FluidStack fluid = tankHelper().getFluid(tool);
    if (!fluid.isEmpty()) {
      return new CacheKey(fluid.getFluid(), fluid.getComponentsPatch());
    }
    return null;
  }

  @Override
  public RecordLoadable<FluidModifierModel> getLoader() {
    return LOADER;
  }

  @Override
  public void addQuads(IToolStackView tool, ModifierEntry modifier, Function<SpriteId, TextureAtlasSprite> spriteGetter, Transformation transforms, boolean isLarge, int startTintIndex, Consumer<Collection<BakedQuad>> quadConsumer, @Nullable ItemLayerPixels pixels) {
    // ensure template exists
    SpriteId template = isLarge ? large() : small();
    if (template != null) {
      // ensure we have fluid
      FluidStack fluid = tankHelper().getFluid(tool);
      if (!fluid.isEmpty()) {
        addQuads(fluid, template, spriteGetter, transforms, quadConsumer);
      }
    }
  }

  /** Adds quads for the given fluid */
  public static void addQuads(FluidStack fluid, SpriteId template, Function<SpriteId,TextureAtlasSprite> spriteGetter, Transformation transforms, Consumer<Collection<BakedQuad>> quadConsumer) {
    // must have texture for the proper state
    // fluid properties
    // 26.1: sprite and tint come from the fluid model
    FluidRenderInfo info = FluidRenderInfo.of(fluid);
    if (info == null) {
      return;
    }
    TextureAtlasSprite fluidSprite = info.still();
    TextureAtlasSprite maskSprite = spriteGetter.apply(template);

    // PORT 1.21.4: UnbakedGeometryHelper was removed; build the fluid clipped to the template shape via the Mantle
    // masked item-layer baker (mask = template, texture = fluid). Color and luminosity are baked in.
    List<BakedQuad> fluidQuads = MantleItemLayerModel.getMaskedQuadsForSprite(info.color(), -1, maskSprite, fluidSprite, transforms.compose(FluidContainerModel.FLUID_TRANSFORM), info.lightLevel(), null);
    quadConsumer.accept(fluidQuads);
  }
}
