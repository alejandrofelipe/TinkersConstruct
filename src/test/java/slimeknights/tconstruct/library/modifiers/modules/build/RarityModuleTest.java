package slimeknights.tconstruct.library.modifiers.modules.build;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolDataNBT;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests {@link RarityModule}: tracks the tool's display rarity in volatile data, keeping the largest ordinal seen
 * (so a rarer material tends to beat out a plainer one regardless of application order).
 * Note: the static {@link RarityModule#getRarity} helper reads the value back off a real {@code ItemStack}'s
 * internal volatile-data NBT layout owned by {@code ToolStack}; reproducing that layout here would couple the test
 * to internal representation details it shouldn't know about, so it's intentionally left untested (would need a
 * gametest or a {@code ToolStack} fixture).
 */
class RarityModuleTest extends BaseMcTest {
  private static final ModifierEntry ENTRY = new ModifierEntry(new ModifierId("test", "foo"), 1);

  @Test
  void setRarity_onEmptyData_setsValue() {
    ModDataNBT data = new ModDataNBT();
    RarityModule.setRarity(data, Rarity.RARE);
    assertThat(data.getInt(RarityModule.RARITY)).isEqualTo(Rarity.RARE.ordinal());
  }

  @Test
  void setRarity_withLowerRarity_keepsHigherExistingValue() {
    ModDataNBT data = new ModDataNBT();
    RarityModule.setRarity(data, Rarity.EPIC);
    RarityModule.setRarity(data, Rarity.COMMON);
    assertThat(data.getInt(RarityModule.RARITY)).isEqualTo(Rarity.EPIC.ordinal());
  }

  @Test
  void setRarity_withHigherRarity_overwritesLowerExistingValue() {
    ModDataNBT data = new ModDataNBT();
    // UNCOMMON (ordinal 1), not COMMON (0 = the empty-data default) - so the pre-existing value is
    // genuinely distinguishable from unset and the overwrite is actually exercised
    RarityModule.setRarity(data, Rarity.UNCOMMON);
    RarityModule.setRarity(data, Rarity.EPIC);
    assertThat(data.getInt(RarityModule.RARITY)).isEqualTo(Rarity.EPIC.ordinal());
  }

  @Test
  void setRarity_equalRarity_doesNotError() {
    ModDataNBT data = new ModDataNBT();
    RarityModule.setRarity(data, Rarity.RARE);
    RarityModule.setRarity(data, Rarity.RARE);
    assertThat(data.getInt(RarityModule.RARITY)).isEqualTo(Rarity.RARE.ordinal());
  }

  @Test
  void addVolatileData_delegatesToSetRarity() {
    RarityModule module = new RarityModule(Rarity.EPIC);
    ToolDataNBT data = new ToolDataNBT();
    module.addVolatileData(mock(IToolContext.class), ENTRY, data);
    assertThat(data.getInt(RarityModule.RARITY)).isEqualTo(Rarity.EPIC.ordinal());
  }

  @Test
  void loader_jsonRoundTrips() {
    RarityModule original = new RarityModule(Rarity.EPIC);
    JsonObject json = new JsonObject();
    RarityModule.LOADER.serialize(original, json);
    RarityModule parsed = RarityModule.LOADER.deserialize(json);
    assertThat(parsed.rarity()).isEqualTo(Rarity.EPIC);
  }

  @Test
  void loader_networkRoundTrips() {
    RarityModule original = new RarityModule(Rarity.UNCOMMON);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    RarityModule.LOADER.encode(buffer, original);
    RarityModule decoded = RarityModule.LOADER.decode(buffer);
    assertThat(decoded.rarity()).isEqualTo(Rarity.UNCOMMON);
  }

  @Test
  void getLoader_returnsStaticLoader() {
    RarityModule module = new RarityModule(Rarity.COMMON);
    assertThat(module.getLoader()).isSameAs(RarityModule.LOADER);
  }
}
