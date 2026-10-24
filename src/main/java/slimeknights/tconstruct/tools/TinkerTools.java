package slimeknights.tconstruct.tools;

import net.minecraft.world.item.equipment.ArmorType;

import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.component.BlocksAttacks;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;
import slimeknights.mantle.registration.object.EnumObject;
import slimeknights.mantle.registration.object.ItemObject;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerModule;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.common.config.ConfigurableAction;
import slimeknights.tconstruct.common.data.tags.MaterialTagProvider;
import slimeknights.tconstruct.library.client.data.material.GeneratorPartTextureJsonGenerator;
import slimeknights.tconstruct.library.client.data.material.MaterialPaletteDebugGenerator;
import slimeknights.tconstruct.library.client.data.material.MaterialPartTextureGenerator;
import slimeknights.tconstruct.library.json.loot.AddToolDataFunction;
import slimeknights.tconstruct.library.json.predicate.tool.HasMaterialPredicate;
import slimeknights.tconstruct.library.json.predicate.tool.HasModifierPredicate;
import slimeknights.tconstruct.library.json.predicate.tool.HasStatTypePredicate;
import slimeknights.tconstruct.library.json.predicate.tool.HasToolHookPredicate;
import slimeknights.tconstruct.library.json.predicate.tool.PersistentDataPredicate;
import slimeknights.tconstruct.library.json.predicate.tool.StatInRangePredicate;
import slimeknights.tconstruct.library.json.predicate.tool.StatInSetPredicate;
import slimeknights.tconstruct.library.json.predicate.tool.ToolContextPredicate;
import slimeknights.tconstruct.library.json.predicate.tool.ToolStackItemPredicate;
import slimeknights.tconstruct.library.json.predicate.tool.ToolStackPredicate;
import slimeknights.tconstruct.library.json.predicate.tool.ToolVariableRangePredicate;
import slimeknights.tconstruct.library.json.predicate.tool.VolatileDataPredicate;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.RandomMaterial;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierManager;
import slimeknights.tconstruct.library.modifiers.modules.behavior.EdibleModule;
import slimeknights.tconstruct.library.modifiers.modules.capacity.OverslimeModule;
import slimeknights.tconstruct.library.recipe.ingredient.ToolHookIngredient;
import slimeknights.tconstruct.library.tools.IndestructibleItemEntity;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.tools.capability.BlockItemProviderModifierHook;
import slimeknights.tconstruct.library.tools.capability.ToolCapabilityProvider;
import slimeknights.tconstruct.library.tools.capability.ToolEnergyCapability;
import slimeknights.tconstruct.library.tools.capability.fluid.ToolFluidCapability;
import slimeknights.tconstruct.library.tools.capability.fluid.ToolTankHelper;
import slimeknights.tconstruct.library.tools.capability.inventory.ToolInventoryCapability;
import slimeknights.tconstruct.library.tools.definition.ModifiableArmorMaterial;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.definition.module.ToolHooks;
import slimeknights.tconstruct.library.tools.definition.module.ToolModule;
import slimeknights.tconstruct.library.tools.definition.module.aoe.AreaOfEffectIterator;
import slimeknights.tconstruct.library.tools.definition.module.aoe.BoxAOEIterator;
import slimeknights.tconstruct.library.tools.definition.module.aoe.CircleAOEIterator;
import slimeknights.tconstruct.library.tools.definition.module.aoe.ConditionalAOEIterator;
import slimeknights.tconstruct.library.tools.definition.module.aoe.TreeAOEIterator;
import slimeknights.tconstruct.library.tools.definition.module.aoe.VeiningAOEIterator;
import slimeknights.tconstruct.library.tools.definition.module.build.MultiplyStatsModule;
import slimeknights.tconstruct.library.tools.definition.module.build.SetStatsModule;
import slimeknights.tconstruct.library.tools.definition.module.build.ToolActionsModule;
import slimeknights.tconstruct.library.tools.definition.module.build.ToolSlotsModule;
import slimeknights.tconstruct.library.tools.definition.module.build.ToolTraitsModule;
import slimeknights.tconstruct.library.tools.definition.module.build.VolatileFlagModule;
import slimeknights.tconstruct.library.tools.definition.module.build.VolatileIntModule;
import slimeknights.tconstruct.library.tools.definition.module.display.CustomMaterialName;
import slimeknights.tconstruct.library.tools.definition.module.display.FixedMaterialToolName;
import slimeknights.tconstruct.library.tools.definition.module.display.MaterialToolNameModule;
import slimeknights.tconstruct.library.tools.definition.module.display.SimpleToolName;
import slimeknights.tconstruct.library.tools.definition.module.display.StatTypesToolNameModule;
import slimeknights.tconstruct.library.tools.definition.module.display.UniqueMaterialToolName;
import slimeknights.tconstruct.library.tools.definition.module.interaction.AttackInteraction;
import slimeknights.tconstruct.library.tools.definition.module.interaction.DualOptionInteraction;
import slimeknights.tconstruct.library.tools.definition.module.interaction.PreferenceSetInteraction;
import slimeknights.tconstruct.library.tools.definition.module.interaction.ToggleableSetInteraction;
import slimeknights.tconstruct.library.tools.definition.module.material.DefaultMaterialsModule;
import slimeknights.tconstruct.library.tools.definition.module.material.MaterialRepairModule;
import slimeknights.tconstruct.library.tools.definition.module.material.MaterialStatsModule;
import slimeknights.tconstruct.library.tools.definition.module.material.MaterialTraitsModule;
import slimeknights.tconstruct.library.tools.definition.module.material.PartStatsModule;
import slimeknights.tconstruct.library.tools.definition.module.material.PartsModule;
import slimeknights.tconstruct.library.tools.definition.module.material.StatlessPartRepairModule;
import slimeknights.tconstruct.library.tools.definition.module.material.ToolMaterialHook;
import slimeknights.tconstruct.library.tools.definition.module.mining.IsEffectiveModule;
import slimeknights.tconstruct.library.tools.definition.module.mining.MaxTierModule;
import slimeknights.tconstruct.library.tools.definition.module.mining.MiningSpeedModifierModule;
import slimeknights.tconstruct.library.tools.definition.module.mining.OneClickBreakModule;
import slimeknights.tconstruct.library.tools.definition.module.weapon.CircleWeaponAttack;
import slimeknights.tconstruct.library.tools.definition.module.weapon.ParticleWeaponAttack;
import slimeknights.tconstruct.library.tools.definition.module.weapon.SweepWeaponAttack;
import slimeknights.tconstruct.library.tools.helper.ModifierLootingHandler;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.item.ModifiableArrowItem;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;
import slimeknights.tconstruct.library.tools.item.ModifiableShurikenItem;
import slimeknights.tconstruct.library.tools.item.armor.ModifiableArmorItem;
import slimeknights.tconstruct.library.tools.item.armor.MultilayerArmorItem;
import slimeknights.tconstruct.library.tools.item.ranged.ModifiableBowItem;
import slimeknights.tconstruct.library.tools.item.ranged.ModifiableCrossbowItem;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.library.utils.BlockSideHitListener;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tools.data.ArmorModelProvider;
import slimeknights.tconstruct.tools.data.ModifierIds;
import slimeknights.tconstruct.tools.data.StationSlotLayoutProvider;
import slimeknights.tconstruct.tools.data.ToolDefinitionDataProvider;
import slimeknights.tconstruct.tools.data.ToolItemModelProvider;
import slimeknights.tconstruct.common.data.BaseRecipeProvider;
import slimeknights.tconstruct.tools.data.ToolsRecipeProvider;
import slimeknights.tconstruct.tools.data.client.ModifierModelMapProvider;
import slimeknights.tconstruct.tools.data.material.MaterialDataProvider;
import slimeknights.tconstruct.tools.data.material.MaterialIds;
import slimeknights.tconstruct.tools.data.material.MaterialRecipeProvider;
import slimeknights.tconstruct.tools.data.material.MaterialRenderInfoProvider;
import slimeknights.tconstruct.tools.data.material.MaterialStatsDataProvider;
import slimeknights.tconstruct.tools.data.material.MaterialTraitsDataProvider;
import slimeknights.tconstruct.tools.data.material.TrimMaterialProvider;
import slimeknights.tconstruct.tools.data.sprite.TinkerMaterialSpriteProvider;
import slimeknights.tconstruct.tools.data.sprite.TinkerPartSpriteProvider;
import slimeknights.tconstruct.tools.data.sprite.TinkerTrimMaterialPaletteGenerator;
import slimeknights.tconstruct.tools.entity.CombatFishingHook;
import slimeknights.tconstruct.tools.entity.ModifiableArrow;
import slimeknights.tconstruct.tools.entity.ThrownShuriken;
import slimeknights.tconstruct.tools.entity.ThrownTool;
import slimeknights.tconstruct.tools.item.CrystalshotItem;
import slimeknights.tconstruct.tools.item.CrystalshotItem.CrystalshotEntity;
import slimeknights.tconstruct.tools.item.ModifiableSwordItem;
import slimeknights.tconstruct.tools.item.SlimeskullItem;
import slimeknights.tconstruct.tools.logic.EquipmentChangeWatcher;
import slimeknights.tconstruct.tools.logic.ModifiableArrowDispenserBehavior;
import slimeknights.tconstruct.tools.logic.ModifiableShurikenDispenserBehavior;
import slimeknights.tconstruct.tools.menu.ToolContainerMenu;
import slimeknights.tconstruct.tools.modules.MeltingFluidEffectiveModule;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static slimeknights.tconstruct.TConstruct.getResource;

