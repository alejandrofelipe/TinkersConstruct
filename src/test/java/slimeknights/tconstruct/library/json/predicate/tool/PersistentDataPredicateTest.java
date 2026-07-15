package slimeknights.tconstruct.library.json.predicate.tool;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests {@link PersistentDataPredicate}: checks whether a key is present in a tool's persistent mod data. */
class PersistentDataPredicateTest extends BaseMcTest {
  private static final ResourceLocation KEY = ResourceLocation.fromNamespaceAndPath("test", "some_key");

  @Test
  void matches_trueWhenKeyPresent() {
    ModDataNBT data = new ModDataNBT();
    data.putBoolean(KEY, true);
    IToolContext tool = mock(IToolContext.class);
    when(tool.getPersistentData()).thenReturn(data);

    assertThat(new PersistentDataPredicate(KEY).matches(tool)).isTrue();
  }

  @Test
  void matches_falseWhenKeyAbsent() {
    IToolContext tool = mock(IToolContext.class);
    when(tool.getPersistentData()).thenReturn(new ModDataNBT());

    assertThat(new PersistentDataPredicate(KEY).matches(tool)).isFalse();
  }

  @Test
  void loader_serializeThenDeserialize_roundTrips() {
    PersistentDataPredicate original = new PersistentDataPredicate(KEY);
    JsonObject json = new JsonObject();
    PersistentDataPredicate.LOADER.serialize(original, json);
    PersistentDataPredicate roundTripped = PersistentDataPredicate.LOADER.deserialize(json);
    assertThat(roundTripped.key()).isEqualTo(KEY);
  }

  @Test
  void loader_networkRoundTrips() {
    PersistentDataPredicate original = new PersistentDataPredicate(KEY);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    PersistentDataPredicate.LOADER.encode(buffer, original);
    PersistentDataPredicate decoded = PersistentDataPredicate.LOADER.decode(buffer);
    assertThat(decoded.key()).isEqualTo(KEY);
  }
}
