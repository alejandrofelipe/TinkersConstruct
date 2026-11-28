package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.world.TinkerWorld;
import slimeknights.tconstruct.world.entity.ArmoredSlimeEntity;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Tinkers slimes (M8 plan R1 guard S9): 26.2 moves {@code Slime} into a new {@code AbstractCubeMob} base that owns size, split and contact damage,
 * with renamed hooks, so an override of ours may silently stop being called. Checks each Tinkers slime's size scaling, its split on death (the
 * armored slime override passes the helmet and the metal core on), its contact damage, and the spawn predicates.
 */
public class SlimeEntityGameTests {
  /** Every Tinkers slime type */
  private static Map<String, Supplier<EntityType<? extends ArmoredSlimeEntity>>> types() {
    return Map.of(
      "sky_slime", TinkerWorld.skySlimeEntity::get,
      "ender_slime", TinkerWorld.enderSlimeEntity::get,
      "terracube", TinkerWorld.terracubeEntity::get);
  }

  /** Contact damage a size 2 slime deals to a player, measured on 26.1.2: the size, plus 2 for the terracube ({@code TerracubeEntity#getAttackDamage}) */
  private static final Map<String, Float> CONTACT_DAMAGE = Map.of("sky_slime", 2f, "ender_slime", 2f, "terracube", 4f);

  /** Spawns a slime of the given type and size with AI (contact damage needs it) but no goals */
  private static <T extends Slime> T slime(GameTestHelper helper, EntityType<T> type, int size, Vec3 pos) {
    T slime = helper.spawn(type, pos);
    slime.removeFreeWill();
    slime.setSize(size, true);
    return slime;
  }

  /**
   * Absolute bounding box width of a size 1 and a size 4 slime by Tinkers type, measured on 26.1.2. Known issue since the 1.21.1 port: the types are
   * registered {@code sized(2.04F, 2.04F)} and {@code Slime#getDefaultDimensions} scales that by the size again, so a size 1 Tinkers slime is
   * 2.04 wide (a vanilla slime is {@code 0.52 × size}: 0.52 and 2.08). Fixing {@code sized} is a deliberate change of these references.
   */
  private static final Map<String, float[]> KNOWN_WIDTH = Map.of(
    "sky_slime", new float[] {2.04f, 8.16f},
    "ender_slime", new float[] {2.04f, 8.16f},
    "terracube", new float[] {2.04f, 8.16f});

