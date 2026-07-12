package slimeknights.tconstruct.test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.ModLoadingContext;
import org.junit.jupiter.api.BeforeAll;

public class BaseMcTest {

  // PORT M6: the old Forge NetworkHooks/NetworkRegistry mocks guarded against Forge's SimpleChannel
  // static init running during vanilla Bootstrap.bootStrap(); NeoForge's payload-based networking has
  // no equivalent static hook here, so the mocking is no longer needed.
  @SuppressWarnings({"ResultOfMethodCallIgnored", "unused"})
  @BeforeAll
  static void setUpRegistries() {
    SharedConstants.setVersion(TestWorldVersion.INSTANCE);
    Bootstrap.bootStrap();
    ModLoadingContext.get().setActiveContainer(new TestModContainer(TestModInfo.INSTANCE));
  }

  /**
   * PORT M6: no-op. NeoForge removed TierSortingRegistry with no replacement registry/bootstrap step;
   * tier ordering now goes through tconstruct's own HarvestTiers (library.utils.HarvestTiers), which
   * needs no setup call. Kept so existing @BeforeAll call sites don't need touching.
   */
  public static void setupTierSorting() {
  }
}
