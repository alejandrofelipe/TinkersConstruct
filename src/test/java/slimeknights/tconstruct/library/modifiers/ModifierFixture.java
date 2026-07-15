package slimeknights.tconstruct.library.modifiers;

import slimeknights.tconstruct.library.module.ModuleHookMap;

public class ModifierFixture {
  public static final ModifierId TEST_1 = new ModifierId("test", "modifier_1");
  public static final ModifierId TEST_2 = new ModifierId("test", "modifier_2");

  public static final Modifier TEST_MODIFIER_1 = new Modifier();
  public static final Modifier TEST_MODIFIER_2 = new Modifier();

  private static boolean init = false;

  public static void init() {
    if (init) {
      return;
    }
    init = true;
    TEST_MODIFIER_1.setId(TEST_1);
    TEST_MODIFIER_2.setId(TEST_2);
    ModifierManager.INSTANCE.staticModifiers.put(TEST_1, TEST_MODIFIER_1);
    ModifierManager.INSTANCE.staticModifiers.put(TEST_2, TEST_MODIFIER_2);
    ModifierManager.INSTANCE.dynamicModifiersLoaded = true;
  }

  /**
   * Creates a fresh, ID-bound modifier wrapping the given hook map, for tests that need {@link Modifier#getHook}
   * (via {@link ModifierEntry#getHook}) to resolve to a specific hook implementation (e.g. a mocked or fake
   * {@code CapacityBarHook}). {@link Modifier}'s {@code setId} is package-private and {@code ModifierEntry}
   * requires a non-null registry name even for a directly-supplied {@link Modifier}, so this needs to live here
   * rather than in the consuming test's own package. Call {@link #init()} first.
   */
  public static Modifier withHooks(ModuleHookMap hooks) {
    Modifier modifier = new Modifier(hooks);
    modifier.setId(new ModifierId("test", "with_hooks"));
    return modifier;
  }
}
