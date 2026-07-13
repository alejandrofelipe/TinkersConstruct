package slimeknights.tconstruct.client.uitest;

import mezz.jei.api.runtime.IJeiRuntime;
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
import slimeknights.tconstruct.tools.TinkerModifiers;

import java.util.List;
import java.util.function.Supplier;

/** Registers TConstruct's automated GUI screenshot scenarios (active only with -Dmantle.uitest=true). */
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
    UiTestScenarios.register(new SmelteryScenario());
    UiTestScenarios.register(new MelterScenario());
    UiTestScenarios.register(new CastingPourScenario());
    UiTestScenarios.register(new BookScenario());
    UiTestScenarios.register(new JeiCategoryScenario());
    UiTestScenarios.register(new JeiItemListCleanupScenario());
    UiTestScenarios.register(new BookInteriorScenario());
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
}
