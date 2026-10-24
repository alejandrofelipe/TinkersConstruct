package slimeknights.tconstruct.gametest;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.DeferredRegister;
import slimeknights.tconstruct.TConstruct;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Registers every {@link TinkerGameTest} method as a 1.21.5 gametest. Each method becomes a
 * {@code minecraft:test_function} entry {@code tconstruct:<method_name>}, plus a {@link FunctionGameTestInstance} of
 * the same id (so the old test names are kept) in the {@code tconstruct:default} environment, running in structure
 * {@code tconstruct:<template>} with {@code maxTicks = timeoutTicks}.
 */
public final class TinkerGameTestRegistration {
  private static final List<Class<?>> TEST_CLASSES = List.of(
    SmelteryGameTests.class, MantleCommandGameTests.class, CapabilityGameTests.class, ToolGameTests.class,
    DatapackTagGapGameTests.class, RecipeValidationGameTests.class, WorldgenGameTests.class);

  private static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, TConstruct.MOD_ID);
  private static final List<Entry> TESTS = new ArrayList<>();

  private TinkerGameTestRegistration() {}

  /** Registers the test functions and the test instance listener on the mod bus; called from the mod constructor. */
  public static void init(IEventBus modBus) {
    // the instances are only registered when gametests are enabled, so skip the functions otherwise too
    if (!GameTestHooks.isGametestEnabled()) {
      return;
    }
    for (Class<?> clazz : TEST_CLASSES) {
      for (Method method : clazz.getDeclaredMethods()) {
        TinkerGameTest test = method.getAnnotation(TinkerGameTest.class);
        if (test != null) {
          if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1 || method.getParameterTypes()[0] != GameTestHelper.class) {
            throw new IllegalStateException("Gametest " + method + " must be static and take a single GameTestHelper");
          }
          ResourceKey<Consumer<GameTestHelper>> function = TEST_FUNCTIONS.register(method.getName(), () -> helper -> invoke(method, helper)).getKey();
          TESTS.add(new Entry(function, test));
        }
      }
    }
    TEST_FUNCTIONS.register(modBus);
    modBus.addListener(TinkerGameTestRegistration::registerTests);
  }

  /** Registers the environment and one test instance per test function */
  private static void registerTests(RegisterGameTestsEvent event) {
    Holder<TestEnvironmentDefinition> environment = event.registerEnvironment(TConstruct.getResource("default"));
    for (Entry entry : TESTS) {
      TestData<Holder<TestEnvironmentDefinition>> data = new TestData<>(environment, TConstruct.getResource(entry.test.template()), entry.test.timeoutTicks(), 0, true);
      event.registerTest(entry.function.location(), new FunctionGameTestInstance(entry.function, data));
    }
  }

  /** Runs the test method, rethrowing its own exception so assertion failures report as such */
  private static void invoke(Method method, GameTestHelper helper) {
    try {
      method.invoke(null, helper);
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof RuntimeException runtime) {
        throw runtime;
      }
      if (e.getCause() instanceof Error error) {
        throw error;
      }
      throw new RuntimeException(e.getCause());
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Cannot access gametest " + method, e);
    }
  }

  private record Entry(ResourceKey<Consumer<GameTestHelper>> function, TinkerGameTest test) {}
}