/**
 * Contains all complete tool items
 */
public final class TinkerTools extends TinkerModule {
  public TinkerTools() {
    SlotType.init();
    BlockSideHitListener.init();
    ModifierLootingHandler.init();
    RandomMaterial.init();
  }

  /** Creative tab for complete tools */
  public static final DeferredHolder<CreativeModeTab,CreativeModeTab> tabTools = CREATIVE_TABS.register(
    "tools", () -> CreativeModeTab.builder().title(TConstruct.makeTranslation("itemGroup", "tools"))
                                  .icon(() -> TinkerTools.pickaxe.get().getRenderTool())
                                  .displayItems(TinkerTools::addTabItems)
                                  .withTabsBefore(TinkerTables.tabTables.getId())
                                  .withSearchBar()
                                  .build());

  /** Loot function type for tool add data */
  public static final DeferredHolder<LootItemFunctionType<?>,LootItemFunctionType<AddToolDataFunction>> lootAddToolData = LOOT_FUNCTIONS.register("add_tool_data", () -> new LootItemFunctionType<>(AddToolDataFunction.CODEC));

  /*
   * Items
   */
  public static final ItemObject<ModifiableItem> pickaxe = ITEMS.register("pickaxe", p -> new ModifiableItem(p, ToolDefinitions.PICKAXE), UNSTACKABLE_PROPS);
  public static final ItemObject<ModifiableItem> sledgeHammer = ITEMS.register("sledge_hammer", p -> new ModifiableItem(p, ToolDefinitions.SLEDGE_HAMMER), UNSTACKABLE_PROPS);
  public static final ItemObject<ModifiableItem> veinHammer = ITEMS.register("vein_hammer", p -> new ModifiableItem(p, ToolDefinitions.VEIN_HAMMER), UNSTACKABLE_PROPS);

