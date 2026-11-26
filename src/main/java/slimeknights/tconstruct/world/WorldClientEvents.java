package slimeknights.tconstruct.world;

import net.minecraft.client.model.object.skull.PiglinHeadModel;
import net.minecraft.client.model.object.skull.SkullModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.common.util.Lazy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.ClientEventBase;
import slimeknights.tconstruct.library.client.particle.SlimeParticle;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.shared.block.SlimeType;
import slimeknights.tconstruct.tools.client.SlimeskullArmorModel;
import slimeknights.tconstruct.tools.data.material.MaterialIds;
import slimeknights.tconstruct.world.block.FoliageType;
import slimeknights.tconstruct.world.client.DragonSkullModel;
import slimeknights.tconstruct.world.client.SkullModelHelper;
import slimeknights.tconstruct.world.client.SlimeColorReloadListener;
import slimeknights.tconstruct.world.client.SlimeColorizer;
import slimeknights.tconstruct.world.client.TinkerHeadSpecialModel;
import slimeknights.tconstruct.world.client.TerracubeRenderer;
import slimeknights.tconstruct.world.client.TinkerSlimeRenderer;

import org.jspecify.annotations.Nullable;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

@SuppressWarnings("unused")
@EventBusSubscriber(modid=TConstruct.MOD_ID, value=Dist.CLIENT)
public class WorldClientEvents extends ClientEventBase {
  @SubscribeEvent
  static void addResourceListener(AddClientReloadListenersEvent event) {
    for (FoliageType type : FoliageType.values()) {
      event.addListener(TConstruct.getResource("slime_color_" + type.getSerializedName()), new SlimeColorReloadListener(type));
    }
  }

  @SubscribeEvent
  static void registerParticleFactories(RegisterParticleProvidersEvent event) {
    event.registerSpecial(TinkerWorld.skySlimeParticle.get(), new SlimeParticle.Factory(SlimeType.SKY));
    event.registerSpecial(TinkerWorld.enderSlimeParticle.get(), new SlimeParticle.Factory(SlimeType.ENDER));
    // PORT M3: SlimeParticle.Factory (library) only has a Factory(SlimeType) ctor; needs a Factory(ItemLike) overload for the clay-ball terracube particle.
    event.registerSpecial(TinkerWorld.terracubeParticle.get(), new SlimeParticle.Factory(Items.CLAY_BALL));
  }

  @SubscribeEvent
  static void registerRenderers(EntityRenderersEvent.RegisterLayerDefinitions event) {
    // TODO: do we really need a separate copy of each head for each mob, or can we reuse them?
    Supplier<LayerDefinition> normalHead = Lazy.of(SkullModel::createMobHeadLayer);
    Supplier<LayerDefinition> customHead = Lazy.of(() -> SkullModelHelper.createHeadLayer(0, 0, 32, 16));
    Supplier<LayerDefinition> headOverlayCustom = Lazy.of(() -> SkullModelHelper.createHeadHatLayer(0, 16, 32, 32));
    registerLayerDefinition(event, TinkerHeadType.BLAZE, normalHead);
    registerLayerDefinition(event, TinkerHeadType.ENDERMAN, customHead);
    registerLayerDefinition(event, TinkerHeadType.STRAY, headOverlayCustom);

    // zombie
    registerLayerDefinition(event, TinkerHeadType.HUSK, Lazy.of(() -> SkullModelHelper.createHeadLayer(0, 0, 64, 64)));
    registerLayerDefinition(event, TinkerHeadType.DROWNED, headOverlayCustom);

    // spiders
    Supplier<LayerDefinition> spiderHead = Lazy.of(() -> SkullModelHelper.createHeadLayer(32, 4, 64, 32));
    registerLayerDefinition(event, TinkerHeadType.SPIDER, spiderHead);
    registerLayerDefinition(event, TinkerHeadType.CAVE_SPIDER, spiderHead);

    // piglin
    Supplier<LayerDefinition> piglinHead = Lazy.of(() -> LayerDefinition.create(PiglinHeadModel.createHeadModel(), 64, 64));
    registerLayerDefinition(event, TinkerHeadType.PIGLIN_BRUTE, piglinHead);
    registerLayerDefinition(event, TinkerHeadType.ZOMBIFIED_PIGLIN, piglinHead);

    // crafted
    registerLayerDefinition(event, TinkerHeadType.VENOMBONE, customHead);
    registerLayerDefinition(event, TinkerHeadType.BLAZING_BONE, customHead);
    registerLayerDefinition(event, TinkerHeadType.NECRONIUM, customHead);
    event.registerLayerDefinition(SkullModelHelper.FLUID_CANNON, headOverlayCustom);
  }

