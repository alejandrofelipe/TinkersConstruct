package slimeknights.tconstruct.tables.block.entity.table;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.model.data.ModelDataManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.util.RetexturedHelper;
import slimeknights.tconstruct.test.BaseMcTest;

import javax.annotation.Nullable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The client copy of a retextured table must refresh its model when the texture changes, else it keeps rendering the old one */
class RetexturedTableBlockEntityTest extends BaseMcTest {
  private Level level;
  private TestTable table;

  @BeforeEach
  void setup() {
    BlockState state = Blocks.CRAFTING_TABLE.defaultBlockState();
    BlockEntityType<?> type = mock(BlockEntityType.class);
    when(type.isValid(any())).thenReturn(true);
    level = mock(Level.class);
    when(level.isClientSide()).thenReturn(true);
    when(level.getModelDataManager()).thenReturn(mock(ModelDataManager.class));
    table = new TestTable(type, state);
    table.setLevel(level);
  }

  /** Loads the given update tag like {@link net.minecraft.world.level.block.entity.BlockEntity#handleUpdateTag} */
  private void load(CompoundTag tag) {
    table.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, tag));
  }

  private void verifyRefreshes(int times) {
    verify(level, times(times)).sendBlockUpdated(any(), any(), any(), anyInt());
  }

  @Test
  void updateTexture_onClient_refreshesModel() {
    table.updateTexture("minecraft:stone");

    assertThat(table.getModelData().get(RetexturedHelper.BLOCK_PROPERTY)).isEqualTo(Blocks.STONE);
    verifyRefreshes(1);

    // reset to no texture
    table.updateTexture("");
    assertThat(table.getModelData().get(RetexturedHelper.BLOCK_PROPERTY)).isNull();
    verifyRefreshes(2);
  }

  @Test
  void updateTexture_unchanged_doesNotRefresh() {
    table.updateTexture("");
    verify(level, never()).sendBlockUpdated(any(), any(), any(), anyInt());
  }

  @Test
  void load_textureChangedOrRemoved_refreshesModel() {
    CompoundTag withTexture = new CompoundTag();
    withTexture.putString("texture", "minecraft:oak_planks");
    load(withTexture);
    assertThat(table.getTexture()).isEqualTo(Blocks.OAK_PLANKS);
    verifyRefreshes(1);

    // same texture again: nothing to refresh
    load(withTexture);
    verifyRefreshes(1);

    // the server saves no texture tag for air
    load(new CompoundTag());
    assertThat(table.getTexture()).isEqualTo(Blocks.AIR);
    verifyRefreshes(2);
  }

  private static class TestTable extends RetexturedTableBlockEntity {
    TestTable(BlockEntityType<?> type, BlockState state) {
      super(type, BlockPos.ZERO, state, Component.empty(), 1);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
      return null;
    }
  }
}