  public static final ItemObject<ModifiableItem> mattock = ITEMS.register("mattock", p -> new ModifiableItem(p, ToolDefinitions.MATTOCK), UNSTACKABLE_PROPS);
  public static final ItemObject<ModifiableItem> pickadze = ITEMS.register("pickadze", p -> new ModifiableItem(p, ToolDefinitions.PICKADZE), UNSTACKABLE_PROPS);
  public static final ItemObject<ModifiableItem> excavator = ITEMS.register("excavator", p -> new ModifiableItem(p, ToolDefinitions.EXCAVATOR), UNSTACKABLE_PROPS);

  public static final ItemObject<ModifiableItem> handAxe = ITEMS.register("hand_axe", p -> new ModifiableItem(p, ToolDefinitions.HAND_AXE), UNSTACKABLE_PROPS);
  public static final ItemObject<ModifiableItem> broadAxe = ITEMS.register("broad_axe", p -> new ModifiableItem(p, ToolDefinitions.BROAD_AXE), UNSTACKABLE_PROPS);

  public static final ItemObject<ModifiableItem> kama = ITEMS.register("kama", p -> new ModifiableItem(p, ToolDefinitions.KAMA), UNSTACKABLE_PROPS);
  public static final ItemObject<ModifiableItem> scythe = ITEMS.register("scythe", p -> new ModifiableItem(p, ToolDefinitions.SCYTHE), UNSTACKABLE_PROPS);

  // 1.21.4: ModifiableItem no longer extends the removed TieredItem, so the old durability(-1) hack is gone (it also
  // conflicts with stacksTo now); the dagger is simply stackable to 2 and manages durability via tool NBT
  public static final ItemObject<ModifiableItem> dagger = ITEMS.register("dagger", p -> new ModifiableSwordItem(p, ToolDefinitions.DAGGER, 2), new Item.Properties().stacksTo(2));
  public static final ItemObject<ModifiableItem> sword = ITEMS.register("sword", p -> new ModifiableSwordItem(p, ToolDefinitions.SWORD), UNSTACKABLE_PROPS);
  public static final ItemObject<ModifiableItem> cleaver = ITEMS.register("cleaver", p -> new ModifiableSwordItem(p, ToolDefinitions.CLEAVER), UNSTACKABLE_PROPS);

