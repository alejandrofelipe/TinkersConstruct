package slimeknights.tconstruct.fluids.data;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import net.minecraft.data.PackOutput.Target;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import slimeknights.mantle.data.GenericDataProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Generates the {@code items/<id>.json} definitions for fluid-container items (buckets and the copper can).
 * <p>
 * PORT 1.21.4: the removed {@code tconstruct:fluid_container} block-model loader is replaced by the
 * {@link slimeknights.tconstruct.library.client.model.FluidContainerModel.Unbaked} item model referenced from a
 * {@link net.minecraft.client.renderer.item.ClientItem}. Buckets bake the contained fluid statically; the copper can
 * resolves its fluid from the stack. The item's {@code minecraft:item_model} component defaults to the registry id,
 * matching the emitted {@code items/<id>.json}.
 */
public class FluidBucketModelProvider extends GenericDataProvider {
  private final String modId;
  public FluidBucketModelProvider(PackOutput packOutput, String modId) {
    super(packOutput, Target.RESOURCE_PACK, "items");
    this.modId = modId;
  }

  /** Wraps a {@code tconstruct:fluid_container} item model in a ClientItem definition */
  private static JsonObject itemDefinition(JsonObject model) {
    JsonObject def = new JsonObject();
    def.add("model", model);
    return def;
  }

  /** Makes the ClientItem JSON for a given bucket, baking in its fluid */
  @SuppressWarnings("deprecation")  // best way to get keys
  private static JsonObject makeBucket(BucketItem bucket) {
    JsonObject model = new JsonObject();
    model.addProperty("type", "tconstruct:fluid_container");
    // bucket_drip supplies the display transforms and the base + mask textures used by neoforge buckets
    model.addProperty("transform_model", "neoforge:item/bucket_drip");
    model.addProperty("base", "minecraft:item/bucket");
    model.addProperty("fluid", "neoforge:item/mask/bucket_fluid_drip");
    // bake the fluid into the model rather than reading from the stack
    JsonObject fluid = new JsonObject();
    fluid.addProperty("id", BuiltInRegistries.FLUID.getKey(bucket.content).toString());
    fluid.addProperty("amount", 1000);
    model.add("contained_fluid", fluid);
    model.addProperty("flip_gas", bucket.content.getFluidType().isLighterThanAir());
    return itemDefinition(model);
  }

  /** Makes the ClientItem JSON for the copper can, resolving its fluid dynamically from the stack */
  private static JsonObject makeCopperCan() {
    JsonObject model = new JsonObject();
    model.addProperty("type", "tconstruct:fluid_container");
    model.addProperty("transform_model", "neoforge:item/default");
    model.addProperty("base", "tconstruct:item/copper_can");
    model.addProperty("fluid", "tconstruct:item/copper_can_fluid");
    return itemDefinition(model);
  }

  @SuppressWarnings("deprecation")  // easiest item set lookup
  @Override
  public CompletableFuture<?> run(CachedOutput cache) {
    List<CompletableFuture<?>> tasks = BuiltInRegistries.ITEM.entrySet().stream()
      .filter(entry -> entry.getKey().location().getNamespace().equals(modId) && entry.getValue() instanceof BucketItem)
      .map(entry -> saveJson(cache, entry.getKey().location(), makeBucket((BucketItem)entry.getValue())))
      .collect(Collectors.toCollection(ArrayList::new));
    tasks.add(saveJson(cache, ResourceLocation.fromNamespaceAndPath(modId, "copper_can"), makeCopperCan()));
    return allOf(tasks);
  }

  @Override
  public String getName() {
    return modId + " Fluid Bucket Model Provider";
  }
}
