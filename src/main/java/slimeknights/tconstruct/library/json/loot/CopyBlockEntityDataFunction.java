package slimeknights.tconstruct.library.json.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import slimeknights.tconstruct.tables.TinkerTables;

import java.util.List;
import java.util.Set;

/**
 * Copies top level keys of the block entity's saved data into the dropped item's {@link DataComponents#BLOCK_ENTITY_DATA}, so placing the item
 * loads them back into the block entity through the vanilla {@link net.minecraft.world.item.BlockItem} path. Empty lists and compounds are skipped,
 * so an emptied block drops an item that stacks with fresh ones.
 */
public class CopyBlockEntityDataFunction extends LootItemConditionalFunction {
  public static final MapCodec<CopyBlockEntityDataFunction> CODEC = RecordCodecBuilder.mapCodec(inst -> commonFields(inst).and(
    ExtraCodecs.nonEmptyList(Codec.STRING.listOf()).fieldOf("keys").forGetter(function -> function.keys)
  ).apply(inst, CopyBlockEntityDataFunction::new));

  /** Keys to copy */
  private final List<String> keys;

  protected CopyBlockEntityDataFunction(List<LootItemCondition> conditions, List<String> keys) {
    super(conditions);
    this.keys = List.copyOf(keys);
  }

  /** Creates a builder copying the given keys */
  public static Builder<?> copy(String... keys) {
    List<String> list = List.of(keys);
    return simpleBuilder(conditions -> new CopyBlockEntityDataFunction(conditions, list));
  }

  @Override
  public LootItemFunctionType<CopyBlockEntityDataFunction> getType() {
    return TinkerTables.lootCopyBlockEntityData.get();
  }

  @Override
  public Set<ContextKey<?>> getReferencedContextParams() {
    return Set.of(LootContextParams.BLOCK_ENTITY);
  }

  @Override
  protected ItemStack run(ItemStack stack, LootContext context) {
    BlockEntity blockEntity = context.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
    if (blockEntity != null) {
      CompoundTag saved = blockEntity.saveCustomOnly(context.getLevel().registryAccess());
      TypedEntityData<BlockEntityType<?>> existing = stack.get(DataComponents.BLOCK_ENTITY_DATA);
      CompoundTag data = existing != null && existing.type() == blockEntity.getType() ? existing.copyTagWithoutId() : new CompoundTag();
      boolean copied = false;
      for (String key : keys) {
        Tag tag = saved.get(key);
        if (tag != null && !(tag instanceof CollectionTag collection && collection.isEmpty()) && !(tag instanceof CompoundTag compound && compound.isEmpty())) {
          data.put(key, tag);
          copied = true;
        }
      }
      if (copied) {
        stack.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.<BlockEntityType<?>>of(blockEntity.getType(), data));
      }
    }
    return stack;
  }
}