  public static final ItemObject<ModifiableCrossbowItem> crossbow = ITEMS.register("crossbow", p -> new ModifiableCrossbowItem(p, ToolDefinitions.CROSSBOW), UNSTACKABLE_PROPS);
  public static final ItemObject<ModifiableBowItem> longbow = ITEMS.register("longbow", p -> new ModifiableBowItem(p, ToolDefinitions.LONGBOW, true), UNSTACKABLE_PROPS);
  public static final ItemObject<ModifiableItem> fishingRod = ITEMS.register("fishing_rod", p -> new ModifiableItem(p, ToolDefinitions.FISHING_ROD), UNSTACKABLE_PROPS);
  public static final ItemObject<ModifiableItem> javelin = ITEMS.register("javelin", p -> new ModifiableItem(p, ToolDefinitions.JAVELIN), UNSTACKABLE_PROPS);
  public static final ItemObject<ModifiableArrowItem> arrow = ITEMS.register("arrow", p -> new ModifiableArrowItem(p, ToolDefinitions.ARROW), ITEM_PROPS);
  public static final ItemObject<ModifiableShurikenItem> shuriken = ITEMS.register("shuriken", p -> new ModifiableShurikenItem(p, ToolDefinitions.SHURIKEN), new Item.Properties().stacksTo(16));
  public static final ItemObject<ModifiableShurikenItem> throwingAxe = ITEMS.register("throwing_axe", p -> new ModifiableShurikenItem(p, ToolDefinitions.THROWING_AXE), new Item.Properties().stacksTo(16));

  public static final ItemObject<ModifiableItem> flintAndBrick = ITEMS.register("flint_and_brick", p -> new ModifiableItem(p, ToolDefinitions.FLINT_AND_BRICK), UNSTACKABLE_PROPS);
  public static final ItemObject<ModifiableItem> skyStaff = ITEMS.register("sky_staff", p -> new ModifiableItem(p, ToolDefinitions.SKY_STAFF), UNSTACKABLE_PROPS);
  public static final ItemObject<ModifiableItem> earthStaff = ITEMS.register("earth_staff", p -> new ModifiableItem(p, ToolDefinitions.EARTH_STAFF), UNSTACKABLE_PROPS);
  public static final ItemObject<ModifiableItem> ichorStaff = ITEMS.register("ichor_staff", p -> new ModifiableItem(p, ToolDefinitions.ICHOR_STAFF), UNSTACKABLE_PROPS);
  public static final ItemObject<ModifiableItem> enderStaff = ITEMS.register("ender_staff", p -> new ModifiableItem(p, ToolDefinitions.ENDER_STAFF), UNSTACKABLE_PROPS);

  // ancient
  public static final ItemObject<ModifiableItem> meltingPan = ITEMS.register("melting_pan", p -> new ModifiableItem(p, ToolDefinitions.MELTING_PAN), UNSTACKABLE_PROPS);
  public static final ItemObject<ModifiableCrossbowItem> warPick = ITEMS.register("war_pick", p -> new ModifiableCrossbowItem(p, ToolDefinitions.WAR_PICK), UNSTACKABLE_PROPS);
  public static final ItemObject<ModifiableItem> battlesign = ITEMS.register("battlesign", p -> new ModifiableItem(p, ToolDefinitions.BATTLESIGN), UNSTACKABLE_PROPS);
  public static final ItemObject<ModifiableItem> swasher = ITEMS.register("swasher", p -> new ModifiableItem(p, ToolDefinitions.SWASHER), UNSTACKABLE_PROPS);
  public static final ItemObject<ModifiableItem> minotaurAxe;
  static {
    // conditionally register minotaur axe as it's the easiest way to keep it out of JEI display
    if (ModList.get().isLoaded("twilightforest")) {
      minotaurAxe = ITEMS.register("minotaur_axe", p -> new ModifiableItem(p, ToolDefinitions.MINOTAUR_AXE), UNSTACKABLE_PROPS);
    } else {
      minotaurAxe = new ItemObject<>(DeferredHolder.<Item,ModifiableItem>create(BuiltInRegistries.ITEM.key(), getResource("minotaur_axe")));
    }
  }

