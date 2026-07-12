package slimeknights.tconstruct.test;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforgespi.language.IModInfo;

public class TestModContainer extends ModContainer {
  public TestModContainer(IModInfo info) {
    super(info);
  }

  // PORT M6: ModContainer.matches/getMod (used for equality against the loaded mod instance) were
  // removed; getEventBus() is the only abstract method left. Not exercised by tests, so a stub is fine.
  @Override
  public IEventBus getEventBus() {
    return null;
  }
}