  @SubscribeEvent
  static void registerSkullModels(EntityRenderersEvent.CreateSkullModels event) {
    // PORT 1.21.4: CreateSkullModels no longer exposes an EntityModelSet; register the layer location plus a
    // ModelPart->SkullModelBase factory and the event bakes the model itself.
    // 1.21.9: the event also takes the skull texture, which it adds to SkullBlockRenderer.SKIN_BY_TYPE (throws on duplicates)
    SkullModelHelper.HEAD_LAYERS.forEach((type, layer) -> {
      Identifier texture = HEAD_TEXTURES.get(type);
      if (type.isPiglin()) {
        event.registerSkullModel(type, layer, PiglinHeadModel::new, texture);
      } else {
        event.registerSkullModel(type, layer, SkullModel::new, texture);
      }
    });
  }

  @SubscribeEvent
  static void registerSpecialModels(RegisterSpecialModelRendererEvent event) {
    event.register(TConstruct.getResource("head"), TinkerHeadSpecialModel.MAP_CODEC);
  }

  @SubscribeEvent
  static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
    event.registerEntityRenderer(TinkerWorld.skySlimeEntity.get(), TinkerSlimeRenderer.SKY_SLIME_FACTORY);
    event.registerEntityRenderer(TinkerWorld.enderSlimeEntity.get(), TinkerSlimeRenderer.ENDER_SLIME_FACTORY);
    event.registerEntityRenderer(TinkerWorld.terracubeEntity.get(), TerracubeRenderer::new);
  }

  @SuppressWarnings("removal")
  @SubscribeEvent
  static void clientSetup(FMLClientSetupEvent event) {
    // skull textures
    event.enqueueWork(() -> {
      registerHeadModel(TinkerHeadType.BLAZE, MaterialIds.blaze);
      registerHeadModel(TinkerHeadType.ENDERMAN, MaterialIds.enderPearl);
      SlimeskullArmorModel.registerHeadModel(MaterialIds.dragonScale, modelSet -> new DragonSkullModel(modelSet.bakeLayer(ModelLayers.DRAGON_SKULL)), Identifier.parse("textures/entity/enderdragon/dragon.png"));
      SlimeskullArmorModel.registerHeadModel(MaterialIds.glass, ModelLayers.CREEPER_HEAD, Identifier.parse("textures/entity/creeper/creeper.png"));
      // skeleton
      SlimeskullArmorModel.registerHeadModel(MaterialIds.bone, ModelLayers.SKELETON_SKULL, Identifier.parse("textures/entity/skeleton/skeleton.png"));
      SlimeskullArmorModel.registerHeadModel(MaterialIds.necroticBone, ModelLayers.WITHER_SKELETON_SKULL, Identifier.parse("textures/entity/skeleton/wither_skeleton.png"));
      registerHeadModel(TinkerHeadType.STRAY, MaterialIds.ice);
      // zombies
      SlimeskullArmorModel.registerHeadModel(MaterialIds.leather, ModelLayers.ZOMBIE_HEAD, Identifier.parse("textures/entity/zombie/zombie.png"));
      registerHeadModel(TinkerHeadType.HUSK, MaterialIds.iron);
      registerHeadModel(TinkerHeadType.DROWNED, MaterialIds.copper);
      // spider
      registerHeadModel(TinkerHeadType.SPIDER, MaterialIds.string);
      registerHeadModel(TinkerHeadType.CAVE_SPIDER, MaterialIds.darkthread);
      // piglins
      SlimeskullArmorModel.registerPiglinHeadModel(MaterialIds.gold, ModelLayers.PIGLIN_HEAD, Identifier.parse("textures/entity/piglin/piglin.png"));
      registerPiglinHeadModel(TinkerHeadType.PIGLIN_BRUTE, MaterialIds.roseGold);
      registerPiglinHeadModel(TinkerHeadType.ZOMBIFIED_PIGLIN, MaterialIds.pigIron);
      // crafted
      registerHeadModel(TinkerHeadType.VENOMBONE,    MaterialIds.venombone);
      registerHeadModel(TinkerHeadType.BLAZING_BONE, MaterialIds.blazingBone);
      registerHeadModel(TinkerHeadType.NECRONIUM,    MaterialIds.necronium);
      SlimeskullArmorModel.registerHeadModel(MaterialIds.knightmetal, SkullModelHelper.FLUID_CANNON, TConstruct.getResource("textures/entity/skull/fluid_cannon.png"));
    });
  }

  @SubscribeEvent
  static void registerBlockColorHandlers(RegisterColorHandlersEvent.Block event) {
    // slime plants - blocks
    for (FoliageType type : FoliageType.values()) {
      event.register(
        (state, reader, pos, index) -> getSlimeColorByPos(pos, type, null),
        TinkerWorld.vanillaSlimeGrass.get(type), TinkerWorld.earthSlimeGrass.get(type), TinkerWorld.skySlimeGrass.get(type),
        TinkerWorld.enderSlimeGrass.get(type), TinkerWorld.ichorSlimeGrass.get(type));
      event.register(
        (state, reader, pos, index) -> getSlimeColorByPos(pos, type, SlimeColorizer.LOOP_OFFSET),
        TinkerWorld.slimeLeaves.get(type));
      event.register(
        (state, reader, pos, index) -> getSlimeColorByPos(pos, type, null),
        TinkerWorld.slimeFern.get(type), TinkerWorld.slimeTallGrass.get(type), TinkerWorld.pottedSlimeFern.get(type));
    }

    // vines
    event.register(
      (state, reader, pos, index) -> getSlimeColorByPos(pos, FoliageType.SKY, SlimeColorizer.LOOP_OFFSET),
      TinkerWorld.skySlimeVine.get());
    event.register(
      (state, reader, pos, index) -> getSlimeColorByPos(pos, FoliageType.ENDER, SlimeColorizer.LOOP_OFFSET),
      TinkerWorld.enderSlimeVine.get());
  }

  // PORT 1.21.4: RegisterColorHandlersEvent.Item and net.minecraft.client.color.item.ItemColors were removed. The slime
  // grass/plant block-items were tinted to match their block color via an item-color alias; this now belongs in each
  // item model JSON as an ItemTintSource (minecraft:constant / a custom tint source). Dropped the registration here;
  // re-add the per-item tint in the item models during Phase-4 runtime rewiring.

  /**
   * Block colors for a slime type
   * @param pos   Block position
   * @param type  Slime foliage color
   * @param add   Offset position
   * @return  Color for the given position, or the default if position is null
   */
  private static int getSlimeColorByPos(@Nullable BlockPos pos, FoliageType type, @Nullable BlockPos add) {
    if (pos == null) {
      return type.getColor();
    }
    if (add != null) {
      pos = pos.offset(add);
    }

    return SlimeColorizer.getColorForPos(pos, type);
  }

  /** Textures for each Tinkers head, registered with the skull models in {@link #registerSkullModels} */
  private static final Map<TinkerHeadType, Identifier> HEAD_TEXTURES = Util.make(new EnumMap<>(TinkerHeadType.class), map -> {
    map.put(TinkerHeadType.BLAZE, Identifier.parse("textures/entity/blaze.png"));
    map.put(TinkerHeadType.ENDERMAN, TConstruct.getResource("textures/entity/skull/enderman.png"));
    map.put(TinkerHeadType.STRAY, TConstruct.getResource("textures/entity/skull/stray.png"));
    map.put(TinkerHeadType.HUSK, Identifier.parse("textures/entity/zombie/husk.png"));
    map.put(TinkerHeadType.DROWNED, TConstruct.getResource("textures/entity/skull/drowned.png"));
    map.put(TinkerHeadType.SPIDER, Identifier.parse("textures/entity/spider/spider.png"));
    map.put(TinkerHeadType.CAVE_SPIDER, Identifier.parse("textures/entity/spider/cave_spider.png"));
    map.put(TinkerHeadType.PIGLIN_BRUTE, Identifier.parse("textures/entity/piglin/piglin_brute.png"));
    map.put(TinkerHeadType.ZOMBIFIED_PIGLIN, Identifier.parse("textures/entity/piglin/zombified_piglin.png"));
    map.put(TinkerHeadType.VENOMBONE, TConstruct.getResource("textures/entity/skull/venombone.png"));
    map.put(TinkerHeadType.BLAZING_BONE, TConstruct.getResource("textures/entity/skull/blazing_bone.png"));
    map.put(TinkerHeadType.NECRONIUM, TConstruct.getResource("textures/entity/skull/necronium.png"));
  });

  /** Registers a skull with the slimeskull renderer, the entity renderer texture comes from {@link #registerSkullModels} */
  private static void registerHeadModel(TinkerHeadType skull, MaterialId materialId) {
    SlimeskullArmorModel.registerHeadModel(materialId, SkullModelHelper.HEAD_LAYERS.get(skull), HEAD_TEXTURES.get(skull));
  }

  /** Registers a skull with the slimeskull renderer, the entity renderer texture comes from {@link #registerSkullModels} */
  private static void registerPiglinHeadModel(TinkerHeadType skull, MaterialId materialId) {
    SlimeskullArmorModel.registerPiglinHeadModel(materialId, SkullModelHelper.HEAD_LAYERS.get(skull), HEAD_TEXTURES.get(skull));
  }

  /** Register a layer without being under the minecraft domain. TODO: is this needed? */
  private static ModelLayerLocation registerLayer(String name) {
    ModelLayerLocation location = new ModelLayerLocation(TConstruct.getResource(name), "main");
    if (!ModelLayers.ALL_MODELS.add(location)) {
      throw new IllegalStateException("Duplicate registration for " + location);
    } else {
      return location;
    }
  }

  /** Register a head layer definition with forge */
  private static void registerLayerDefinition(EntityRenderersEvent.RegisterLayerDefinitions event, TinkerHeadType head, Supplier<LayerDefinition> supplier) {
    event.registerLayerDefinition(SkullModelHelper.HEAD_LAYERS.get(head), supplier);
  }
}