  // armor
  public static final EnumObject<ArmorType,ModifiableArmorItem> travelersGear = ITEMS.registerEnum("travelers", ModifiableArmorMaterial.ARMOR_SLOTS, (key, type) -> new MultilayerArmorItem(ArmorDefinitions.TRAVELERS, type, UNSTACKABLE_PROPS.setId(key)));
  public static final EnumObject<ArmorType,ModifiableArmorItem> plateArmor = ITEMS.registerEnum("plate", ModifiableArmorMaterial.ARMOR_SLOTS, (key, type) -> new MultilayerArmorItem(ArmorDefinitions.PLATE, type, UNSTACKABLE_PROPS.setId(key)));
  public static final EnumObject<ArmorType,ModifiableArmorItem> slimesuit = new EnumObject.Builder<ArmorType,ModifiableArmorItem>(ArmorType.class)
    .put(ArmorType.HELMET, ITEMS.register("slime_helmet", p -> new SlimeskullItem(ArmorDefinitions.SLIMESUIT, SlimeskullItem.MODEL_LOCATION, p), UNSTACKABLE_PROPS))
    // TODO 1.21: rename to slime chestplate as we no longer need the migration
    .put(ArmorType.CHESTPLATE, ITEMS.register("slimy_chestplate", p -> new MultilayerArmorItem(ArmorDefinitions.SLIMESUIT, ArmorType.CHESTPLATE, p), UNSTACKABLE_PROPS))
    .putAll(ITEMS.registerEnum("slime", new ArmorType[] {ArmorType.LEGGINGS, ArmorType.BOOTS}, (key, type) -> new MultilayerArmorItem(ArmorDefinitions.SLIMESUIT, type, UNSTACKABLE_PROPS.setId(key))))
    .build();
  public static final ItemObject<MultilayerArmorItem> slimeWings = ITEMS.register("slime_wings", p -> new MultilayerArmorItem(ArmorDefinitions.SLIMESUIT, ArmorType.CHESTPLATE, p, ArmorDefinitions.SLIME_WINGS, TinkerTools.slimeWings.getId()), UNSTACKABLE_PROPS);

  // shields
  public static final ItemObject<ModifiableItem> travelersShield = ITEMS.register("travelers_shield", p -> new ModifiableItem(p, ArmorDefinitions.TRAVELERS_SHIELD), UNSTACKABLE_PROPS);
  public static final ItemObject<ModifiableItem> plateShield = ITEMS.register("plate_shield", p -> new ModifiableItem(p, ArmorDefinitions.PLATE_SHIELD), UNSTACKABLE_PROPS);

  // arrows
  public static final ItemObject<ArrowItem> crystalshotItem = ITEMS.register("crystalshot", CrystalshotItem::new, ITEM_PROPS);

  /* Particles */
  public static final DeferredHolder<ParticleType<?>,SimpleParticleType> hammerAttackParticle = PARTICLE_TYPES.register("hammer_attack", () -> new SimpleParticleType(true));
  public static final DeferredHolder<ParticleType<?>,SimpleParticleType> axeAttackParticle = PARTICLE_TYPES.register("axe_attack", () -> new SimpleParticleType(true));
  public static final DeferredHolder<ParticleType<?>,SimpleParticleType> bonkAttackParticle = PARTICLE_TYPES.register("bonk", () -> new SimpleParticleType(true));

  /* Entities */
  public static final DeferredHolder<EntityType<?>,EntityType<IndestructibleItemEntity>> indestructibleItem = ENTITIES.register("indestructible_item", () ->
    EntityType.Builder.<IndestructibleItemEntity>of(IndestructibleItemEntity::new, MobCategory.MISC)
                      .sized(0.25F, 0.25F)
                      .fireImmune());
  public static final DeferredHolder<EntityType<?>,EntityType<CrystalshotEntity>> crystalshotEntity = ENTITIES.register("crystalshot", () ->
    EntityType.Builder.<CrystalshotEntity>of(CrystalshotEntity::new, MobCategory.MISC)
                      .sized(0.5F, 0.5F)
                      .clientTrackingRange(4)
                      .updateInterval(20));
  public static final DeferredHolder<EntityType<?>,EntityType<CombatFishingHook>> fishingHook = ENTITIES.register("fishing_bobber", () -> EntityType.Builder.<CombatFishingHook>of(CombatFishingHook::new, MobCategory.MISC).noSave().noSummon().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(5));
  public static final DeferredHolder<EntityType<?>,EntityType<ModifiableArrow>> materialArrow = ENTITIES.register("arrow", () -> EntityType.Builder.<ModifiableArrow>of(ModifiableArrow::new, MobCategory.MISC).sized(0.5F, 0.5F).clientTrackingRange(4).updateInterval(20));
  public static final DeferredHolder<EntityType<?>,EntityType<ThrownShuriken>> thrownShuriken = ENTITIES.register("thrown_shuriken", () -> EntityType.Builder.<ThrownShuriken>of(ThrownShuriken::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));
  public static final DeferredHolder<EntityType<?>,EntityType<ThrownTool>> thrownTool = ENTITIES.register("thrown_tool", () -> EntityType.Builder.<ThrownTool>of(ThrownTool::new, MobCategory.MISC).sized(0.5f, 0.5f).clientTrackingRange(4).updateInterval(20));
  static {
    // used for the fishing bobber
    DATA_SERIALIZERS.register("material_variant", () -> MaterialVariantId.DATA_ACCESSOR);
  }


  /* Containers */
  public static final DeferredHolder<MenuType<?>,MenuType<ToolContainerMenu>> toolContainer = MENUS.register("tool_container", ToolContainerMenu::forClient);


  /*
   * Events
   */

