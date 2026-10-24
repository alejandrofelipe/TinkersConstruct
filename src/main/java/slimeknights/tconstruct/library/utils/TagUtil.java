package slimeknights.tconstruct.library.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Optional;

/** Helpers related to Tag */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TagUtil {
  /** Replacement for the removed {@code Tag.TAG_ANY_NUMERIC}, matches any numeric tag type in {@link #isType(Tag, int)} */
  public static final int TAG_ANY_NUMERIC = 99;

  /* Helper functions */

  /**
   * Checks if the given tag has the given type, replacement for the removed type check in {@code CompoundTag#contains(String, int)}
   * @param tag   Tag to check, may be null
   * @param type  Tag type, see {@link Tag} for values, or {@link #TAG_ANY_NUMERIC}
   * @return  True if the tag is non-null and matches the type
   */
  public static boolean isType(@Nullable Tag tag, int type) {
    return tag != null && (tag.getId() == type || (type == TAG_ANY_NUMERIC && tag instanceof NumericTag));
  }

  /**
   * Checks if the given compound contains the key with the given type.
   * 1.21.5: replacement for the removed {@code CompoundTag#contains(String, int)}.
   * @param tag   Compound to check
   * @param key   Key to check
   * @param type  Tag type, see {@link Tag} for values, or {@link #TAG_ANY_NUMERIC}
   * @return  True if the key is present with the given type
   */
  public static boolean contains(CompoundTag tag, String key, int type) {
    return isType(tag.get(key), type);
  }

  /**
   * Checks if all elements in the list have the given type. Empty lists match any type.
   * @param list  List to check
   * @param type  Element type
   * @return  True if all elements match
   */
  public static boolean isListOf(ListTag list, int type) {
    for (Tag element : list) {
      if (!isType(element, type)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Gets a list from the compound if all its elements have the given type.
   * 1.21.5: replacement for the removed {@code CompoundTag#getList(String, int)}; like before, returns the stored list (not a copy) when it matches, or a new empty list otherwise.
   * @param tag   Compound to read
   * @param key   List key
   * @param type  Element type
   * @return  List from the compound, or an empty list if missing or of the wrong type
   */
  public static ListTag getList(CompoundTag tag, String key, int type) {
    if (tag.get(key) instanceof ListTag list && isListOf(list, type)) {
      return list;
    }
    return new ListTag();
  }

  /**
   * Writes a block position to a tag, same format as the removed {@code NbtUtils.writeBlockPos} (an int array, matches {@link BlockPos#CODEC}).
   * @param pos  Position to write
   * @return  Position tag
   */
  public static Tag writePos(BlockPos pos) {
    return new IntArrayTag(new int[] {pos.getX(), pos.getY(), pos.getZ()});
  }

  /**
   * Reads a block position from a tag, replacement for the removed {@code NbtUtils.readBlockPos}.
   * @param parent  Parent tag
   * @param key     Position key
   * @return  Block position, or empty if missing or invalid
   */
  public static Optional<BlockPos> readPos(CompoundTag parent, String key) {
    return parent.read(key, BlockPos.CODEC);
  }

  /**
   * Parses an item stack, treating an empty tag as an empty stack. Replacement for the removed {@code ItemStack.parseOptional}.
   * @param registries  Registry access
   * @param tag         Tag to parse
   * @return  Parsed stack, or empty if the tag is empty or invalid
   */
  public static ItemStack parseOptionalItem(HolderLookup.Provider registries, CompoundTag tag) {
    return tag.isEmpty() ? ItemStack.EMPTY : ItemStack.parse(registries, tag).orElse(ItemStack.EMPTY);
  }

  /**
   * Reads a block position from Tag
   * @param parent  Parent tag
   * @param key     Position key
   * @param offset  Amount to offset position by
   * @return  Block position, or null if invalid or missing
   */
  @Nullable
  public static BlockPos readOptionalPos(CompoundTag parent, String key, BlockPos offset) {
    // 1.21: NbtUtils.writeBlockPos stores an INT_ARRAY (no longer a compound), and readBlockPos(parent, key) already
    // returns an empty Optional when the key is missing or mistyped — a TAG_COMPOUND guard here silently dropped every
    // position (breaking multiblock structure loads on both sides), so rely on the Optional instead
    return readPos(parent, key).map(pos -> pos.offset(offset)).orElse(null);
  }

  /**
   * Checks if the given tag is a numeric type
   * @param tag  Tag to check
   * @return  True if the type matches
   */
  public static boolean isNumeric(Tag tag) {
    byte type = tag.getId();
    return type == Tag.TAG_BYTE || type == Tag.TAG_SHORT || type == Tag.TAG_INT || type == Tag.TAG_LONG || type == Tag.TAG_FLOAT || type == Tag.TAG_DOUBLE;
  }
}
