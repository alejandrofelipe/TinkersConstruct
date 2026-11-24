package slimeknights.tconstruct.test;

import net.minecraft.SharedConstants;
import net.minecraft.WorldVersion;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
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

  /** 1.21.9: pack versions are major.minor {@link PackFormat}s */
  @Override
  public PackFormat packVersion(PackType pPackType) {
    return PackFormat.of(0);
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
