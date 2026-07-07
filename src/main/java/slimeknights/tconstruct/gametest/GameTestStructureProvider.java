package slimeknights.tconstruct.gametest;

import net.minecraft.SharedConstants;
import net.minecraft.core.Vec3i;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import net.minecraft.data.PackOutput.Target;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.data.GenericNBTProvider;

import java.util.concurrent.CompletableFuture;

/** Emits empty NxNxN gametest arena templates so no hand-authored NBT is needed. */
public class GameTestStructureProvider extends GenericNBTProvider {
  public GameTestStructureProvider(PackOutput output) {
    super(output, Target.DATA_PACK, "structure/gametest");
  }

  @Override
  public CompletableFuture<?> run(CachedOutput cache) {
    return CompletableFuture.allOf(
      save(cache, "empty_5x5x5", new Vec3i(5, 5, 5)),
      save(cache, "empty_9x9x9", new Vec3i(9, 9, 9)));
  }

  /** Builds and saves a single empty arena template of the given size */
  private CompletableFuture<?> save(CachedOutput cache, String name, Vec3i size) {
    CompoundTag nbt = new CompoundTag();
    nbt.put("size", newIntList(size.getX(), size.getY(), size.getZ()));
    nbt.put("blocks", new ListTag());   // all air
    nbt.put("entities", new ListTag());
    ListTag palette = new ListTag();
    palette.add(NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
    nbt.put("palette", palette);
    nbt.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
    return saveNBT(cache, TConstruct.getResource(name), nbt);
  }

  private static ListTag newIntList(int x, int y, int z) {
    ListTag list = new ListTag();
    list.add(IntTag.valueOf(x));
    list.add(IntTag.valueOf(y));
    list.add(IntTag.valueOf(z));
    return list;
  }

  @Override
  public String getName() {
    return "TConstruct gametest structures";
  }
}
