package slimeknights.tconstruct.client.uitest;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.RecipeTransferResult;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IRecipesGui;
import com.mojang.blaze3d.platform.NativeImage;
import net.blay09.mods.craftingtweaks.client.GuiTweakButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import slimeknights.mantle.block.entity.IRetexturedBlockEntity;
import slimeknights.mantle.client.model.util.ModelHelper;
import slimeknights.mantle.client.render.RenderTargetCapture;
import slimeknights.mantle.client.screen.MultiModuleScreen;
import slimeknights.mantle.client.screen.book.BookScreen;
import slimeknights.mantle.client.screen.book.element.StructureElement;
import slimeknights.mantle.client.uitest.UiTestContext;
import slimeknights.mantle.client.uitest.UiTestScenario;
import slimeknights.mantle.client.uitest.UiTestScenarios;
import slimeknights.mantle.client.uitest.UiTestSuite;
import slimeknights.mantle.plugin.jei.entity.EntityIngredientRenderer;
import slimeknights.mantle.recipe.ingredient.EntityIngredient;
import slimeknights.mantle.registration.object.EnumObject;
import slimeknights.mantle.util.RetexturedHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.library.client.armor.ArmorModelManager.ArmorModelDispatcher;
import slimeknights.tconstruct.library.client.armor.TinkerArmorLayer;
import slimeknights.tconstruct.library.client.armor.texture.ArmorTextureSupplier.ArmorTexture;
import slimeknights.tconstruct.library.client.armor.texture.ArmorTextureSupplier.TextureType;
import slimeknights.tconstruct.library.client.armor.texture.TintedArmorTexture;
import slimeknights.tconstruct.library.client.armor.texture.TrimArmorTextureSupplier.TrimArmorTexture;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfo;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfoLoader;
import slimeknights.tconstruct.library.client.modifiers.ModifierIconManager;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.ModifierManager;
import slimeknights.tconstruct.library.recipe.FluidValues;
import slimeknights.tconstruct.library.recipe.casting.IDisplayableCastingRecipe;
import slimeknights.tconstruct.library.recipe.melting.MeltingRecipe;
import slimeknights.tconstruct.library.recipe.modifiers.adding.IDisplayModifierRecipe;
import slimeknights.tconstruct.library.recipe.molding.MoldingRecipe;
import slimeknights.tconstruct.library.recipe.partbuilder.Pattern;
import slimeknights.tconstruct.library.recipe.tinkerstation.building.ToolBuildingRecipe;
import slimeknights.tconstruct.library.recipe.worktable.IModifierWorktableRecipe;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.tools.SlotType.SlotCount;
import slimeknights.tconstruct.library.tools.nbt.MaterialIdNBT;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.item.armor.ModifiableArmorItem;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.shared.TinkerCommons;
import slimeknights.tconstruct.gametest.SmelteryRigs;
import slimeknights.tconstruct.plugin.jei.JEIPlugin;
import slimeknights.tconstruct.plugin.jei.TConstructJEIConstants;
import slimeknights.tconstruct.plugin.jei.entity.DefaultEntityMeltingRecipe;
import slimeknights.tconstruct.plugin.jei.modifiers.ModifierBookmarkIngredientRenderer;
import slimeknights.tconstruct.plugin.jei.modifiers.ModifierIngredientRenderer;
import slimeknights.tconstruct.plugin.jei.modifiers.SlotIngredientRenderer;
import slimeknights.tconstruct.plugin.jei.partbuilder.PatternIngredientRenderer;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.ChannelBlock;
import slimeknights.tconstruct.smeltery.block.FaucetBlock;
import slimeknights.tconstruct.smeltery.block.component.SearedTankBlock.TankType;
import slimeknights.tconstruct.smeltery.block.controller.ControllerBlock;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.FaucetBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.AlloyerBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.HeatingStructureBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.MelterBlockEntity;
import slimeknights.tconstruct.smeltery.client.screen.HeatingStructureScreen;
import slimeknights.tconstruct.smeltery.client.screen.IScreenWithFluidTank;
import slimeknights.tconstruct.smeltery.client.screen.MelterScreen;
import slimeknights.tconstruct.smeltery.item.CopperCanItem;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tables.block.entity.table.CraftingStationBlockEntity;
import slimeknights.tconstruct.tables.menu.CraftingStationContainerMenu;
import slimeknights.tconstruct.tables.menu.TinkerStationContainerMenu;
import slimeknights.tconstruct.tables.client.inventory.TinkerStationScreen;
import slimeknights.tconstruct.tables.client.inventory.ToolTableScreen;
import slimeknights.tconstruct.tables.client.inventory.widget.TinkerTabsWidget;
import slimeknights.tconstruct.tools.TinkerModifiers;
import slimeknights.tconstruct.tools.TinkerToolParts;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.client.ToolContainerScreen;
import slimeknights.tconstruct.tools.data.ModifierIds;
import slimeknights.tconstruct.tools.menu.ToolContainerMenu;
import slimeknights.tconstruct.tools.data.material.MaterialIds;
import slimeknights.tconstruct.tools.modules.cosmetic.TrimModule;
import slimeknights.tconstruct.tools.recipe.severing.PlayerBeheadingRecipe;
import slimeknights.tconstruct.world.TinkerHeadType;
import slimeknights.tconstruct.world.TinkerWorld;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Registers TConstruct's automated GUI screenshot scenarios (active only with -Dmantle.uitest=true). */
// NeoForge 21.8 removed EventBusSubscriber.bus(): the bus is inferred per event, FMLClientSetupEvent is a mod-bus event
@EventBusSubscriber(modid = TConstruct.MOD_ID, value = Dist.CLIENT)
public class TinkerUiTestScenarios {
  /** Fixed build site in the committed superflat world, far from spawn interference. */
  private static final BlockPos SITE = new BlockPos(100, -60, 100);

  @SubscribeEvent
  static void clientSetup(FMLClientSetupEvent event) {
    if (!UiTestScenarios.isActive()) {
      return;
    }
    UiTestScenarios.register(new BlockGuiScenario("tinker_station", SITE.offset(0, 0, 0), () -> TinkerTables.tinkerStation.get().defaultBlockState()));
    UiTestScenarios.register(new BlockGuiScenario("part_builder", SITE.offset(4, 0, 0), () -> TinkerTables.partBuilder.get().defaultBlockState()));
    // Crafting Tweaks pilot: opens the crafting station so its CT convenience buttons (rotate/balance/clear) render in the shot.
    UiTestScenarios.register(new BlockGuiScenario("crafting_station", SITE.offset(70, 0, 0), () -> TinkerTables.craftingStation.get().defaultBlockState()));
    UiTestScenarios.register(new SmelteryScenario());
    UiTestScenarios.register(new MelterScenario());
    UiTestScenarios.register(new CastingPourScenario());
    UiTestScenarios.register(new BookScenario());
    UiTestScenarios.register(new JeiCategoryScenario<>("melting", TConstructJEIConstants.MELTING));
    UiTestScenarios.register(new JeiItemListCleanupScenario());
    UiTestScenarios.register(new JeiCategoryTooltipsScenario());
    UiTestScenarios.register(new BookInteriorScenario());
    UiTestScenarios.register(new StationReflowScenario());
    UiTestScenarios.register(new StationCollapsedScenario());
    UiTestScenarios.register(new StationCollapsedOverlayScenario());
    UiTestScenarios.register(new ExtraHeartsScenario());
    UiTestScenarios.register(new DynamicBlockModelsScenario());
    UiTestScenarios.register(new HeadItemsScenario());
    UiTestScenarios.register(new DynamicItemIconsScenario());
    UiTestScenarios.register(new ToolInventoryScenario());
    UiTestScenarios.register(new TrimmedArmorScenario());
    UiTestScenarios.register(new WornArmorScenario());
    UiTestScenarios.register(new ItemAtlasGridScenario());
    // every other Tinkers JEI category (JEIPlugin#registerCategories), melting is registered above
    // 1.21.10 (JEI 26) listed all 1859 table / 1016 basin display recipes; JEI 27 hides the ones only showing hidden fluids
    // (container filling with 30 absent compat or tag-hidden fluids: 120 table, 300 basin; variantless potion: 4 table, 10 basin)
    UiTestScenarios.register(new JeiCategoryScenario<>("casting_table", TConstructJEIConstants.CASTING_TABLE, IDisplayableCastingRecipe::getFluids, 1735));
    UiTestScenarios.register(new JeiCategoryScenario<>("casting_basin", TConstructJEIConstants.CASTING_BASIN, IDisplayableCastingRecipe::getFluids, 706));
    // patternless (pick up) molding recipes have a null pattern, which the category used to NPE on
    UiTestScenarios.register(new JeiCategoryScenario<>("molding", TConstructJEIConstants.MOLDING, "a recipe without pattern", recipe -> recipe.getPattern() == null));
    UiTestScenarios.register(new JeiCategoryScenario<>("alloy", TConstructJEIConstants.ALLOY));
    // the generated default recipe is always there, so require a datapack recipe (synced to the client)
    // entity melting and severing must also lay out every recipe: 1.21.11 broke the ones with an entity without spawn egg
    // (e.g. the player) as JEI 27 checks the entity/egg focus link sizes
    UiTestScenarios.register(new JeiCategoryScenario<>("entity_melting", TConstructJEIConstants.ENTITY_MELTING, "a datapack recipe", recipe -> !(recipe instanceof DefaultEntityMeltingRecipe), true));
    UiTestScenarios.register(new JeiCategoryScenario<>("foundry", TConstructJEIConstants.FOUNDRY));
    UiTestScenarios.register(new JeiCategoryScenario<>("modifiers", TConstructJEIConstants.MODIFIERS));
    UiTestScenarios.register(new JeiCategoryScenario<>("severing", TConstructJEIConstants.SEVERING, "the player beheading recipe", recipe -> recipe instanceof PlayerBeheadingRecipe, true));
    UiTestScenarios.register(new JeiCategoryScenario<>("tool_building", TConstructJEIConstants.TOOL_BUILDING));
    UiTestScenarios.register(new JeiCategoryScenario<>("part_builder", TConstructJEIConstants.PART_BUILDER));
    UiTestScenarios.register(new JeiCategoryScenario<>("worktable", TConstructJEIConstants.MODIFIER_WORKTABLE));
    UiTestScenarios.register(new BookStructureScenario());
    UiTestScenarios.register(new FluidBlocksScenario());
    UiTestScenarios.register(new CastingCoolingScenario());
    UiTestScenarios.register(new AoeOutlineScenario("aoe_outline", SITE.offset(130, 0, 0), false, true));
    UiTestScenarios.register(new AoeOutlineScenario("aoe_outline_high_contrast", SITE.offset(136, 0, 0), true, false));
    UiTestScenarios.register(new StationCarriedClicksScenario());
    UiTestScenarios.register(new JeiTankClickableScenario());
    UiTestScenarios.register(new JeiRecipeTransferScenario());
    UiTestScenarios.register(new JeiTinkerStationTransferScenario());
    UiTestScenarios.register(new JeiToolInventoryTransferScenario());
    UiTestScenarios.register(new JeiCraftingStationSlotScenario());
    UiTestScenarios.register(new JeiSmelterySlotScenario());
    UiTestScenarios.register(new JeiSmelteryTankScenario());
    if (ModList.get().isLoaded("craftingtweaks")) {
      UiTestScenarios.register(new CraftingTweaksRotateScenario());
    }
  }

  /** Places a single block and opens its GUI. */
  private record BlockGuiScenario(String name, BlockPos pos, Supplier<BlockState> state) implements UiTestScenario {
    @Override
    public Identifier id() {
      return TConstruct.getResource(name);
    }

    @Override
    public void prepare(UiTestContext ctx) {
      ctx.sendCommand("tp @s " + (pos.getX() - 2) + " " + pos.getY() + " " + pos.getZ());
      ctx.runOnServer(() -> ctx.serverLevel().setBlockAndUpdate(pos, state.get()));
    }

    @Override
    public int prepareSettleTicks() {
      return 20;
    }

    @Override
    public void open(UiTestContext ctx) {
      ctx.useBlock(pos);
    }
  }

  /**
   * Builds a small smeltery, melts an iron ingot, and opens the controller GUI. Unlike the melter,
   * no IN_STRUCTURE handling is needed here: the controller's block entity structure scan forms the
   * multiblock (updating the block state itself) during the 100-tick prepare settle.
   */
  private static class SmelteryScenario implements UiTestScenario {
    /** volatile: written on the server thread in prepare, read on the client thread in open */
    private volatile BlockPos controller;

    @Override
    public Identifier id() {
      return TConstruct.getResource("smeltery");
    }

    @Override
    public void prepare(UiTestContext ctx) {
      BlockPos origin = SITE.offset(10, 0, 0);
      ctx.sendCommand("tp @s " + (origin.getX() + 1) + " " + origin.getY() + " " + (origin.getZ() + 5));
      ctx.runOnServer(() -> controller = SmelteryRigs.buildSmeltery(ctx.serverLevel(), origin));
    }

    @Override
    public int prepareSettleTicks() {
      return 100; // ticks for the multiblock structure to finish forming
    }

    @Override
    public void open(UiTestContext ctx) {
      // inserted here rather than in prepare(): insertMeltable throws IllegalStateException if the
      // smeltery hasn't formed yet, and prepare() runs long before that; open() fires only after
      // prepareSettleTicks() ticks (well past formation), and this server task is queued ahead of
      // the useBlock() interaction packet below
      ctx.runOnServer(() -> SmelteryRigs.insertMeltable(ctx.serverLevel(), controller, new ItemStack(Items.IRON_INGOT)));
      ctx.useBlock(controller);
    }
  }

  /** Places a melter on a filled fuel tank and opens its GUI. */
  private static class MelterScenario implements UiTestScenario {
    private final BlockPos pos = SITE.offset(20, 1, 0);

    @Override
    public Identifier id() {
      return TConstruct.getResource("melter");
    }

    @Override
    public void prepare(UiTestContext ctx) {
      ctx.sendCommand("tp @s " + (pos.getX() - 2) + " " + (pos.getY() - 1) + " " + pos.getZ());
      ctx.runOnServer(() -> {
        var level = ctx.serverLevel();
        // tank placed after the melter so its neighbor shape update forms the melter (IN_STRUCTURE=true,
        // which gates the GUI) — setBlockAndUpdate never runs getStateForPlacement; see SmelteryGameTests' alloyer note
        level.setBlockAndUpdate(pos, TinkerSmeltery.searedMelter.get().defaultBlockState());
        BlockPos fuel = pos.below();
        level.setBlockAndUpdate(fuel, TinkerSmeltery.searedTank.get(TankType.FUEL_TANK).defaultBlockState());
        SmelteryRigs.fillTank(level, fuel, new FluidStack(Fluids.LAVA, 4000));
      });
    }

    @Override
    public void open(UiTestContext ctx) {
      ctx.useBlock(pos);
    }
  }

  /** No menu: captures the in-world pour (validates FluidRenderer's world path). */
  private static class CastingPourScenario implements UiTestScenario {
    /** volatile: written on the server thread in prepare, read on the client thread in open */
    private volatile SmelteryRigs.CastingRig rig;

    @Override
    public Identifier id() {
      return TConstruct.getResource("casting_pour");
    }

    @Override
    public void prepare(UiTestContext ctx) {
      BlockPos origin = SITE.offset(30, 0, 0);
      ctx.sendCommand("tp @s " + (origin.getX() - 3) + " " + origin.getY() + " " + origin.getZ());
      ctx.runOnServer(() -> rig = SmelteryRigs.buildCastingRig(ctx.serverLevel(), origin));
    }

    @Override
    public void open(UiTestContext ctx) {
      ctx.runOnServer(() -> {
        if (ctx.serverLevel().getBlockEntity(rig.table()) instanceof CastingBlockEntity table) {
          table.setItem(CastingBlockEntity.INPUT, new ItemStack(TinkerSmeltery.ingotCast.get()));
        } else {
          throw new IllegalStateException("Expected casting table at " + rig.table() + ", found " + ctx.serverLevel().getBlockEntity(rig.table()));
        }
      });
      ctx.useBlock(rig.faucet()); // opens the tap — pour starts
      ctx.lookAt(rig.faucet());
    }

    @Override
    public int settleTicks() {
      return 30; // capture mid-pour
    }

    @Override
    public void close(UiTestContext ctx) { /* nothing open */ }
  }

  /** Opens "Materials and You" and captures the rendered page (first real run of Mantle's book system). */
  private static class BookScenario implements UiTestScenario {
    @Override
    public Identifier id() {
      return TConstruct.getResource("book_materials_and_you");
    }

    @Override
    public void prepare(UiTestContext ctx) {
      // hotbar.0 (not /give) so the book lands in a known slot deterministically: /give places into
      // the first free slot, which is only hotbar 0 for a *totally* empty inventory, and the saved
      // uitest world persists across repeated local runs, so a prior run's leftovers could push a
      // /give'd book into a later slot. MAIN_HAND reads inventory.selected, which defaults to 0 and
      // nothing earlier in the suite touches the player's inventory.
      ctx.sendCommand("item replace entity @s hotbar.0 with tconstruct:materials_and_you");
    }

    @Override
    public int prepareSettleTicks() {
      return 20;
    }

    @Override
    public void open(UiTestContext ctx) {
      InteractionResult result = ctx.player().getItemInHand(InteractionHand.MAIN_HAND)
        .getItem().use(ctx.mc().level, ctx.player(), InteractionHand.MAIN_HAND);
      if (result == InteractionResult.PASS) {
        throw new IllegalStateException("book item use() passed - book screen did not open");
      }
    }

    @Override
    public int settleTicks() {
      return 40; // book textures/pages lazy-load
    }
  }

  /** Opens the book and advances past the cover to a two-page spread (guards interior pages against the 1.21 blur pass). */
  private static class BookInteriorScenario implements UiTestScenario {
    @Override
    public Identifier id() {
      return TConstruct.getResource("book_interior");
    }

    @Override
    public void prepare(UiTestContext ctx) {
      // same deterministic hotbar.0 placement as book_materials_and_you (see its comment)
      ctx.sendCommand("item replace entity @s hotbar.0 with tconstruct:materials_and_you");
    }

    @Override
    public int prepareSettleTicks() {
      return 20;
    }

    @Override
    public void open(UiTestContext ctx) {
      InteractionResult result = ctx.player().getItemInHand(InteractionHand.MAIN_HAND)
        .getItem().use(ctx.mc().level, ctx.player(), InteractionHand.MAIN_HAND);
      if (result == InteractionResult.PASS) {
        throw new IllegalStateException("book item use() passed - book screen did not open");
      }
      if (!(ctx.mc().screen instanceof BookScreen book)) {
        throw new IllegalStateException("expected BookScreen after use(), found " + ctx.mc().screen);
      }
      // advance cover -> index -> first full spread; same logic the next-page arrow button runs
      book.nextPage();
      book.nextPage();
    }

    @Override
    public int settleTicks() {
      return 40; // book textures/pages lazy-load
    }
  }

  /** No GUI: asserts the JEI runtime cleanup left no modifier crystal / creative slot item entries. */
  private static class JeiItemListCleanupScenario implements UiTestScenario {
    @Override
    public Identifier id() {
      return TConstruct.getResource("jei_item_list_cleanup");
    }

    @Override
    public void prepare(UiTestContext ctx) { /* nothing to build */ }

