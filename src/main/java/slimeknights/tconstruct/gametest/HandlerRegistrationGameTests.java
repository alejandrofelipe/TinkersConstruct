package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.fluids.item.ContainerFoodItem;
import slimeknights.tconstruct.fluids.item.MagmaBottleItem;
import slimeknights.tconstruct.fluids.item.PotionBucketItem;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.component.SearedBlock;
import slimeknights.tconstruct.smeltery.block.component.SearedTankBlock.TankType;
import slimeknights.tconstruct.smeltery.item.CopperCanItem;
import slimeknights.tconstruct.smeltery.item.TankItem;
import slimeknights.tconstruct.tools.data.ModifierIds;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Handler registration contract (the 1.21.1 → 1.21.10 class of bug where the getters were ported but the central
 * {@code RegisterCapabilitiesEvent} registration was missing, so blocks, tools and fluid containers silently exposed no item/fluid handler).
 * Every {@code tconstruct} block entity type must be listed in {@link #BLOCK_ENTITIES} with the handlers it exposes (or none, with the reason),
 * and every {@code tconstruct} item must expose exactly the handlers its kind implies ({@link #ITEM_KINDS}); an unlisted type, a missing
 * handler and an unexpected handler all fail. Behaviour of the handlers (transactions, interop) is covered by {@link CapabilityGameTests}.
 */
public class HandlerRegistrationGameTests {
  /** The transfer handlers checked on block entities and items */
  private enum Handler {
    ITEM(Capabilities.Item.BLOCK, Capabilities.Item.ITEM),
    FLUID(Capabilities.Fluid.BLOCK, Capabilities.Fluid.ITEM),
    ENERGY(Capabilities.Energy.BLOCK, Capabilities.Energy.ITEM);

    private final BlockCapability<?, Direction> block;
    private final ItemCapability<?, ItemAccess> item;

    Handler(BlockCapability<?, Direction> block, ItemCapability<?, ItemAccess> item) {
      this.block = block;
      this.item = item;
    }
  }

  /** Every side a block capability is queried on: unsided first */
  private static final Direction[] ALL_SIDES = {null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
  private static final Set<Direction> SIX_SIDES = EnumSet.allOf(Direction.class);

  /**
   * Handlers a block entity type exposes.
   * @param standalone  Handlers present when one of its blocks is placed on its own, on the unsided query and on each of {@code sides}
   * @param structure   Handlers only present in a formed heating structure (null on their own), checked in {@link #block_entity_handlers_in_structure}
   * @param sides       Sides the standalone handlers must be present on beside the unsided query; other sides are not checked
   * @param reason      Why the type exposes no handler, or what the handlers serve
   */
  private record Expect(Set<Handler> standalone, Set<Handler> structure, Set<Direction> sides, String reason) {
    boolean expects(Handler handler) {
      return standalone.contains(handler) || structure.contains(handler);
    }
  }

  private static Expect standalone(String reason, Handler... handlers) {
    return new Expect(handlers(handlers), Set.of(), SIX_SIDES, reason);
  }

  private static Expect sided(String reason, Set<Direction> sides, Handler... handlers) {
    return new Expect(handlers(handlers), Set.of(), sides, reason);
  }

  private static Expect structure(String reason, Set<Handler> standalone, Handler... structure) {
    return new Expect(standalone, handlers(structure), SIX_SIDES, reason);
  }

  private static Expect none(String reason) {
    return new Expect(Set.of(), Set.of(), SIX_SIDES, reason);
  }

  private static Set<Handler> handlers(Handler... handlers) {
    return handlers.length == 0 ? Set.of() : EnumSet.copyOf(Arrays.asList(handlers));
  }

  /** Expected handlers of every {@code tconstruct} block entity type, by registry path. A new type fails the tests until listed here. */
  private static final Map<String, Expect> BLOCK_ENTITIES = Map.ofEntries(
    // smeltery structure parts
    Map.entry("smeltery_component", none("structure bricks: servants that only point to their master")),
    Map.entry("drain",    structure("proxies the formed structure's fluids", Set.of(), Handler.FLUID)),
    Map.entry("chute",    structure("proxies the formed structure's melting inventory", Set.of(), Handler.ITEM)),
    Map.entry("duct",     structure("own filter slot; proxies the formed structure's fluids", handlers(Handler.ITEM), Handler.FLUID)),
    Map.entry("smeltery", structure("melting inventory (empty until formed); fluid tank only once the structure is valid", handlers(Handler.ITEM), Handler.FLUID)),
    Map.entry("foundry",  structure("melting inventory (empty until formed); fluid tank only once the structure is valid", handlers(Handler.ITEM), Handler.FLUID)),
    // tanks and fluid blocks
    Map.entry("tank",         standalone("fluid tank", Handler.FLUID)),
    Map.entry("lantern",      standalone("fluid tank", Handler.FLUID)),
    Map.entry("fluid_cannon", standalone("ammo slot and fluid tank", Handler.ITEM, Handler.FLUID)),
    Map.entry("proxy_tank",   standalone("item slot and the fluid of the held container", Handler.ITEM, Handler.FLUID)),
    Map.entry("channel",      sided("fluid input on top; side ports exist only when connected inward, the bottom never", Set.of(Direction.UP), Handler.FLUID)),
    Map.entry("faucet",       none("pulls from the block behind it and pours into the block below; holds no handler of its own")),
    Map.entry("gauge",        none("display only: shows the fluid of the tank behind it")),
    // melters and alloyer
    Map.entry("melter",  standalone("melting slots and fluid tank", Handler.ITEM, Handler.FLUID)),
    Map.entry("alloyer", standalone("output tank", Handler.FLUID)),
    Map.entry("heater",  standalone("fuel slot", Handler.ITEM)),
    // casting
    Map.entry("table",        standalone("cast/output slots and fluid tank", Handler.ITEM, Handler.FLUID)),
    Map.entry("basin",        standalone("cast/output slots and fluid tank", Handler.ITEM, Handler.FLUID)),
    Map.entry("casting_tank", standalone("item slots and fluid tank", Handler.ITEM, Handler.FLUID)),
    // tables and chests
    Map.entry("crafting_station",   standalone("crafting grid", Handler.ITEM)),
    Map.entry("tinker_station",     standalone("station slots", Handler.ITEM)),
    Map.entry("part_builder",       standalone("pattern and material slots", Handler.ITEM)),
    Map.entry("modifier_worktable", standalone("tool and input slots", Handler.ITEM)),
    Map.entry("modifier_chest",     standalone("chest inventory", Handler.ITEM)),
    Map.entry("part_chest",         standalone("chest inventory", Handler.ITEM)),
    Map.entry("cast_chest",         standalone("chest inventory", Handler.ITEM)),
    // decoration
    Map.entry("material_block", none("decorative block that stores its material for rendering; no inventory")));


  /* Block entities */

  /**
   * Places every block of every {@code tconstruct} block entity type on its own and checks its handlers on every side against
   * {@link #BLOCK_ENTITIES}: listed standalone handlers must resolve, handlers not listed at all must not. Structure-proxied handlers are
   * checked by {@link #block_entity_handlers_in_structure}.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void block_entity_handlers_every_type(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
    List<String> failures = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    int checked = 0;
    for (ResourceLocation id : new TreeSet<>(BuiltInRegistries.BLOCK_ENTITY_TYPE.keySet())) {
      if (!TConstruct.MOD_ID.equals(id.getNamespace())) {
        continue;
      }
      seen.add(id.getPath());
      Expect expect = BLOCK_ENTITIES.get(id.getPath());
      if (expect == null) {
        failures.add(id + ": block entity type not listed in HandlerRegistrationGameTests.BLOCK_ENTITIES; list the handlers it exposes, or none with the reason");
        continue;
      }
      BlockEntityType<?> type = Objects.requireNonNull(BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(id));
      List<Block> blocks = type.getValidBlocks().stream().sorted(Comparator.comparing(block -> BuiltInRegistries.BLOCK.getKey(block).toString())).toList();
      int placed = 0;
      for (Block block : blocks) {
        BlockState state = block.defaultBlockState();
        // smeltery components only have a block entity inside a structure
        if (state.hasProperty(SearedBlock.IN_STRUCTURE)) {
          state = state.setValue(SearedBlock.IN_STRUCTURE, true);
        }
        // building block sets list slabs, stairs and walls without one; the round trip test guards those
        if (!state.hasBlockEntity()) {
          continue;
        }
        String label = id + " / " + BuiltInRegistries.BLOCK.getKey(block);
        level.setBlockAndUpdate(pos, state);
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || be.getType() != type) {
          failures.add(label + ": placing the block did not create the block entity");
        } else {
          checkStandalone(level, pos, label, expect, failures);
          placed++;
          checked++;
        }
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
      }
      if (placed == 0) {
        failures.add(id + ": no valid block placed a block entity, handlers unchecked");
      }
    }
    for (String path : BLOCK_ENTITIES.keySet()) {
      if (!seen.contains(path)) {
        failures.add(TConstruct.MOD_ID + ":" + path + ": listed in BLOCK_ENTITIES but no such block entity type is registered; remove the entry");
      }
    }
    helper.killAllEntitiesOfClass(ItemEntity.class);
    TConstruct.LOG.info("Block entity handlers: {} blocks of {} tconstruct block entity types checked", checked, seen.size());
    assertNoFailures(helper, "block entity handler", failures);
    helper.succeed();
  }

  /** Checks one placed block entity against its standalone expectation */
  private static void checkStandalone(ServerLevel level, BlockPos pos, String label, Expect expect, List<String> failures) {
    for (Handler handler : Handler.values()) {
      if (expect.standalone.contains(handler)) {
        List<String> missing = new ArrayList<>();
        for (Direction side : ALL_SIDES) {
          if ((side == null || expect.sides.contains(side)) && level.getCapability(handler.block, pos, side) == null) {
            missing.add(String.valueOf(side));
          }
        }
        if (!missing.isEmpty()) {
          failures.add(label + ": no " + handler + " handler on side(s) " + missing + " (registration missing in RegisterCapabilitiesEvent?)");
        }
      } else if (!expect.expects(handler)) {
        List<String> present = new ArrayList<>();
        for (Direction side : ALL_SIDES) {
          if (level.getCapability(handler.block, pos, side) != null) {
            present.add(String.valueOf(side));
          }
        }
        if (!present.isEmpty()) {
          failures.add(label + ": unexpected " + handler + " handler on side(s) " + present + "; add it to BLOCK_ENTITIES if intended");
        }
      }
    }
  }

  /**
   * Builds a seared smeltery and a scorched foundry (bricks, fuel tank, drain, chute, duct, controller) and checks every structure block:
   * the structure-proxied handlers ({@link Expect#structure}) resolve on every side, the controllers expose a non-empty melting inventory,
   * and no block exposes a handler its entry does not list. Every type with structure handlers must be found.
   */
  @TinkerGameTest(template = "gametest/empty_9x9x9", timeoutTicks = 200)
  public static void block_entity_handlers_in_structure(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    SmelteryRigs.buildHeatingStructure(level, helper.absolutePos(new BlockPos(1, 1, 1)), TinkerSmeltery.searedBricks.get().defaultBlockState(),
      TinkerSmeltery.searedTank.get(TankType.FUEL_TANK), TinkerSmeltery.searedDrain.get(), TinkerSmeltery.searedDuct.get(), TinkerSmeltery.searedChute.get(),
      TinkerSmeltery.smelteryController.get());
    SmelteryRigs.buildHeatingStructure(level, helper.absolutePos(new BlockPos(1, 1, 5)), TinkerSmeltery.scorchedBricks.get().defaultBlockState(),
      TinkerSmeltery.scorchedTank.get(TankType.FUEL_TANK), TinkerSmeltery.scorchedDrain.get(), TinkerSmeltery.scorchedDuct.get(), TinkerSmeltery.scorchedChute.get(),
      TinkerSmeltery.foundryController.get());
    // let the structures form before querying, the proxies cache their master's handler
    helper.runAfterDelay(60, () -> {
      List<String> failures = new ArrayList<>();
      Set<String> found = new HashSet<>();
      for (BlockPos relative : BlockPos.betweenClosed(0, 0, 0, 8, 8, 8)) {
        BlockPos pos = helper.absolutePos(relative);
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
          continue;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
        if (id == null || !TConstruct.MOD_ID.equals(id.getNamespace())) {
          continue;
        }
        Expect expect = BLOCK_ENTITIES.get(id.getPath());
        if (expect == null) {
          continue; // reported by block_entity_handlers_every_type
        }
        found.add(id.getPath());
        String label = id + " at " + relative.immutable();
        // a formed controller has melting slots, proving the structure (and so the proxies' master) is valid
        if (be.getType() == TinkerSmeltery.smeltery.get() || be.getType() == TinkerSmeltery.foundry.get()) {
          ResourceHandler<ItemResource> items = level.getCapability(Capabilities.Item.BLOCK, pos, null);
          if (items == null || items.size() == 0) {
            failures.add(label + ": formed controller exposes " + (items == null ? "no" : "an empty") + " melting inventory");
          }
        }
        for (Handler handler : Handler.values()) {
          if (expect.expects(handler)) {
            if (!expect.structure.contains(handler)) {
              continue; // standalone handlers are covered by block_entity_handlers_every_type
            }
            // presence only: an unfiltered duct proxies no fluid, so its handler is legitimately empty
            for (Direction side : ALL_SIDES) {
              if (level.getCapability(handler.block, pos, side) == null) {
                failures.add(label + ": no " + handler + " handler on side " + side + " in a formed structure (registration missing, or the proxy lost its master?)");
              }
            }
          } else {
            for (Direction side : ALL_SIDES) {
              if (level.getCapability(handler.block, pos, side) != null) {
                failures.add(label + ": unexpected " + handler + " handler on side " + side + " in a formed structure");
              }
            }
          }
        }
      }
      BLOCK_ENTITIES.forEach((path, expect) -> {
        if (!expect.structure.isEmpty() && !found.contains(path)) {
          failures.add(TConstruct.MOD_ID + ":" + path + ": has structure handlers but was not found in the test structures; add it to them");
        }
      });
      assertNoFailures(helper, "structure handler", failures);
      helper.succeed();
    });
  }


  /* Items */

  /** A kind of {@code tconstruct} item and the handlers its default stack exposes */
  private record ItemKind(String name, Predicate<Item> matches, Set<Handler> handlers) {}

  /** Item kinds, first match wins; any other {@code tconstruct} item must expose no handler. */
  private static final List<ItemKind> ITEM_KINDS = List.of(
    new ItemKind("tank block item (tanks, lanterns, casting tank, fluid cannons)", item -> item instanceof TankItem, handlers(Handler.FLUID)),
    new ItemKind("copper can", item -> item instanceof CopperCanItem, handlers(Handler.FLUID)),
    new ItemKind("fluid food container (venom, slime bottles)", item -> item instanceof ContainerFoodItem.FluidContainerFoodItem
                                                                       || item instanceof slimeknights.mantle.item.ContainerFoodItem.FluidContainerFoodItem, handlers(Handler.FLUID)),
    new ItemKind("magma bottle", item -> item instanceof MagmaBottleItem, handlers(Handler.FLUID)),
    new ItemKind("potion bucket", item -> item instanceof PotionBucketItem, handlers(Handler.FLUID)),
    new ItemKind("plain fluid bucket (NeoForge registers exact BucketItem classes)", item -> item.getClass() == BucketItem.class, handlers(Handler.FLUID)),
    // tool handlers come from modifiers: none on a bare stack, see modifiable_items_expose_modifier_handlers
    new ItemKind("modifiable tool", item -> item instanceof IModifiable, Set.of()));

  @Nullable
  private static ItemKind kindOf(Item item) {
    for (ItemKind kind : ITEM_KINDS) {
      if (kind.matches.test(item)) {
        return kind;
      }
    }
    return null;
  }

  /** Puts the stack in a one slot handler, standing in for a pipe or machine inventory */
  private static ItemAccess accessFor(ItemStack stack) {
    ItemStacksResourceHandler slot = new ItemStacksResourceHandler(1);
    slot.set(0, ItemResource.of(stack), stack.getCount());
    return ItemAccess.forHandlerIndex(slot, 0);
  }

  /**
   * The default stack of every {@code tconstruct} item exposes exactly the handlers of its {@link #ITEM_KINDS kind}: fluid containers
   * (tank items, copper cans, bottles, buckets) a fluid handler, everything else none.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void item_handlers_every_item(GameTestHelper helper) {
    List<String> failures = new ArrayList<>();
    int withHandlers = 0;
    int checked = 0;
    for (ResourceLocation id : new TreeSet<>(BuiltInRegistries.ITEM.keySet())) {
      if (!TConstruct.MOD_ID.equals(id.getNamespace())) {
        continue;
      }
      Item item = BuiltInRegistries.ITEM.getValue(id);
      ItemKind kind = kindOf(item);
      Set<Handler> expected = kind == null ? Set.of() : kind.handlers;
      ItemAccess access = accessFor(new ItemStack(item));
      checked++;
      for (Handler handler : Handler.values()) {
        boolean present = access.getCapability(handler.item) != null;
        if (expected.contains(handler) && !present) {
          failures.add(id + " (" + kind.name + "): no " + handler + " handler (registration missing in RegisterCapabilitiesEvent?)");
        } else if (!expected.contains(handler) && present) {
          failures.add(id + (kind == null ? "" : " (" + kind.name + ")") + ": unexpected " + handler + " handler; add its kind to ITEM_KINDS if intended");
        }
      }
      if (!expected.isEmpty()) {
        withHandlers++;
      }
    }
    TConstruct.LOG.info("Item handlers: {} tconstruct items checked, {} expected to expose a handler", checked, withHandlers);
    helper.assertTrue(withHandlers > 0, Component.literal("no tconstruct item is expected to expose a handler"));
    assertNoFailures(helper, "item handler", failures);
    helper.succeed();
  }

  /**
   * Every modifiable item (tools, armor, ranged and so on, not just the pickaxe of {@link CapabilityGameTests}) exposes the handlers its
   * modifiers provide: built with random materials plus the tank and pocket modifiers, it must expose a fluid and an item handler with at
   * least one tank or slot. Energy has no shipped modifier, so only its registration path (shared with the other two) is exercised.
   */
  @TinkerGameTest(template = "gametest/empty_5x5x5", timeoutTicks = 100)
  public static void modifiable_items_expose_modifier_handlers(GameTestHelper helper) {
    List<String> failures = new ArrayList<>();
    RandomSource random = RandomSource.create(0);
    int checked = 0;
    for (ResourceLocation id : new TreeSet<>(BuiltInRegistries.ITEM.keySet())) {
      Item item = BuiltInRegistries.ITEM.getValue(id);
      if (!TConstruct.MOD_ID.equals(id.getNamespace()) || !(item instanceof IModifiable modifiable)) {
        continue;
      }
      checked++;
      ItemStack stack;
      try {
        ToolStack tool = ToolBuildHandler.buildToolRandomMaterials(modifiable, random);
        tool.addModifier(ModifierIds.tank, 1);
        tool.addModifier(ModifierIds.pocket, 1);
        stack = tool.createStack();
      } catch (RuntimeException e) {
        TConstruct.LOG.error("Could not build {} with the tank and pocket modifiers", id, e);
        failures.add(id + ": could not build it with the tank and pocket modifiers: " + e);
        continue;
      }
      ItemAccess access = accessFor(stack);
      for (Handler handler : List.of(Handler.FLUID, Handler.ITEM)) {
        Object found = access.getCapability(handler.item);
        if (!(found instanceof ResourceHandler<?> resource) || resource.size() == 0) {
          failures.add(id + ": " + handler + " handler from its " + (handler == Handler.FLUID ? "tank" : "pocket") + " modifier is "
                       + (found == null ? "missing (ToolCapabilityProvider registration?)" : "empty"));
        }
      }
    }
    helper.assertTrue(checked > 0, Component.literal("no modifiable tconstruct items found"));
    TConstruct.LOG.info("Modifier handlers: {} modifiable items checked", checked);
    assertNoFailures(helper, "modifiable item handler", failures);
    helper.succeed();
  }

  private static void assertNoFailures(GameTestHelper helper, String what, List<String> failures) {
    helper.assertTrue(failures.isEmpty(), Component.literal(failures.size() + " " + what + " failure(s):\n" + String.join("\n", failures)));
  }
}
