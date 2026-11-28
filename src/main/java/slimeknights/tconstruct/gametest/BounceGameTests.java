package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.shared.TinkerEffects;
import slimeknights.tconstruct.shared.TinkerMaterials;
import slimeknights.tconstruct.shared.block.SlimeType;
import slimeknights.tconstruct.world.TinkerWorld;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Bounce physics reference (M8 plan R1 guard S5): 26.2 removes {@code Block#updateEntityMovementAfterFallOn}, where Tinkers' congealed slime and
 * slimesteel implement their bounce today, in favour of a restitution property applied by {@code Entity}. A port through the new hook compiles and may
 * bounce differently or not at all. These tests drop a player, an item and a mob onto every bouncy block and record the apex of the first rebound,
 * relative to the block top, against reference numbers measured on 26.1.2; plus the antigravity effect hitting a ceiling (fall damage and rebound).
 */
public class BounceGameTests {
  /** Height of the drop start above the pad top */
  private static final double DROP = 6;
  /** Allowed difference to the reference apex, in blocks */
  private static final double TOLERANCE = 0.1;
  /** Pad positions in the 9x9x9 arena, one per bouncy block */
  private static final List<BlockPos> PADS = List.of(
    new BlockPos(1, 0, 1), new BlockPos(4, 0, 1), new BlockPos(7, 0, 1),
    new BlockPos(1, 0, 4), new BlockPos(4, 0, 4), new BlockPos(7, 0, 4),
    new BlockPos(1, 0, 7));

  /** Every bouncy block, in pad order */
  private static Map<String, Supplier<BlockState>> pads() {
    Map<String, Supplier<BlockState>> pads = new LinkedHashMap<>();
    for (SlimeType type : SlimeType.values()) {
      pads.put("congealed_" + type.getSerializedName(), () -> TinkerWorld.congealedSlime.get(type).defaultBlockState());
    }
    pads.put("slimesteel", () -> TinkerMaterials.slimesteel.get().defaultBlockState());
    pads.put("slime_block", Blocks.SLIME_BLOCK::defaultBlockState);
    return pads;
  }

  /**
   * Rebound apex above the pad top measured on 26.1.2 (M8 Phase 0.5c), by entity kind and pad. Congealed slime returns all of the speed of a living
   * entity (0.8 for an item), slimesteel 0.75 (0.6), the vanilla slime block 1.0 (0.8).
   */
  private static final Map<String, Double> APEX = Map.ofEntries(
    Map.entry("player/congealed_earth", 4.052), Map.entry("player/congealed_sky", 4.052), Map.entry("player/congealed_ichor", 4.052),
    Map.entry("player/congealed_ender", 4.052), Map.entry("player/slimesteel", 2.279), Map.entry("player/slime_block", 4.052),
    Map.entry("mob/congealed_earth", 4.052), Map.entry("mob/congealed_sky", 4.052), Map.entry("mob/congealed_ichor", 4.052),
    Map.entry("mob/congealed_ender", 4.052), Map.entry("mob/slimesteel", 2.279), Map.entry("mob/slime_block", 4.052),
    // items: congealed slime also lifts them off the ground (no friction tick), so they rise higher than from the vanilla slime block
    Map.entry("item/congealed_earth", 2.299), Map.entry("item/congealed_sky", 2.299), Map.entry("item/congealed_ichor", 2.299),
    Map.entry("item/congealed_ender", 2.299), Map.entry("item/slimesteel", 1.024), Map.entry("item/slime_block", 1.934));

  /** One falling entity and its rebound bookkeeping */
  private static final class Drop {
    private final String key;
    private final Entity entity;
    private final double padTop;
    private double lowest = Double.MAX_VALUE;
    private double highest = -Double.MAX_VALUE;
    private boolean rising;
    private Double apex;

    private Drop(String key, Entity entity, double padTop) {
      this.key = key;
      this.entity = entity;
      this.padTop = padTop;
    }

    /** Updates the bookkeeping after the entity moved this tick */
    private void update() {
      if (apex != null) {
        return;
      }
      double y = entity.getY();
      double velocity = entity.getDeltaMovement().y;
      lowest = Math.min(lowest, y);
      if (velocity > 0) {
        rising = true;
        highest = Math.max(highest, y);
      } else if (rising) {
        apex = Math.max(highest, y) - padTop;
      }
    }

    @Override
    public String toString() {
      return apex != null ? String.format(Locale.ROOT, "%s=%.3f", key, apex) : String.format(Locale.ROOT, "%s=no rebound (lowest %.3f above the pad)", key, lowest - padTop);
    }
  }

  /** Builds the pads, drops one entity of the kind above each and checks the rebound apexes once all are measured */
  private static void dropOnPads(GameTestHelper helper, String kind, Function<Vec3, Entity> spawner, boolean tickManually) {
    List<Drop> drops = new ArrayList<>();
    int i = 0;
    for (Map.Entry<String, Supplier<BlockState>> pad : pads().entrySet()) {
      BlockPos pos = PADS.get(i++);
      BlockState state = pad.getValue().get();
      helper.setBlock(pos, state);
      double padTop = helper.absolutePos(pos).getY() + state.getCollisionShape(helper.getLevel(), helper.absolutePos(pos)).max(net.minecraft.core.Direction.Axis.Y);
      Entity entity = spawner.apply(new Vec3(pos.getX() + 0.5, padTop - helper.absolutePos(BlockPos.ZERO).getY() + DROP, pos.getZ() + 0.5));
      entity.setDeltaMovement(Vec3.ZERO);
      drops.add(new Drop(kind + "/" + pad.getKey(), entity, padTop));
    }
    helper.onEachTick(() -> {
      for (Drop drop : drops) {
        if (tickManually) {
          drop.entity.tick();
        }
        drop.update();
      }
      if (drops.stream().allMatch(drop -> drop.apex != null)) {
        checkApexes(helper, drops);
      }
    });
    helper.runBeforeTestEnd(() -> helper.fail(Component.literal("not every drop rebounded: " + drops)));
  }

  /** Compares the measured apexes with the reference, naming every difference */
  private static void checkApexes(GameTestHelper helper, List<Drop> drops) {
    TConstruct.LOG.info("Bounce apexes: {}", drops);
    List<String> problems = new ArrayList<>();
    for (Drop drop : drops) {
      Double expected = APEX.get(drop.key);
      if (expected == null) {
        problems.add(drop + " has no reference");
      } else if (Math.abs(expected - drop.apex) > TOLERANCE) {
        problems.add(String.format(Locale.ROOT, "%s, expected %.3f", drop, expected));
      }
    }
    if (problems.isEmpty()) {
      helper.succeed();
    } else {
      helper.fail(Component.literal("rebound apex differs from the 26.1.2 reference: " + String.join("; ", problems)));
    }
  }

  /**
   * A player, ticked by the test as the mock player is not in the level, falls onto each pad.
   * <p>
   * Limitation: this is a server side mock player ticked by hand. A real player's movement, and so its bounce, runs on the client
   * ({@code LocalPlayer}), which this test does not cover. If 26.2's restitution hook behaves differently between the sides, Phase 4 must check the
   * client side bounce in a uitest (see {@code docs/boundary-contracts.md}, S5 row).
   */
  @TinkerGameTest(template = "gametest/empty_9x9x9", timeoutTicks = 200)
  public static void slime_bounce_heights_player(GameTestHelper helper) {
    dropOnPads(helper, "player", pos -> {
      Player player = helper.makeMockPlayer(GameType.SURVIVAL);
      player.setPos(helper.absoluteVec(pos));
      return player;
    }, true);
  }

  /** An item falls onto each pad */
  @TinkerGameTest(template = "gametest/empty_9x9x9", timeoutTicks = 200)
  public static void slime_bounce_heights_item(GameTestHelper helper) {
    dropOnPads(helper, "item", pos -> {
      var item = helper.spawnItem(Items.STONE, pos);
      item.setNeverPickUp();
      // an item on the ground only moves on ticks where (tickCount + id) % 4 == 0, which delays the rebound off a block that leaves it on the ground by
      // up to 3 ticks depending on the entity id; start every item in the same phase so the apex is reproducible
      item.tickCount = Math.floorMod(-item.getId(), 4);
      return item;
    }, false);
  }

  /** A mob without AI goals falls onto each pad */
  @TinkerGameTest(template = "gametest/empty_9x9x9", timeoutTicks = 200)
  public static void slime_bounce_heights_mob(GameTestHelper helper) {
    dropOnPads(helper, "mob", pos -> helper.spawnWithNoFreeWill(EntityType.COW, pos), false);
  }


  /* Antigravity */

  /** Ceiling blocks for the antigravity test, in pad order */
  private static final List<String> CEILINGS = List.of("stone", "congealed_earth", "slimesteel", "slime_block");
  /** Damage taken and rebound below the ceiling contact measured on 26.1.2, as {@code damage, rebound} */
  private static final Map<String, double[]> CEILING_REFERENCE = Map.of(
    // on 26.1.2 no ceiling rebounds: the effect sets the upwards speed and the block hooks only reverse a downwards one; slime cancels the damage
    "stone", new double[] {1, 0},
    "congealed_earth", new double[] {0, 0},
    "slimesteel", new double[] {0, 0},
    "slime_block", new double[] {0, 0});

  /**
   * The antigravity effect makes a mob fall up into a ceiling: {@code AntigravityEffect} deals the fall damage of the ceiling block (none for slime)
   * and calls the block's bounce hook with the upwards speed. Records the damage taken and how far the mob rebounds away from the ceiling.
   */
  @TinkerGameTest(template = "gametest/empty_9x9x9", timeoutTicks = 200)
  public static void antigravity_ceiling_bounce(GameTestHelper helper) {
    Map<String, BlockState> ceilings = Map.of(
      "stone", Blocks.STONE.defaultBlockState(),
      "congealed_earth", TinkerWorld.congealedSlime.get(SlimeType.EARTH).defaultBlockState(),
      "slimesteel", TinkerMaterials.slimesteel.get().defaultBlockState(),
      "slime_block", Blocks.SLIME_BLOCK.defaultBlockState());
    Holder<net.minecraft.world.effect.MobEffect> antigravity = TinkerEffects.antigravity;
    List<CeilingHit> hits = new ArrayList<>();
    for (int i = 0; i < CEILINGS.size(); i++) {
      String name = CEILINGS.get(i);
      BlockPos column = PADS.get(i);
      BlockPos ceiling = column.atY(8);
      helper.setBlock(ceiling, ceilings.get(name));
      Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, new Vec3(column.getX() + 0.5, 1, column.getZ() + 0.5));
      cow.addEffect(new MobEffectInstance(antigravity, 400, 0, false, false));
      hits.add(new CeilingHit(name, cow, helper.absolutePos(ceiling).getY()));
    }
    helper.onEachTick(() -> {
      hits.forEach(CeilingHit::update);
      if (hits.stream().allMatch(hit -> hit.done)) {
        TConstruct.LOG.info("Antigravity ceiling hits: {}", hits);
        List<String> problems = new ArrayList<>();
        for (CeilingHit hit : hits) {
          double[] expected = CEILING_REFERENCE.get(hit.name);
          if (expected == null) {
            problems.add(hit + " has no reference");
          } else if (Math.abs(expected[0] - hit.damage()) > 0.01 || Math.abs(expected[1] - hit.rebound()) > TOLERANCE) {
            problems.add(String.format(Locale.ROOT, "%s, expected damage %.2f rebound %.3f", hit, expected[0], expected[1]));
          }
        }
        if (problems.isEmpty()) {
          helper.succeed();
        } else {
          helper.fail(Component.literal("antigravity ceiling hit differs from the 26.1.2 reference: " + String.join("; ", problems)));
        }
      }
    });
    helper.runBeforeTestEnd(() -> helper.fail(Component.literal("not every mob reached its ceiling: " + hits)));
  }

  /** A mob under antigravity rising into a ceiling: damage taken and the farthest it moved back down after touching it */
  private static final class CeilingHit {
    /** Ticks to watch after the first contact */
    private static final int WATCH = 30;
    private final String name;
    private final Cow cow;
    private final double ceilingBottom;
    private final float startHealth;
    private int contactTicks = -1;
    private double contactTop;
    private double lowestTop = Double.MAX_VALUE;
    private boolean done;

    private CeilingHit(String name, Cow cow, double ceilingBottom) {
      this.name = name;
      this.cow = cow;
      this.ceilingBottom = ceilingBottom;
      this.startHealth = cow.getHealth();
    }

    private void update() {
      if (done) {
        return;
      }
      double top = cow.getBoundingBox().maxY;
      if (contactTicks < 0) {
        if (top >= ceilingBottom - 1.0E-3) {
          contactTicks = 0;
          contactTop = top;
          lowestTop = top;
        }
      } else {
        lowestTop = Math.min(lowestTop, top);
        if (++contactTicks >= WATCH) {
          done = true;
        }
      }
    }

    private float damage() {
      return startHealth - cow.getHealth();
    }

    private double rebound() {
      return contactTop - lowestTop;
    }

    @Override
    public String toString() {
      return contactTicks < 0 ? String.format(Locale.ROOT, "%s=no contact (top %.3f, ceiling %.3f)", name, cow.getBoundingBox().maxY, ceilingBottom)
                              : String.format(Locale.ROOT, "%s=damage %.2f rebound %.3f", name, damage(), rebound());
    }
  }
}
