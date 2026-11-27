package slimeknights.tconstruct.common.json;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.conditions.ICondition;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.config.Config;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ConfigEnabledCondition implements ICondition, LootItemCondition {
  public static final Identifier ID = TConstruct.getResource("config");
  /* Map of config names to condition cache */
  private static final Map<String,ConfigEnabledCondition> PROPS = new HashMap<>();

  /**
   * Codec serving as both the NeoForge data-load condition codec and the loot condition codec. The condition stores only
   * its config name; the matching {@link ConfigEnabledCondition} (with its bound {@link BooleanSupplier}) is resolved from
   * the static {@link #PROPS} registry, mirroring the old JSON {@code prop} field.
   */
  public static final MapCodec<ConfigEnabledCondition> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
    Codec.STRING.comapFlatMap(ConfigEnabledCondition::byName, ConfigEnabledCondition::getConfigName).fieldOf("prop").forGetter(c -> c)
  ).apply(inst, c -> c));

  private final String configName;
  private final BooleanSupplier supplier;

  /** Gets the config name for serialization */
  private String getConfigName() {
    return configName;
  }

  /** Looks up a registered condition by its property name */
  private static DataResult<ConfigEnabledCondition> byName(String prop) {
    ConfigEnabledCondition config = PROPS.get(prop.toLowerCase(Locale.ROOT));
    if (config == null) {
      return DataResult.error(() -> "Invalid property name '" + prop + "'");
    }
    return DataResult.success(config);
  }

  @Override
  public boolean test(IContext context) {
    return supplier.getAsBoolean();
  }

  @Override
  public boolean test(LootContext lootContext) {
    return supplier.getAsBoolean();
  }

  @Override
  public MapCodec<ConfigEnabledCondition> codec() {
    return CODEC;
  }

  /**
   * Adds a condition
   * @param prop     Property name
   * @param supplier Boolean supplier
   * @return Added condition
   */
  private static ConfigEnabledCondition add(String prop, BooleanSupplier supplier) {
    ConfigEnabledCondition conf = new ConfigEnabledCondition(prop, supplier);
    PROPS.put(prop.toLowerCase(Locale.ROOT), conf);
    return conf;
  }

  /**
   * Adds a condition
   * @param prop     Property name
   * @param supplier Config value
   * @return Added condition
   */
  private static ConfigEnabledCondition add(String prop, BooleanValue supplier) {
    return add(prop, supplier::get);
  }

  @Override
  public String toString() {
    return "config_setting_enabled(\"" + this.configName + "\")";
  }

  /* Properties */
  public static final ConfigEnabledCondition SPAWN_WITH_BOOK = add("spawn_with_book", Config.COMMON.shouldSpawnWithTinkersBook);
  public static final ConfigEnabledCondition GRAVEL_TO_FLINT = add("gravel_to_flint", Config.COMMON.addGravelToFlintRecipe);
  public static final ConfigEnabledCondition CHEAPER_NETHERITE_ALLOY = add("cheaper_netherite_alloy", Config.COMMON.cheaperNetheriteAlloy);
  public static final ConfigEnabledCondition WITHER_BONE_DROP = add("wither_bone_drop", Config.COMMON.witherBoneDrop);
  /** @deprecated use datapacks to remove specific recipes */
  @Deprecated(forRemoval = true)
  public static final ConfigEnabledCondition WITHER_BONE_CONVERSION = add("wither_bone_conversion", () -> false);
  public static final ConfigEnabledCondition SLIME_RECIPE_FIX = add("slime_recipe_fix", Config.COMMON.glassRecipeFix);
  public static final ConfigEnabledCondition GLASS_RECIPE_FIX = add("glass_recipe_fix", Config.COMMON.glassRecipeFix);
  public static final ConfigEnabledCondition ALLOW_INGOTLESS_ALLOYS = add("allow_ingotless_alloys", Config.COMMON.allowIngotlessAlloys);
  public static final ConfigEnabledCondition FORCE_INTEGRATION_MATERIALS = add("force_integration_materials", Config.COMMON.forceIntegrationMaterials);
  public static final ConfigEnabledCondition SLIMY_LOOT_CHESTS = add("slimy_loot_chests", Config.COMMON.slimyLootChests);
  public static final ConfigEnabledCondition SYNC_KNOCKBACK_RESISTANCE = add("sync_knockback_resistance", Config.COMMON.syncKnockbackResistance);
  /** 26.1: trades are data, so the old trade weight only switches the {@code tconstruct:ancient_tool} trade on (> 0) or off (0) */
  public static final ConfigEnabledCondition WANDERER_ANCIENT_TOOL = add("wanderer_ancient_tool", () -> Config.COMMON.wandererAncientToolWeight.get() > 0);
}
