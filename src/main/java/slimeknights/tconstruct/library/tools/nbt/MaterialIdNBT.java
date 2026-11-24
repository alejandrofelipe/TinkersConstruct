package slimeknights.tconstruct.library.tools.nbt;

import com.google.common.collect.ImmutableList;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import slimeknights.tconstruct.library.materials.IMaterialRegistry;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.utils.TagUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Similar to {@link slimeknights.tconstruct.library.tools.nbt.MaterialNBT}, but does not check materials against the registry.
 * Used for rendering so we can have client side only materials for UIs. Anything logic based should use the regular material NBT
 */
@EqualsAndHashCode
@ToString
public class MaterialIdNBT {
  /** Instance containing no materials, for errors with parsing NBT */
  public final static MaterialIdNBT EMPTY = new MaterialIdNBT(ImmutableList.of());

  /** List of materials contained in this NBT */
  @Getter
  private final List<MaterialVariantId> materials;

  /** Creates a new material NBT */
  public MaterialIdNBT(List<? extends MaterialVariantId> materials) {
    this.materials = ImmutableList.copyOf(materials);
  }

  /**
   * Gets the material at the given index
   * @param index  Index
   * @return  Material, or unknown if index is invalid
   */
  public MaterialVariantId getMaterial(int index) {
    if (index >= materials.size() || index < 0) {
      return IMaterial.UNKNOWN_ID;
    }
    return materials.get(index);
  }

  /** Resolves all redirects, replacing with material redirects */
  public MaterialIdNBT resolveRedirects() {
    // read every frame by tool models (1.21.9), so only allocate once something redirects
    ImmutableList.Builder<MaterialVariantId> builder = null;
    IMaterialRegistry registry = MaterialRegistry.getInstance();
    for (int i = 0; i < materials.size(); i++) {
      MaterialVariantId id = materials.get(i);
      MaterialId original = id.getId();
      MaterialId resolved = registry.resolve(original);
      if (builder == null && resolved != original) {
        builder = ImmutableList.builder();
        builder.addAll(materials.subList(0, i));
      }
      if (builder != null) {
        builder.add(MaterialVariantId.create(resolved, id.getVariant()));
      }
    }
    // return a new instance only if things changed
    return builder == null ? this : new MaterialIdNBT(builder.build());
  }

  /**
   * Parses the material list from NBT
   * @param nbt  NBT instance
   * @return  MaterialNBT instance
   */
  public static MaterialIdNBT readFromNBT(@Nullable Tag nbt) {
    if (nbt == null || nbt.getId() != Tag.TAG_LIST) {
      return EMPTY;
    }
    ListTag listNBT = (ListTag) nbt;
    if (listNBT.isEmpty() || !TagUtil.isListOf(listNBT, Tag.TAG_STRING)) {
      return EMPTY;
    }

    List<MaterialVariantId> materials = listNBT.stream()
      .map(tag -> tag.asString().orElse(""))
      .map(MaterialVariantId::tryParse)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
    return new MaterialIdNBT(materials);
  }

  /**
   * Writes this material list to NBT
   * @return  List of materials
   */
  public ListTag serializeToNBT() {
    return materials.stream()
                    .map(MaterialVariantId::toString)
                    .map(StringTag::valueOf)
                    .collect(Collectors.toCollection(ListTag::new));
  }

  /**
   * Parses the material list from a stack, with material redirects resolved in memory (1.21.9: stacks the server never read, e.g. on the client, are
   * not verified, see {@link ToolDataVerifier})
   * @param stack  Tool stack instance
   * @return  MaterialNBT instance
   */
  public static MaterialIdNBT from(ItemStack stack) {
    CustomData data = stack.get(DataComponents.CUSTOM_DATA);
    if (data != null) {
      MaterialIdNBT materials = readFromNBT(TagUtil.getList(data.tag, ToolStack.TAG_MATERIALS, Tag.TAG_STRING));
      return MaterialRegistry.isFullyLoaded() ? materials.resolveRedirects() : materials;
    }
    return EMPTY;
  }

  /** Writes this material list to the given stack */
  public ItemStack updateStack(ItemStack stack) {
    CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(ToolStack.TAG_MATERIALS, serializeToNBT()));
    return stack;
  }

  /** Writes this material list to the given stack */
  @SuppressWarnings("UnusedReturnValue")
  public CompoundTag updateNBT(CompoundTag nbt) {
    nbt.put(ToolStack.TAG_MATERIALS, serializeToNBT());
    return nbt;
  }
}
