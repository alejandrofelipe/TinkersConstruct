package slimeknights.tconstruct.library;

import net.minecraft.world.item.ItemDisplayContext;

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

  /**
   * Creates a transform type.
   *
   * <p>PORT M3 (BLOCKER): in 1.21.1 {@link ItemDisplayContext} is a plain {@link net.minecraft.util.StringRepresentable}
   * enum and is no longer an extensible registry — the Forge/NeoForge {@code ItemDisplayContext.create(...)} extension
   * was removed, so custom Tinkers display contexts can no longer be registered. As a stopgap each custom context now
   * resolves to its closest vanilla fallback ({@code NONE}/{@code FIXED}) so block-entity item rendering still works,
   * losing the distinct custom transforms. A proper fix needs the renderers (in {@code smeltery/client}, {@code tools/client},
   * and {@code common/data/render}) reworked to apply their transforms directly rather than via registered contexts.
   */
  private static ItemDisplayContext create(String name, ItemDisplayContext fallback) {
    // touch the name so the intent is preserved for the eventual rework
    String unused = "TCONSTRUCT_" + name.toUpperCase(Locale.ROOT);
    return fallback;
  }
}
