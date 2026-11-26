package slimeknights.tconstruct.plugin.jsonthings;

import dev.gigaherz.jsonthings.things.IFlexBlock;
import dev.gigaherz.jsonthings.things.serializers.FlexBlockType;
import dev.gigaherz.jsonthings.things.serializers.IBlockSerializer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FlowingFluid;
import net.neoforged.neoforge.common.util.Lazy;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.plugin.jsonthings.block.FlexBurningLiquidBlock;
import slimeknights.tconstruct.plugin.jsonthings.block.FlexMobEffectLiquidBlock;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Collection of custom block types added by Tinkers. Fields of each type in a thingpack block definition:
 * <ul>
 *   <li>{@code tconstruct:burning_liquid}: {@code fluid} (optional, defaults to the block name), {@code burn_time} (seconds
 *       entities inside burn), {@code damage} (fire damage per hit).</li>
 *   <li>{@code tconstruct:mob_effect_liquid}: {@code fluid} (optional, defaults to the block name), {@code effect} (mob effect
 *       ID), {@code level} (effect level, 1 = amplifier 0). Thingpacks written for older versions set the level in
 *       {@code burn_time} (upstream read it from there); {@code burn_time} is only used when {@code level} is absent.</li>
 * </ul>
 */
public class FlexBlockTypes {
  /** Creates the supplier for a fluid in a fluid block */
  private static Supplier<FlowingFluid> fluidSupplier(Identifier name) {
    return Lazy.of(() -> {
      if (Loadables.FLUID.fromKey(name, "fluid") instanceof FlowingFluid flowing) {
        return flowing;
      } else {
        throw new RuntimeException("LiquidBlock requires a flowing fluid");
      }
    });
  }

  /** Initializes the block types */
  public static void init() {
    register("burning_liquid", data -> {
      Identifier fluidField = Loadables.RESOURCE_LOCATION.getOrDefault(data, "fluid", null);
      int burnTime = GsonHelper.getAsInt(data, "burn_time");
      float damage = GsonHelper.getAsFloat(data, "damage");
      return (props, builder) -> {
        final List<Property<?>> _properties = builder.getProperties();
        return new FlexBurningLiquidBlock(props, builder.getPropertyDefaultValues(), fluidSupplier(Objects.requireNonNullElse(fluidField, builder.getRegistryName())), burnTime, damage) {
          @Override
          protected void createBlockStateDefinition(Builder<Block,BlockState> stateBuilder) {
            super.createBlockStateDefinition(stateBuilder);
            _properties.forEach(stateBuilder::add);
          }
        };
      };
    });
    register("mob_effect_liquid", data -> {
      Identifier fluidField = Loadables.RESOURCE_LOCATION.getOrDefault(data, "fluid", null);
      Identifier effectName = Loadables.RESOURCE_LOCATION.getIfPresent(data, "effect");
      // legacy thingpacks set the level in burn_time, which upstream read it from
      int effectLevel = data.has("level") ? GsonHelper.getAsInt(data, "level") : GsonHelper.getAsInt(data, "burn_time");
      return (props, builder) -> {
        final List<Property<?>> _properties = builder.getProperties();
        Lazy<MobEffect> effect = Lazy.of(() -> Loadables.MOB_EFFECT.fromKey(effectName, "effect"));
        return new FlexMobEffectLiquidBlock(props, builder.getPropertyDefaultValues(), fluidSupplier(Objects.requireNonNullElse(fluidField, builder.getRegistryName())), () -> new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect.get()), 5*20, effectLevel - 1)) {
          @Override
          protected void createBlockStateDefinition(Builder<Block,BlockState> stateBuilder) {
            super.createBlockStateDefinition(stateBuilder);
            _properties.forEach(stateBuilder::add);
          }
        };
      };
    });
  }

  /** Local helper to register our stuff */
  private static <T extends Block & IFlexBlock> void register(String name, IBlockSerializer<T> factory) {
    FlexBlockType.register(TConstruct.resourceString(name), factory, FlexBlockType.DefaultTypeProperties.builder().defaultLayer("translucent").defaultSeeThrough(true).defaultIgnitedByLava(false).defaultReplaceable(true));
  }
}
