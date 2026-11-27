package slimeknights.tconstruct.world.client;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SkullSpecialRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.util.StringRepresentable;
import slimeknights.tconstruct.world.TinkerHeadType;

import org.jspecify.annotations.Nullable;

/**
 * Special item model for Tinkers heads, registered as {@code tconstruct:head}.
 * PORT 1.21.4: head items render through a {@code minecraft:special} item model. Vanilla's {@code minecraft:head} resolves
 * its {@code kind} by name from {@link net.minecraft.world.level.block.SkullBlock.Type#TYPES}, which only holds the vanilla
 * types, so this resolves a {@link TinkerHeadType} instead and bakes the vanilla {@link SkullSpecialRenderer} with it. The
 * model comes from the skull models registered in {@code CreateSkullModels} and the texture from
 * {@link SkullBlockRenderer#SKIN_BY_TYPE}, same as the placed head.
 * 26.1: special model unbakeds are typed by their argument, heads have none like vanilla's {@link SkullSpecialRenderer.Unbaked}.
 */
public record TinkerHeadSpecialModel(TinkerHeadType kind) implements NoDataSpecialModelRenderer.Unbaked {
  public static final MapCodec<TinkerHeadSpecialModel> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
    StringRepresentable.fromEnum(TinkerHeadType::values).fieldOf("kind").forGetter(TinkerHeadSpecialModel::kind)
  ).apply(inst, TinkerHeadSpecialModel::new));

  @Override
  public MapCodec<TinkerHeadSpecialModel> type() {
    return MAP_CODEC;
  }

  @Nullable
  @Override
  public SkullSpecialRenderer bake(SpecialModelRenderer.BakingContext context) {
    SkullModelBase model = SkullBlockRenderer.createModel(context.entityModelSet(), kind);
    // 1.21.8: SkullSpecialRenderer takes the render type directly; the texture comes from SKIN_BY_TYPE like the placed head
    return model != null ? new SkullSpecialRenderer(model, 0, SkullBlockRenderer.getSkullRenderType(kind, null)) : null;
  }
}
