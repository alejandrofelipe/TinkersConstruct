package slimeknights.tconstruct.library.tools.capability;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/** Capability to allow an entity to store modifiers, used on projectiles fired from modifiable items */
public class EntityModifierCapability {
  /** Default instance to use with orElse */
  public static final EntityModifiers EMPTY = new EntityModifiers() {
    @Override
    public ModifierNBT getModifiers() {
      return ModifierNBT.EMPTY;
    }

    @Override
    public void setModifiers(ModifierNBT nbt) {}

    @Override
    public void addModifiers(ModifierNBT nbt) {}
  };

  private EntityModifierCapability() {}

  /* Static helpers */

  /** List of predicates to check if the entity supports this capability */
  private static final List<Predicate<Entity>> ENTITY_PREDICATES = new ArrayList<>();

  /** Capability ID */
  private static final ResourceLocation ID = TConstruct.getResource("modifiers");
  /**
   * Capability instance. Under NeoForge this is an {@link EntityCapability} registered for supported entity types on
   * {@link RegisterCapabilitiesEvent}.
   * <p>
   * PORT M3: the Forge capability serialized its {@link ModifierNBT} (projectiles persisted modifiers across save/load
   * via {@code ICapabilitySerializable}). NeoForge entity capabilities are stateless, so the per-entity state lives in a
   * {@link WeakHashMap} below and is no longer serialized. For full parity, register a
   * {@code net.neoforged.neoforge.attachment.AttachmentType<ModifierNBT>} on the entity and back {@link #getOrCreate}
   * with it (the AttachmentType registration belongs in the entrypoint).
   */
  public static final EntityCapability<EntityModifiers,Void> CAPABILITY = EntityCapability.createVoid(ID, EntityModifiers.class);

  /** Per-entity modifier storage. See PORT note on {@link #CAPABILITY}. */
  private static final Map<Entity,Provider> PROVIDERS = new WeakHashMap<>();

  /** Gets (creating if needed) the provider for the given entity */
  private static Provider getOrCreate(Entity entity) {
    return PROVIDERS.computeIfAbsent(entity, e -> new Provider());
  }

  /** Gets the capability for the entity or an empty instance if missing */
  public static EntityModifiers getCapability(Entity entity) {
    EntityModifiers modifiers = entity.getCapability(CAPABILITY);
    return modifiers != null ? modifiers : EMPTY;
  }

  /** Gets the data or an empty instance if missing */
  public static ModifierNBT getOrEmpty(Entity entity) {
    return getCapability(entity).getModifiers();
  }

  /** Checks if the given entity supports this capability */
  public static boolean supportCapability(Entity entity) {
    for (Predicate<Entity> entityPredicate : ENTITY_PREDICATES) {
      if (entityPredicate.test(entity)) {
        return true;
      }
    }
    return false;
  }

  /** Registers a predicate of entites that need this capability */
  public static void registerEntityPredicate(Predicate<Entity> predicate) {
    ENTITY_PREDICATES.add(predicate);
  }

  /**
   * Registers the capability for all entity types matching a registered predicate. Wire onto
   * {@link RegisterCapabilitiesEvent} centrally (M3).
   * <p>
   * The Forge code attached based on per-instance predicates; NeoForge registers per entity type, so the predicate is
   * evaluated against the type's sample entity at registration. Since the predicates only inspect the entity type, this
   * preserves behavior; the provider still re-checks via the {@link WeakHashMap} cache.
   */
  public static void register(RegisterCapabilitiesEvent event) {
    for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
      event.registerEntity(CAPABILITY, type, (entity, ctx) -> supportCapability(entity) ? getOrCreate(entity) : null);
    }
  }

  /** Capability provider instance */
  private static class Provider implements EntityModifiers {
    @Getter @Setter
    private ModifierNBT modifiers = ModifierNBT.EMPTY;
  }

  /** Interface for callers to use */
  public interface EntityModifiers {
    /** Gets the stored modifiers */
    ModifierNBT getModifiers();

    /** Sets the stored modifiers */
    void setModifiers(ModifierNBT nbt);

    /** Adds additional modifiers to the stored modifiers */
    default void addModifiers(ModifierNBT nbt) {
      ModifierNBT existing = getModifiers();
      if (existing.isEmpty()) {
        setModifiers(nbt);
      } else {
        setModifiers(ModifierNBT.builder().add(existing).add(nbt).build());
      }
    }
  }
}
