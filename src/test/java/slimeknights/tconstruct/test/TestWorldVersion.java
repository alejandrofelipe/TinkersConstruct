package slimeknights.tconstruct.test;

import net.minecraft.SharedConstants;
import net.minecraft.WorldVersion;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.world.level.storage.DataVersion;

import java.util.Date;

public class TestWorldVersion implements WorldVersion {
  public static final TestWorldVersion INSTANCE = new TestWorldVersion();
  // 26.1: the file fixer (DataFixers) rejects fixes newer than the game's data version, so a 0 version fails Bootstrap
  private static final DataVersion DATA = new DataVersion(SharedConstants.WORLD_VERSION, "tconstruct_test");

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
