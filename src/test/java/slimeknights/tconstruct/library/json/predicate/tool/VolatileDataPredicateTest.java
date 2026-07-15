package slimeknights.tconstruct.library.json.predicate.tool;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests {@link VolatileDataPredicate}: checks whether a key is present in a tool's volatile mod data. */
class VolatileDataPredicateTest extends BaseMcTest {
  private static final ResourceLocation KEY = ResourceLocation.fromNamespaceAndPath("test", "some_key");

  @Test
  void matches_trueWhenKeyPresent() {
    ModDataNBT data = new ModDataNBT();
    data.putBoolean(KEY, true);
    IToolStackView tool = mock(IToolStackView.class);
    when(tool.getVolatileData()).thenReturn(data);

    assertThat(new VolatileDataPredicate(KEY).matches(tool)).isTrue();
  }

  @Test
  void matches_falseWhenKeyAbsent() {
    IToolStackView tool = mock(IToolStackView.class);
    when(tool.getVolatileData()).thenReturn(new ModDataNBT());

    assertThat(new VolatileDataPredicate(KEY).matches(tool)).isFalse();
  }

  @Test
  void loader_serializeThenDeserialize_roundTrips() {
    VolatileDataPredicate original = new VolatileDataPredicate(KEY);
    JsonObject json = new JsonObject();
    VolatileDataPredicate.LOADER.serialize(original, json);
    VolatileDataPredicate roundTripped = VolatileDataPredicate.LOADER.deserialize(json);
    assertThat(roundTripped.key()).isEqualTo(KEY);
  }

  @Test
  void loader_networkRoundTrips() {
    VolatileDataPredicate original = new VolatileDataPredicate(KEY);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    VolatileDataPredicate.LOADER.encode(buffer, original);
    VolatileDataPredicate decoded = VolatileDataPredicate.LOADER.decode(buffer);
    assertThat(decoded.key()).isEqualTo(KEY);
  }
}
