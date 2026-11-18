package slimeknights.tconstruct.library.utils;

import com.mojang.logging.LogUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Consumer;

/** Helpers related to Tag */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TagUtil {
  private static final Logger LOGGER = LogUtils.getLogger();
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
    // 1.21.8: ItemStack.parse was removed; same codec and context it used internally
    return tag.isEmpty() ? ItemStack.EMPTY : ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
      .resultOrPartial(error -> LOGGER.error("Tried to load invalid item: '{}'", error)).orElse(ItemStack.EMPTY);
  }

  /**
   * Encodes a non-empty item stack, merging it into the given prefix. Replacement for the removed {@code ItemStack.save(Provider, Tag)}, same tag shape.
   * @param registries  Registry access
   * @param stack       Stack to encode, must not be empty
   * @param prefix      Tag to merge the stack into
   * @return  Encoded tag
   */
  public static Tag saveItem(HolderLookup.Provider registries, ItemStack stack, Tag prefix) {
    if (stack.isEmpty()) {
      throw new IllegalStateException("Cannot encode empty ItemStack");
    }
    return ItemStack.CODEC.encode(stack, registries.createSerializationContext(NbtOps.INSTANCE), prefix).getOrThrow();
  }


  /* Value IO bridges */

  /**
   * Writes data through a {@link ValueOutput} into a new compound tag, for places that still need a raw tag (update tags, packets, legacy blobs).
   * @param path        Path for problem reporting
   * @param registries  Registry access
   * @param writer      Logic writing the data
   * @return  Written tag
   */
  public static CompoundTag writeTag(ProblemReporter.PathElement path, HolderLookup.Provider registries, Consumer<ValueOutput> writer) {
    try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(path, LOGGER)) {
      TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
      writer.accept(output);
      return output.buildResult();
    }
  }

  /**
   * Reads data from a raw compound tag through a {@link ValueInput}, for places that still receive a raw tag (legacy blobs, item data).
   * @param path        Path for problem reporting
   * @param registries  Registry access
   * @param tag         Tag to read
   * @param reader      Logic reading the data
   */
  public static void readTag(ProblemReporter.PathElement path, HolderLookup.Provider registries, CompoundTag tag, Consumer<ValueInput> reader) {
    try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(path, LOGGER)) {
      reader.accept(TagValueInput.create(reporter, registries, tag));
    }
  }

  /**
   * Reads a block position from a value input, relative to the given offset. Same format as {@link #writePos(BlockPos)}.
   * @param input   Input to read
   * @param key     Position key
   * @param offset  Amount to offset position by
   * @return  Block position, or null if invalid or missing
   */
  @Nullable
  public static BlockPos readOptionalPos(ValueInput input, String key, BlockPos offset) {
    return input.read(key, BlockPos.CODEC).map(pos -> pos.offset(offset)).orElse(null);
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
