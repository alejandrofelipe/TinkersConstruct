package slimeknights.tconstruct.test;

import net.minecraft.SharedConstants;
import net.minecraft.WorldVersion;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.storage.DataVersion;

import java.util.Date;

public class TestWorldVersion implements WorldVersion {
  public static final TestWorldVersion INSTANCE = new TestWorldVersion();
  private static final DataVersion DATA = new DataVersion(0, "tconstruct_test");

  @Override
  public DataVersion dataVersion() {
    return DATA;
  }

  @Override
  public String id() {
    return "tconstruct_test";
  }

  @Override
  public String name() {
    return "Tinkers' Construct Automated Test";
  }

  @Override
  public int protocolVersion() {
    return SharedConstants.getProtocolVersion();
  }

  @Override
  public int packVersion(PackType pPackType) {
    return 0;
  }

  @Override
  public Date buildTime() {
    return new Date();
  }

  @Override
  public boolean stable() {
    return true;
  }
}
