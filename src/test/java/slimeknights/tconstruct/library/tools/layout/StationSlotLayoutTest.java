package slimeknights.tconstruct.library.tools.layout;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.recipe.partbuilder.Pattern;
import slimeknights.tconstruct.test.BaseMcTest;
import slimeknights.tconstruct.test.TestHelper;

import static org.assertj.core.api.Assertions.assertThat;

class StationSlotLayoutTest extends BaseMcTest {
  @Test
  @Disabled("1.21 port: LayoutSlot.write/read run the filter Ingredient through Ingredient.CONTENTS_STREAM_CODEC, "
    + "which NeoForge gates behind RegistryManager.isNonSyncedBuiltInRegistry(registry) - it throws "
    + "IllegalStateException (\"Cannot use ID syncing for non-synced built-in registry\") unless the item registry "
    + "was tracked through a real FML registration/sync lifecycle (RegisterEvent, "
    + "RegistryManager.postNewRegistryEvent/takeVanillaSnapshot), which never runs in a bare JUnit test. Needs "
    + "either a fuller FML/NeoForge test harness bootstrap, or reflectively priming RegistryManager's private "
    + "vanillaRegistryKeys/snapshot state - unlike BaseMcTest's LoadingModList guard, RegistryManager has no public "
    + "seam to prime from test code. Same root cause as LayoutIconTest.item_bufferReadWrite.")
  void layoutSlot_bufferReadWrite() {
    LayoutSlot slot = new LayoutSlot(new Pattern("test:pattern"), "name", 5, 6, Ingredient.of(Items.BOOK));
    // PORT M6: Ingredient.CONTENTS_STREAM_CODEC needs a registry-aware buffer (item registry lookup).
    // RegistryAccess.EMPTY has NO registries at all (verified: throws "Missing registry: minecraft:item") -
    // fromRegistryOfRegistries(BuiltInRegistries.REGISTRY) wires the built-in registries (Item included,
    // already populated by BaseMcTest's bootstrap) into a real RegistryAccess.
    RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), ConnectionType.OTHER);
    slot.write(buffer);

    LayoutSlot decoded = LayoutSlot.read(buffer);
    Pattern pattern = decoded.getIcon();
    assertThat(pattern).isNotNull();
    assertThat(pattern.toString()).isEqualTo("test:pattern");
    assertThat(decoded.getTranslationKey()).isEqualTo("name");
    assertThat(decoded.getX()).isEqualTo(5);
    assertThat(decoded.getY()).isEqualTo(6);
    Ingredient ingredient = decoded.getFilter();
    assertThat(ingredient).isNotNull();
    ItemStack[] stacks = ingredient.getItems();
    assertThat(stacks).hasSize(1);
    assertThat(stacks[0].getItem()).isEqualTo(Items.BOOK);
    assertThat(TestHelper.getTag(stacks[0])).isNull();
  }

  @Test
  void stationLayout_getSlot() {
    StationSlotLayout layout = StationSlotLayout
      .builder()
      .toolSlot(1, 2)
      .addInputSlot(null, 3, 4)
      .addInputSlot(null, 5, 6)
      .build();

    LayoutSlot slot = layout.getSlot(0);
    assertThat(slot.getX()).isEqualTo(1);
    assertThat(slot.getY()).isEqualTo(2);
    slot = layout.getSlot(1);
    assertThat(slot.getX()).isEqualTo(3);
    assertThat(slot.getY()).isEqualTo(4);
    slot = layout.getSlot(2);
    assertThat(slot.getX()).isEqualTo(5);
    assertThat(slot.getY()).isEqualTo(6);
    slot = layout.getSlot(3);
    assertThat(slot.getX()).isEqualTo(-1);
    assertThat(slot.getY()).isEqualTo(-1);
    slot = layout.getSlot(-1);
    assertThat(slot.getX()).isEqualTo(-1);
    assertThat(slot.getY()).isEqualTo(-1);
  }

  // decoded tested in packet test
}
