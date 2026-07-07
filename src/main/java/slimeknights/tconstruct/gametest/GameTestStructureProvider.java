package slimeknights.tconstruct.gametest;

import net.minecraft.core.Vec3i;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.PackOutput.Target;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.SharedConstants;
import slimeknights.tconstruct.TConstruct;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Emits empty NxNxN gametest arena templates so no hand-authored NBT is needed. */
public class GameTestStructureProvider implements DataProvider {
  private final PackOutput output;
  public GameTestStructureProvider(PackOutput output) {
    this.output = output;
  }

  @Override
  public CompletableFuture<?> run(CachedOutput cache) {
    return CompletableFuture.allOf(
      save(cache, "empty_5x5x5", new Vec3i(5, 5, 5)),
      save(cache, "empty_9x9x9", new Vec3i(9, 9, 9)));
  }

  private CompletableFuture<?> save(CachedOutput cache, String name, Vec3i size) {
    CompoundTag nbt = new CompoundTag();
    nbt.put("size", newIntList(size.getX(), size.getY(), size.getZ()));
    nbt.put("blocks", new ListTag());   // all air
    nbt.put("entities", new ListTag());
    ListTag palette = new ListTag();
    palette.add(NbtUtils.writeBlockState(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()));
    nbt.put("palette", palette);
    nbt.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
    Path path = output.getOutputFolder(Target.DATA_PACK)
      .resolve(TConstruct.MOD_ID).resolve("structure").resolve("gametest").resolve(name + ".nbt");
    return CompletableFuture.runAsync(() -> {
      try {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.writeCompressed(nbt, new DataOutputStream(bytes));
        cache.writeIfNeeded(path, bytes.toByteArray(),
          com.google.common.hash.Hashing.sha1().hashBytes(bytes.toByteArray()));
      } catch (Exception e) {
        throw new RuntimeException("Failed to save gametest template " + name, e);
      }
    });
  }

  private static ListTag newIntList(int x, int y, int z) {
    ListTag list = new ListTag();
    list.add(net.minecraft.nbt.IntTag.valueOf(x));
    list.add(net.minecraft.nbt.IntTag.valueOf(y));
    list.add(net.minecraft.nbt.IntTag.valueOf(z));
    return list;
  }

  @Override
  public String getName() {
    return "TConstruct gametest structures";
  }
}
