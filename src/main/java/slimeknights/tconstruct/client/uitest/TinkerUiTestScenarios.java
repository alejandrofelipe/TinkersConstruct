package slimeknights.tconstruct.client.uitest;

import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.client.screen.book.BookScreen;
import slimeknights.mantle.client.uitest.UiTestContext;
import slimeknights.mantle.client.uitest.UiTestScenario;
import slimeknights.mantle.client.uitest.UiTestScenarios;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.gametest.SmelteryRigs;
import slimeknights.tconstruct.plugin.jei.JEIPlugin;
import slimeknights.tconstruct.plugin.jei.TConstructJEIConstants;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.component.SearedTankBlock.TankType;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tables.client.inventory.TinkerStationScreen;
import slimeknights.tconstruct.tables.client.inventory.ToolTableScreen;
import slimeknights.tconstruct.tools.TinkerModifiers;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Registers TConstruct's automated GUI screenshot scenarios (active only with -Dmantle.uitest=true). */
// EventBusSubscriber.bus() is deprecated-for-removal in NeoForge; bus = Bus.MOD is explicit and correct here
// (clientSetup handles the mod-bus FMLClientSetupEvent). Suppressed rather than dropped, since inference behavior
// in 21.1.234 is unconfirmed and a wrong bus would silently stop the uitest scenarios from registering.
@SuppressWarnings("removal")
@EventBusSubscriber(modid = TConstruct.MOD_ID, value = Dist.CLIENT, bus = Bus.MOD)
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
    UiTestScenarios.register(new JeiCategoryScenario());
    UiTestScenarios.register(new JeiItemListCleanupScenario());
    UiTestScenarios.register(new BookInteriorScenario());
    UiTestScenarios.register(new StationReflowScenario());
    UiTestScenarios.register(new StationCollapsedScenario());
    UiTestScenarios.register(new StationCollapsedOverlayScenario());
    UiTestScenarios.register(new ExtraHeartsScenario());
  }

  /** Places a single block and opens its GUI. */
  private record BlockGuiScenario(String name, BlockPos pos, Supplier<BlockState> state) implements UiTestScenario {
    @Override
    public ResourceLocation id() {
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
    public ResourceLocation id() {
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
    public ResourceLocation id() {
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
    public ResourceLocation id() {
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
    public ResourceLocation id() {
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
      InteractionResultHolder<ItemStack> result = ctx.player().getItemInHand(InteractionHand.MAIN_HAND)
        .getItem().use(ctx.mc().level, ctx.player(), InteractionHand.MAIN_HAND);
      if (result.getResult() == InteractionResult.PASS) {
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
    public ResourceLocation id() {
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
      InteractionResultHolder<ItemStack> result = ctx.player().getItemInHand(InteractionHand.MAIN_HAND)
        .getItem().use(ctx.mc().level, ctx.player(), InteractionHand.MAIN_HAND);
      if (result.getResult() == InteractionResult.PASS) {
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
    public ResourceLocation id() {
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

  /** Opens the JEI recipes GUI on the Tinkers melting category and captures it. */
  private static class JeiCategoryScenario implements UiTestScenario {
    @Override
    public ResourceLocation id() {
      return TConstruct.getResource("jei_melting_category");
    }

    @Override
    public void prepare(UiTestContext ctx) { /* nothing to build; JEI's async indexing captures the runtime long before this 7th scenario */ }

    @Override
    public void open(UiTestContext ctx) {
      IJeiRuntime runtime = JEIPlugin.runtime;
      if (runtime == null) {
        throw new IllegalStateException("JEI runtime not captured - onRuntimeAvailable never fired");
      }
      runtime.getRecipesGui().showTypes(List.of(TConstructJEIConstants.MELTING));
    }

    @Override
    public int settleTicks() {
      return 40; // recipe layouts/item renders settle
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
    public ResourceLocation id() {
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
    public ResourceLocation id() {
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
    /** Retained so the poller can remove itself once it fires (and as a close() safety net). */
    private final Consumer<ClientTickEvent.Post> overlayPoller = this::openOverlayWhenReady;

    @Override
    public ResourceLocation id() {
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
      NeoForge.EVENT_BUS.unregister(this.overlayPoller); // safety net if the screen never resolved and it never fired
      if (!(ctx.mc().screen instanceof TinkerStationScreen station)) {
        throw new IllegalStateException("station_collapsed_overlay expected TinkerStationScreen at capture, found " + ctx.mc().screen);
      }
      // fail loudly if the overlay never opened - otherwise a collapsed-but-no-overlay PNG would still pass the run
      if (station.getOpenOverlay() != ToolTableScreen.OverlayPanel.TOOL_INFO) {
        throw new IllegalStateException("station_collapsed_overlay expected TOOL_INFO overlay open at capture, found " + station.getOpenOverlay());
      }
      ctx.mc().setScreen(null);
      ctx.mc().execute(() -> ctx.mc().getWindow().setWindowed(restoreW, restoreH));
    }
  }

  /** No menu: boosts the player past 20 HP and captures the in-world HUD to verify Mantle's custom extra-heart renderer. */
  private static class ExtraHeartsScenario implements UiTestScenario {
    private final BlockPos pos = SITE.offset(60, 0, 0);

    @Override
    public ResourceLocation id() {
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
}
