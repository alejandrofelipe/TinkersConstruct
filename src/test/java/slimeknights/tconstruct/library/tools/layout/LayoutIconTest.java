package slimeknights.tconstruct.library.tools.layout;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.recipe.partbuilder.Pattern;
import slimeknights.tconstruct.library.tools.layout.LayoutIcon.ItemStackIcon;
import slimeknights.tconstruct.library.tools.layout.LayoutIcon.PatternIcon;
import slimeknights.tconstruct.test.BaseMcTest;
import slimeknights.tconstruct.test.TestHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LayoutIconTest extends BaseMcTest {
  /* Empty */

  @Test
  void empty_getValue_isEmpty() {
    assertThat(LayoutIcon.EMPTY.getValue(ItemStack.class)).isNull();
    assertThat(LayoutIcon.EMPTY.getValue(Pattern.class)).isNull();
  }

  @Test
  void empty_bufferReadWrite() {
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    LayoutIcon.EMPTY.write(buffer);

    LayoutIcon decoded = LayoutIcon.read(buffer);
    assertThat(decoded).isSameAs(LayoutIcon.EMPTY);
  }

  @Test
  void empty_jsonSerialize() {
    JsonObject json = LayoutIcon.EMPTY.toJson();
    assertThat(json.entrySet()).isEmpty();
  }

  @Test
  void empty_jsonDeserialize() {
    JsonObject json = new JsonObject();
    LayoutIcon parsed = LayoutIcon.SERIALIZER.deserialize(json, LayoutIcon.class, mock(JsonDeserializationContext.class));
    assertThat(parsed).isSameAs(LayoutIcon.EMPTY);
  }


  /* Item */

  @Test
  void item_getValue_hasSameItem() {
    ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
    LayoutIcon itemIcon = LayoutIcon.ofItem(stack);
    ItemStack contained = itemIcon.getValue(ItemStack.class);
    assertThat(contained).isNotNull();
    assertThat(contained).isSameAs(stack);
    assertThat(itemIcon.getValue(Pattern.class)).isNull();
  }

  @Test
  @Disabled("1.21 port: LayoutIcon.ItemStackIcon.write/read run through ItemStack.OPTIONAL_STREAM_CODEC, which "
    + "NeoForge gates behind RegistryManager.isNonSyncedBuiltInRegistry(registry) - it throws IllegalStateException "
    + "(\"Cannot use ID syncing for non-synced built-in registry\") unless the item registry was tracked through a "
    + "real FML registration/sync lifecycle (RegisterEvent, RegistryManager.postNewRegistryEvent/takeVanillaSnapshot), "
    + "which never runs in a bare JUnit test. Needs either a fuller FML/NeoForge test harness bootstrap, or "
    + "reflectively priming RegistryManager's private vanillaRegistryKeys/snapshot state - unlike BaseMcTest's "
    + "LoadingModList guard, RegistryManager has no public seam to prime from test code.")
  void item_bufferReadWrite() {
    ItemStack original = new ItemStack(Items.DIAMOND_PICKAXE);
    LayoutIcon itemIcon = LayoutIcon.ofItem(original);
    // PORT M6: ItemStack.OPTIONAL_STREAM_CODEC needs a registry-aware buffer (item registry lookup). Unlike
    // UpdateTinkerSlotLayoutsPacketTest's Pattern-only round trip, this one actually resolves an Item, and
    // RegistryAccess.EMPTY has NO registries at all (verified: throws "Missing registry: minecraft:item") -
    // fromRegistryOfRegistries(BuiltInRegistries.REGISTRY) wires the built-in registries (Item included,
    // already populated by BaseMcTest's bootstrap) into a real RegistryAccess.
    RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), ConnectionType.OTHER);
    itemIcon.write(buffer);

    LayoutIcon decoded = LayoutIcon.read(buffer);
    assertThat(decoded).isInstanceOf(ItemStackIcon.class);
    ItemStack stack = decoded.getValue(ItemStack.class);
    assertThat(stack).isNotNull();
    assertThat(ItemStack.matches(original, stack)).isTrue();
  }

  @Test
  void item_jsonSerialize() {
    // PORT M6: main's LayoutIcon now serializes via ItemStack.CODEC (id/count/components) instead of the
    // old item/nbt pair (confirmed against the real datagen output in station_layouts/pickaxe.json). A
    // fresh stack has no custom data component, so there's no "components" key to assert on here.
    ItemStack original = new ItemStack(Items.DIAMOND_PICKAXE);
    LayoutIcon itemIcon = LayoutIcon.ofItem(original);
    JsonObject json = itemIcon.toJson();
    assertThat(GsonHelper.getAsString(json, "id")).isEqualTo(BuiltInRegistries.ITEM.getKey(Items.DIAMOND_PICKAXE).toString());
    assertThat(GsonHelper.getAsInt(json, "count")).isEqualTo(1);
  }

  @Test
  void item_jsonDeserialize() {
    // PORT M6: parse via the real ItemStack.CODEC shape (id + components.minecraft:custom_data), matching
    // station_layouts/pickaxe.json's real datagen output - the old item/nbt pair no longer round-trips.
    JsonObject customData = new JsonObject();
    customData.addProperty("test", 1);
    JsonObject components = new JsonObject();
    components.add("minecraft:custom_data", customData);
    JsonObject json = new JsonObject();
    json.addProperty("id", BuiltInRegistries.ITEM.getKey(Items.DIAMOND).toString());
    json.add("components", components);
    LayoutIcon icon = LayoutIcon.SERIALIZER.deserialize(json, LayoutIcon.class, mock(JsonDeserializationContext.class));
    assertThat(icon).isInstanceOf(ItemStackIcon.class);
    ItemStack stack = icon.getValue(ItemStack.class);
    assertThat(stack).isNotNull();
    assertThat(stack.getItem()).isEqualTo(Items.DIAMOND);
    CompoundTag nbt = TestHelper.getTag(stack);
    assertThat(nbt).isNotNull();
    assertThat(nbt.getInt("test")).isEqualTo(1);
  }


  /* Pattern */

  @Test
  void pattern_getValue_hasSamePattern() {
    Pattern pattern = new Pattern("test:the_pattern");
    LayoutIcon itemIcon = LayoutIcon.ofPattern(pattern);
    Pattern contained = itemIcon.getValue(Pattern.class);
    assertThat(contained).isNotNull();
    assertThat(contained).isEqualTo(pattern);
    assertThat(itemIcon.getValue(ItemStack.class)).isNull();
  }

  @Test
  void pattern_bufferReadWrite() {
    Pattern pattern = new Pattern("test:the_pattern");
    LayoutIcon icon = LayoutIcon.ofPattern(pattern);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    icon.write(buffer);

    LayoutIcon decoded = LayoutIcon.read(buffer);
    assertThat(decoded).isInstanceOf(PatternIcon.class);
    Pattern contained = decoded.getValue(Pattern.class);
    assertThat(contained).isNotNull();
    assertThat(contained).isEqualTo(pattern);
  }

  @Test
  void pattern_jsonSerialize() {
    Pattern pattern = new Pattern("test:the_pattern");
    LayoutIcon icon = LayoutIcon.ofPattern(pattern);
    JsonObject json = icon.toJson();
    assertThat(json.entrySet()).hasSize(1);
    assertThat(GsonHelper.getAsString(json, "pattern")).isEqualTo(pattern.toString());
  }

  @Test
  void pattern_jsonDeserialize() {
    JsonObject json = new JsonObject();
    json.addProperty("pattern", "test:json_pattern");
    LayoutIcon parsed = LayoutIcon.SERIALIZER.deserialize(json, LayoutIcon.class, mock(JsonDeserializationContext.class));
    Pattern contained = parsed.getValue(Pattern.class);
    assertThat(contained).isNotNull();
    assertThat(contained).isEqualTo(new Pattern("test:json_pattern"));
  }
}
