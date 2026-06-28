package slimeknights.tconstruct.library;

import net.minecraft.world.item.ItemDisplayContext;
import slimeknights.tconstruct.TConstruct;

import java.util.Locale;

/** Custom transform types used for tinkers item rendering */
public class TinkerItemDisplays {
  private TinkerItemDisplays() {}

  /**
   * Touches this class so the static {@link #create(String, ItemDisplayContext)} calls below run, which
   * is what registers the custom display contexts.
   *
   * <p>PORT 1.21.1: {@code ItemDisplayContext} is now a {@link net.minecraft.util.StringRepresentable}
   * enum rather than a Forge registry, and {@code ItemDisplayContext.create(...)} (a NeoForge extension)
   * both creates and registers the context at class-init time. The old {@code RegisterEvent} listener over
   * {@code ForgeRegistries.DISPLAY_CONTEXTS} is gone — classloading this type is the registration.
   */
  public static void init() {}

  /** Used by the melter and smeltery for display of items its melting */
  public static ItemDisplayContext MELTER = create("melter", ItemDisplayContext.NONE);
  /** Used by the part builder, crafting station, tinkers station, and tinker anvil */
  public static ItemDisplayContext TABLE = create("table", ItemDisplayContext.NONE);
  /** Used by the casting table for item rendering */
  public static ItemDisplayContext CASTING_TABLE = create("casting_table", ItemDisplayContext.FIXED);
  /** Used by the casting basin for item rendering */
  public static ItemDisplayContext CASTING_BASIN = create("casting_basin", ItemDisplayContext.NONE);
  /** Used by the fluid cannon for display of the item in front */
  public static ItemDisplayContext FLUID_CANNON = create("fluid_cannon", ItemDisplayContext.FIXED);
  /** Used by throwing to allow adjusting the tool position */
  public static ItemDisplayContext THROWN = create("thrown", ItemDisplayContext.FIXED);

  /** Creates a transform type */
  private static ItemDisplayContext create(String name, ItemDisplayContext fallback) {
    String key = "TCONSTRUCT_" + name.toUpperCase(Locale.ROOT);
    if (fallback == ItemDisplayContext.NONE) {
      return ItemDisplayContext.create(key, TConstruct.getResource(name), null);
    }
    return ItemDisplayContext.create(key, TConstruct.getResource(name), fallback);
  }
}
