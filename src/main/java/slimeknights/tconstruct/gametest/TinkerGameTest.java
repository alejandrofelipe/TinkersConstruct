package slimeknights.tconstruct.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code public static void name(GameTestHelper)} method as a Tinkers gametest. 1.21.5 removed vanilla's
 * {@code @GameTest} (and NeoForge's {@code @GameTestHolder}/{@code @PrefixGameTestTemplate}); tests are now registry
 * entries, built from this annotation by {@link TinkerGameTestRegistration}. Attribute names match the old
 * {@code @GameTest} so test methods kept their shape.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TinkerGameTest {
  /** Structure the test runs in, as a path in the tconstruct namespace (e.g. {@code gametest/empty_5x5x5}) */
  String template();

  /** Ticks before the test times out */
  int timeoutTicks();
}