  /** Size sets the bounding box, health and attack damage of every Tinkers slime */
  @TinkerGameTest(template = "gametest/empty_9x9x9", timeoutTicks = 20)
  public static void tinker_slimes_size(GameTestHelper helper) {
    List<String> problems = new ArrayList<>();
    List<String> measured = new ArrayList<>();
    int x = 1;
    for (Map.Entry<String, Supplier<EntityType<? extends ArmoredSlimeEntity>>> entry : types().entrySet()) {
      EntityType<? extends ArmoredSlimeEntity> type = entry.getValue().get();
      for (int size : new int[] {1, 2, 4}) {
        ArmoredSlimeEntity slime = slime(helper, type, size, new Vec3(x, 1, 4.5));
        measured.add(String.format(Locale.ROOT, "%s/%d: width %.3f, health %.1f, attack %.1f", entry.getKey(), size, slime.getBbWidth(), slime.getMaxHealth(), slime.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)));
        if (slime.getSize() != size) {
          problems.add(entry.getKey() + " set to size " + size + " reports " + slime.getSize());
        }
        if (Math.abs(slime.getBbWidth() - type.getWidth() * size) > 1.0E-4) {
          problems.add(String.format(Locale.ROOT, "%s size %d is %.3f wide, expected %.3f", entry.getKey(), size, slime.getBbWidth(), type.getWidth() * size));
        }
        if (size == 1 || size == 4) {
          float known = KNOWN_WIDTH.get(entry.getKey())[size == 1 ? 0 : 1];
          if (Math.abs(slime.getBbWidth() - known) > 1.0E-4) {
            problems.add(String.format(Locale.ROOT, "%s size %d is %.3f wide, the 26.1.2 reference is %.3f (known issue since the 1.21.1 port: sized(2.04) "
                                                    + "instead of vanilla's 0.52 x size; if the size was fixed, update KNOWN_WIDTH deliberately)",
                                       entry.getKey(), size, slime.getBbWidth(), known));
          }
        }
        if (slime.getMaxHealth() != size * size) {
          problems.add(entry.getKey() + " size " + size + " has " + slime.getMaxHealth() + " max health, expected " + size * size);
        }
        slime.discard();
      }
      x += 3;
    }
    TConstruct.LOG.info("Tinkers slime sizes: {}", measured);
    helper.assertTrue(problems.isEmpty(), Component.literal(String.join("; ", problems)));
    helper.succeed();
  }

  /** Sets the metal core through the protected setter the spawn logic uses */
  private static void setMetal(ArmoredSlimeEntity slime) {
    try {
      Method setMetal = ArmoredSlimeEntity.class.getDeclaredMethod("setMetal", boolean.class);
      setMetal.setAccessible(true);
      setMetal.invoke(slime, true);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("cannot set the metal core", e);
    }
  }

  /**
   * A size 2 armored slime with a helmet that never drops and a metal core splits into 2 to 4 size 1 slimes of its type on death
   * ({@code ArmoredSlimeEntity#remove}): one of them wears the helmet, every one has the metal core.
   */
  @TinkerGameTest(template = "gametest/empty_9x9x9", timeoutTicks = 100)
  public static void tinker_slimes_split_on_death(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    List<Runnable> checks = new ArrayList<>();
    int x = 1;
    for (Map.Entry<String, Supplier<EntityType<? extends ArmoredSlimeEntity>>> entry : types().entrySet()) {
      EntityType<? extends ArmoredSlimeEntity> type = entry.getValue().get();
      ArmoredSlimeEntity slime = slime(helper, type, 2, new Vec3(x + 0.5, 1, 4.5));
      slime.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
      slime.setDropChance(EquipmentSlot.HEAD, 0);
      setMetal(slime);
      slime.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
      helper.assertTrue(slime.isDeadOrDying(), Component.literal(entry.getKey() + " survived the kill"));
      AABB area = helper.absoluteAABB(new AABB(x - 1, 0, 2, x + 2, 4, 7));
      checks.add(() -> {
        helper.assertTrue(slime.isRemoved(), Component.literal(entry.getKey() + " is not removed yet"));
        List<? extends ArmoredSlimeEntity> children = level.getEntities(type, area, child -> child != slime && child.isAlive());
        helper.assertTrue(children.size() >= 2 && children.size() <= 4, Component.literal(entry.getKey() + " split into " + children.size() + " slimes, expected 2 to 4"));
        for (ArmoredSlimeEntity child : children) {
          helper.assertTrue(child.getSize() == 1, Component.literal(entry.getKey() + " child has size " + child.getSize() + ", expected 1"));
          helper.assertTrue(child.isMetal(), Component.literal(entry.getKey() + " child lost the metal core"));
        }
        helper.assertTrue(children.stream().anyMatch(child -> child.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET)),
                          Component.literal(entry.getKey() + ": no child wears the helmet"));
      });
      x += 3;
    }
    helper.succeedWhen(() -> checks.forEach(Runnable::run));
  }

  /** A size 2 slime touching a player deals its contact damage ({@code Slime#playerTouch} through {@code isDealsDamage} and {@code getAttackDamage}) */
  @TinkerGameTest(template = "gametest/empty_9x9x9", timeoutTicks = 20)
  public static void tinker_slimes_contact_damage(GameTestHelper helper) {
    List<String> measured = new ArrayList<>();
    List<String> problems = new ArrayList<>();
    int x = 1;
    for (Map.Entry<String, Supplier<EntityType<? extends ArmoredSlimeEntity>>> entry : types().entrySet()) {
      ArmoredSlimeEntity slime = slime(helper, entry.getValue().get(), 2, new Vec3(x + 0.5, 1, 4.5));
      Player player = helper.makeMockPlayer(GameType.SURVIVAL);
      Vec3 pos = slime.position();
      player.setPos(pos.x, pos.y, pos.z);
      float health = player.getHealth();
      slime.playerTouch(player);
      float damage = health - player.getHealth();
      measured.add(String.format(Locale.ROOT, "%s=%.2f", entry.getKey(), damage));
      Float expected = CONTACT_DAMAGE.get(entry.getKey());
      if (expected == null) {
        problems.add(String.format(Locale.ROOT, "%s dealt %.2f, no reference", entry.getKey(), damage));
      } else if (Math.abs(expected - damage) > 0.01) {
        problems.add(String.format(Locale.ROOT, "%s dealt %.2f, expected %.2f", entry.getKey(), damage, expected));
      }
      x += 3;
    }
    TConstruct.LOG.info("Tinkers slime contact damage (difficulty {}): {}", helper.getLevel().getDifficulty(), measured);
    helper.assertTrue(problems.isEmpty(), Component.literal("slime contact damage differs from the 26.1.2 reference: " + String.join("; ", problems)));
    helper.succeed();
  }

  /** First block of the tag, for a spawn floor */
  private static Block first(TagKey<Block> tag) {
    for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
      return holder.value();
    }
    throw new IllegalStateException("block tag " + tag.location() + " is empty");
  }

  /** Checks the registered spawn rules of the type on the floor block, for the spawn reason */
  private static boolean canSpawn(GameTestHelper helper, EntityType<?> type, BlockPos floor, Block block, EntitySpawnReason reason) {
    helper.setBlock(floor, block);
    return SpawnPlacements.checkSpawnRules(type, helper.getLevel(), reason, helper.absolutePos(floor.above()), helper.getLevel().getRandom());
  }

  /**
   * The spawn predicates registered for Tinkers slimes ({@code SlimePlacementPredicate}, {@code TerracubeEntity#canSpawnHere}) and the earth slime
   * spawn added to the vanilla slime: natural spawns need their slime blocks below (or water for the terracube), spawners always work.
   */
  @TinkerGameTest(template = "gametest/empty_9x9x9", timeoutTicks = 20)
  public static void tinker_slimes_spawn_rules(GameTestHelper helper) {
    helper.assertTrue(helper.getLevel().getDifficulty() != Difficulty.PEACEFUL, Component.literal("the gametest level is peaceful, the spawn rules refuse everything"));
    BlockPos floor = new BlockPos(4, 1, 4);
    Map<String, Boolean> cases = new java.util.LinkedHashMap<>();
    cases.put("sky_slime on " + first(TinkerTags.Blocks.SKY_SLIME_SPAWN), canSpawn(helper, TinkerWorld.skySlimeEntity.get(), floor, first(TinkerTags.Blocks.SKY_SLIME_SPAWN), EntitySpawnReason.NATURAL));
    cases.put("ender_slime on " + first(TinkerTags.Blocks.ENDER_SLIME_SPAWN), canSpawn(helper, TinkerWorld.enderSlimeEntity.get(), floor, first(TinkerTags.Blocks.ENDER_SLIME_SPAWN), EntitySpawnReason.NATURAL));
    cases.put("slime on " + first(TinkerTags.Blocks.EARTH_SLIME_SPAWN), canSpawn(helper, EntityType.SLIME, floor, first(TinkerTags.Blocks.EARTH_SLIME_SPAWN), EntitySpawnReason.NATURAL));
    cases.put("sky_slime spawner on stone", canSpawn(helper, TinkerWorld.skySlimeEntity.get(), floor, Blocks.STONE, EntitySpawnReason.SPAWNER));
    cases.put("terracube spawner on stone", canSpawn(helper, TinkerWorld.terracubeEntity.get(), floor, Blocks.STONE, EntitySpawnReason.SPAWNER));
    helper.setBlock(floor.above(), Blocks.WATER);
    cases.put("terracube in water", canSpawn(helper, TinkerWorld.terracubeEntity.get(), floor, Blocks.WATER, EntitySpawnReason.NATURAL));
    helper.setBlock(floor.above(), Blocks.AIR);
    Map<String, Boolean> rejected = new java.util.LinkedHashMap<>();
    rejected.put("sky_slime on stone", canSpawn(helper, TinkerWorld.skySlimeEntity.get(), floor, Blocks.STONE, EntitySpawnReason.NATURAL));
    rejected.put("ender_slime on " + first(TinkerTags.Blocks.SKY_SLIME_SPAWN), canSpawn(helper, TinkerWorld.enderSlimeEntity.get(), floor, first(TinkerTags.Blocks.SKY_SLIME_SPAWN), EntitySpawnReason.NATURAL));
    List<String> problems = new ArrayList<>();
    cases.forEach((name, allowed) -> {
      if (!allowed) {
        problems.add(name + " is refused");
      }
    });
    rejected.forEach((name, allowed) -> {
      if (allowed) {
        problems.add(name + " is allowed");
      }
    });
    helper.assertTrue(problems.isEmpty(), Component.literal("slime spawn rules: " + String.join("; ", problems)));
    helper.succeed();
  }
}
