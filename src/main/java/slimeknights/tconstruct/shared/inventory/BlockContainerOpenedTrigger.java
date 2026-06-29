package slimeknights.tconstruct.shared.inventory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import javax.annotation.Nullable;
import java.util.Optional;

/** Criteria that triggers when a container is opened */
public class BlockContainerOpenedTrigger extends SimpleCriterionTrigger<BlockContainerOpenedTrigger.Instance> {
  @Override
  public Codec<Instance> codec() {
    return Instance.CODEC;
  }

  /** Triggers this criteria */
  public void trigger(@Nullable BlockEntity tileEntity, @Nullable Inventory inv) {
    if (tileEntity != null && inv != null && inv.player instanceof ServerPlayer serverPlayer) {
      this.trigger(serverPlayer, instance -> instance.test(tileEntity.getType()));
    }
  }

  /** Trigger instance matching a specific block entity type. */
  public record Instance(Optional<ContextAwarePredicate> player, BlockEntityType<?> type) implements SimpleCriterionTrigger.SimpleInstance {
    public static final Codec<Instance> CODEC = RecordCodecBuilder.create(inst -> inst.group(
      EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
      ResourceLocation.CODEC.fieldOf("type").<BlockEntityType<?>>xmap(
        id -> BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(id).orElseThrow(() -> new IllegalArgumentException("Unknown tile entity '" + id + "'")),
        type -> BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type)
      ).forGetter(Instance::type)
    ).apply(inst, Instance::new));

    /** Builds a criterion matching the given block entity type, for use in datagen. */
    public static Criterion<Instance> container(BlockEntityType<?> type) {
      return slimeknights.tconstruct.shared.TinkerCommons.CONTAINER_OPENED_TRIGGER.createCriterion(new Instance(Optional.empty(), type));
    }

    /** Tests if this instance matches */
    public boolean test(BlockEntityType<?> type) {
      return this.type == type;
    }
  }
}