    @Override
    public void open(UiTestContext ctx) {
      IJeiRuntime runtime = JEIPlugin.runtime;
      if (runtime == null) {
        throw new IllegalStateException("JEI runtime not captured - onRuntimeAvailable never fired");
      }
      // both items are shown through the modifier ingredient type instead of as item stacks, so the
      // runtime cleanup must leave no entry of either item: filled variants come from the creative
      // tabs, blank ones from JEI's ShowHiddenItems registry pass
      List<ItemStack> leftovers = runtime.getIngredientManager().getAllItemStacks().stream()
        .filter(stack -> stack.is(TinkerModifiers.modifierCrystal.asItem()) || stack.is(TinkerModifiers.creativeSlotItem.asItem()))
        .toList();
      if (!leftovers.isEmpty()) {
        throw new IllegalStateException("JEI item list still contains " + leftovers.size() + " hidden-item entries, first: " + leftovers.get(0));
      }
    }

    @Override
    public void close(UiTestContext ctx) { /* nothing open */ }
  }

  /**
   * No GUI: asks every Tinkers JEI category with hover tooltips for its tooltip at a point inside each tooltip area and at one outside,
   * through {@link IRecipeCategory#getTooltip(ITooltipBuilder, Object, IRecipeSlotsView, double, double)} as JEI's recipe layout does,
   * and checks the exact lines (cooling time, ore, cast kept/consumed, pattern consumed, anvil, incremental/free modifiers, worktable
   * description). Pins the tooltips across the move off JEI's deprecated {@code getTooltipStrings}. Also checks the tooltip of each Tinkers and
   * Mantle ingredient renderer through the overload JEI 27 calls, pinning the move off the deprecated {@code getTooltip(T, TooltipFlag)}.
   */
  private static class JeiCategoryTooltipsScenario implements UiTestScenario {
    @Override
    public Identifier id() {
      return TConstruct.getResource("jei_category_tooltips");
    }

    @Override
    public void prepare(UiTestContext ctx) { /* nothing to build */ }

    @Override
    public void open(UiTestContext ctx) {
      IJeiRuntime runtime = JEIPlugin.runtime;
      if (runtime == null) {
        throw new IllegalStateException("JEI runtime not captured - onRuntimeAvailable never fired");
      }
      IRecipeManager recipes = runtime.getRecipeManager();

      // melting: ore marker, cooling time over the arrow
      MeltingRecipe melting = prefer(recipes, TConstructJEIConstants.MELTING, recipe -> recipe.getOreType() != null);
      check(runtime, TConstructJEIConstants.MELTING, melting, 90, 35, melting.getOreType() != null ? List.of(Component.translatable(jeiKey("melting.ore"))) : List.of());
      check(runtime, TConstructJEIConstants.MELTING, melting, 60, 20, List.of(Component.translatable(jeiKey("melting.time"), melting.getTime() / 4)));
      check(runtime, TConstructJEIConstants.MELTING, melting, 2, 2, List.of());
      // casting: cast kept or consumed
      IDisplayableCastingRecipe cast = prefer(recipes, TConstructJEIConstants.CASTING_TABLE, IDisplayableCastingRecipe::hasCast);
      check(runtime, TConstructJEIConstants.CASTING_TABLE, cast, 65, 42,
            cast.hasCast() ? List.of(Component.translatable(jeiKey(cast.isConsumed() ? "casting.cast_consumed" : "casting.cast_kept"))) : List.of());
      check(runtime, TConstructJEIConstants.CASTING_TABLE, cast, 110, 2, List.of());
      // molding: consumed pattern (Tinkers' own recipes keep their patterns, so this may only check the empty case)
      MoldingRecipe molding = prefer(recipes, TConstructJEIConstants.MOLDING, recipe -> recipe.isPatternConsumed() && recipe.getPattern() != null && !recipe.getPattern().isEmpty());
      check(runtime, TConstructJEIConstants.MOLDING, molding, 55, 10, molding.isPatternConsumed() && molding.getPattern() != null && !molding.getPattern().isEmpty()
                                                                   ? List.of(Component.translatable(jeiKey("molding.pattern_consumed"))) : List.of());
      check(runtime, TConstructJEIConstants.MOLDING, molding, 2, 50, List.of());
      // tool building: anvil requirement
      ToolBuildingRecipe tool = prefer(recipes, TConstructJEIConstants.TOOL_BUILDING, ToolBuildingRecipe::requiresAnvil);
      check(runtime, TConstructJEIConstants.TOOL_BUILDING, tool, 80, 48, tool.requiresAnvil() ? List.of(Component.translatable(jeiKey("tinkering.tool_building.anvil"))) : List.of());
      check(runtime, TConstructJEIConstants.TOOL_BUILDING, tool, 2, 2, List.of());
      // modifiers: incremental marker, free slot marker
      IDisplayModifierRecipe incremental = prefer(recipes, TConstructJEIConstants.MODIFIERS, IDisplayModifierRecipe::isIncremental);
      check(runtime, TConstructJEIConstants.MODIFIERS, incremental, 85, 61, incremental.isIncremental() ? List.of(Component.translatable(jeiKey("modifiers.incremental"))) : List.of());
      IDisplayModifierRecipe free = prefer(recipes, TConstructJEIConstants.MODIFIERS, recipe -> recipe.getSlots() == null);
      check(runtime, TConstructJEIConstants.MODIFIERS, free, 110, 60, free.getSlots() == null ? List.of(Component.translatable(jeiKey("modifiers.free"))) : List.of());
      check(runtime, TConstructJEIConstants.MODIFIERS, free, 120, 2, List.of());
      // worktable: description along the top
      IModifierWorktableRecipe worktable = prefer(recipes, TConstructJEIConstants.MODIFIER_WORKTABLE, recipe -> true);
      check(runtime, TConstructJEIConstants.MODIFIER_WORKTABLE, worktable, 30, 5, List.of(worktable.getDescription(null)));
      check(runtime, TConstructJEIConstants.MODIFIER_WORKTABLE, worktable, 30, 30, List.of());

      // ingredient renderers: the tooltip overload JEI 27 calls (builder + tooltip context) must reach each renderer's lines
      checkIngredient(new EntityIngredientRenderer(16), new EntityIngredient.EntityInput(EntityType.PIG), List.of(EntityType.PIG.getDescription()));
      Pattern pattern = new Pattern(TConstruct.getResource("pick_head"));
      checkIngredient(PatternIngredientRenderer.INSTANCE, pattern, List.of(pattern.getDisplayName()));
      checkIngredient(SlotIngredientRenderer.INGREDIENT, new SlotCount(SlotType.UPGRADE, 1), List.of(Component.translatable(jeiKey("modifier_slot.ingredient"), SlotType.UPGRADE.getDisplayName())));
      checkIngredient(SlotIngredientRenderer.INPUT, new SlotCount(SlotType.UPGRADE, 2), List.of(Component.translatable(jeiKey("modifiers.slots"), 2, SlotType.UPGRADE.getDisplayName())));
      checkIngredient(SlotIngredientRenderer.OUTPUT, new SlotCount(SlotType.UPGRADE, 1), List.of(SlotType.UPGRADE.format(1)));
      Modifier modifier = TinkerModifiers.ambidextrous.get();
      checkIngredient(new ModifierIngredientRenderer(100, 10), new ModifierEntry(modifier, 1), modifier.getDescriptionList(1));
      List<Component> bookmark = new ArrayList<>();
      bookmark.add(Component.translatable("jei.tconstruct.modifier_ingredient", Component.translatable(modifier.getTranslationKey())));
      bookmark.addAll(modifier.getDescriptionList());
      checkIngredient(ModifierBookmarkIngredientRenderer.INSTANCE, new ModifierEntry(modifier, 1), bookmark);
    }

    /** Collects an ingredient tooltip as JEI 27 does (the builder overload with a tooltip context, normal flag) and compares it to the expected lines */
    private static <T> void checkIngredient(IIngredientRenderer<T> renderer, T ingredient, List<Component> expected) {
      List<Object> lines = new ArrayList<>();
      Minecraft mc = Minecraft.getInstance();
      renderer.getTooltip(recordingTooltip(lines, renderer.getClass().getSimpleName()), ingredient, Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
      if (!lines.equals(expected)) {
        throw new IllegalStateException(renderer.getClass().getSimpleName() + " tooltip of " + ingredient + ": expected " + expected + " but got " + lines);
      }
    }

    /** A tooltip builder recording the lines; a proxy rather than an implementation, so nothing overrides the builder's deprecated legacy methods */
    private static ITooltipBuilder recordingTooltip(List<Object> lines, String user) {
      return (ITooltipBuilder) Proxy.newProxyInstance(ITooltipBuilder.class.getClassLoader(), new Class<?>[] {ITooltipBuilder.class}, (proxy, method, args) -> {
        switch (method.getName()) {
          case "add" -> lines.add(args[0]);
          case "addAll" -> lines.addAll((Collection<?>) args[0]);
          default -> throw new UnsupportedOperationException("tooltip builder method " + method.getName() + " used by " + user);
        }
        return null;
      });
    }

    private static String jeiKey(String path) {
      return TConstruct.makeTranslationKey("jei", path);
    }

    /** Gets the first recipe matching the predicate, the one whose tooltip has lines, or the first recipe if none does */
    private static <T> T prefer(IRecipeManager recipes, IRecipeType<T> type, Predicate<T> predicate) {
      List<T> all = recipes.createRecipeLookup(type).get().toList();
      if (all.isEmpty()) {
        throw new IllegalStateException("JEI category " + type.getUid() + " has no recipes");
      }
      return all.stream().filter(predicate).findFirst().orElse(all.get(0));
    }

    /** Collects the tooltip of the category at the given point and compares it to the expected lines */
    private static <T> void check(IJeiRuntime runtime, IRecipeType<T> type, T recipe, double mouseX, double mouseY, List<Component> expected) {
      IRecipeManager recipes = runtime.getRecipeManager();
      IRecipeCategory<T> category = recipes.getRecipeCategory(type);
      List<Object> lines = new ArrayList<>();
      ITooltipBuilder tooltip = recordingTooltip(lines, type.getUid().toString());
      IRecipeSlotsView slots = recipes.createRecipeLayoutDrawable(category, recipe, runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup())
        .map(IRecipeLayoutDrawable::getRecipeSlotsView).orElseThrow(() -> new IllegalStateException("JEI could not lay out a " + type.getUid() + " recipe"));
      category.getTooltip(tooltip, recipe, slots, mouseX, mouseY);
      if (!lines.equals(expected)) {
        throw new IllegalStateException(type.getUid() + " tooltip at " + mouseX + ", " + mouseY + ": expected " + expected + " but got " + lines);
      }
    }

    @Override
    public void close(UiTestContext ctx) { /* nothing open */ }
  }

  /**
   * Opens the JEI recipes GUI on one Tinkers recipe category and captures its first page. Fails when the runtime is missing,
   * the category is not registered, no recipe of the type matches {@code mustShow} (any recipe by default), a recipe does not
   * lay out (when {@code allMustLayOut}), or the recipes GUI did not open. The mouse is parked in the corner every tick while
   * open, so no hover tooltip covers the recipes.
   */
  private static class JeiCategoryScenario<T> implements UiTestScenario {
    private final String name;
    private final IRecipeType<T> type;
    private final String mustShowDescription;
    /** A recipe JEI must list, e.g. one kind the category dropped before (JEI only logs recipes that fail to lay out) */
    private final Predicate<T> mustShow;
    /**
     * If true, every recipe of the category must lay out. JEI keeps a recipe whose setRecipe throws, but only logs
     * "Found a broken recipe": its ingredients after the throw are never indexed and its layout fails, so the recipes GUI skips it.
     */
    private final boolean allMustLayOut;
    /**
     * Fluids of a casting recipe, to check the recipes JEI hides: since JEI 27 a recipe whose focus linked slots only show
     * hidden ingredients is hidden, so only recipes showing just {@link JEIPlugin#getRemovedFluids()} or fluids tagged
     * {@link TinkerTags.Fluids#HIDDEN_IN_RECIPE_VIEWERS} may be hidden. Null skips the check.
     */
    @Nullable
    private final Function<T, List<FluidStack>> castingFluids;
    /** Minimum number of recipes the recipes GUI must list, when {@link #castingFluids} is set */
    private final int minVisible;
    /** Re-parks every tick: a recentering cursor event from opening the screen may land after open() */
    private final Consumer<ClientTickEvent.Post> mouseParker = event -> new UiTestContext(Minecraft.getInstance()).parkMouse();

    private JeiCategoryScenario(String name, IRecipeType<T> type, String mustShowDescription, Predicate<T> mustShow, boolean allMustLayOut,
                                @Nullable Function<T, List<FluidStack>> castingFluids, int minVisible) {
      this.name = name;
      this.type = type;
      this.mustShowDescription = mustShowDescription;
      this.mustShow = mustShow;
      this.allMustLayOut = allMustLayOut;
      this.castingFluids = castingFluids;
      this.minVisible = minVisible;
    }

    private JeiCategoryScenario(String name, IRecipeType<T> type, String mustShowDescription, Predicate<T> mustShow, boolean allMustLayOut) {
      this(name, type, mustShowDescription, mustShow, allMustLayOut, null, 0);
    }

    private JeiCategoryScenario(String name, IRecipeType<T> type, String mustShowDescription, Predicate<T> mustShow) {
      this(name, type, mustShowDescription, mustShow, false);
    }

    private JeiCategoryScenario(String name, IRecipeType<T> type, Function<T, List<FluidStack>> castingFluids, int minVisible) {
      this(name, type, "any recipe", recipe -> true, false, castingFluids, minVisible);
    }

    private JeiCategoryScenario(String name, IRecipeType<T> type) {
      this(name, type, "any recipe", recipe -> true);
    }

    @Override
    public Identifier id() {
      return TConstruct.getResource("jei_" + name + "_category");
    }

    @Override
    public void prepare(UiTestContext ctx) { /* nothing to build; JEI's async indexing captures the runtime long before the JEI scenarios */ }

    @Override
    public void open(UiTestContext ctx) {
      IJeiRuntime runtime = JEIPlugin.runtime;
      if (runtime == null) {
        throw new IllegalStateException("JEI runtime not captured - onRuntimeAvailable never fired");
      }
      IRecipeManager recipes = runtime.getRecipeManager();
      // throws if the category was never registered
      Objects.requireNonNull(recipes.getRecipeCategory(type), () -> "no JEI category for " + type.getUid());
      if (recipes.createRecipeLookup(type).get().noneMatch(mustShow)) {
        throw new IllegalStateException("JEI category " + type.getUid() + " is missing " + mustShowDescription);
      }
      if (castingFluids != null) {
        checkHiddenRecipes(recipes, castingFluids);
      }
      if (allMustLayOut) {
        IRecipeCategory<T> category = recipes.getRecipeCategory(type);
        IFocusGroup noFocus = runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
        List<T> broken = recipes.createRecipeLookup(type).includeHidden().get()
          .filter(recipe -> recipes.createRecipeLayoutDrawable(category, recipe, noFocus).isEmpty())
          .toList();
        if (!broken.isEmpty()) {
          throw new IllegalStateException("JEI could not lay out " + broken.size() + " " + type.getUid() + " recipe(s), see the log for a broken recipe, first: " + broken.get(0));
        }
      }
      runtime.getRecipesGui().showTypes(List.of(type));
      ctx.parkMouse();
      NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, mouseParker);
    }

    /** Fails when JEI lists fewer than {@link #minVisible} recipes, or hides a recipe showing any fluid the plugin did not remove */
    private void checkHiddenRecipes(IRecipeManager recipes, Function<T, List<FluidStack>> fluids) {
      Set<T> visible = Collections.newSetFromMap(new IdentityHashMap<>());
      recipes.createRecipeLookup(type).get().forEach(visible::add);
      if (visible.size() < minVisible) {
        throw new IllegalStateException("JEI category " + type.getUid() + " lists " + visible.size() + " recipes, expected at least " + minVisible);
      }
      // fluids removed at runtime or hidden by tag; the variantless potion fluid is removed, but a potion variant has contents
      Set<Fluid> removed = JEIPlugin.getRemovedFluids().stream().map(FluidStack::getFluid).collect(Collectors.toSet());
      Predicate<FluidStack> isRemoved = fluid -> fluid.is(TinkerTags.Fluids.HIDDEN_IN_RECIPE_VIEWERS)
                                                 || removed.contains(fluid.getFluid()) && !fluid.has(DataComponents.POTION_CONTENTS);
      List<T> wronglyHidden = recipes.createRecipeLookup(type).includeHidden().get()
        .filter(recipe -> !visible.contains(recipe))
        .filter(recipe -> fluids.apply(recipe).isEmpty() || !fluids.apply(recipe).stream().allMatch(isRemoved))
        .toList();
      if (!wronglyHidden.isEmpty()) {
        throw new IllegalStateException("JEI category " + type.getUid() + " hides " + wronglyHidden.size() + " recipe(s) showing fluids that are not removed, first fluids: "
                                        + fluids.apply(wronglyHidden.get(0)).stream().map(fluid -> fluid.getFluid() + " " + fluid.getComponentsPatch()).toList());
      }
    }

    @Override
    public int settleTicks() {
      return 30; // recipe layouts/item renders settle
    }

    @Override
    public void close(UiTestContext ctx) {
      if (!(ctx.mc().screen instanceof IRecipesGui)) {
        throw new IllegalStateException(id().getPath() + " expected the JEI recipes GUI at capture, found " + ctx.mc().screen);
      }
    }

    @Override
    public void cleanup(UiTestContext ctx) {
      NeoForge.EVENT_BUS.unregister(mouseParker);
      ctx.mc().setScreen(null);
    }
  }

  /**
   * Opens "Mighty Smelting" on the smeltery structure page and captures the 3D multiblock preview, drawn by Mantle's
   * {@code StructurePiPRenderer} as a picture-in-picture with absolute bounds.
   */
  private static class BookStructureScenario implements UiTestScenario {
    private static final String PAGE = "smeltery.structure";

    @Override
    public Identifier id() {
      return TConstruct.getResource("book_structure");
    }

    @Override
    public void prepare(UiTestContext ctx) {
      // same deterministic hotbar.0 placement as book_materials_and_you (see its comment)
      ctx.sendCommand("item replace entity @s hotbar.0 with tconstruct:mighty_smelting");
    }

    @Override
    public int prepareSettleTicks() {
      return 20;
    }

    @Override
    public void open(UiTestContext ctx) {
      InteractionResult result = ctx.player().getItemInHand(InteractionHand.MAIN_HAND)
        .getItem().use(ctx.mc().level, ctx.player(), InteractionHand.MAIN_HAND);
      if (result == InteractionResult.PASS) {
        throw new IllegalStateException("book item use() passed - book screen did not open");
      }
      if (!(ctx.mc().screen instanceof BookScreen book)) {
        throw new IllegalStateException("expected BookScreen after use(), found " + ctx.mc().screen);
      }
      int page = book.book.findPageNumber(PAGE, book.advancementCache);
      if (page < 0) {
        throw new IllegalStateException("book page " + PAGE + " not found");
      }
      int side = book.openPage(page);
      StructureElement structure = book.getElements(side).stream()
        .filter(StructureElement.class::isInstance).map(StructureElement.class::cast)
        .findFirst().orElseThrow(() -> new IllegalStateException("book page " + PAGE + " has no structure element"));
      if (structure.renderInfo.structureLength == 0 || structure.renderInfo.structureHeight == 0 || structure.renderInfo.structureWidth == 0) {
        throw new IllegalStateException("book page " + PAGE + " has an empty structure");
      }
    }

    @Override
    public int settleTicks() {
      return 40; // book textures/pages lazy-load
    }
  }

  /**
   * Fills the crafting station grid, presses the Crafting Tweaks rotate button once the menu is open, captures the rotated
   * grid, and asserts the items moved. The button is pressed from a client tick poller as the menu opens through a server
   * round trip, see {@link StationCollapsedOverlayScenario}.
   */
  private static class CraftingTweaksRotateScenario implements UiTestScenario {
    private final BlockPos pos = SITE.offset(80, 0, 0);
    private final Consumer<ClientTickEvent.Post> poller = this::rotateWhenReady;
    private boolean rotated = false;

