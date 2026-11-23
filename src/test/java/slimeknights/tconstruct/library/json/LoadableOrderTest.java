package slimeknights.tconstruct.library.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.primitive.StringLoadable;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers Mantle's {@code MapLoadable} and {@code SetLoadable}: a map or set read from JSON or the network must keep the
 * read order. They used {@link Map#copyOf} and {@link Set#copyOf}, which iterate in a per-JVM random order, so a value
 * decoded from the network could re-encode its entries in a different order than the server sent them (recipe stream
 * codec mismatch on dedicated servers, e.g. the entity sets of entity melting and severing recipes).
 */
class LoadableOrderTest extends BaseMcTest {
  private static final Loadable<Map<String,Integer>> LOADABLE = StringLoadable.DEFAULT.mapWithValues(IntLoadable.ANY_FULL);
  private static final Loadable<Set<String>> SET_LOADABLE = StringLoadable.DEFAULT.set();

  /** 16 keys in a fixed shuffled order, enough that a hash ordered map practically never matches it */
  private static List<String> keys() {
    List<String> keys = new ArrayList<>();
    for (int i = 0; i < 16; i++) {
      keys.add("key_" + i);
    }
    Collections.shuffle(keys, new Random(42));
    return keys;
  }

  @Test
  void convert_keepsJsonOrder() {
    List<String> keys = keys();
    JsonObject json = new JsonObject();
    for (int i = 0; i < keys.size(); i++) {
      json.addProperty(keys.get(i), i);
    }
    Map<String,Integer> map = LOADABLE.convert(json, "test");
    assertThat(map.keySet()).containsExactlyElementsOf(keys);
    assertThatThrownBy(() -> map.put("extra", 1)).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void decode_keepsNetworkOrder_andReencodesIdentically() {
    List<String> keys = keys();
    Map<String,Integer> original = new LinkedHashMap<>();
    for (int i = 0; i < keys.size(); i++) {
      original.put(keys.get(i), i);
    }
    byte[] first = encode(original);
    Map<String,Integer> decoded = LOADABLE.decode(new FriendlyByteBuf(Unpooled.wrappedBuffer(first)));
    assertThat(decoded.keySet()).containsExactlyElementsOf(keys);
    assertThat(encode(decoded)).isEqualTo(first);
    assertThatThrownBy(() -> decoded.put("extra", 1)).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void set_convert_keepsJsonOrder() {
    List<String> keys = keys();
    JsonArray json = new JsonArray();
    keys.forEach(json::add);
    Set<String> set = SET_LOADABLE.convert(json, "test");
    assertThat(set).containsExactlyElementsOf(keys);
    assertThatThrownBy(() -> set.add("extra")).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void set_decode_keepsNetworkOrder_andReencodesIdentically() {
    List<String> keys = keys();
    byte[] first = encodeSet(new LinkedHashSet<>(keys));
    Set<String> decoded = SET_LOADABLE.decode(new FriendlyByteBuf(Unpooled.wrappedBuffer(first)));
    assertThat(decoded).containsExactlyElementsOf(keys);
    assertThat(encodeSet(decoded)).isEqualTo(first);
  }

  private static byte[] encodeSet(Set<String> set) {
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    try {
      SET_LOADABLE.encode(buffer, set);
      return ByteBufUtil.getBytes(buffer);
    } finally {
      buffer.release();
    }
  }

  private static byte[] encode(Map<String,Integer> map) {
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    try {
      LOADABLE.encode(buffer, map);
      return ByteBufUtil.getBytes(buffer);
    } finally {
      buffer.release();
    }
  }
}
