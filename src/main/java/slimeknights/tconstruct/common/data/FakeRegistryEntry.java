package slimeknights.tconstruct.common.data;

import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import slimeknights.tconstruct.common.TinkerEffect;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Handles creating fake registry entries to datagen entries based on other mods.
 *
 * <p>In 1.20/Forge this temporarily unfroze the registry via {@code ForgeRegistry#unfreeze()}. NeoForge exposes
 * {@link MappedRegistry#unfreeze()}, so we unfreeze the target registry and register the dummy value into the vanilla
 * {@link Registry}. Datagen runs in a throwaway process, so leaving the registry unfrozen afterwards is harmless.
 *
 * <p>This only works for registries whose entries can be built without an intrusive holder (block, item, mob effect).
 * Entity references cannot be faked this way (the {@code EntityType} constructor needs an unfrozen intrusive holder map
 * that {@code unfreeze()} does not restore); datagen for absent entities should serialize the entity id directly.
 */
public class FakeRegistryEntry {
  /** Creates a dummy registry entry */
  private static <T> T getOrCreate(Registry<T> registry, ResourceLocation id, Supplier<T> constructor) {
    if (!registry.containsKey(id)) {
      // game registries are frozen by datagen time; NeoForge lets us reopen them to add the placeholder
      if (registry instanceof MappedRegistry<?> mapped) {
        mapped.unfreeze();
      }
      return Registry.register(registry, id, constructor.get());
    }
    return Objects.requireNonNull(registry.get(id));
  }

  /** Gets or creates a fake block with the given ID */
  public static Block block(ResourceLocation id) {
    return getOrCreate(BuiltInRegistries.BLOCK, id, () -> new Block(BlockBehaviour.Properties.of()));
  }

  /** Gets or creates a fake item with the given ID */
  public static Item item(ResourceLocation id) {
    return getOrCreate(BuiltInRegistries.ITEM, id, () -> new Item(new Item.Properties()));
  }

  /** Gets or creates a fake mob effect with the given ID */
  public static MobEffect effect(ResourceLocation id) {
    return getOrCreate(BuiltInRegistries.MOB_EFFECT, id, () -> new TinkerEffect(MobEffectCategory.NEUTRAL, false));
  }
}
