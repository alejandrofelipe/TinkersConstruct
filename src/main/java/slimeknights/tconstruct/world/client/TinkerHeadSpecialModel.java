package slimeknights.tconstruct.world.client;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.special.SkullSpecialRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.util.StringRepresentable;
import slimeknights.tconstruct.world.TinkerHeadType;

import javax.annotation.Nullable;

/**
 * Special item model for Tinkers heads, registered as {@code tconstruct:head}.
 * PORT 1.21.4: head items render through a {@code minecraft:special} item model. Vanilla's {@code minecraft:head} resolves
 * its {@code kind} by name from {@link net.minecraft.world.level.block.SkullBlock.Type#TYPES}, which only holds the vanilla
 * types, so this resolves a {@link TinkerHeadType} instead and bakes the vanilla {@link SkullSpecialRenderer} with it. The
 * model comes from the skull models registered in {@code CreateSkullModels} and the texture from
 * {@link SkullBlockRenderer#SKIN_BY_TYPE}, same as the placed head.
 */
public record TinkerHeadSpecialModel(TinkerHeadType kind) implements SpecialModelRenderer.Unbaked {
  public static final MapCodec<TinkerHeadSpecialModel> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
    StringRepresentable.fromEnum(TinkerHeadType::values).fieldOf("kind").forGetter(TinkerHeadSpecialModel::kind)
  ).apply(inst, TinkerHeadSpecialModel::new));

  @Override
  public MapCodec<TinkerHeadSpecialModel> type() {
    return MAP_CODEC;
  }

  @Nullable
  @Override
  public SpecialModelRenderer<?> bake(EntityModelSet modelSet) {
    SkullModelBase model = SkullBlockRenderer.createModel(modelSet, kind);
    return model != null ? new SkullSpecialRenderer(kind, model, null, 0) : null;
  }
}