  @SubscribeEvent
  void commonSetup(FMLCommonSetupEvent event) {
    EquipmentChangeWatcher.register();
    ToolCapabilityProvider.register(ToolFluidCapability.Provider::new);
    ToolCapabilityProvider.register(ToolInventoryCapability.Provider::new);
    ToolCapabilityProvider.register((stack, tool) -> new ToolEnergyCapability.Provider(tool));
    ToolCapabilityProvider.register((stack, tool) -> new BlockItemProviderModifierHook.Provider(tool));
    for (ConfigurableAction action : Config.COMMON.toolTweaks) {
      event.enqueueWork(action);
    }
    event.enqueueWork(() -> {
      DispenserBlock.registerBehavior(TinkerTools.arrow.get(), ModifiableArrowDispenserBehavior.INSTANCE);
      DispenserBlock.registerBehavior(TinkerTools.shuriken.get(), ModifiableShurikenDispenserBehavior.INSTANCE);
      DispenserBlock.registerBehavior(TinkerTools.throwingAxe.get(), ModifiableShurikenDispenserBehavior.INSTANCE);
      ModifierUtil.registerShieldDisabler(entity -> {
        // 1.21.5: Player#disableShield was removed, shields are disabled through their blocking component
        if (entity instanceof Player player && player.isBlocking() && player.level() instanceof ServerLevel serverLevel) {
          ItemStack shield = player.getUseItem();
          BlocksAttacks blocksAttacks = shield.get(DataComponents.BLOCKS_ATTACKS);
          if (blocksAttacks != null) {
            blocksAttacks.disable(serverLevel, player, ModifierUtil.SHIELD_DISABLE_SECONDS, shield);
          }
        }
      }, EntityType.PLAYER);
    });
    ModifierHooks.init();
    ToolHooks.init();
  }

  /** 1.21.5: trim material ingredients provide their material through a component */
  @SubscribeEvent
  void modifyDefaultComponents(ModifyDefaultComponentsEvent event) {
    TrimMaterialProvider.addIngredientComponents(event);
  }