    @Override
    public Identifier id() {
      return TConstruct.getResource("crafting_tweaks_rotate");
    }

    @Override
    public void prepare(UiTestContext ctx) {
      rotated = false;
      ctx.sendCommand("tp @s " + (pos.getX() - 2) + " " + pos.getY() + " " + pos.getZ());
      ctx.runOnServer(() -> {
        ServerLevel level = ctx.serverLevel();
        level.setBlockAndUpdate(pos, TinkerTables.craftingStation.get().defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof CraftingStationBlockEntity station)) {
          throw new IllegalStateException("Expected crafting station at " + pos + ", found " + level.getBlockEntity(pos));
        }
        // the saved world persists across runs, so reset the whole grid: an L along the top row and left column
        for (int i = 0; i < 9; i++) {
          station.setItem(i, ItemStack.EMPTY);
        }
        station.setItem(0, new ItemStack(Items.STICK));
        station.setItem(1, new ItemStack(Items.COBBLESTONE));
        station.setItem(3, new ItemStack(Items.COBBLESTONE));
      });
    }

    @Override
    public int prepareSettleTicks() {
      return 20;
    }

    @Override
    public void open(UiTestContext ctx) {
      ctx.useBlock(pos);
      NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, poller);
    }

    /** Presses rotate on the first tick the menu is open with the grid synced, then self-removes. */
    private void rotateWhenReady(ClientTickEvent.Post event) {
      if (rotated || !(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen)
          || !(screen.getMenu() instanceof CraftingStationContainerMenu menu) || !menu.getSlot(0).getItem().is(Items.STICK)) {
        return;
      }
      for (GuiEventListener child : screen.children()) {
        if (child instanceof GuiTweakButton button && button.getTooltipComponents().stream().anyMatch(CraftingTweaksRotateScenario::isRotateTooltip)) {
          // 1.21.9: event based input; a plain left click at the button's corner, no modifiers
          button.onClick(new MouseButtonEvent(button.getX(), button.getY(), new MouseButtonInfo(0, 0)), false);
          rotated = true;
          NeoForge.EVENT_BUS.unregister(poller);
          return;
        }
      }
    }

    private static boolean isRotateTooltip(Component component) {
      return component.getContents() instanceof TranslatableContents translatable && "tooltip.craftingtweaks.rotate".equals(translatable.getKey());
    }

    @Override
    public int settleTicks() {
      return 30; // menu round trip + rotate packet + slot sync
    }

    @Override
    public void close(UiTestContext ctx) {
      if (!(ctx.mc().screen instanceof AbstractContainerScreen<?> screen) || !(screen.getMenu() instanceof CraftingStationContainerMenu menu)) {
        throw new IllegalStateException("crafting_tweaks_rotate expected the crafting station menu at capture, found " + ctx.mc().screen);
      }
      if (!rotated) {
        throw new IllegalStateException("crafting_tweaks_rotate found no Crafting Tweaks rotate button on the crafting station");
      }
      // the stick started in the top left corner; Crafting Tweaks rotates the grid ring one step, any rotation moves it out of slot 0
      int stick = -1;
      for (int i = 0; i < 9; i++) {
        if (menu.getSlot(i).getItem().is(Items.STICK)) {
          stick = i;
        }
      }
      if (stick == -1 || stick == 0) {
        throw new IllegalStateException("crafting_tweaks_rotate expected the stick to leave slot 0, found it in slot " + stick);
      }
    }

    @Override
    public void cleanup(UiTestContext ctx) {
      NeoForge.EVENT_BUS.unregister(poller); // still registered if the menu never opened
      ctx.mc().setScreen(null);
    }
  }

  /**
   * Reflow tier: resizes the window to 760x480 (auto GUI scale 2 -> 380x240 GUI px) and opens a lone
   * tinker station, which at 380 GUI resolves to REFLOW with 4 selector columns and narrowed (100px)
   * info panels - guards acceptance #2.
   *
   * Chest-free by design: the tinker station never wires a chest side inventory (only the crafting
   * station, modifier worktable and part builder call addChestSideInventory), so its
   * sideInventoryWidth() is always 0 and an adjacent chest would render nothing here. No screen has
   * both the selector and a chest, so acceptance #4 ("selector reflows around a chest") is impossible
   * as written; its computeLayout math stays covered by the doubleChestEatsSelectorSide unit test.
   */
  private static class StationReflowScenario implements UiTestScenario {
    /** Fresh site clear of the other scenarios' rigs, which occupy SITE +0..+30 on X. */
    private final BlockPos pos = SITE.offset(40, 0, 0);
    private int restoreW = 1280, restoreH = 720;

    @Override
    public Identifier id() {
      return TConstruct.getResource("station_reflow");
    }

    @Override
    public void prepare(UiTestContext ctx) {
      restoreW = ctx.mc().getWindow().getWidth();
      restoreH = ctx.mc().getWindow().getHeight();
      ctx.mc().execute(() -> ctx.mc().getWindow().setWindowed(760, 480));
      // place the station, mirroring BlockGuiScenario: tp beside it, then set the block on the server
      ctx.sendCommand("tp @s " + (pos.getX() - 2) + " " + pos.getY() + " " + pos.getZ());
      ctx.runOnServer(() -> ctx.serverLevel().setBlockAndUpdate(pos, TinkerTables.tinkerStation.get().defaultBlockState()));
    }

    @Override
    public void open(UiTestContext ctx) {
      ctx.useBlock(pos); // opens the real station menu via the server, exactly like BlockGuiScenario
    }

    @Override
    public int settleTicks() {
      return 30; // resize + reinit + panel reflow settle
    }

    @Override
    public void close(UiTestContext ctx) {
      // the menu opens through a server round-trip, so assert here (post-settle) rather than in open():
      // fail loudly if the station screen never resolved instead of silently shipping a world screenshot
      if (!(ctx.mc().screen instanceof TinkerStationScreen)) {
        throw new IllegalStateException("station_reflow expected TinkerStationScreen at capture, found " + ctx.mc().screen);
      }
    }

    @Override
    public void cleanup(UiTestContext ctx) {
      ctx.mc().setScreen(null);
      ctx.mc().execute(() -> ctx.mc().getWindow().setWindowed(restoreW, restoreH));
    }
  }

  /**
   * Collapsed tier: resizes the window to 640x480 (auto GUI scale 2 -> 320x240 GUI px) and opens a lone
   * tinker station, which at 320 GUI resolves to COLLAPSED - the selector stays at 3 columns but the info
   * panels collapse to two edge tabs (infoPanelWidth 0) and the armor stand is dropped - guards acceptance
   * #3's first half. Same chest-free lone-station setup as {@link StationReflowScenario}, only smaller.
   */
  private static class StationCollapsedScenario implements UiTestScenario {
    /** Fresh site clear of the other station scenarios (reflow sits at +40). */
    private final BlockPos pos = SITE.offset(50, 0, 0);
    private int restoreW = 1280, restoreH = 720;

    @Override
    public Identifier id() {
      return TConstruct.getResource("station_collapsed");
    }

    @Override
    public void prepare(UiTestContext ctx) {
      restoreW = ctx.mc().getWindow().getWidth();
      restoreH = ctx.mc().getWindow().getHeight();
      ctx.mc().execute(() -> ctx.mc().getWindow().setWindowed(640, 480));
      ctx.sendCommand("tp @s " + (pos.getX() - 2) + " " + pos.getY() + " " + pos.getZ());
      ctx.runOnServer(() -> ctx.serverLevel().setBlockAndUpdate(pos, TinkerTables.tinkerStation.get().defaultBlockState()));
    }

    @Override
    public void open(UiTestContext ctx) {
      ctx.useBlock(pos); // opens the real station menu via the server, exactly like StationReflowScenario
    }

    @Override
    public int settleTicks() {
      return 30; // resize + reinit + panel reflow settle
    }

    @Override
    public void close(UiTestContext ctx) {
      // same post-settle assert as station_reflow: the menu opens through a server round-trip, so fail loudly
      // here rather than in open() if the station screen never resolved
      if (!(ctx.mc().screen instanceof TinkerStationScreen)) {
        throw new IllegalStateException("station_collapsed expected TinkerStationScreen at capture, found " + ctx.mc().screen);
      }
    }

    @Override
    public void cleanup(UiTestContext ctx) {
      ctx.mc().setScreen(null);
      ctx.mc().execute(() -> ctx.mc().getWindow().setWindowed(restoreW, restoreH));
    }
  }

  /**
   * Collapsed tier with an open panel overlay: same 640x480 lone station as {@link StationCollapsedScenario},
   * then pops the tool-info panel as a centered on-demand overlay over the inventory area - guards acceptance
   * #3's second half (the collapsed overlay is legible above the slots).
   *
   * The station menu opens asynchronously (server round-trip), so {@code screen.openOverlay(...)} can't run
   * synchronously in open(); a self-removing {@link ClientTickEvent.Post} poller (mirroring how {@link
   * slimeknights.mantle.client.uitest.UiTestSuite} itself is tick-driven) opens the overlay on the first tick
   * the screen has resolved, well inside the settle window. Chosen over the plan's suggested self-rescheduling
   * {@code mc().execute(...)}: Minecraft drains its whole task queue per frame, so a task that re-queues itself
   * while the screen is still pending would spin within one frame instead of yielding to the network tick.
   */
  private static class StationCollapsedOverlayScenario implements UiTestScenario {
    /** Fresh site clear of the other station scenarios (reflow +40, collapsed +50). */
    private final BlockPos pos = SITE.offset(54, 0, 0);
    private int restoreW = 1280, restoreH = 720;
    /** Set once the resolved screen has had the overlay opened, so the poller stops and won't toggle it back shut. */
    private boolean overlayOpened = false;
    /** Retained so the poller can remove itself once it fires (and as a cleanup() safety net). */
    private final Consumer<ClientTickEvent.Post> overlayPoller = this::openOverlayWhenReady;

    @Override
    public Identifier id() {
      return TConstruct.getResource("station_collapsed_overlay");
    }

    @Override
    public void prepare(UiTestContext ctx) {
      restoreW = ctx.mc().getWindow().getWidth();
      restoreH = ctx.mc().getWindow().getHeight();
      ctx.mc().execute(() -> ctx.mc().getWindow().setWindowed(640, 480));
      ctx.sendCommand("tp @s " + (pos.getX() - 2) + " " + pos.getY() + " " + pos.getZ());
      ctx.runOnServer(() -> ctx.serverLevel().setBlockAndUpdate(pos, TinkerTables.tinkerStation.get().defaultBlockState()));
    }

    @Override
    public void open(UiTestContext ctx) {
      ctx.useBlock(pos); // async: the station menu resolves a few ticks later via the server round-trip
      NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, this.overlayPoller); // so open the overlay from a client-tick poller, not here
    }

    /** Opens the tool-info overlay on the first tick the async station screen has resolved, then self-removes. */
    private void openOverlayWhenReady(ClientTickEvent.Post event) {
      if (this.overlayOpened) {
        return;
      }
      if (Minecraft.getInstance().screen instanceof TinkerStationScreen station) {
        station.openOverlay(ToolTableScreen.OverlayPanel.TOOL_INFO);
        this.overlayOpened = true;
        NeoForge.EVENT_BUS.unregister(this.overlayPoller);
      }
    }

    @Override
    public int settleTicks() {
      return 30; // resize + reinit + panel reflow + overlay-open settle
    }

    @Override
    public void close(UiTestContext ctx) {
      if (!(ctx.mc().screen instanceof TinkerStationScreen station)) {
        throw new IllegalStateException("station_collapsed_overlay expected TinkerStationScreen at capture, found " + ctx.mc().screen);
      }
      // fail loudly if the overlay never opened - otherwise a collapsed-but-no-overlay PNG would still pass the run
      if (station.getOpenOverlay() != ToolTableScreen.OverlayPanel.TOOL_INFO) {
        throw new IllegalStateException("station_collapsed_overlay expected TOOL_INFO overlay open at capture, found " + station.getOpenOverlay());
      }
    }

    @Override
    public void cleanup(UiTestContext ctx) {
      NeoForge.EVENT_BUS.unregister(this.overlayPoller); // still registered if the screen never resolved and it never fired
      ctx.mc().setScreen(null);
      ctx.mc().execute(() -> ctx.mc().getWindow().setWindowed(restoreW, restoreH));
    }
  }

  /**
   * No menu: captures the 1.21.5 block-state-level dynamic models in the world - a 3x3 clear glass wall
   * ({@code mantle:connected}: no inner borders when the neighbors connect), a crafting station retextured to dark oak
   * ({@code mantle:retextured}, data from the block entity's model data) and a seared fuel tank filled with lava
   * ({@code tconstruct:tank}).
   */
  private static class DynamicBlockModelsScenario implements UiTestScenario {
    private final BlockPos origin = SITE.offset(90, 0, 0);

    @Override
    public Identifier id() {
      return TConstruct.getResource("dynamic_block_models");
    }

    @Override
    public void prepare(UiTestContext ctx) {
      ctx.sendCommand("tp @s " + (origin.getX() - 5) + " " + origin.getY() + " " + origin.getZ());
      ctx.runOnServer(() -> {
        ServerLevel level = ctx.serverLevel();
        // glass wall to the left of the view
        for (int z = -3; z <= -1; z++) {
          for (int y = 0; y < 3; y++) {
            level.setBlockAndUpdate(origin.offset(0, y, z), TinkerCommons.clearGlass.get().defaultBlockState());
          }
        }
        // retextured table in the middle
        BlockPos table = origin.offset(0, 0, 1);
        level.setBlockAndUpdate(table, TinkerTables.craftingStation.get().defaultBlockState());
        if (level.getBlockEntity(table) instanceof IRetexturedBlockEntity retextured) {
          retextured.updateTexture(BuiltInRegistries.BLOCK.getKey(Blocks.DARK_OAK_PLANKS).toString());
        } else {
          throw new IllegalStateException("Expected a retextured block entity at " + table + ", found " + level.getBlockEntity(table));
        }
        // tank with fluid to the right
        BlockPos tank = origin.offset(0, 0, 3);
        level.setBlockAndUpdate(tank, TinkerSmeltery.searedTank.get(TankType.FUEL_TANK).defaultBlockState());
        SmelteryRigs.fillTank(level, tank, new FluidStack(Fluids.LAVA, 2000));
      });
    }

    @Override
    public int prepareSettleTicks() {
      return 30; // block entity data sync + chunk remesh
    }

    @Override
    public void open(UiTestContext ctx) {
      ctx.lookAt(origin.offset(0, 1, 0));
    }

    @Override
    public int settleTicks() {
      return 20;
    }

    @Override
    public void close(UiTestContext ctx) { /* nothing open */ }
  }

  /**
   * Opens the player inventory with every Tinkers head item in the hotbar/inventory, one held (rendered by the player
   * preview) and one worn, to verify the {@code tconstruct:head} special item model draws the heads instead of nothing.
   * The Tinkers spawn eggs follow the heads, to check their generated textures (missing since 1.21.5 removed the template).
   */
  private static class HeadItemsScenario implements UiTestScenario {
    @Override
    public Identifier id() {
      return TConstruct.getResource("head_items");
    }

    @Override
    public void prepare(UiTestContext ctx) {
      // survival: in creative the inventory screen is replaced by the creative tabs, without main inventory or player preview
      ctx.sendCommand("gamemode survival");
      ctx.sendCommand("clear @s");
      TinkerHeadType[] types = TinkerHeadType.values();
      for (int i = 0; i < types.length; i++) {
        String slot = i < 9 ? "hotbar." + i : "inventory." + (i - 9);
        ctx.sendCommand("item replace entity @s " + slot + " with " + BuiltInRegistries.ITEM.getKey(TinkerWorld.headItems.get(types[i])));
      }
      ctx.sendCommand("item replace entity @s armor.head with " + BuiltInRegistries.ITEM.getKey(TinkerWorld.headItems.get(TinkerHeadType.PIGLIN_BRUTE)));
      // spawn eggs after the heads: their generated textures reproduce the tinted pre 1.21.5 template
      for (int i = 0; i < SPAWN_EGGS.size(); i++) {
        ctx.sendCommand("item replace entity @s inventory." + (types.length - 9 + i) + " with " + BuiltInRegistries.ITEM.getKey(SPAWN_EGGS.get(i).asItem()));
      }
    }

    /** Tinkers spawn eggs shown after the heads */
    private static final List<ItemLike> SPAWN_EGGS = List.of(TinkerWorld.skySlimeEntity, TinkerWorld.enderSlimeEntity, TinkerWorld.terracubeEntity);

    @Override
    public int prepareSettleTicks() {
      return 20;
    }

    @Override
    public void open(UiTestContext ctx) {
      for (ItemLike egg : SPAWN_EGGS) {
        Identifier texture = BuiltInRegistries.ITEM.getKey(egg.asItem()).withPath(path -> "textures/item/" + path + ".png");
        if (ctx.mc().getResourceManager().getResource(texture).isEmpty()) {
          throw new IllegalStateException("missing spawn egg texture " + texture);
        }
      }
      LocalPlayer player = Objects.requireNonNull(ctx.mc().player);
      player.getInventory().setSelectedSlot(0); // hotbar.0 -> the blaze head is held
      ctx.mc().setScreen(new InventoryScreen(player));
    }

    @Override
    public void cleanup(UiTestContext ctx) {
      // here rather than in close() so a failed assert in open() doesn't leave the rest of the suite in survival
      ctx.mc().setScreen(null);
      ctx.sendCommand("clear @s");
      ctx.sendCommand("gamemode creative");
    }
  }

  /**
   * Opens the player inventory filled with items drawn by the Mantle/Tinkers custom item models (retextured tables, tools,
   * material parts, fluid containers, material blocks, NBT key) to guard the 1.21.6+ GUI item atlas: it caches one icon
   * per model identity, so a custom model that appends no identity shares a slot with every other one (station tabs
   * showed an arrow instead of the table). Asserts each stack has a non-empty identity that is stable across resolves
   * and distinct from the others (including a foil-only and a modifier-only variant), that foil and animated textures
   * mark the slot animated, then captures the inventory for a visual check.
   */
  private static class DynamicItemIconsScenario implements UiTestScenario {
    @Override
    public Identifier id() {
      return TConstruct.getResource("dynamic_item_icons");
    }

    /** Built fresh per call: once on the server thread for the inventory, once on the client thread for the assert */
    private static List<ItemStack> stacks() {
      ItemStack cobaltPickaxe = new MaterialIdNBT(List.of(MaterialIds.cobalt, MaterialIds.wood, MaterialIds.iron)).updateStack(new ItemStack(TinkerTools.pickaxe));
      // same materials plus a modifier with its own overlay, so only the modifier part of the tool cache key differs
      ToolStack netheritePickaxe = ToolStack.from(cobaltPickaxe.copy());
      netheritePickaxe.addModifier(ModifierIds.netherite, 1);
      // same model variant as the plain iron head, only the foil differs
      ItemStack foilPickHead = TinkerToolParts.pickHead.get().withMaterial(MaterialIds.iron);
      foilPickHead.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
      return List.of(
        new ItemStack(TinkerTables.craftingStation),
        RetexturedHelper.setTexture(new ItemStack(TinkerTables.craftingStation), Blocks.DARK_OAK_PLANKS),
        RetexturedHelper.setTexture(new ItemStack(TinkerTables.tinkerStation), Blocks.BIRCH_PLANKS),
        RetexturedHelper.setTexture(new ItemStack(TinkerTables.partBuilder), Blocks.CRIMSON_PLANKS),
        cobaltPickaxe,
        netheritePickaxe.createStack(),
        TinkerTools.pickaxe.get().getRenderTool(),
        TinkerTools.arrow.get().getRenderTool(),
        TinkerToolParts.pickHead.get().withMaterial(MaterialIds.iron),
        foilPickHead,
        TinkerToolParts.pickHead.get().withMaterial(MaterialIds.cobalt),
        new ItemStack(TinkerFluids.moltenIron.getBucket()),
        CopperCanItem.setFluid(new ItemStack(TinkerSmeltery.copperCan), new FluidStack(Fluids.WATER, FluidValues.BOTTLE)),
        CopperCanItem.setFluid(new ItemStack(TinkerSmeltery.copperCan), new FluidStack(Fluids.LAVA, FluidValues.BOTTLE)),
        new ItemStack(TinkerTables.tinkersAnvil),
        new ItemStack(TinkerModifiers.creativeSlotItem)
      );
    }

    @Override
    public void prepare(UiTestContext ctx) {
      // survival: in creative the inventory screen is replaced by the creative tabs
      ctx.sendCommand("gamemode survival");
      ctx.runOnServer(() -> {
        Inventory inventory = ctx.serverLevel().getServer().getPlayerList().getPlayers().getFirst().getInventory();
        inventory.clearContent();
        List<ItemStack> stacks = stacks();
        for (int i = 0; i < stacks.size(); i++) {
          inventory.setItem(i, stacks.get(i));
        }
      });
    }

    @Override
    public int prepareSettleTicks() {
      return 20;
    }

    @Override
    public void open(UiTestContext ctx) {
      // resolve each stack like GuiGraphics#renderItem does, twice: the identity must be stable for the atlas to reuse it
      ItemModelResolver resolver = ctx.mc().getItemModelResolver();
      List<ItemStack> stacks = stacks();
      List<Object> identities = new ArrayList<>();
      for (ItemStack stack : stacks) {
        TrackingItemStackRenderState first = new TrackingItemStackRenderState();
        resolver.updateForTopItem(first, stack, ItemDisplayContext.GUI, ctx.mc().level, ctx.player(), 0);
        TrackingItemStackRenderState second = new TrackingItemStackRenderState();
        resolver.updateForTopItem(second, stack, ItemDisplayContext.GUI, ctx.mc().level, ctx.player(), 0);
        Object identity = first.getModelIdentity();
        if (((List<?>) identity).isEmpty()) {
          throw new IllegalStateException("dynamic_item_icons: " + stack + " has an empty GUI model identity");
        }
        if (!identity.equals(second.getModelIdentity())) {
          throw new IllegalStateException("dynamic_item_icons: " + stack + " GUI model identity is not stable across resolves");
        }
        // glint and the animated molten iron texture must re-render their atlas slot every frame
        if ((stack.hasFoil() || stack.is(TinkerFluids.moltenIron.getBucket())) && !first.isAnimated()) {
          throw new IllegalStateException("dynamic_item_icons: " + stack + " should be animated in the GUI atlas");
        }
        for (int i = 0; i < identities.size(); i++) {
          if (identities.get(i).equals(identity)) {
            throw new IllegalStateException("dynamic_item_icons: " + stack + " shares its GUI model identity with " + stacks.get(i));
          }
        }
        identities.add(identity);
      }
      LocalPlayer player = Objects.requireNonNull(ctx.mc().player);
      ctx.mc().setScreen(new InventoryScreen(player));
    }

    @Override
    public void cleanup(UiTestContext ctx) {
      // here rather than in close() so a failed assert in open() doesn't leave the rest of the suite in survival
      ctx.mc().setScreen(null);
      ctx.sendCommand("clear @s");
      ctx.sendCommand("gamemode creative");
    }
  }

  /**
   * Gives the player a pickaxe with the pocket modifier holding a few items, filled through the tool's item capability, and uses it in the air:
   * the use opens the tool inventory menu through the server, which needs the tool's {@code Capabilities.Item.ITEM} (never registered from the
   * 1.21.1 port until 1.21.10, M5 decision D-C1). Captures the opened tool inventory screen with its 3 pocket slots.
   */
  private static class ToolInventoryScenario implements UiTestScenario {
    private final BlockPos pos = SITE.offset(80, 0, 0);

    @Override
    public Identifier id() {
      return TConstruct.getResource("tool_inventory");
    }

    @Override
    public void prepare(UiTestContext ctx) {
      // look straight up so the use hits the air, not a block
      ctx.sendCommand("tp @s " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " 0 -90");
      ctx.runOnServer(() -> {
        ToolStack tool = ToolStack.createTool(TinkerTools.pickaxe.get(), TinkerTools.pickaxe.get().getToolDefinition(),
          MaterialNBT.of(MaterialVariant.of(MaterialIds.cobalt, ""), MaterialVariant.of(MaterialIds.wood, ""), MaterialVariant.of(MaterialIds.iron, "")));
        tool.addModifier(ModifierIds.pocket, 1);
        ItemStacksResourceHandler slot = new ItemStacksResourceHandler(1);
        slot.set(0, ItemResource.of(tool.createStack()), 1);
        ResourceHandler<ItemResource> pockets = ItemAccess.forHandlerIndex(slot, 0).getCapability(Capabilities.Item.ITEM);
        if (pockets == null) {
          throw new IllegalStateException("tool_inventory: pickaxe with pockets exposes no item capability");
        }
        try (Transaction tx = Transaction.openRoot()) {
          pockets.insert(0, ItemResource.of(Items.DIAMOND), 5, tx);
          pockets.insert(1, ItemResource.of(Items.TORCH), 32, tx);
          tx.commit();
        }
        Inventory inventory = ctx.serverLevel().getServer().getPlayerList().getPlayers().getFirst().getInventory();
        inventory.clearContent();
        inventory.setItem(0, slot.getResource(0).toStack());
      });
    }

    @Override
    public int prepareSettleTicks() {
      return 20;
    }

    @Override
    public void open(UiTestContext ctx) {
      LocalPlayer player = ctx.player();
      player.getInventory().setSelectedSlot(0);
      // the client side use also sees the capability and sends the use to the server, which opens the menu
      Objects.requireNonNull(ctx.mc().gameMode).useItem(player, InteractionHand.MAIN_HAND);
    }

    @Override
    public int settleTicks() {
      return 20; // server round trip for the menu
    }

    @Override
    public void close(UiTestContext ctx) {
      // the menu opens through the server, so assert after the settle: fail loudly instead of shipping a world screenshot
      if (!(ctx.mc().screen instanceof ToolContainerScreen screen)) {
        throw new IllegalStateException("tool_inventory expected ToolContainerScreen at capture, found " + ctx.mc().screen);
      }
      int slots = screen.getMenu().getItemHandler().size();
      if (slots != 3) {
        throw new IllegalStateException("tool_inventory expected 3 pocket slots, found " + slots);
      }
      ctx.mc().setScreen(null);
    }

    @Override
    public void cleanup(UiTestContext ctx) {
      ctx.mc().setScreen(null);
      ctx.sendCommand("clear @s");
      // look ahead again: the following screen captures show the world behind them
      ctx.sendCommand("tp @s ~ ~ ~ 0 0");
    }
  }

  /**
   * Opens a survival inventory with trimmed plate armor, worn (player preview) and as items: vanilla trim materials (iron, gold, resin) and a
   * Tinkers one (cobalt). 1.21.11 moved vanilla's {@code trims/items/*_trim_<material>} permutations from the blocks atlas to the items atlas, so
   * {@code TrimModifierModel} looks them up there, and on the blocks atlas for Tinkers materials; without that the item icons fall back to a flat
   * tint of the base texture. Asserts the trim data is on the client stacks and every sprite the item and worn models look up exists (item icons,
   * including an armor specific {@code iron_darker} palette, and not duplicated on the blocks atlas; worn textures on the armor trims atlas under
   * {@code trims/entity/humanoid(_leggings)/}, where vanilla moved them in 1.21.4).
   * <p>
   * Worn armor (M6 Phase 4): Tinkers armor draws through {@code TinkerArmorLayer}, not vanilla's equipment layer, which drew it as iron armor
   * since 1.21.4. Asserts each worn piece has Tinkers' equipment asset (a transparent layer vanilla skips) and an armor renderer whose layers give a material texture and a trim,
   * then after the capture samples the player preview: the copper helmet must be orange and the gold leggings yellow, not grey.
   */
  private static class TrimmedArmorScenario implements UiTestScenario {
    /** One trimmed armor piece: slot, plating/maille material, trim pattern and trim material */
    private record Trim(ArmorType type, MaterialId material, String pattern, String trimMaterial) {
      ItemStack build() {
        ModifiableArmorItem item = TinkerTools.plateArmor.get(type);
        ToolStack tool = ToolStack.createTool(item, item.getToolDefinition(), MaterialNBT.of(MaterialVariant.of(material, ""), MaterialVariant.of(material, "")));
        if (trimMaterial != null) {
          ModifierId trim = TinkerModifiers.trim.getId();
          tool.getPersistentData().putString(TrimModule.materialKey(trim), trimMaterial);
          tool.getPersistentData().putString(TrimModule.patternKey(trim), pattern);
          tool.addModifier(trim, 1);
        }
        return tool.createStack();
      }
    }

    /** Worn: one material per slot */
    private static final List<Trim> WORN = List.of(
      new Trim(ArmorType.HELMET, MaterialIds.copper, "minecraft:sentry", "minecraft:iron"),
      new Trim(ArmorType.CHESTPLATE, MaterialIds.iron, "minecraft:coast", "minecraft:gold"),
      new Trim(ArmorType.LEGGINGS, MaterialIds.gold, "minecraft:dune", "minecraft:resin"),
      new Trim(ArmorType.BOOTS, MaterialIds.iron, "minecraft:wild", "tconstruct:cobalt"));
    /** In the inventory: the worn set rotated through the other slots, iron on iron (vanilla would pick iron_darker), and an untrimmed reference */
    private static final List<Trim> CARRIED = List.of(
      new Trim(ArmorType.HELMET, MaterialIds.iron, "minecraft:sentry", "minecraft:iron"),
      new Trim(ArmorType.HELMET, MaterialIds.iron, "minecraft:sentry", null),
      new Trim(ArmorType.CHESTPLATE, MaterialIds.copper, "minecraft:coast", "minecraft:resin"),
      new Trim(ArmorType.CHESTPLATE, MaterialIds.iron, "minecraft:coast", "tconstruct:cobalt"),
      new Trim(ArmorType.LEGGINGS, MaterialIds.iron, "minecraft:dune", "minecraft:iron"),
      new Trim(ArmorType.LEGGINGS, MaterialIds.copper, "minecraft:dune", "minecraft:gold"),
      new Trim(ArmorType.BOOTS, MaterialIds.gold, "minecraft:wild", "minecraft:resin"),
      new Trim(ArmorType.BOOTS, MaterialIds.copper, "minecraft:wild", "minecraft:iron"));
    /** Vanilla palettes beyond the base ones, which vanilla stitches on the items atlas */
    private static final List<String> EXTRA_PALETTES = List.of("iron_darker", "gold_darker");
    /** Worn helmet region of the player preview in GUI pixels, relative to the inventory corner: front left of the head above the eyes, left of the sentry trim */
    private static final Rect2i HELMET_REGION = new Rect2i(42, 15, 5, 4);
    /** Iron sentry trim on the worn helmet in GUI pixels, relative to the inventory corner: the stripe down the middle of the front */
    private static final Rect2i HELMET_TRIM_REGION = new Rect2i(49, 15, 2, 5);
    /** Worn leggings region of the player preview in GUI pixels, relative to the inventory corner: front of the legs below the dune trim */
    private static final Rect2i LEGGINGS_REGION = new Rect2i(44, 58, 12, 3);
    /** Copper plating: hue 12-16 and saturation 0.48-0.71 as captured; skin tones are mostly above that hue or below that saturation */
    private static final IntPredicate COPPER = hued(5, 18, 0.45f, 110);
    /** Gold plating: hue 45-52 and saturation 0.67-0.84 as captured */
    private static final IntPredicate GOLD = hued(40, 60, 0.5f, 110);

    @Override
    public Identifier id() {
      return TConstruct.getResource("trimmed_armor");
    }

    @Override
    public void prepare(UiTestContext ctx) {
      // survival: in creative the inventory screen is replaced by the creative tabs, without main inventory or player preview
      ctx.sendCommand("gamemode survival");
      ctx.runOnServer(() -> {
        ServerPlayer player = ctx.serverLevel().getServer().getPlayerList().getPlayers().getFirst();
        player.getInventory().clearContent();
        for (Trim trim : WORN) {
          player.setItemSlot(trim.type.getSlot(), trim.build());
        }
        for (int i = 0; i < CARRIED.size(); i++) {
          player.getInventory().setItem(9 + i, CARRIED.get(i).build());
        }
      });
    }

    @Override
    public int prepareSettleTicks() {
      return 20;
    }

    @Override
    public void open(UiTestContext ctx) {
      LocalPlayer player = ctx.player();
      Registry<TrimMaterial> materials = player.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL);
      Registry<TrimPattern> patterns = player.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN);
      TextureAtlas blocks = ctx.mc().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
      TextureAtlas items = ctx.mc().getAtlasManager().getAtlasOrThrow(AtlasIds.ITEMS);
      TextureAtlas armorTrims = ctx.mc().getAtlasManager().getAtlasOrThrow(AtlasIds.ARMOR_TRIMS);
      ModifierId trimModifier = TinkerModifiers.trim.getId();
      for (Trim trim : WORN) {
        // the data synced to the client
        ItemStack worn = player.getItemBySlot(trim.type.getSlot());
        ToolStack tool = ToolStack.from(worn);
        String material = tool.getPersistentData().getString(TrimModule.materialKey(trimModifier));
        if (!worn.is(TinkerTools.plateArmor.get(trim.type)) || tool.getModifierLevel(trimModifier) != 1 || !trim.trimMaterial.equals(material)) {
          throw new IllegalStateException("trimmed_armor: worn " + trim.type + " lacks its trim, found " + worn + " with material '" + material + "'");
        }
        TrimMaterial trimMaterial = materials.getValue(Identifier.parse(trim.trimMaterial));
        TrimPattern pattern = patterns.getValue(Identifier.parse(trim.pattern));
        if (trimMaterial == null || pattern == null) {
          throw new IllegalStateException("trimmed_armor: client registries lack trim material " + trim.trimMaterial + " or pattern " + trim.pattern);
        }
        String suffix = trimMaterial.assets().base().suffix();
        // item icon, as TrimModifierModel looks it up: vanilla materials on the items atlas only, Tinkers materials on the blocks atlas
        Identifier icon = Identifier.withDefaultNamespace("trims/items/" + trim.type.getName() + "_trim_" + suffix);
        if (Identifier.parse(trim.trimMaterial).getNamespace().equals("minecraft")) {
          assertSprite(items, icon);
          assertNoSprite(blocks, icon);
        } else {
          assertSprite(blocks, icon);
        }
        // the worn texture, as TrimArmorTextureSupplier looks it up
        Identifier patternAsset = pattern.assetId();
        String armorPath = "trims/entity/" + (trim.type == ArmorType.LEGGINGS ? "humanoid_leggings/" : "humanoid/") + patternAsset.getPath();
        assertSprite(armorTrims, patternAsset.withPath(armorPath + "_" + suffix));
        assertWornRenderer(worn, trim, player.registryAccess());
      }
      for (String palette : EXTRA_PALETTES) {
        Identifier icon = Identifier.withDefaultNamespace("trims/items/helmet_trim_" + palette);
        assertSprite(items, icon);
        assertNoSprite(blocks, icon);
      }
      InventoryScreen screen = new InventoryScreen(player);
      ctx.mc().setScreen(screen);
      // mouse on the center of the player preview, so the player faces the camera
      ctx.moveMouse(screen.leftPos + 50.5, screen.topPos + 43);
    }

    /** Fails unless the worn stack draws through the Tinkers armor layer with a material texture and a trim, instead of a vanilla equipment asset */
    private static void assertWornRenderer(ItemStack worn, Trim trim, RegistryAccess access) {
      Equippable equippable = worn.get(DataComponents.EQUIPPABLE);
      if (equippable == null || !equippable.assetId().equals(Optional.of(ModifiableArmorItem.EQUIPMENT_ASSET))) {
        throw new IllegalStateException("trimmed_armor: worn " + trim.type + " has equipment asset " + (equippable == null ? null : equippable.assetId()) + ", vanilla would draw it instead of the armor model");
      }
      if (!(IClientItemExtensions.of(worn) instanceof ArmorModelDispatcher dispatcher)) {
        throw new IllegalStateException("trimmed_armor: worn " + trim.type + " has no armor model client extensions, nothing draws it");
      }
      TextureType textureType = TextureType.fromSlot(trim.type.getSlot());
      List<ArmorTexture> textures = dispatcher.getModel(worn).layers().stream()
        .map(layer -> layer.getArmorTexture(worn, textureType, access))
        .filter(texture -> texture != ArmorTexture.EMPTY)
        .toList();
      // the plating layer is the material's untinted texture, or a greyscale one tinted by the material's color
      MaterialRenderInfo info = MaterialRenderInfoLoader.INSTANCE.getRenderInfo(trim.material).orElseThrow(() -> new IllegalStateException("trimmed_armor: no render info for " + trim.material));
      Identifier untinted = info.texture();
      if (textures.isEmpty() || !(textures.getFirst() instanceof TintedArmorTexture plating)
          || !(plating.color() == info.vertexColor() || plating.color() == -1 && untinted != null && plating.texture().getPath().endsWith('_' + untinted.getNamespace() + '_' + untinted.getPath() + ".png"))) {
        String found = textures.isEmpty() ? "nothing" : textures.getFirst() instanceof TintedArmorTexture tinted ? tinted.texture() + " #" + Integer.toHexString(tinted.color()) : textures.getFirst().toString();
        throw new IllegalStateException("trimmed_armor: worn " + trim.type + " plating is not the " + trim.material + " texture (color #" + Integer.toHexString(info.vertexColor()) + "), found " + found);
      }
      if (textures.stream().noneMatch(texture -> texture instanceof TrimArmorTexture)) {
        throw new IllegalStateException("trimmed_armor: worn " + trim.type + " lacks a trim texture, layers give " + textures);
      }
    }

    /** Fails if the atlas has no sprite with that name */
    private static void assertSprite(TextureAtlas atlas, Identifier name) {
      if (MissingTextureAtlasSprite.getLocation().equals(atlas.getSprite(name).contents().name())) {
        throw new IllegalStateException("trimmed_armor: atlas " + atlas.location() + " has no sprite " + name);
      }
    }

    /** Fails if the atlas has a sprite with that name, as a sprite defined in two atlases is rejected by future versions */
    private static void assertNoSprite(TextureAtlas atlas, Identifier name) {
      if (atlas.getTextures().containsKey(name)) {
        throw new IllegalStateException("trimmed_armor: atlas " + atlas.location() + " duplicates the sprite " + name);
      }
    }

    @Override
    public void close(UiTestContext ctx) {
      // the frame just captured is still in the main target
      if (ctx.mc().screen instanceof InventoryScreen screen) {
        try (NativeImage image = RenderTargetCapture.capture(ctx.mc().getMainRenderTarget(), false)) {
          double scale = (double) image.getWidth() / ctx.mc().getWindow().getGuiScaledWidth();
          // the copper plating beside a neutral stripe: grey (vanilla iron) armor fails the first, skin or hair fails at least one of them
          assertGuiRegion(image, scale, screen, HELMET_REGION, "trimmed_armor: worn copper helmet", COPPER);
          assertGuiRegion(image, scale, screen, HELMET_TRIM_REGION, "trimmed_armor: iron trim on the worn helmet", NEUTRAL);
          assertGuiRegion(image, scale, screen, LEGGINGS_REGION, "trimmed_armor: worn gold leggings", GOLD);
        }
      } else {
        throw new IllegalStateException("trimmed_armor: the inventory closed before the capture, found " + ctx.mc().screen);
      }
      ctx.mc().setScreen(null);
    }

    /** Asserts a region of the screen given in GUI pixels relative to the inventory corner, see {@link #assertRegion} */
    private static void assertGuiRegion(NativeImage image, double scale, AbstractContainerScreen<?> screen, Rect2i region, String description, IntPredicate test) {
      assertRegion(image,
        (int) Math.round((screen.leftPos + region.getX()) * scale), (int) Math.round((screen.topPos + region.getY()) * scale),
        (int) Math.round((screen.leftPos + region.getX() + region.getWidth()) * scale), (int) Math.round((screen.topPos + region.getY() + region.getHeight()) * scale),
        description, test);
    }

    @Override
    public void cleanup(UiTestContext ctx) {
      // here rather than in close() so a failed assert in open() doesn't leave the rest of the suite in survival
      ctx.mc().setScreen(null);
      ctx.sendCommand("clear @s");
      ctx.sendCommand("gamemode creative");
    }
  }

  /**
   * No menu: Tinkers armor worn by entities other than the player, drawn by {@code TinkerArmorLayer} on their renderers (M6 Phase 4): an armor
   * stand with the slimeskull (bone skull on skyslime) and slime suit, an armor stand with travelers gear and a zombie with plate armor. Asserts
   * the renderer of each spawned entity has the Tinkers armor layer, then samples the frame: the slime suit must be teal, the travelers cuirass
   * leather brown and the zombie's plate gold, which the bare stands and zombie are not.
   */
  private static class WornArmorScenario implements UiTestScenario {
    private final BlockPos origin = SITE.offset(100, 0, 0);
    /** Entities spawned by this scenario (on the server thread), removed on cleanup */
    private final List<LivingEntity> spawned = new CopyOnWriteArrayList<>();

    @Override
    public Identifier id() {
      return TConstruct.getResource("worn_armor");
    }

    /** Builds an armor piece from the given materials */
    private static ItemStack armor(ModifiableArmorItem item, MaterialId... materials) {
      return ToolStack.createTool(item, item.getToolDefinition(), MaterialNBT.of(Arrays.stream(materials).map(material -> MaterialVariant.of(material, "")).toArray(MaterialVariant[]::new))).createStack();
    }

    /** Equips the four pieces and adds the entity facing the player, west of it */
    private void spawn(ServerLevel level, LivingEntity entity, int z, ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet) {
      entity.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + z + 0.5);
      entity.setYRot(90);
      entity.setYHeadRot(90);
      entity.setYBodyRot(90);
      entity.setItemSlot(EquipmentSlot.HEAD, head);
      entity.setItemSlot(EquipmentSlot.CHEST, chest);
      entity.setItemSlot(EquipmentSlot.LEGS, legs);
      entity.setItemSlot(EquipmentSlot.FEET, feet);
      level.addFreshEntity(entity);
      spawned.add(entity);
    }

    @Override
    public void prepare(UiTestContext ctx) {
      ctx.sendCommand("tp @s " + (origin.getX() - 4) + " " + origin.getY() + " " + origin.getZ());
      ctx.runOnServer(() -> {
        ServerLevel level = ctx.serverLevel();
        EnumObject<ArmorType, ModifiableArmorItem> slime = TinkerTools.slimesuit;
        spawn(level, new ArmorStand(level, 0, 0, 0), -2,
              armor(slime.get(ArmorType.HELMET), MaterialIds.bone, MaterialIds.skyslime), armor(slime.get(ArmorType.CHESTPLATE), MaterialIds.bone, MaterialIds.skyslime),
              armor(slime.get(ArmorType.LEGGINGS), MaterialIds.bone, MaterialIds.skyslime), armor(slime.get(ArmorType.BOOTS), MaterialIds.bone, MaterialIds.skyslime));
        EnumObject<ArmorType, ModifiableArmorItem> travelers = TinkerTools.travelersGear;
        spawn(level, new ArmorStand(level, 0, 0, 0), 0,
              armor(travelers.get(ArmorType.HELMET), MaterialIds.copper, MaterialIds.leather), armor(travelers.get(ArmorType.CHESTPLATE), MaterialIds.copper, MaterialIds.leather),
              armor(travelers.get(ArmorType.LEGGINGS), MaterialIds.copper, MaterialIds.leather), armor(travelers.get(ArmorType.BOOTS), MaterialIds.copper, MaterialIds.leather));
        Zombie zombie = new Zombie(level);
        zombie.addTag(UiTestSuite.ALLOW_MOB_TAG);
        zombie.setNoAi(true);
        zombie.setSilent(true);
        EnumObject<ArmorType, ModifiableArmorItem> plate = TinkerTools.plateArmor;
        spawn(level, zombie, 2,
              armor(plate.get(ArmorType.HELMET), MaterialIds.gold, MaterialIds.gold), armor(plate.get(ArmorType.CHESTPLATE), MaterialIds.gold, MaterialIds.gold),
              armor(plate.get(ArmorType.LEGGINGS), MaterialIds.gold, MaterialIds.gold), armor(plate.get(ArmorType.BOOTS), MaterialIds.gold, MaterialIds.gold));
      });
    }

    @Override
    public int prepareSettleTicks() {
      return 20; // entity tracking sync
    }

    @Override
    public void open(UiTestContext ctx) {
      ClientLevel level = Objects.requireNonNull(ctx.mc().level);
      if (spawned.size() != 3) {
        throw new IllegalStateException("worn_armor: expected 3 spawned entities, found " + spawned);
      }
      // the client copies of the entities this scenario spawned (the integrated server and client share entity ids)
      for (LivingEntity spawnedEntity : spawned) {
        Entity entity = level.getEntity(spawnedEntity.getId());
        if (entity == null) {
          throw new IllegalStateException("worn_armor: " + spawnedEntity + " is not on the client");
        }
        if (!(ctx.mc().getEntityRenderDispatcher().getRenderer(entity) instanceof LivingEntityRenderer<?,?,?> renderer) || renderer.layers.stream().noneMatch(layer -> layer instanceof TinkerArmorLayer)) {
          throw new IllegalStateException("worn_armor: the renderer of " + entity + " lacks the Tinkers armor layer");
        }
      }
      ctx.lookAt(origin.offset(0, 1, 0));
    }

    /** Chest of the slime suit stand, as fractions of the frame: skyslime is dark teal in this light, hue 178-180 and saturation 0.39-0.46 as captured */
    private static final float[] SLIME_REGION = {350 / 1280f, 390 / 720f, 400 / 1280f, 490 / 720f};
    private static final IntPredicate SKYSLIME = hued(165, 195, 0.3f, 40);
    /** Leather cuirass of the travelers stand: hue 10-25, saturation from 0.48; the bare stand's oak is about hue 38 */
    private static final float[] TRAVELERS_REGION = {615 / 1280f, 440 / 720f, 645 / 1280f, 500 / 720f};
    private static final IntPredicate LEATHER = hued(0, 25, 0.4f, 30);
    /** Gold chestplate of the zombie: hue 44-58, saturation from 0.61; the bare zombie is green and blue */
    private static final float[] ZOMBIE_REGION = {865 / 1280f, 420 / 720f, 925 / 1280f, 510 / 720f};
    private static final IntPredicate GOLD = hued(40, 60, 0.5f, 60);

    @Override
    public void close(UiTestContext ctx) {
      // the frame just captured is still in the main target
      try (NativeImage image = RenderTargetCapture.capture(ctx.mc().getMainRenderTarget(), false)) {
        assertFrameRegion(image, SLIME_REGION, "worn_armor: skyslime suit on the armor stand", SKYSLIME);
        assertFrameRegion(image, TRAVELERS_REGION, "worn_armor: travelers cuirass on the armor stand", LEATHER);
        assertFrameRegion(image, ZOMBIE_REGION, "worn_armor: gold plate on the zombie", GOLD);
      }
    }

    /** Asserts a region given in fractions of the frame, see {@link #assertRegion} */
    private static void assertFrameRegion(NativeImage image, float[] region, String description, IntPredicate test) {
      int width = image.getWidth(), height = image.getHeight();
      assertRegion(image, Math.round(region[0] * width), Math.round(region[1] * height), Math.round(region[2] * width), Math.round(region[3] * height), description, test);
    }

    @Override
    public void cleanup(UiTestContext ctx) {
      List<LivingEntity> entities = List.copyOf(spawned);
      spawned.clear();
      ctx.runOnServer(() -> entities.forEach(Entity::discard));
    }
  }

  /**
   * Draws a grid of item models and modifier icons whose atlas lookups the 1.21.11 items/blocks atlas split changed: the copper can and a tank
   * (fluid container models, one layer per atlas), material-less parts (the base part texture), molten fluid buckets, and modifier icons made of
   * {@code minecraft:item/...} textures (now on the items atlas) over Tinkers {@code gui/modifiers} frames (blocks atlas). Asserts each icon
   * texture resolves through {@code BLOCK_OR_ITEM}, then captures the grid on a plain screen.
   */
  private static class ItemAtlasGridScenario implements UiTestScenario {
    /** Modifiers whose icons start with a vanilla item texture, see {@code tinkering/modifier_icons.json} */
    private static final List<ModifierId> ICON_MODIFIERS = List.of(ModifierIds.soulbound, ModifierIds.diamond, ModifierIds.emerald, ModifierIds.netherite, TinkerModifiers.overslime.getId());

    private static List<ItemStack> stacks() {
      return List.of(
        new ItemStack(TinkerSmeltery.copperCan),
        CopperCanItem.setFluid(new ItemStack(TinkerSmeltery.copperCan), new FluidStack(Fluids.WATER, FluidValues.BOTTLE)),
        CopperCanItem.setFluid(new ItemStack(TinkerSmeltery.copperCan), new FluidStack(TinkerFluids.moltenCobalt.get(), FluidValues.INGOT)),
        new ItemStack(TinkerSmeltery.searedTank.get(TankType.FUEL_TANK)),
        new ItemStack(TinkerToolParts.pickHead.get()),
        new ItemStack(TinkerToolParts.toolHandle.get()),
        new ItemStack(TinkerToolParts.plating.get(ArmorType.HELMET)),
        new ItemStack(TinkerFluids.moltenIron.getBucket()),
        new ItemStack(TinkerFluids.moltenCobalt.getBucket()),
        new ItemStack(TinkerModifiers.modifierCrystal.get()));
    }

    @Override
    public Identifier id() {
      return TConstruct.getResource("item_atlas_grid");
    }

    @Override
    public void prepare(UiTestContext ctx) {}

    @Override
    public int prepareSettleTicks() {
      return 5;
    }

    @Override
    public void open(UiTestContext ctx) {
      List<Modifier> modifiers = new ArrayList<>();
      for (ModifierId id : ICON_MODIFIERS) {
        if (!ModifierManager.INSTANCE.contains(id)) {
          throw new IllegalStateException("item_atlas_grid: modifier " + id + " is not loaded on the client");
        }
        modifiers.add(ModifierManager.INSTANCE.get(id));
      }
      // the vanilla item textures used by those icons must resolve through the items atlas, not to the missing sprite
      for (String texture : List.of("item/echo_shard", "item/diamond", "item/emerald", "item/netherite_ingot", "item/slime_ball")) {
        TextureAtlasSprite sprite = ModelHelper.getSprite(new Material(ModelManager.BLOCK_OR_ITEM, Identifier.withDefaultNamespace(texture)));
        if (MissingTextureAtlasSprite.getLocation().equals(sprite.contents().name())) {
          throw new IllegalStateException("item_atlas_grid: modifier icon texture minecraft:" + texture + " resolves to the missing sprite");
        }
      }
      ctx.mc().setScreen(new GridScreen(stacks(), modifiers));
      ctx.parkMouse();
    }
  }

  /* Pixel asserts on a captured frame */

  /** Bright neutral (grey to white) pixels, such as an iron trim */
  private static final IntPredicate NEUTRAL = color -> {
    int max = Math.max(ARGB.red(color), Math.max(ARGB.green(color), ARGB.blue(color)));
    int min = Math.min(ARGB.red(color), Math.min(ARGB.green(color), ARGB.blue(color)));
    return max >= 120 && max - min < 0.2f * max;
  };

  /**
   * Pixels of a hue range that are bright and saturated enough, so grey (vanilla iron armor) and dark (shadow, hair) pixels fail.
   * @param minHue         Minimum hue in degrees
   * @param maxHue         Maximum hue in degrees
   * @param minSaturation  Minimum saturation, {@code (max - min) / max}
   * @param minBrightness  Minimum value of the brightest channel
   */
  private static IntPredicate hued(float minHue, float maxHue, float minSaturation, int minBrightness) {
    return color -> {
      int r = ARGB.red(color), g = ARGB.green(color), b = ARGB.blue(color);
      int max = Math.max(r, Math.max(g, b));
      int min = Math.min(r, Math.min(g, b));
      if (max < minBrightness || max - min < minSaturation * max) {
        return false;
      }
      float hue = hue(r, g, b, max, min);
      return hue >= minHue && hue <= maxHue;
    };
  }

  /** Hue in degrees of the color, 0 for grey */
  private static float hue(int r, int g, int b, int max, int min) {
    if (max == min) {
      return 0;
    }
    float delta = max - min;
    float hue;
    if (max == r) {
      hue = (g - b) / delta;
    } else if (max == g) {
      hue = 2 + (b - r) / delta;
    } else {
      hue = 4 + (r - g) / delta;
    }
    hue *= 60;
    return hue < 0 ? hue + 360 : hue;
  }

  /**
   * Fails unless 3/4 of the pixels in the image region pass the test. Logs the counts and average color, to recalibrate the regions.
   * @param x0  First column in image pixels
   * @param y0  First row in image pixels
   * @param x1  Column after the last
   * @param y1  Row after the last
   */
  private static void assertRegion(NativeImage image, int x0, int y0, int x1, int y1, String description, IntPredicate test) {
    int matching = 0;
    long red = 0, green = 0, blue = 0;
    for (int y = y0; y < y1; y++) {
      for (int x = x0; x < x1; x++) {
        int color = image.getPixel(x, y);
        red += ARGB.red(color);
        green += ARGB.green(color);
        blue += ARGB.blue(color);
        if (test.test(color)) {
          matching++;
        }
      }
    }
    int total = Math.max(1, (x1 - x0) * (y1 - y0));
    String stats = String.format("%d of %d pixels match in [%d,%d..%d,%d], average #%02X%02X%02X", matching, total, x0, y0, x1, y1, red / total, green / total, blue / total);
    TConstruct.LOG.info("{}: {}", description, stats);
    if (matching * 4 < total * 3) {
      throw new IllegalStateException(description + " has the wrong color: " + stats);
    }
  }

  /** Plain screen drawing items in a row at 2x scale, and modifier icons below */
  private static class GridScreen extends Screen {
    private final List<ItemStack> stacks;
    private final List<Modifier> modifiers;

    private GridScreen(List<ItemStack> stacks, List<Modifier> modifiers) {
      super(Component.empty());
      this.stacks = stacks;
      this.modifiers = modifiers;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      super.render(graphics, mouseX, mouseY, partialTick);
      int left = 20;
      graphics.pose().pushMatrix();
      graphics.pose().scale(2, 2);
      for (int i = 0; i < stacks.size(); i++) {
        graphics.renderItem(stacks.get(i), left / 2 + i * 20, 20);
      }
      graphics.pose().popMatrix();
      for (int i = 0; i < modifiers.size(); i++) {
        ModifierIconManager.renderIcon(graphics, modifiers.get(i), left + i * 40, 110, 100, 32);
      }
    }
  }

  /** No menu: boosts the player past 20 HP and captures the in-world HUD to verify Mantle's custom extra-heart renderer. */
  private static class ExtraHeartsScenario implements UiTestScenario {
    private final BlockPos pos = SITE.offset(60, 0, 0);

    @Override
    public Identifier id() {
      return TConstruct.getResource("extra_hearts");
    }

    @Override
    public void prepare(UiTestContext ctx) {
      ctx.sendCommand("tp @s " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
      // 40 max HP -> a full extra heart row, so the custom multi-color rows render; heal to full so all show filled
      ctx.sendCommand("effect give @s minecraft:health_boost 600 4 true");
      ctx.sendCommand("effect give @s minecraft:instant_health 1 20 true");
    }

    @Override
    public int prepareSettleTicks() {
      return 20;
    }

    @Override
    public void open(UiTestContext ctx) {
      // no screen — the in-world HUD health bar is the capture target
    }

    @Override
    public void close(UiTestContext ctx) { /* nothing open */ }
  }

  /** Fills the fluid handler of the block at the position through its capability in one committed transaction, failing unless all of it fits. */
  private static void fillFluid(ServerLevel level, BlockPos pos, @Nullable Direction side, FluidStack fluid) {
    ResourceHandler<FluidResource> handler = level.getCapability(Capabilities.Fluid.BLOCK, pos, side);
    if (handler == null) {
      throw new IllegalStateException("No fluid handler at " + pos + ", found " + level.getBlockState(pos));
    }
    int filled;
    try (Transaction tx = Transaction.openRoot()) {
      filled = handler.insert(FluidResource.of(fluid), fluid.getAmount(), tx);
      tx.commit();
    }
    if (filled != fluid.getAmount()) {
      throw new IllegalStateException(level.getBlockState(pos) + " at " + pos + " accepted only " + filled + "/" + fluid.getAmount() + " mB");
    }
  }

  /** Gets the client side block entity at the position as the given type, failing with a scenario specific message otherwise. */
  private static <T> T clientBlockEntity(UiTestContext ctx, BlockPos pos, Class<T> type, String scenario) {
    BlockEntity be = Objects.requireNonNull(ctx.mc().level).getBlockEntity(pos);
    if (!type.isInstance(be)) {
      throw new IllegalStateException(scenario + " expected a " + type.getSimpleName() + " at " + pos + " on the client, found " + be);
    }
    return type.cast(be);
  }

  /**
   * No menu: the fluid holding block entity renderers in the world, which 1.21.9 moved to extracted render states. From left to right: a melter
   * on a fuel tank holding molten gold, an alloyer holding molten cobalt, a gauge on a half full tank of molten copper, and a tank pouring molten
   * iron through a faucet into a channel, which flows sideways into a second channel and down into a casting basin. The capture is taken mid
   * pour, so the faucet stream, both channel flows and the partially filled basin show.
   */
  private static class FluidBlocksScenario implements UiTestScenario {
    private static final String NAME = "fluid_blocks";
    private final BlockPos origin = SITE.offset(110, 0, 0);
    private final BlockPos melter = origin.offset(0, 1, -3);
    private final BlockPos alloyer = origin.offset(0, 1, -1);
    private final BlockPos gauge = origin.offset(0, 1, 1);
    private final BlockPos faucet = origin.offset(0, 2, 3);
    private final BlockPos basin = origin.offset(0, 0, 4);

    @Override
    public Identifier id() {
      return TConstruct.getResource(NAME);
    }

    @Override
    public void prepare(UiTestContext ctx) {
      ctx.sendCommand("tp @s " + (origin.getX() - 5) + " " + origin.getY() + " " + origin.getZ());
      ctx.runOnServer(() -> {
        ServerLevel level = ctx.serverLevel();
        // the saved world persists across runs: clear the site first so every block entity starts empty
        for (int y = 0; y <= 2; y++) {
          for (int z = -3; z <= 4; z++) {
            level.setBlockAndUpdate(origin.offset(0, y, z), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(origin.offset(1, y, z), Blocks.AIR.defaultBlockState());
          }
        }
        // melter facing the camera; the fuel tank goes in after it so its shape update forms the melter (see MelterScenario)
        level.setBlockAndUpdate(melter, TinkerSmeltery.searedMelter.get().defaultBlockState().setValue(ControllerBlock.FACING, Direction.WEST));
        level.setBlockAndUpdate(melter.below(), TinkerSmeltery.searedTank.get(TankType.FUEL_TANK).defaultBlockState());
        SmelteryRigs.fillTank(level, melter.below(), new FluidStack(Fluids.LAVA, 2000));
        fillFluid(level, melter, null, new FluidStack(TinkerFluids.moltenGold.get(), FluidValues.INGOT * 12));
        // alloyer facing the camera, half full
        level.setBlockAndUpdate(alloyer, TinkerSmeltery.scorchedAlloyer.get().defaultBlockState().setValue(ControllerBlock.FACING, Direction.WEST));
        level.setBlockAndUpdate(alloyer.below(), TinkerSmeltery.scorchedTank.get(TankType.FUEL_TANK).defaultBlockState());
        fillFluid(level, alloyer, null, new FluidStack(TinkerFluids.moltenCobalt.get(), FluidValues.INGOT * 24));
        // gauge on the camera side of a half full ingot tank
        BlockPos gaugeTank = gauge.east();
        level.setBlockAndUpdate(gaugeTank, TinkerSmeltery.searedTank.get(TankType.INGOT_TANK).defaultBlockState());
        SmelteryRigs.fillTank(level, gaugeTank, new FluidStack(TinkerFluids.moltenCopper.get(), FluidValues.INGOT * 24));
        level.setBlockAndUpdate(gauge, TinkerSmeltery.obsidianGauge.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.WEST));
        // pour rig, built bottom up: basin, the channel above it flowing down, the channel feeding it, the source tank and its faucet.
        // 7 ingots never fill the 9 ingot block cast, so the basin stays partially filled
        level.setBlockAndUpdate(basin, TinkerSmeltery.searedBasin.get().defaultBlockState());
        level.setBlockAndUpdate(basin.above(), TinkerSmeltery.searedChannel.get().defaultBlockState()
          .setValue(ChannelBlock.DOWN, true).setValue(ChannelBlock.NORTH, ChannelBlock.ChannelConnection.IN));
        level.setBlockAndUpdate(faucet.below(), TinkerSmeltery.searedChannel.get().defaultBlockState()
          .setValue(ChannelBlock.SOUTH, ChannelBlock.ChannelConnection.OUT));
        BlockPos source = faucet.east();
        level.setBlockAndUpdate(source, TinkerSmeltery.searedTank.get(TankType.INGOT_TANK).defaultBlockState());
        SmelteryRigs.fillTank(level, source, new FluidStack(TinkerFluids.moltenIron.get(), FluidValues.INGOT * 7));
        level.setBlockAndUpdate(faucet, TinkerSmeltery.searedFaucet.get().defaultBlockState().setValue(FaucetBlock.FACING, Direction.WEST));
      });
    }

    @Override
    public int prepareSettleTicks() {
      return 30; // block entity data sync + chunk remesh
    }

    @Override
    public void open(UiTestContext ctx) {
      ctx.useBlock(faucet); // opens the tap
      ctx.lookAt(origin.offset(0, 1, 1));
    }

    @Override
    public int settleTicks() {
      return 40; // the 630 mB source pours at 10 mB per tick, so this is mid pour
    }

    @Override
    public void close(UiTestContext ctx) {
      // every renderer must have something to draw, else the capture says nothing
      if (clientBlockEntity(ctx, melter, MelterBlockEntity.class, NAME).getTank().getFluid().isEmpty()) {
        throw new IllegalStateException(NAME + ": the melter tank is empty on the client");
      }
      if (clientBlockEntity(ctx, alloyer, AlloyerBlockEntity.class, NAME).getTank().getFluid().isEmpty()) {
        throw new IllegalStateException(NAME + ": the alloyer tank is empty on the client");
      }
      if (!clientBlockEntity(ctx, faucet, FaucetBlockEntity.class, NAME).isPouring()) {
        throw new IllegalStateException(NAME + ": the faucet is not pouring at capture");
      }
      int basinFluid = clientBlockEntity(ctx, basin, CastingBlockEntity.class, NAME).getTank().getFluid().getAmount();
      if (basinFluid <= 0 || basinFluid >= FluidValues.INGOT * 7) {
        throw new IllegalStateException(NAME + ": expected the basin partially filled through the channels at capture, found " + basinFluid + " mB");
      }
    }
  }

  /**
   * No menu: a casting table (ingot cast) and a casting basin filled with molten gold through their fluid capability, captured while the
   * recipe output fades in tinted orange to white. The game is frozen with {@code /tick freeze} once the table is past half its cooling
   * time, so the capture always shows the table mid fade (and the slower basin earlier in its fade).
   */
  private static class CastingCoolingScenario implements UiTestScenario {
    private static final String NAME = "casting_cooling";
    private final BlockPos origin = SITE.offset(120, 0, 0);
    private final BlockPos table = origin.offset(0, 0, -1);
    private final BlockPos basin = origin.offset(0, 0, 1);
    private final Consumer<ClientTickEvent.Post> freezer = this::freezeWhenHalfCooled;
    private boolean frozen = false;

    @Override
    public Identifier id() {
      return TConstruct.getResource(NAME);
    }

    @Override
    public void prepare(UiTestContext ctx) {
      frozen = false;
      ctx.sendCommand("tp @s " + (origin.getX() - 3) + " " + origin.getY() + " " + origin.getZ());
      ctx.runOnServer(() -> {
        ServerLevel level = ctx.serverLevel();
        // replace both blocks so leftovers of a previous run (outputs, casts) are gone
        level.setBlockAndUpdate(table, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(basin, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(table, TinkerSmeltery.searedTable.get().defaultBlockState());
        level.setBlockAndUpdate(basin, TinkerSmeltery.searedBasin.get().defaultBlockState());
        if (level.getBlockEntity(table) instanceof CastingBlockEntity casting) {
          casting.setItem(CastingBlockEntity.INPUT, new ItemStack(TinkerSmeltery.ingotCast.get()));
        } else {
          throw new IllegalStateException("Expected casting table at " + table + ", found " + level.getBlockEntity(table));
        }
      });
    }

    @Override
    public int prepareSettleTicks() {
      return 20;
    }

    @Override
    public void open(UiTestContext ctx) {
      ctx.runOnServer(() -> {
        ServerLevel level = ctx.serverLevel();
        // a full fill starts the recipe on commit
        fillFluid(level, table, Direction.UP, new FluidStack(TinkerFluids.moltenGold.get(), FluidValues.INGOT));
        fillFluid(level, basin, Direction.UP, new FluidStack(TinkerFluids.moltenGold.get(), FluidValues.METAL_BLOCK));
      });
      ctx.lookAt(origin);
      NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, freezer);
    }

    /** Freezes the game once the client table is past 55% of its cooling time; the renderer fades from the client timer. */
    private void freezeWhenHalfCooled(ClientTickEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (frozen || mc.level == null || !(mc.level.getBlockEntity(table) instanceof CastingBlockEntity casting)) {
        return;
      }
      int coolingTime = casting.getCoolingTime();
      if (coolingTime > 0 && casting.getTimer() * 100 >= coolingTime * 55) {
        new UiTestContext(mc).sendCommand("tick freeze");
        frozen = true;
        NeoForge.EVENT_BUS.unregister(freezer);
      }
    }

    @Override
    public int settleTicks() {
      return 80; // the gold ingot cools in about 65 ticks, the freeze lands past half of it
    }

    @Override
    public void close(UiTestContext ctx) {
      if (!frozen) {
        throw new IllegalStateException(NAME + ": the casting table never got past half its cooling time");
      }
      for (BlockPos pos : List.of(table, basin)) {
        CastingBlockEntity casting = clientBlockEntity(ctx, pos, CastingBlockEntity.class, NAME);
        if (casting.getCoolingTime() <= 0 || casting.getTimer() <= 0 || casting.getTimer() >= casting.getCoolingTime()
            || !casting.getItem(CastingBlockEntity.OUTPUT).isEmpty() || casting.getRecipeOutput().isEmpty()) {
          throw new IllegalStateException(NAME + ": expected the casting at " + pos + " mid cooling, found timer " + casting.getTimer() + "/" + casting.getCoolingTime()
                                          + ", output " + casting.getItem(CastingBlockEntity.OUTPUT) + ", recipe output " + casting.getRecipeOutput());
        }
      }
    }

    @Override
    public void cleanup(UiTestContext ctx) {
      NeoForge.EVENT_BUS.unregister(freezer);
      ctx.sendCommand("tick unfreeze");
    }
  }

  /** Screen that draws nothing and does not pause the game, so the world keeps running behind it without the vanilla attack key handling. */
  private static class PassThroughScreen extends Screen {
    private PassThroughScreen() {
      super(Component.empty());
    }

    @Override
    public boolean isPauseScreen() {
      return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
  }

  /**
   * No menu: the player holds a sledge hammer (3x3) and looks at the center of a 3x3 stone wall, so the extra block outlines from
   * {@code ToolRenderEvents} (1.21.9: a custom block outline renderer) draw around the 8 neighbors next to the vanilla outline, black or with the
   * high contrast outline option. With {@code breaking}, the center block is mined to half progress in survival, so the extra block breaking
   * overlay shows too. Mining runs behind a {@link PassThroughScreen}: with no screen, vanilla stops any mining every tick the attack key is up.
   */
  private static class AoeOutlineScenario implements UiTestScenario {
    private final String name;
    private final BlockPos center;
    private final boolean highContrast;
    private final boolean breaking;
    private final Consumer<ClientTickEvent.Post> miner = this::mineTick;
    private boolean previousHighContrast;
    private boolean started;

    private AoeOutlineScenario(String name, BlockPos center, boolean highContrast, boolean breaking) {
      this.name = name;
      this.center = center.above();
      this.highContrast = highContrast;
      this.breaking = breaking;
    }

    @Override
    public Identifier id() {
      return TConstruct.getResource(name);
    }

    @Override
    public void prepare(UiTestContext ctx) {
      started = false;
      previousHighContrast = ctx.mc().options.highContrastBlockOutline().get();
      ctx.mc().options.highContrastBlockOutline().set(highContrast);
      // survival: creative breaks blocks instantly
      ctx.sendCommand("gamemode survival");
      ctx.sendCommand("tp @s " + (center.getX() - 3) + " " + (center.getY() - 1) + " " + center.getZ());
      ctx.runOnServer(() -> {
        ServerLevel level = ctx.serverLevel();
        for (int y = -1; y <= 1; y++) {
          for (int z = -1; z <= 1; z++) {
            level.setBlockAndUpdate(center.offset(0, y, z), Blocks.STONE.defaultBlockState());
          }
        }
        ToolStack hammer = ToolStack.createTool(TinkerTools.sledgeHammer.get(), TinkerTools.sledgeHammer.get().getToolDefinition(), MaterialNBT.of(
          MaterialVariant.of(MaterialIds.iron, ""), MaterialVariant.of(MaterialIds.wood, ""), MaterialVariant.of(MaterialIds.iron, ""), MaterialVariant.of(MaterialIds.iron, "")));
        Inventory inventory = level.getServer().getPlayerList().getPlayers().getFirst().getInventory();
        inventory.clearContent();
        inventory.setItem(0, hammer.createStack());
      });
    }

    @Override
    public int prepareSettleTicks() {
      return 20;
    }

    @Override
    public void open(UiTestContext ctx) {
      ctx.player().getInventory().setSelectedSlot(0);
      ctx.lookAt(center);
      if (breaking) {
        ctx.mc().setScreen(new PassThroughScreen());
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, miner);
      }
    }

    /** Starts mining the center block, then keeps mining until half way and stops there, leaving the progress in place. */
    private void mineTick(ClientTickEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      MultiPlayerGameMode gameMode = mc.gameMode;
      if (gameMode == null || !(mc.screen instanceof PassThroughScreen)) {
        return;
      }
      if (!started) {
        gameMode.startDestroyBlock(center, Direction.WEST);
        started = true;
      } else if (gameMode.getDestroyStage() >= 5) {
        NeoForge.EVENT_BUS.unregister(miner);
      } else {
        gameMode.continueDestroyBlock(center, Direction.WEST);
      }
    }

    @Override
    public int settleTicks() {
      return breaking ? 60 : 20;
    }

    @Override
    public void close(UiTestContext ctx) {
      if (!(ctx.mc().hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(center)) {
        throw new IllegalStateException(name + " expected the crosshair on " + center + ", found " + ctx.mc().hitResult);
      }
      if (!ctx.player().getMainHandItem().is(TinkerTools.sledgeHammer.get())) {
        throw new IllegalStateException(name + " expected a sledge hammer in the main hand, found " + ctx.player().getMainHandItem());
      }
      if (breaking) {
        MultiPlayerGameMode gameMode = Objects.requireNonNull(ctx.mc().gameMode);
        if (!gameMode.isDestroying() || gameMode.getDestroyStage() < 1) {
          throw new IllegalStateException(name + " expected the center block partially mined, found stage " + gameMode.getDestroyStage());
        }
      }
      ctx.mc().setScreen(null);
    }

    @Override
    public void cleanup(UiTestContext ctx) {
      NeoForge.EVENT_BUS.unregister(miner);
      ctx.mc().setScreen(null);
      ctx.mc().options.highContrastBlockOutline().set(previousHighContrast);
      ctx.sendCommand("clear @s");
      ctx.sendCommand("gamemode creative");
    }
  }

  /**
   * Input checks on the tinker station with synthetic events (1.21.9 event based input): picks up a stack from the player inventory,
   * double-clicks the emptied slot to gather the matching stacks (vanilla takes the double click from the event since 1.21.9, the
   * Tinkers screens forward it), then clicks the station tab with the stack still carried, which must not drop it ({@code hasClickedOutside}).
   * Captures the screen with the gathered stack on the cursor.
   */
  private static class StationCarriedClicksScenario implements UiTestScenario {
    private static final String NAME = "station_carried_clicks";
    /** Cobblestone stacks placed in the first main inventory slots, gathered into one stack */
    private static final int[] COUNTS = {10, 20, 5};
    /** First main inventory slot, after the hotbar */
    private static final int FIRST_SLOT = 9;
    private final BlockPos pos = SITE.offset(140, 0, 0);
    private final Consumer<ClientTickEvent.Post> clicker = this::clickTick;
    private int step = 0;

    @Override
    public Identifier id() {
      return TConstruct.getResource(NAME);
    }

    @Override
    public void prepare(UiTestContext ctx) {
      step = 0;
      ctx.sendCommand("tp @s " + (pos.getX() - 2) + " " + pos.getY() + " " + pos.getZ());
      ctx.runOnServer(() -> {
        ServerLevel level = ctx.serverLevel();
        level.setBlockAndUpdate(pos, TinkerTables.tinkerStation.get().defaultBlockState());
        Inventory inventory = level.getServer().getPlayerList().getPlayers().getFirst().getInventory();
        inventory.clearContent();
        for (int i = 0; i < COUNTS.length; i++) {
          inventory.setItem(FIRST_SLOT + i, new ItemStack(Items.COBBLESTONE, COUNTS[i]));
        }
      });
    }

    @Override
    public int prepareSettleTicks() {
      return 20;
    }

    @Override
    public void open(UiTestContext ctx) {
      ctx.useBlock(pos);
      NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, clicker);
    }

    /** Finds the menu slot showing the given player inventory slot */
    @Nullable
    private static Slot playerSlot(AbstractContainerMenu menu, int index) {
      for (Slot slot : menu.slots) {
        if (slot.container instanceof Inventory && slot.getContainerSlot() == index) {
          return slot;
        }
      }
      return null;
    }

    /** Presses and releases the left mouse button at the GUI position */
    private static void click(AbstractContainerScreen<?> screen, double x, double y, boolean doubleClick) {
      MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0));
      screen.mouseClicked(event, doubleClick);
      screen.mouseReleased(event);
    }

    /** One click per tick once the menu is synced, so each click's slot change is applied before the next */
    private void clickTick(ClientTickEvent.Post event) {
      if (!(Minecraft.getInstance().screen instanceof TinkerStationScreen screen)) {
        return;
      }
      Slot first = playerSlot(screen.getMenu(), FIRST_SLOT);
      switch (step) {
        case 0 -> {
          if (first == null || !first.getItem().is(Items.COBBLESTONE) || first.getItem().getCount() != COUNTS[0]) {
            return; // menu contents not synced yet
          }
          click(screen, screen.getGuiLeft() + first.x + 8, screen.getGuiTop() + first.y + 8, false);
          step = 1;
        }
        case 1 -> {
          Objects.requireNonNull(first);
          click(screen, screen.getGuiLeft() + first.x + 8, screen.getGuiTop() + first.y + 8, true);
          step = 2;
        }
        default -> {
          TinkerTabsWidget tabs = screen.children().stream().filter(TinkerTabsWidget.class::isInstance).map(TinkerTabsWidget.class::cast)
            .findFirst().orElseThrow(() -> new IllegalStateException(NAME + ": the station has no tabs widget"));
          Rect2i area = tabs.getArea();
          click(screen, area.getX() + area.getWidth() / 2.0, area.getY() + area.getHeight() / 2.0, false);
          step = 3;
          NeoForge.EVENT_BUS.unregister(clicker);
        }
      }
    }

    @Override
    public int settleTicks() {
      return 30; // menu round trip + three clicks + slot sync
    }

    @Override
    public void close(UiTestContext ctx) {
      if (!(ctx.mc().screen instanceof TinkerStationScreen screen)) {
        throw new IllegalStateException(NAME + " expected TinkerStationScreen at capture, found " + ctx.mc().screen);
      }
      if (step != 3) {
        throw new IllegalStateException(NAME + " stopped at click step " + step);
      }
      int total = COUNTS[0] + COUNTS[1] + COUNTS[2];
      ItemStack carried = screen.getMenu().getCarried();
      if (!carried.is(Items.COBBLESTONE) || carried.getCount() != total) {
        throw new IllegalStateException(NAME + " expected " + total + " gathered cobblestone on the cursor, found " + carried);
      }
      for (Slot slot : screen.getMenu().slots) {
        if (slot.container instanceof Inventory && slot.getItem().is(Items.COBBLESTONE)) {
          throw new IllegalStateException(NAME + ": the double click left " + slot.getItem() + " in inventory slot " + slot.getContainerSlot());
        }
      }
      List<ItemEntity> dropped = ctx.player().level().getEntitiesOfClass(ItemEntity.class, ctx.player().getBoundingBox().inflate(16));
      if (!dropped.isEmpty()) {
        throw new IllegalStateException(NAME + ": the tab click dropped " + dropped.getFirst().getItem());
      }
      ctx.mc().setScreen(null);
    }

    @Override
    public void cleanup(UiTestContext ctx) {
      NeoForge.EVENT_BUS.unregister(clicker);
      ctx.mc().setScreen(null);
      ctx.sendCommand("clear @s");
    }
  }

  /**
   * JEI and the fluid tanks of our screens: opens a melter on a lava fuel tank, finds the fuel gauge through
   * {@code IScreenWithFluidTank} and asks JEI's screen helper (which calls {@code GuiContainerTankHandler}) what is under the
   * mouse there. The clickable lava must cover that point in screen coordinates, which is what JEI's own mouse-over check uses.
   * Then presses JEI's show uses key (U) over the gauge through the screen key event JEI listens to; the capture is the JEI recipes
   * GUI on the uses of lava.
   */
  private static class JeiTankClickableScenario implements UiTestScenario {
    private static final String NAME = "jei_tank_clickable";
    private final BlockPos pos = SITE.offset(146, 1, 0);
    private final Consumer<ClientTickEvent.Post> poller = this::pressWhenReady;
    private boolean pressed = false;
    @Nullable
    private String failure = null;

    @Override
    public Identifier id() {
      return TConstruct.getResource(NAME);
    }

    @Override
    public void prepare(UiTestContext ctx) {
      pressed = false;
      failure = null;
      ctx.sendCommand("tp @s " + (pos.getX() - 2) + " " + (pos.getY() - 1) + " " + pos.getZ());
      ctx.runOnServer(() -> {
        ServerLevel level = ctx.serverLevel();
        // same rig as the melter scenario: the tank placed after the melter forms it
        level.setBlockAndUpdate(pos, TinkerSmeltery.searedMelter.get().defaultBlockState());
        BlockPos fuel = pos.below();
        level.setBlockAndUpdate(fuel, TinkerSmeltery.searedTank.get(TankType.FUEL_TANK).defaultBlockState());
        SmelteryRigs.fillTank(level, fuel, new FluidStack(Fluids.LAVA, 4000));
      });
    }

    @Override
    public void open(UiTestContext ctx) {
      ctx.useBlock(pos);
      NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, poller);
    }

    /** Point of the screen showing lava, or null if the gauge has not received the fuel yet */
    @Nullable
    private static int[] findLava(MelterScreen screen) {
      for (int y = screen.getGuiTop(); y < screen.getGuiTop() + screen.getYSize(); y++) {
        for (int x = screen.getGuiLeft(); x < screen.getGuiLeft() + screen.getXSize(); x++) {
          IScreenWithFluidTank.FluidLocation fluid = screen.getFluidUnderMouse(x, y);
          if (fluid != null && fluid.fluid().is(Fluids.LAVA)) {
            return new int[] { x, y };
          }
        }
      }
      return null;
    }

    /** Once the gauge shows lava: checks JEI's clickable ingredient there, then presses the show uses key over it */
    private void pressWhenReady(ClientTickEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (pressed || !(mc.screen instanceof MelterScreen screen)) {
        return;
      }
      int[] point = findLava(screen);
      if (point == null) {
        return;
      }
      pressed = true;
      NeoForge.EVENT_BUS.unregister(poller);
      IJeiRuntime runtime = JEIPlugin.runtime;
      if (runtime == null) {
        failure = "JEI runtime not captured - onRuntimeAvailable never fired";
        return;
      }
      List<IClickableIngredient<?>> clickables = runtime.getScreenHelper().getClickableIngredientUnderMouse(screen, point[0], point[1]).toList();
      IClickableIngredient<?> lava = clickables.stream()
        .filter(clickable -> clickable.getTypedIngredient().getIngredient(NeoForgeTypes.FLUID_STACK).filter(fluid -> fluid.is(Fluids.LAVA)).isPresent())
        .findFirst().orElse(null);
      if (lava == null) {
        failure = "JEI found no lava under the melter fuel gauge at " + point[0] + "," + point[1] + ", found " + clickables;
        return;
      }
      Rect2i area = lava.getArea();
      if (!area.contains(point[0], point[1])) {
        failure = "JEI's clickable lava area (" + area.getX() + "," + area.getY() + " " + area.getWidth() + "x" + area.getHeight()
                  + ") does not contain the mouse at " + point[0] + "," + point[1] + " - not in screen coordinates";
        return;
      }
      new UiTestContext(mc).moveMouse(point[0] + 0.5, point[1] + 0.5);
      // as KeyboardHandler would: screen key events go Pre, the screen, then Post (where JEI handles its hotkeys)
      KeyEvent key = new KeyEvent(GLFW.GLFW_KEY_U, 0, 0);
      if (!NeoForge.EVENT_BUS.post(new ScreenEvent.KeyPressed.Pre(screen, key)).isCanceled() && !screen.keyPressed(key)) {
        NeoForge.EVENT_BUS.post(new ScreenEvent.KeyPressed.Post(screen, key));
      }
    }

    @Override
    public int settleTicks() {
      return 40; // menu round trip + fuel sync + recipes GUI
    }

    @Override
    public void close(UiTestContext ctx) {
      if (failure != null) {
        throw new IllegalStateException(NAME + ": " + failure);
      }
      if (!pressed) {
        throw new IllegalStateException(NAME + ": the melter fuel gauge never showed lava, found screen " + ctx.mc().screen);
      }
      if (!(ctx.mc().screen instanceof IRecipesGui)) {
        throw new IllegalStateException(NAME + ": the show uses key over the lava gauge did not open the JEI recipes GUI, found " + ctx.mc().screen);
      }
      ctx.mc().setScreen(null);
    }

    @Override
    public void cleanup(UiTestContext ctx) {
      NeoForge.EVENT_BUS.unregister(poller);
      ctx.parkMouse();
      ctx.mc().setScreen(null);
    }
  }

  /**
   * JEI recipe transfer into the crafting station from its side inventory: the only planks are in a chest next to the station
   * (a native slot over NeoForge's {@code VanillaContainerWrapper} around the chest's {@code Container}, set directly, from
   * {@code IModifiableItemHandlerProvider.find}). JEI's transfer handler for the vanilla stick recipe ({@code CraftingStationTransferInfo})
   * must accept the recipe and move both planks into the grid, and the server must report the transfer as a success (JEI 27 context path).
   * Inventories exposing only a native {@code ResourceHandler} get a side inventory set through transactions since transfer phase 6, covered
   * by the {@code side_inventory_*} gametests.
   */
  private static class JeiRecipeTransferScenario implements UiTestScenario {
    private static final String NAME = "jei_recipe_transfer";
    private static final Identifier STICK = Identifier.withDefaultNamespace("stick");
    private final BlockPos pos = SITE.offset(150, 0, 0);
    private final BlockPos chest = pos.south();
    private final Consumer<ClientTickEvent.Post> poller = this::transferWhenReady;
    private boolean transferred = false;
    @Nullable
    private UiTestTransferContext<?,?> transfer = null;
    @Nullable
    private String failure = null;

    @Override
    public Identifier id() {
      return TConstruct.getResource(NAME);
    }

    @Override
    public void prepare(UiTestContext ctx) {
      transferred = false;
      transfer = null;
      failure = null;
      ctx.sendCommand("clear @s");
      ctx.sendCommand("tp @s " + (pos.getX() - 2) + " " + pos.getY() + " " + pos.getZ());
      ctx.runOnServer(() -> {
        ServerLevel level = ctx.serverLevel();
        level.setBlockAndUpdate(pos, TinkerTables.craftingStation.get().defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof CraftingStationBlockEntity station)) {
          throw new IllegalStateException("Expected crafting station at " + pos + ", found " + level.getBlockEntity(pos));
        }
        for (int i = 0; i < 9; i++) {
          station.setItem(i, ItemStack.EMPTY);
        }
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        if (!(level.getBlockEntity(chest) instanceof ChestBlockEntity chestBE)) {
          throw new IllegalStateException("Expected chest at " + chest + ", found " + level.getBlockEntity(chest));
        }
        chestBE.clearContent();
        chestBE.setItem(4, new ItemStack(Items.OAK_PLANKS, 2));
      });
    }

    @Override
    public int prepareSettleTicks() {
      return 20;
    }

    @Override
    public void open(UiTestContext ctx) {
      ctx.useBlock(pos);
      NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, poller);
    }

    /** Planks anywhere in the station menu outside the grid (side inventory and player inventory) */
    private static int planksOutsideGrid(CraftingStationContainerMenu menu) {
      int count = 0;
      for (int i = 10; i < menu.slots.size(); i++) {
        ItemStack stack = menu.getSlot(i).getItem();
        if (stack.is(Items.OAK_PLANKS)) {
          count += stack.getCount();
        }
      }
      return count;
    }

    /** Runs JEI's transfer of the stick recipe once the menu shows the planks in the side inventory */
    private void transferWhenReady(ClientTickEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (transferred || !(mc.screen instanceof AbstractContainerScreen<?> screen) || !(screen.getMenu() instanceof CraftingStationContainerMenu menu)
          || planksOutsideGrid(menu) != 2 || mc.player == null) {
        return;
      }
      transferred = true;
      NeoForge.EVENT_BUS.unregister(poller);
      try {
        IJeiRuntime runtime = requireJeiRuntime();
        @SuppressWarnings("unchecked")
        AbstractContainerScreen<CraftingStationContainerMenu> stationScreen = (AbstractContainerScreen<CraftingStationContainerMenu>) screen;
        transfer = runJeiTransfer(runtime, stationScreen, RecipeTypes.CRAFTING, craftingRecipe(runtime, STICK), STICK.toString());
      } catch (IllegalStateException e) {
        failure = e.getMessage();
      }
    }

    @Override
    public int settleTicks() {
      return 30; // menu round trip + transfer packet + slot sync
    }

    @Override
    public void close(UiTestContext ctx) {
      if (failure != null) {
        throw new IllegalStateException(NAME + ": " + failure);
      }
      if (!(ctx.mc().screen instanceof AbstractContainerScreen<?> screen) || !(screen.getMenu() instanceof CraftingStationContainerMenu menu)) {
        throw new IllegalStateException(NAME + " expected the crafting station menu at capture, found " + ctx.mc().screen);
      }
      if (!transferred) {
        throw new IllegalStateException(NAME + ": the side inventory never showed the 2 planks of the adjacent chest");
      }
      int inGrid = 0;
      for (int i = 0; i < 9; i++) {
        ItemStack stack = menu.getSlot(i).getItem();
        if (stack.is(Items.OAK_PLANKS)) {
          inGrid += stack.getCount();
        }
      }
      if (inGrid != 2 || planksOutsideGrid(menu) != 0) {
        throw new IllegalStateException(NAME + " expected both planks moved into the grid, found " + inGrid + " in the grid and " + planksOutsideGrid(menu) + " outside");
      }
      requireTransferSuccess(NAME, transfer);
      ctx.mc().setScreen(null);
    }

    @Override
    public void cleanup(UiTestContext ctx) {
      NeoForge.EVENT_BUS.unregister(poller);
      ctx.mc().setScreen(null);
    }
  }

  /**
   * JEI recipe transfer of a pickaxe into the tinker station through {@code TinkerStationTransferInfo}: the three parts JEI shows first for the
   * pickaxe tool building recipe are in the player inventory, the station keeps its default (repair) layout, whose three input slots take any item.
   * The parts must end up in the station inputs and the server must report the transfer as a success.
   */
  private static class JeiTinkerStationTransferScenario implements UiTestScenario {
    private static final String NAME = "jei_tinker_station_transfer";
    private final BlockPos pos = SITE.offset(174, 0, 0);
    private final Consumer<ClientTickEvent.Post> poller = this::transferWhenReady;
    private final List<ItemStack> parts = new ArrayList<>();
    @Nullable
    private ToolBuildingRecipe recipe = null;
    private boolean transferred = false;
    @Nullable
    private UiTestTransferContext<?,?> transfer = null;
    @Nullable
    private String failure = null;

    @Override
    public Identifier id() {
      return TConstruct.getResource(NAME);
    }

    @Override
    public void prepare(UiTestContext ctx) {
      transferred = false;
      transfer = null;
      failure = null;
      parts.clear();
      IJeiRuntime runtime = requireJeiRuntime();
      IRecipeManager recipes = runtime.getRecipeManager();
      recipe = recipes.createRecipeLookup(TConstructJEIConstants.TOOL_BUILDING).get()
        .filter(r -> r.getOutput().asItem() == TinkerTools.pickaxe.asItem()).findFirst()
        .orElseThrow(() -> new IllegalStateException(NAME + ": JEI has no pickaxe tool building recipe"));
      IRecipeSlotsView slots = recipes.createRecipeLayoutDrawable(recipes.getRecipeCategory(TConstructJEIConstants.TOOL_BUILDING), recipe, runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup())
        .map(IRecipeLayoutDrawable::getRecipeSlotsView).orElseThrow(() -> new IllegalStateException(NAME + ": JEI could not lay out the pickaxe recipe"));
      slots.getSlotViews(RecipeIngredientRole.INPUT).forEach(view -> view.getItemStacks().filter(stack -> !stack.isEmpty()).findFirst().ifPresent(stack -> parts.add(stack.copy())));
      if (parts.size() != 3) {
        throw new IllegalStateException(NAME + ": expected the pickaxe recipe to show 3 parts, found " + parts);
      }
      ctx.sendCommand("clear @s");
      ctx.sendCommand("tp @s " + (pos.getX() - 2) + " " + pos.getY() + " " + pos.getZ());
      List<ItemStack> toGive = parts.stream().map(ItemStack::copy).toList();
      ctx.runOnServer(() -> {
        ServerLevel level = ctx.serverLevel();
        level.setBlockAndUpdate(pos, TinkerTables.tinkerStation.get().defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof Container station)) {
          throw new IllegalStateException("Expected a tinker station at " + pos + ", found " + level.getBlockEntity(pos));
        }
        station.clearContent();
        Inventory inventory = level.getServer().getPlayerList().getPlayers().getFirst().getInventory();
        for (int i = 0; i < toGive.size(); i++) {
          inventory.setItem(9 + i, toGive.get(i));
        }
      });
    }

    @Override
    public int prepareSettleTicks() {
      return 20;
    }

    @Override
    public void open(UiTestContext ctx) {
      ctx.useBlock(pos);
      NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, poller);
    }

    /** Counts the parts in the station inputs (true) or anywhere else in the menu (false) */
    private int countParts(TinkerStationContainerMenu menu, boolean inInputs) {
      int count = 0;
      for (Slot slot : menu.slots) {
        if (menu.getInputSlots().contains(slot) == inInputs) {
          ItemStack stack = slot.getItem();
          if (parts.stream().anyMatch(part -> ItemStack.isSameItemSameComponents(part, stack))) {
            count += stack.getCount();
          }
        }
      }
      return count;
    }

    /** Runs JEI's transfer of the pickaxe recipe once the menu shows the parts in the player inventory */
    private void transferWhenReady(ClientTickEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (transferred || recipe == null || !(mc.screen instanceof AbstractContainerScreen<?> screen) || !(screen.getMenu() instanceof TinkerStationContainerMenu menu)
          || countParts(menu, false) != parts.size()) {
        return;
      }
      transferred = true;
      NeoForge.EVENT_BUS.unregister(poller);
      try {
        @SuppressWarnings("unchecked")
        AbstractContainerScreen<TinkerStationContainerMenu> stationScreen = (AbstractContainerScreen<TinkerStationContainerMenu>) screen;
        transfer = runJeiTransfer(requireJeiRuntime(), stationScreen, TConstructJEIConstants.TOOL_BUILDING, recipe, "the pickaxe");
      } catch (IllegalStateException e) {
        failure = e.getMessage();
      }
    }

    @Override
    public int settleTicks() {
      return 30; // menu round trip + transfer packet + slot sync
    }

    @Override
    public void close(UiTestContext ctx) {
      if (failure != null) {
        throw new IllegalStateException(NAME + ": " + failure);
      }
      if (!(ctx.mc().screen instanceof AbstractContainerScreen<?> screen) || !(screen.getMenu() instanceof TinkerStationContainerMenu menu)) {
        throw new IllegalStateException(NAME + " expected the tinker station menu at capture, found " + ctx.mc().screen);
      }
      if (!transferred) {
        throw new IllegalStateException(NAME + ": the player inventory never showed the 3 pickaxe parts");
      }
      int inInputs = countParts(menu, true);
      int outside = countParts(menu, false);
      if (inInputs != parts.size() || outside != 0) {
        throw new IllegalStateException(NAME + " expected the 3 parts moved into the station inputs, found " + inInputs + " in the inputs and " + outside + " outside");
      }
      requireTransferSuccess(NAME, transfer);
      ctx.mc().setScreen(null);
    }

    @Override
    public void cleanup(UiTestContext ctx) {
      NeoForge.EVENT_BUS.unregister(poller);
      ctx.mc().setScreen(null);
      ctx.sendCommand("clear @s");
    }
  }

  /**
   * JEI recipe transfer into a tool's 2x2 crafting grid (workbench modifier) through {@code ToolInventoryTransferInfo}, the path that trims the
   * recipe slots to the small grid: a pickaxe with the workbench modifier is held, 2 planks are in the player inventory, and the stick recipe must
   * move both planks into the grid with a success reported by the server.
   */
  private static class JeiToolInventoryTransferScenario implements UiTestScenario {
    private static final String NAME = "jei_tool_inventory_transfer";
    private static final Identifier STICK = Identifier.withDefaultNamespace("stick");
    private final BlockPos pos = SITE.offset(180, 0, 0);
    private final Consumer<ClientTickEvent.Post> poller = this::transferWhenReady;
    private boolean transferred = false;
    @Nullable
    private UiTestTransferContext<?,?> transfer = null;
    @Nullable
    private String failure = null;

    @Override
    public Identifier id() {
      return TConstruct.getResource(NAME);
    }

    @Override
    public void prepare(UiTestContext ctx) {
      transferred = false;
      transfer = null;
      failure = null;
      // look straight up so the use hits the air, not a block
      ctx.sendCommand("tp @s " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " 0 -90");
      ctx.runOnServer(() -> {
        ToolStack tool = ToolStack.createTool(TinkerTools.pickaxe.get(), TinkerTools.pickaxe.get().getToolDefinition(),
          MaterialNBT.of(MaterialVariant.of(MaterialIds.cobalt, ""), MaterialVariant.of(MaterialIds.wood, ""), MaterialVariant.of(MaterialIds.iron, "")));
        tool.addModifier(ModifierIds.workbench, 1);
        Inventory inventory = ctx.serverLevel().getServer().getPlayerList().getPlayers().getFirst().getInventory();
        inventory.clearContent();
        inventory.setItem(0, tool.createStack());
        inventory.setItem(9, new ItemStack(Items.OAK_PLANKS, 2));
      });
    }

    @Override
    public int prepareSettleTicks() {
      return 20;
    }

    @Override
    public void open(UiTestContext ctx) {
      LocalPlayer player = ctx.player();
      player.getInventory().setSelectedSlot(0);
      Objects.requireNonNull(ctx.mc().gameMode).useItem(player, InteractionHand.MAIN_HAND);
      NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, poller);
    }

    /** Planks in the tool's crafting grid (true) or anywhere else in the menu (false) */
    private static int planks(ToolContainerMenu menu, boolean inGrid) {
      int count = 0;
      for (int i = 0; i < menu.slots.size(); i++) {
        boolean grid = i >= 1 && i < menu.getToolInventoryStart();
        ItemStack stack = menu.getSlot(i).getItem();
        if (grid == inGrid && stack.is(Items.OAK_PLANKS)) {
          count += stack.getCount();
        }
      }
      return count;
    }

    /** Runs JEI's transfer of the stick recipe once the tool menu shows the planks */
    private void transferWhenReady(ClientTickEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (transferred || !(mc.screen instanceof ToolContainerScreen screen) || planks(screen.getMenu(), false) != 2) {
        return;
      }
      transferred = true;
      NeoForge.EVENT_BUS.unregister(poller);
      try {
        IJeiRuntime runtime = requireJeiRuntime();
        transfer = runJeiTransfer(runtime, screen, RecipeTypes.CRAFTING, craftingRecipe(runtime, STICK), STICK.toString());
      } catch (IllegalStateException e) {
        failure = e.getMessage();
      }
    }

    @Override
    public int settleTicks() {
      return 30; // menu round trip + transfer packet + slot sync
    }

    @Override
    public void close(UiTestContext ctx) {
      if (failure != null) {
        throw new IllegalStateException(NAME + ": " + failure);
      }
      if (!(ctx.mc().screen instanceof ToolContainerScreen screen)) {
        throw new IllegalStateException(NAME + " expected ToolContainerScreen at capture, found " + ctx.mc().screen);
      }
      ToolContainerMenu menu = screen.getMenu();
      int gridSize = menu.getToolInventoryStart() - 1;
      if (gridSize != 4) {
        throw new IllegalStateException(NAME + " expected the workbench 2x2 crafting grid, found " + gridSize + " grid slots");
      }
      if (!transferred) {
        throw new IllegalStateException(NAME + ": the tool menu never showed the 2 planks");
      }
      int inGrid = planks(menu, true);
      int outside = planks(menu, false);
      if (inGrid != 2 || outside != 0) {
        throw new IllegalStateException(NAME + " expected both planks moved into the grid, found " + inGrid + " in the grid and " + outside + " outside");
      }
      requireTransferSuccess(NAME, transfer);
      ctx.mc().setScreen(null);
    }

    @Override
    public void cleanup(UiTestContext ctx) {
      NeoForge.EVENT_BUS.unregister(poller);
      ctx.mc().setScreen(null);
      // close the menu on the server before clearing: closing hands the crafting grid back to the inventory, which may land after a plain clear
      ctx.runOnServer(() -> {
        ServerPlayer player = ctx.serverLevel().getServer().getPlayerList().getPlayers().getFirst();
        player.closeContainer();
        player.getInventory().clearContent();
      });
      // look ahead again: the following screen captures show the world behind them
      ctx.sendCommand("tp @s ~ ~ ~ 0 0");
    }
  }

  /** Gets the captured JEI runtime, or throws when {@code onRuntimeAvailable} never fired */
  private static IJeiRuntime requireJeiRuntime() {
    IJeiRuntime runtime = JEIPlugin.runtime;
    if (runtime == null) {
      throw new IllegalStateException("JEI runtime not captured - onRuntimeAvailable never fired");
    }
    return runtime;
  }

  /** Finds the crafting recipe with the given id in JEI */
  private static RecipeHolder<CraftingRecipe> craftingRecipe(IJeiRuntime runtime, Identifier id) {
    return runtime.getRecipeManager().createRecipeLookup(RecipeTypes.CRAFTING).get()
      .filter(holder -> holder.id().identifier().equals(id)).findFirst()
      .orElseThrow(() -> new IllegalStateException("JEI has no " + id + " crafting recipe"));
  }

  /**
   * Runs a JEI recipe transfer the way the recipes GUI's + button does since JEI 27: through the handler's context overload, first the check
   * without transfer, then the transfer. Throws when JEI has no handler for the menu, cannot lay out the recipe or refuses it.
   * @return  the transfer's context, which records the result the server reports
   */
  private static <R, C extends AbstractContainerMenu> UiTestTransferContext<R, C> runJeiTransfer(IJeiRuntime runtime, AbstractContainerScreen<C> screen, IRecipeType<R> type, R recipe, String what) {
    IRecipeManager recipes = runtime.getRecipeManager();
    IRecipeCategory<R> category = recipes.getRecipeCategory(type);
    IRecipeSlotsView slots = recipes.createRecipeLayoutDrawable(category, recipe, runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup())
      .map(IRecipeLayoutDrawable::getRecipeSlotsView).orElseThrow(() -> new IllegalStateException("JEI could not lay out " + what));
    C menu = screen.getMenu();
    IRecipeTransferHandler<C, R> handler = runtime.getRecipeTransferManager().getRecipeTransferHandler(menu, category)
      .orElseThrow(() -> new IllegalStateException("JEI has no recipe transfer handler for " + type.getUid() + " in " + menu.getClass().getSimpleName()));
    Player player = Objects.requireNonNull(Minecraft.getInstance().player);
    // what the + button checks first, then the click
    IRecipeTransferError error = handler.transferRecipe(new UiTestTransferContext<>(recipe, type, screen, slots, player), false);
    if (error != null) {
      throw new IllegalStateException("JEI refuses to transfer " + what + " into " + menu.getClass().getSimpleName() + ": " + error.getType());
    }
    UiTestTransferContext<R, C> context = new UiTestTransferContext<>(recipe, type, screen, slots, player);
    error = handler.transferRecipe(context, true);
    if (error != null) {
      throw new IllegalStateException("JEI failed to transfer " + what + " into " + menu.getClass().getSimpleName() + ": " + error.getType());
    }
    return context;
  }

  /** Fails unless the server reported the transfer as a success */
  private static void requireTransferSuccess(String name, @Nullable UiTestTransferContext<?,?> transfer) {
    RecipeTransferResult result = transfer == null ? null : transfer.result;
    if (result != RecipeTransferResult.SUCCESS) {
      throw new IllegalStateException(name + " expected the server to report the transfer as a success, got " + result);
    }
  }

  /** What JEI's recipes GUI builds for a transfer (its own context class is internal), recording the result the server sends back */
  private static final class UiTestTransferContext<R, C extends AbstractContainerMenu> implements IRecipeTransferContext<R, C> {
    /** Well above the ids JEI hands out itself, which count up from zero */
    private static int nextTransferId = 1_000_000;
    private final int transferId = nextTransferId++;
    private final R recipe;
    private final IRecipeType<R> type;
    private final AbstractContainerScreen<C> screen;
    private final IRecipeSlotsView slots;
    private final Player player;
    @Nullable
    private RecipeTransferResult result = null;

    private UiTestTransferContext(R recipe, IRecipeType<R> type, AbstractContainerScreen<C> screen, IRecipeSlotsView slots, Player player) {
      this.recipe = recipe;
      this.type = type;
      this.screen = screen;
      this.slots = slots;
      this.player = player;
    }

    @Override
    public int getTransferId() {
      return transferId;
    }

    @Override
    public void completeRecipeTransfer(RecipeTransferResult result) {
      this.result = result;
    }

    @Override
    public R getRecipe() {
      return recipe;
    }

    @Override
    public IRecipeType<R> getRecipeType() {
      return type;
    }

    @Override
    public C getContainer() {
      return screen.getMenu();
    }

    @Override
    public AbstractContainerScreen<C> getScreen() {
      return screen;
    }

    @Override
    public IRecipeSlotsView getRecipeSlots() {
      return slots;
    }

    @Override
    public Player getPlayer() {
      return player;
    }

    @Override
    public boolean isMaxTransfer() {
      return false;
    }
  }

  /** Presses a key on a screen as KeyboardHandler would: screen key events go Pre, the screen, then Post (where JEI handles its hotkeys) */
  private static void pressScreenKey(Screen screen, int key) {
    KeyEvent event = new KeyEvent(key, 0, 0);
    if (!NeoForge.EVENT_BUS.post(new ScreenEvent.KeyPressed.Pre(screen, event)).isCanceled() && !screen.keyPressed(event)) {
      NeoForge.EVENT_BUS.post(new ScreenEvent.KeyPressed.Post(screen, event));
    }
  }

  /**
   * JEI on a {@link MultiModuleScreen} with a side panel left of its main window, which moves {@code leftPos} away from where the main
   * window and its slots are drawn ({@link MultiModuleScreen#cornerX}); JEI builds slot areas from {@code getGuiLeft()}, which used to
   * return that {@code leftPos} (areas shifted left by the panel width). Once the screen shows the target, moves the mouse over it, checks
   * the clickable ingredient JEI's screen helper finds there (the expected ingredient, with an area containing the mouse in screen
   * coordinates, which JEI's mouse handling checks) and presses a JEI key over it; the capture is the JEI recipes GUI.
   */
  private abstract static class JeiSidePanelScenario implements UiTestScenario {
    /** Ticks to wait for the screen to render with the mouse over a slot target */
    private static final int HOVER_TIMEOUT = 40;
    private final String name;
    private final int key;
    private final Consumer<ClientTickEvent.Post> poller = this::tick;
    /** 0: waiting for the target, 1: hovering it, 2: done (key pressed or failed) */
    private int stage = 0;
    private int ticks = 0;
    @Nullable
    private Target target = null;
    @Nullable
    private String failure = null;

    /** Point to hover in screen coordinates, and for slots the slot vanilla must report as hovered there */
    protected record Target(int x, int y, @Nullable Slot slot) {
      static Target slot(MultiModuleScreen<?> screen, Slot slot) {
        return new Target(screen.cornerX + slot.x + 8, screen.cornerY + slot.y + 8, slot);
      }
    }

    protected JeiSidePanelScenario(String name, int key) {
      this.name = name;
      this.key = key;
    }

    @Override
    public Identifier id() {
      return TConstruct.getResource(name);
    }

    /** Target on the open screen, or null if the screen does not show it (yet) */
    @Nullable
    protected abstract Target findTarget(MultiModuleScreen<?> screen);

    /** Checks if the clickable ingredient is the one expected at the target */
    protected abstract boolean isExpected(IClickableIngredient<?> clickable);

    /** Resets the state, call at the start of {@link #prepare} */
    protected void reset() {
      stage = 0;
      ticks = 0;
      target = null;
      failure = null;
    }

    /** Starts watching the screen, call from {@link #open} */
    protected void startPolling() {
      NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, poller);
    }

    private static String describe(List<Rect2i> areas) {
      return areas.stream().map(area -> area.getX() + "," + area.getY() + " " + area.getWidth() + "x" + area.getHeight()).toList().toString();
    }

    private void fail(String message) {
      failure = message;
      stage = 2;
      NeoForge.EVENT_BUS.unregister(poller);
    }

    private void tick(ClientTickEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (stage == 2 || !(mc.screen instanceof MultiModuleScreen<?> screen)) {
        return;
      }
      ticks++;
      if (stage == 0) {
        // let the screen render a few frames first, slot positions of the side panels follow their module on render
        target = ticks < 5 ? null : findTarget(screen);
        if (target == null) {
          return;
        }
        if (screen.getModuleAreas().stream().noneMatch(area -> area.getX() < screen.cornerX)) {
          fail("the screen has no side panel left of its main window (corner " + screen.cornerX + ", modules " + describe(screen.getModuleAreas()) + ")");
          return;
        }
        stage = 1;
        ticks = 0;
        new UiTestContext(mc).moveMouse(target.x + 0.5, target.y + 0.5);
        return;
      }
      // wait for a render with the mouse there, which updates vanilla's hovered slot (what JEI reads for slots)
      assert target != null;
      if (target.slot != null && screen.getSlotUnderMouse() != target.slot) {
        if (ticks > HOVER_TIMEOUT) {
          fail("vanilla hovers " + screen.getSlotUnderMouse() + " instead of slot " + target.slot.index + " with the mouse at " + target.x + "," + target.y);
        }
        return;
      }
      IJeiRuntime runtime = JEIPlugin.runtime;
      if (runtime == null) {
        fail("JEI runtime not captured - onRuntimeAvailable never fired");
        return;
      }
      List<IClickableIngredient<?>> clickables = runtime.getScreenHelper().getClickableIngredientUnderMouse(screen, target.x, target.y).toList();
      IClickableIngredient<?> clickable = clickables.stream().filter(this::isExpected).findFirst().orElse(null);
      if (clickable == null) {
        fail("JEI found no expected ingredient under the mouse at " + target.x + "," + target.y + ", found " + clickables);
        return;
      }
      Rect2i area = clickable.getArea();
      if (!area.contains(target.x, target.y)) {
        fail("JEI's clickable area (" + area.getX() + "," + area.getY() + " " + area.getWidth() + "x" + area.getHeight() + ") does not contain the mouse at "
             + target.x + "," + target.y + " (gui left/top " + screen.getGuiLeft() + "," + screen.getGuiTop() + ", main window corner " + screen.cornerX + "," + screen.cornerY
             + ", modules " + describe(screen.getModuleAreas()) + ")");
        return;
      }
      stage = 2;
      NeoForge.EVENT_BUS.unregister(poller);
      pressScreenKey(screen, key);
    }

    @Override
    public int settleTicks() {
      return 60; // menu round trip + sync + hover + recipes GUI
    }

    @Override
    public void close(UiTestContext ctx) {
      if (failure != null) {
        throw new IllegalStateException(name + ": " + failure);
      }
      if (stage != 2) {
        throw new IllegalStateException(name + ": the screen never showed the target (stage " + stage + "), found screen " + ctx.mc().screen);
      }
      if (!(ctx.mc().screen instanceof IRecipesGui)) {
        throw new IllegalStateException(name + ": the JEI key over the target did not open the JEI recipes GUI, found " + ctx.mc().screen);
      }
      ctx.mc().setScreen(null);
    }

    @Override
    public void cleanup(UiTestContext ctx) {
      NeoForge.EVENT_BUS.unregister(poller);
      ctx.parkMouse();
      ctx.mc().setScreen(null);
    }
  }

  /**
   * Show recipes (R) over an item in the main crafting grid of a crafting station whose side panel shows the chest next to it: the grid
   * slot is drawn from the main window corner, while the GUI left JEI reads includes the side panel.
   */
  private static class JeiCraftingStationSlotScenario extends JeiSidePanelScenario {
    private final BlockPos pos = SITE.offset(156, 0, 0);

    JeiCraftingStationSlotScenario() {
      super("jei_crafting_station_slot", GLFW.GLFW_KEY_R);
    }

    @Override
    public void prepare(UiTestContext ctx) {
      reset();
      ctx.sendCommand("clear @s");
      ctx.sendCommand("tp @s " + (pos.getX() - 2) + " " + pos.getY() + " " + pos.getZ());
      ctx.runOnServer(() -> {
        ServerLevel level = ctx.serverLevel();
        level.setBlockAndUpdate(pos, TinkerTables.craftingStation.get().defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof CraftingStationBlockEntity station)) {
          throw new IllegalStateException("Expected crafting station at " + pos + ", found " + level.getBlockEntity(pos));
        }
        for (int i = 0; i < 9; i++) {
          station.setItem(i, ItemStack.EMPTY);
        }
        station.setItem(0, new ItemStack(Items.OAK_PLANKS));
        BlockPos chest = pos.south();
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        if (!(level.getBlockEntity(chest) instanceof ChestBlockEntity chestBE)) {
          throw new IllegalStateException("Expected chest at " + chest + ", found " + level.getBlockEntity(chest));
        }
        chestBE.clearContent();
        chestBE.setItem(0, new ItemStack(Items.STICK));
      });
    }

    @Override
    public int prepareSettleTicks() {
      return 20;
    }

    @Override
    public void open(UiTestContext ctx) {
      ctx.useBlock(pos);
      startPolling();
    }

    @Nullable
    @Override
    protected Target findTarget(MultiModuleScreen<?> screen) {
      if (screen.getMenu() instanceof CraftingStationContainerMenu menu && menu.getSlot(0).getItem().is(Items.OAK_PLANKS)) {
        return Target.slot(screen, menu.getSlot(0));
      }
      return null;
    }

    @Override
    protected boolean isExpected(IClickableIngredient<?> clickable) {
      return clickable.getTypedIngredient().getIngredient(VanillaTypes.ITEM_STACK).filter(stack -> stack.is(Items.OAK_PLANKS)).isPresent();
    }
  }

  /** Builds the smallest smeltery at the origin, standing in front of its controller */
  private static void buildSmelteryFacingPlayer(UiTestContext ctx, BlockPos origin, AtomicReference<BlockPos> controller) {
    ctx.sendCommand("tp @s " + (origin.getX() + 1) + " " + origin.getY() + " " + (origin.getZ() + 5));
    ctx.runOnServer(() -> controller.set(SmelteryRigs.buildSmeltery(ctx.serverLevel(), origin)));
  }

  /**
   * Show uses (U) over an item in a melting slot of a smeltery: the melting slots are in the side panel left of the main window, drawn
   * from the main window corner like every slot. A feather has no melting recipe, so it stays in the slot.
   */
  private static class JeiSmelterySlotScenario extends JeiSidePanelScenario {
    private final AtomicReference<BlockPos> controller = new AtomicReference<>();

    JeiSmelterySlotScenario() {
      super("jei_smeltery_slot", GLFW.GLFW_KEY_U);
    }

    @Override
    public void prepare(UiTestContext ctx) {
      reset();
      buildSmelteryFacingPlayer(ctx, SITE.offset(162, 0, 0), controller);
    }

    @Override
    public int prepareSettleTicks() {
      return 100; // structure formation, see SmelteryScenario
    }

    @Override
    public void open(UiTestContext ctx) {
      BlockPos pos = controller.get();
      ctx.runOnServer(() -> SmelteryRigs.insertMeltable(ctx.serverLevel(), pos, new ItemStack(Items.FEATHER)));
      ctx.useBlock(pos);
      startPolling();
    }

    @Nullable
    @Override
    protected Target findTarget(MultiModuleScreen<?> screen) {
      if (screen instanceof HeatingStructureScreen) {
        for (Slot slot : screen.getMenu().slots) {
          if (slot.getItem().is(Items.FEATHER)) {
            return Target.slot(screen, slot);
          }
        }
      }
      return null;
    }

    @Override
    protected boolean isExpected(IClickableIngredient<?> clickable) {
      return clickable.getTypedIngredient().getIngredient(VanillaTypes.ITEM_STACK).filter(stack -> stack.is(Items.FEATHER)).isPresent();
    }
  }

  /**
   * Show uses (U) over the molten iron in a smeltery tank, the tank clickable of {@code GuiContainerTankHandler} on the screen with the
   * most offset coordinates: the main window corner is right of the GUI left because of the melting side panel.
   */
  private static class JeiSmelteryTankScenario extends JeiSidePanelScenario {
    private final AtomicReference<BlockPos> controller = new AtomicReference<>();

    JeiSmelteryTankScenario() {
      super("jei_smeltery_tank_clickable", GLFW.GLFW_KEY_U);
    }

    @Override
    public void prepare(UiTestContext ctx) {
      reset();
      buildSmelteryFacingPlayer(ctx, SITE.offset(168, 0, 0), controller);
    }

    @Override
    public int prepareSettleTicks() {
      return 100; // structure formation, see SmelteryScenario
    }

    @Override
    public void open(UiTestContext ctx) {
      BlockPos pos = controller.get();
      ctx.runOnServer(() -> {
        if (!(ctx.serverLevel().getBlockEntity(pos) instanceof HeatingStructureBlockEntity smeltery)) {
          throw new IllegalStateException("Expected smeltery controller at " + pos + ", found " + ctx.serverLevel().getBlockEntity(pos));
        }
        int amount = FluidValues.INGOT * 4;
        try (Transaction tx = Transaction.openRoot()) {
          int filled = smeltery.getFluidCapability().insert(FluidResource.of(new FluidStack(TinkerFluids.moltenIron.get(), amount)), amount, tx);
          if (filled != amount) {
            throw new IllegalStateException("Smeltery at " + pos + " accepted only " + filled + "/" + amount + "mb of molten iron (not formed yet?)");
          }
          tx.commit();
        }
      });
      ctx.useBlock(pos);
      startPolling();
    }

    @Nullable
    @Override
    protected Target findTarget(MultiModuleScreen<?> screen) {
      if (screen instanceof HeatingStructureScreen smeltery) {
        for (int y = screen.getGuiTop(); y < screen.getGuiTop() + screen.getYSize(); y++) {
          for (int x = screen.getGuiLeft(); x < screen.getGuiLeft() + screen.getXSize(); x++) {
            IScreenWithFluidTank.FluidLocation fluid = smeltery.getFluidUnderMouse(x, y);
            if (fluid != null && fluid.fluid().is(TinkerFluids.moltenIron.get())) {
              return new Target(x, y, null);
            }
          }
        }
      }
      return null;
    }

    @Override
    protected boolean isExpected(IClickableIngredient<?> clickable) {
      return clickable.getTypedIngredient().getIngredient(NeoForgeTypes.FLUID_STACK).filter(fluid -> fluid.is(TinkerFluids.moltenIron.get())).isPresent();
    }
  }
}
