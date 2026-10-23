package slimeknights.tconstruct.common.registration;

import lombok.Getter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredHolder;
import slimeknights.mantle.registration.deferred.BlockDeferredRegister;
import slimeknights.mantle.registration.object.ItemObject;
import slimeknights.tconstruct.world.block.BuddingCrystalBlock;
import slimeknights.tconstruct.world.block.CrystalBlock;
import slimeknights.tconstruct.world.block.CrystalClusterBlock;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/** Item object for geode related blocks. Main methods represent the block */
public class GeodeItemObject extends ItemObject<Item> {
  private final Supplier<? extends Block> block;
  private final Supplier<? extends Block> budding;
  private final Supplier<? extends Block> cluster;
  private final Supplier<? extends Block> smallBud;
  private final Supplier<? extends Block> mediumBud;
  private final Supplier<? extends Block> largeBud;
  public GeodeItemObject(DeferredHolder<Item,? extends Item> shard, BlockDeferredRegister register, MapColor color, SoundType blockSound, SoundEvent chimeSound, Map<BudSize,SoundType> clusterSounds, int baseLight, Properties props) {
    super(shard);
    // allow the crystals to glow optionally
    IntFunction<ToIntFunction<BlockState>> light = extra -> {
      int calculated = Math.min(extra + baseLight, 15);
      return state -> calculated;
    };
    String name = shard.getId().getPath();
    // 1.21.4: block items own their Properties, so set the id from the block's registry name before construction
    Function<Block, ? extends BlockItem> blockItem = block -> new BlockItem(block, props.setId(ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block))));
    ToIntFunction<BlockState> crystalLight = light.apply(0);
    block = register.register(name + "_block", p -> new CrystalBlock(chimeSound, p), BlockBehaviour.Properties.of().mapColor(color).lightLevel(crystalLight).strength(1.5F).sound(blockSound).requiresCorrectToolForDrops(), blockItem);
    budding = register.register("budding_" + name, p -> new BuddingCrystalBlock(this, chimeSound, p), BlockBehaviour.Properties.of().mapColor(color).randomTicks().lightLevel(crystalLight).strength(1.5F).sound(blockSound).requiresCorrectToolForDrops().pushReaction(PushReaction.DESTROY), blockItem);
    // buds
    Supplier<BlockBehaviour.Properties> budProps = () -> BlockBehaviour.Properties.of().mapColor(color).forceSolidOn().noOcclusion().randomTicks().strength(1.5F).requiresCorrectToolForDrops().pushReaction(PushReaction.DESTROY);
    cluster   = register.register(name + "_cluster", p -> new CrystalClusterBlock(chimeSound, 7, 3, p), budProps.get().lightLevel(light.apply(5)).sound(clusterSounds.get(BudSize.CLUSTER)), blockItem);
    smallBud  = register.register("small_" + name + "_bud",  p -> new CrystalClusterBlock(chimeSound, 3, 3, p), budProps.get().lightLevel(light.apply(1)).sound(clusterSounds.get(BudSize.SMALL)),  blockItem);
    mediumBud = register.register("medium_" + name + "_bud", p -> new CrystalClusterBlock(chimeSound, 4, 3, p), budProps.get().lightLevel(light.apply(2)).sound(clusterSounds.get(BudSize.MEDIUM)), blockItem);
    largeBud  = register.register("large_" + name + "_bud",  p -> new CrystalClusterBlock(chimeSound, 5, 3, p), budProps.get().lightLevel(light.apply(4)).sound(clusterSounds.get(BudSize.LARGE)),  blockItem);
  }

  /** Gets the block form of this */
  public Block getBlock() {
    return block.get();
  }

  /** Gets the budding form of the crystal */
  public Block getBudding() {
    return budding.get();
  }

  /** Gets a specific size of bud */
  public Block getBud(BudSize size) {
    return switch (size) {
      case SMALL -> smallBud.get();
      case MEDIUM -> mediumBud.get();
      case LARGE -> largeBud.get();
      case CLUSTER -> cluster.get();
    };
  }

  /** Variants for the bud */
  public enum BudSize {
    SMALL,
    MEDIUM,
    LARGE,
    CLUSTER;

    public static final BudSize[] SIZES = {SMALL, MEDIUM, LARGE};

    @Getter
    private final String name = name().toLowerCase(Locale.ROOT);
    @Getter
    private final int size = ordinal() + 1;

    /** Gets the next bud size */
    public BudSize getNext() {
      return switch (this) {
        case SMALL -> MEDIUM;
        case MEDIUM -> LARGE;
        case LARGE -> CLUSTER;
        default -> SMALL;
      };
    }
  }
}