  @SubscribeEvent
  void registerRecipeSerializers(RegisterEvent event) {
    if (event.getRegistryKey() == Registries.RECIPE_SERIALIZER) {
      // PORT 1.21.1: ItemPredicate.register and CraftingHelper.register are gone. ToolHookIngredient is now
      // registered as an IngredientType in TinkerIngredients; ToolStackItemPredicate is a standalone predicate
      // pending rework onto the new ItemSubPredicate API.

      // register tool stats that are not defined directly in the class; safer than static init registration
      ToolStats.register(OverslimeModule.OVERSLIME_STAT);
      ToolStats.register(ToolTankHelper.CAPACITY_STAT);
      ToolStats.register(ToolEnergyCapability.MAX_STAT);
      ToolStats.register(EdibleModule.HUNGER);
      ToolStats.register(EdibleModule.SATURATION);

      ToolModule.LOADER.register(getResource("empty"), ToolModule.EMPTY.getLoader());
      // tool definition components
      ToolModule.LOADER.register(getResource("base_stats"), SetStatsModule.LOADER);
      ToolModule.LOADER.register(getResource("multiply_stats"), MultiplyStatsModule.LOADER);
      ToolModule.LOADER.register(getResource("tool_actions"), ToolActionsModule.LOADER);
      ToolModule.LOADER.register(getResource("traits"), ToolTraitsModule.LOADER);
      ToolModule.LOADER.register(getResource("modifier_slots"), ToolSlotsModule.LOADER);
      ToolModule.LOADER.register(getResource("volatile_flag"), VolatileFlagModule.LOADER);
      ToolModule.LOADER.register(getResource("volatile_int"), VolatileIntModule.LOADER);
      // harvest
      ToolModule.LOADER.register(getResource("is_effective"), IsEffectiveModule.LOADER);
      ToolModule.LOADER.register(getResource("mining_speed_modifier"), MiningSpeedModifierModule.LOADER);
      ToolModule.LOADER.register(getResource("max_tier"), MaxTierModule.LOADER);
      ToolModule.LOADER.register(getResource("one_click_break"), OneClickBreakModule.LOADER);
      // material
      ToolModule.LOADER.register(getResource("material_stats"), MaterialStatsModule.LOADER);
      ToolModule.LOADER.register(getResource("part_stats"), PartStatsModule.LOADER);
      ToolModule.LOADER.register(getResource("material_traits"), MaterialTraitsModule.LOADER);
      ToolModule.LOADER.register(getResource("tool_parts"), PartsModule.LOADER);
      ToolModule.LOADER.register(getResource("material_repair"), MaterialRepairModule.LOADER);
      ToolModule.LOADER.register(getResource("default_materials"), DefaultMaterialsModule.LOADER);
      ToolModule.LOADER.register(getResource("statless_part_repair"), StatlessPartRepairModule.LOADER);
      // aoe
      AreaOfEffectIterator.LOADER.register(getResource("empty"), AreaOfEffectIterator.EMPTY.getLoader());
      AreaOfEffectIterator.register(getResource("box_aoe"), BoxAOEIterator.LOADER);
      AreaOfEffectIterator.register(getResource("circle_aoe"), CircleAOEIterator.LOADER);
      AreaOfEffectIterator.register(getResource("tree_aoe"), TreeAOEIterator.LOADER);
      AreaOfEffectIterator.register(getResource("vein_aoe"), VeiningAOEIterator.LOADER);
      AreaOfEffectIterator.register(getResource("conditional_aoe"), ConditionalAOEIterator.LOADER);
      // attack
      ToolModule.LOADER.register(getResource("sweep_melee"), SweepWeaponAttack.LOADER);
      ToolModule.LOADER.register(getResource("circle_melee"), CircleWeaponAttack.LOADER);
      ToolModule.LOADER.register(getResource("melee_particle"), ParticleWeaponAttack.LOADER);
      // generic tool modules
      ToolModule.LOADER.register(getResource("attack_interaction"), AttackInteraction.LOADER);
      ToolModule.LOADER.register(getResource("dual_option_interaction"), DualOptionInteraction.LOADER);
      ToolModule.LOADER.register(getResource("preference_set_interaction"), PreferenceSetInteraction.LOADER);
      ToolModule.LOADER.register(getResource("toggleable_set_interaction"), ToggleableSetInteraction.LOADER);
      // special tool modules
      ToolModule.LOADER.register(getResource("melting_fluid_effective"), MeltingFluidEffectiveModule.LOADER);
      // display name
      ToolModule.LOADER.register(getResource("item_name"), SimpleToolName.ITEM.getLoader());
      ToolModule.LOADER.register(getResource("material_name"), MaterialToolNameModule.LOADER);
      ToolModule.LOADER.register(getResource("stat_types_name"), StatTypesToolNameModule.LOADER);
      ToolModule.LOADER.register(getResource("fixed_material_name"), FixedMaterialToolName.LOADER);
      ToolModule.LOADER.register(getResource("unique_material_name"), UniqueMaterialToolName.LOADER);
      ToolModule.LOADER.register(getResource("custom_material_name"), CustomMaterialName.LOADER);
      // tool predicates
      ToolContextPredicate.LOADER.register(getResource("has_upgrades"), ToolContextPredicate.HAS_UPGRADES.getLoader());
      ToolContextPredicate.LOADER.register(getResource("has_modifier"), HasModifierPredicate.LOADER);
      ToolContextPredicate.LOADER.register(getResource("has_material"), HasMaterialPredicate.LOADER);
      ToolContextPredicate.LOADER.register(getResource("has_stat_type"), HasStatTypePredicate.LOADER);
      ToolContextPredicate.LOADER.register(getResource("has_persistent_key"), PersistentDataPredicate.LOADER);
      ToolContextPredicate.LOADER.register(getResource("has_hook"), HasToolHookPredicate.LOADER);
      ToolStackPredicate.LOADER.register(getResource("not_broken"), ToolStackPredicate.NOT_BROKEN.getLoader());
      ToolStackPredicate.LOADER.register(getResource("stat_in_range"), StatInRangePredicate.LOADER);
      ToolStackPredicate.LOADER.register(getResource("stat_in_set"), StatInSetPredicate.LOADER);
      ToolStackPredicate.LOADER.register(getResource("has_volatile_key"), VolatileDataPredicate.LOADER);
      ToolStackPredicate.LOADER.register(getResource("variable_range"), ToolVariableRangePredicate.LOADER);
    }
  }

  @SubscribeEvent
  void gatherDataServer(final GatherDataEvent.Server event) {
    PackOutput packOutput = event.getGenerator().getPackOutput();
    event.addProvider(new BaseRecipeProvider.Runner(packOutput, event.getLookupProvider(), "Tinkers' Construct Tool Recipes", ToolsRecipeProvider::new));
    event.addProvider(new BaseRecipeProvider.Runner(packOutput, event.getLookupProvider(), "Tinkers' Construct Material Recipes", MaterialRecipeProvider::new));
    MaterialDataProvider materials = new MaterialDataProvider(packOutput);
    event.addProvider(materials);
    event.addProvider(new MaterialStatsDataProvider(packOutput, materials));
    event.addProvider(new MaterialTraitsDataProvider(packOutput, materials));
    event.addProvider(new ToolDefinitionDataProvider(packOutput));
    event.addProvider(new StationSlotLayoutProvider(packOutput));
    event.addProvider(new MaterialTagProvider(packOutput));
  }

