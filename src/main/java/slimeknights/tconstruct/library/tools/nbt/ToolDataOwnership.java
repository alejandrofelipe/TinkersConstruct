package slimeknights.tconstruct.library.tools.nbt;

import com.google.common.collect.MapMaker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Tracks which stack may change the tag of a tool's {@link CustomData} in place.
 * <p>
 * {@link ToolStack} reads and writes the live tag of the custom data component for speed and so tools bound to the same stack see each other's
 * changes. Up to 1.21.8 every stack had its own tag, as {@code Item#verifyComponentsAfterLoad} gave a fresh component to each stack built by
 * {@code ItemStack#copy()} or decoding. Since 1.21.9 copies share the component instance, so changing the tag in place would also change every
 * copy, such as the equipment snapshot {@code LivingEntity} compares against to fire equipment changes and update attributes.
 * <p>
 * A stack owns a custom data instance once {@link ToolStack} gave it a fresh one; building any stack around an existing component map (copy, split,
 * resource conversion, decoding) releases the instance (see {@code ItemStackMixin}), as it may now be shared. A tool bound to a stack that does not
 * own its data first gives the stack its own copy. Keys and values are weak and compared by identity.
 * <p>
 * Not covered: component patches taken without copying the stack ({@code ItemStack#getComponentsPatch}, {@code applyComponents}) still share the
 * instance with its owner.
 */
public final class ToolDataOwnership {
  private static final Map<CustomData, ItemStack> OWNERS = new MapMaker().weakKeys().weakValues().makeMap();

  private ToolDataOwnership() {}

  /** Checks if the stack owns the given data, so a tool bound to it may change the tag in place */
  public static boolean owns(ItemStack stack, CustomData data) {
    return OWNERS.get(data) == stack;
  }

  /** Makes the stack the owner of the given data, which must be a fresh instance only the stack holds */
  public static void claim(ItemStack stack, CustomData data) {
    OWNERS.put(data, stack);
  }

  /** Releases the given data, called when a new stack is built around it */
  public static void release(@Nullable CustomData data) {
    if (data != null) {
      OWNERS.remove(data);
    }
  }
}
