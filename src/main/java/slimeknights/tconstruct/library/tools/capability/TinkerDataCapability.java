package slimeknights.tconstruct.library.tools.capability;

import lombok.Getter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import slimeknights.mantle.registration.object.IdAwareObject;
import slimeknights.tconstruct.TConstruct;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Capability to make it easy for Tinkers to store common data on the player, primarily used for armor
 * Data stored in this capability is not saved to NBT, most often its filled by the relevant equipment events
 */
public class TinkerDataCapability {
  private TinkerDataCapability() {}

  /** Capability ID */
  private static final ResourceLocation ID = TConstruct.getResource("modifier_data");
  /**
   * Capability instance. Under NeoForge, Forge's attached capability providers and {@code AttachCapabilitiesEvent}
   * no longer exist; this is exposed as an {@link EntityCapability} registered for all living entity types on
   * {@link RegisterCapabilitiesEvent}. The data is not serialized, matching the old behavior.
   */
  public static final EntityCapability<Holder,Void> CAPABILITY = EntityCapability.createVoid(ID, Holder.class);

  /**
   * Per-entity holder storage. NeoForge entity capability providers are stateless, so the mutable per-entity holder
   * lives here. Weak keys let entries be collected with their entities. Not serialized, reset on relog (matches the
   * Forge behavior where this data was never written to NBT).
   */
  private static final Map<LivingEntity,Holder> HOLDERS = new WeakHashMap<>();

  /** Gets (creating if needed) the holder for the given entity */
  private static Holder getOrCreate(LivingEntity entity) {
    return HOLDERS.computeIfAbsent(entity, e -> new Holder());
  }

  /**
   * Registers the data capability for all living entity types. Wire onto {@link RegisterCapabilitiesEvent} centrally
   * (M3). Registers for every entity type whose instances may be living entities.
   */
  public static void register(RegisterCapabilitiesEvent event) {
    for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
      event.registerEntity(CAPABILITY, type, (entity, ctx) -> entity instanceof LivingEntity living ? getOrCreate(living) : null);
    }
  }

  /** Gets the data capability from an entity, or null if missing */
  @Nullable
  public static TinkerDataCapability.Holder getData(LivingEntity entity) {
    return entity.getCapability(CAPABILITY);
  }

  /** Class for generic keys */
  @SuppressWarnings("unused")
  public static class TinkerDataKey<T> implements IdAwareObject<ResourceLocation> {
    /** Name for debug */
    @Getter
    private final ResourceLocation id;

    protected TinkerDataKey(ResourceLocation id) {
      this.id = id;
    }

    /** Creates a new key */
    public static <T> TinkerDataKey<T> of(ResourceLocation id) {
      return new TinkerDataKey<>(id);
    }

    @Override
    public String toString() {
      return "TinkerDataKey{" + id + '}';
    }
  }

  /** Extension key that can automatically create an instance if missing */
  public static class ComputableDataKey<T> extends TinkerDataKey<T> implements Function<TinkerDataKey<?>, T> {
    private final Supplier<T> constructor;
    private ComputableDataKey(ResourceLocation name, Supplier<T> constructor) {
      super(name);
      this.constructor = constructor;
    }

    /** Creates a new instance */
    public static <T> ComputableDataKey<T> of(ResourceLocation name, Supplier<T> constructor) {
      return new ComputableDataKey<>(name, constructor);
    }

    @Override
    public T apply(TinkerDataKey<?> tinkerDataKey) {
      return constructor.get();
    }
  }


  /** Data class holding the tinker data */
  public static class Holder {
    private final Map<TinkerDataKey<?>, Object> data = new IdentityHashMap<>();

    /**
     * Adds a value to the holder
     * @param key    Key to add
     * @param value  Value to add
     * @param <T>    Data type
     */
    public <T> void put(TinkerDataKey<T> key, T value) {
      data.put(key, value);
    }

    /**
     * Adds the given value to the float data key
     * @param key    Key to add
     * @param value  Value to add
     */
    public void add(TinkerDataKey<Float> key, float value) {
      float newValue = get(key, 0f) + value;
      if (newValue == 0) {
        data.remove(key);
      } else {
        data.put(key, newValue);
      }
    }

    /**
     * Removes a value to the holder
     * @param key  Key to remove
     */
    public void remove(TinkerDataKey<?> key) {
      data.remove(key);
    }

    /**
     * Gets a value from the holder, or a default if missing
     * @param key           Holder key
     * @param defaultValue  Value
     * @param <T>           Data type
     * @return  Data or default
     */
    @SuppressWarnings("unchecked")
    public <S, T extends S> S get(TinkerDataKey<T> key, S defaultValue) {
      return (T) data.getOrDefault(key, defaultValue);
    }

    /**
     * Gets a value from the holder, or null if missing
     * @param key           Holder key
     * @param <T>           Data type
     * @return  Data or default
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T get(TinkerDataKey<T> key) {
      return (T) data.get(key);
    }

    /** Gets the value from the holder, creating it if missing */
    @SuppressWarnings("unchecked")
    public <T> T computeIfAbsent(TinkerDataKey<T> key, Function<TinkerDataKey<?>,T> constructor) {
      return (T) data.computeIfAbsent(key, constructor);
    }

    /** Gets the value from the holder, creating it if missing */
    public <T, U extends TinkerDataKey<T> & Function<TinkerDataKey<?>,T>> T computeIfAbsent(U key) {
      return computeIfAbsent(key, key);
    }

    /**
     * Checks if the given key is present
     * @param key  Key to check
     * @return  true if present
     */
    public boolean contains(TinkerDataKey<?> key) {
      return data.containsKey(key);
    }
  }
}
