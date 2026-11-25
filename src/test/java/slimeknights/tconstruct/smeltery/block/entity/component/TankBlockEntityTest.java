package slimeknights.tconstruct.smeltery.block.entity.component;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import slimeknights.mantle.transfer.SingleFluidTank;
import slimeknights.tconstruct.library.utils.NBTTags;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;

class TankBlockEntityTest extends BaseMcTest {
  /** Block entity without a level, so the light update after a load is skipped */
  private final BlockEntity blockEntity = Mockito.mock(BlockEntity.class);

  /** Reads the tank child of the given block entity tag, the same way {@link TankBlockEntity#loadAdditional} does */
  private static ValueInput tankInput(CompoundTag blockEntityTag) {
    return TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, blockEntityTag).childOrEmpty(NBTTags.TANK);
  }

  @Test
  void updateTank_missingTankTag_clearsFluid() {
    // an empty tank saves no tank tag, e.g. the update packet of an empty fuel tank in a melter, which only holds the master position
    CompoundTag tag = new CompoundTag();
    tag.putString("master", "unrelated");
    SingleFluidTank tank = new SingleFluidTank(1000);
    tank.setFluid(new FluidStack(Fluids.WATER, 500));

    TankBlockEntity.updateTank(blockEntity, tank, tankInput(tag));

    assertThat(tank.isEmpty()).isTrue();
  }

  @Test
  void updateTank_withTankTag_readsFluid() {
    SingleFluidTank saved = new SingleFluidTank(1000);
    saved.setFluid(new FluidStack(Fluids.LAVA, 250));
    TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
    saved.serialize(output.child(NBTTags.TANK));
    SingleFluidTank tank = new SingleFluidTank(1000);

    TankBlockEntity.updateTank(blockEntity, tank, tankInput(output.buildResult()));

    assertThat(tank.getFluid().getFluid()).isEqualTo(Fluids.LAVA);
    assertThat(tank.getFluidAmount()).isEqualTo(250);
  }
}