  @SubscribeEvent
  void gatherDataClient(final GatherDataEvent.Client event) {
    PackOutput packOutput = event.getGenerator().getPackOutput();
    ResourceManager resourceManager = event.getResourceManager(PackType.CLIENT_RESOURCES);
    event.addProvider(new ToolItemModelProvider(packOutput, resourceManager));
    TinkerMaterialSpriteProvider materialSprites = new TinkerMaterialSpriteProvider();
    TinkerPartSpriteProvider partSprites = new TinkerPartSpriteProvider();
    event.addProvider(new MaterialRenderInfoProvider(packOutput, materialSprites, resourceManager));
    event.addProvider(new GeneratorPartTextureJsonGenerator(packOutput, TConstruct.MOD_ID, partSprites));
    event.addProvider(new MaterialPartTextureGenerator(packOutput, resourceManager, partSprites, materialSprites));
    event.addProvider(new MaterialPaletteDebugGenerator(packOutput, TConstruct.MOD_ID, materialSprites));
    event.addProvider(new ArmorModelProvider(packOutput));
    event.addProvider(new TinkerTrimMaterialPaletteGenerator(packOutput, resourceManager, materialSprites));
    event.addProvider(new ModifierModelMapProvider(packOutput));
  }

  /** Adds all relevant items to the creative tab */
  private static void addTabItems(ItemDisplayParameters itemDisplayParameters, CreativeModeTab.Output tab) {
    // start with tools that lack materials
    Consumer<ItemStack> output = tab::accept;
    acceptTool(output, flintAndBrick);
    acceptTool(output, skyStaff);
    acceptTool(output, earthStaff);
    acceptTool(output, ichorStaff);
    acceptTool(output, enderStaff);

    // small tools
    acceptTool(output, pickaxe);
    acceptTool(output, pickadze);
    acceptTool(output, mattock);
    acceptTool(output, handAxe);
    acceptTool(output, kama);
    acceptTool(output, dagger);
    acceptTool(output, sword);

    // broad tools
    acceptTool(output, sledgeHammer);
    acceptTool(output, veinHammer);
    acceptTool(output, excavator);
    acceptTool(output, broadAxe);
    acceptTool(output, scythe);
    acceptTool(output, cleaver);

    // ranged tools
    acceptTool(output, crossbow);
    acceptTool(output, longbow);
    acceptTool(output, fishingRod);
    acceptTool(output, javelin);
    acceptTool(output, arrow);
    acceptTool(output, shuriken);
    acceptEFLN(shuriken.get(), tab);
    acceptTool(output, throwingAxe);

    // ancient tools
    acceptTool(output, meltingPan);
    acceptTool(output, warPick);
    acceptTool(output, battlesign);
    acceptTool(output, swasher);
    if (ModList.get().isLoaded("twilightforest")) {
      acceptTool(output, minotaurAxe);
    }

    // armor
    acceptTools(output, travelersGear);
    acceptTool(output, travelersShield);
    acceptTools(output, plateArmor);
    acceptTool(output, plateShield);
    acceptTools(output, slimesuit);
    acceptTool(output, slimeWings);
  }

  /** Adds a tool to the tab */
  private static void acceptTool(Consumer<ItemStack> output, Supplier<? extends IModifiable> tool) {
    ToolBuildHandler.addVariants(output, tool.get(), "");
  }

  /** Adds a tool to the tab */
  private static void acceptTools(Consumer<ItemStack> output, EnumObject<?,? extends IModifiable> tools) {
    tools.forEach(tool -> ToolBuildHandler.addVariants(output, tool, ""));
  }

  /**
   * Creates a EFLN using the given shuriken style item.
   * @param item  Item to add
   * @param tab   Creative tab to fill
   */
  private static void acceptEFLN(IModifiable item, CreativeModeTab.Output tab) {
    ToolDefinition definition = item.getToolDefinition();
    if (ToolMaterialHook.stats(definition).size() == 2) {
      IMaterial gunpowder = MaterialRegistry.getMaterial(MaterialIds.gunpowder);
      IMaterial prismarine = MaterialRegistry.getMaterial(MaterialIds.prismarine);
      if (gunpowder != IMaterial.UNKNOWN && prismarine != IMaterial.UNKNOWN) {
        ToolStack efln = ToolStack.createTool(item.asItem(), definition, MaterialNBT.of(gunpowder, prismarine));
        if (ModifierManager.INSTANCE.contains(ModifierIds.redirected)) {
          efln.addModifier(ModifierIds.redirected, 1);
        }
        ItemStack stack = efln.createStack();
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, TConstruct.makeTranslation("item", "efln_ball"));
        tab.accept(stack);
      }
    }
  }
}
