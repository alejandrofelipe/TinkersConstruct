package slimeknights.tconstruct.tools.logic;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import slimeknights.tconstruct.library.events.ToolEquipmentChangeEvent;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.tools.context.EquipmentChangeContext;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Logic to make it easy for modifiers to react to equipment changes, primarily used for armor.
 * <p>
 * In NeoForge 1.21 the old per-entity {@link slimeknights.tconstruct.library.tools.capability} provider was replaced by
 * a simple client-side tracking map. The previous {@code PlayerLastEquipment} capability only ever ran on the client, so
 * a {@link WeakHashMap} keyed by player is sufficient without any capability/attachment registration.
 */
public class EquipmentChangeWatcher {
  private EquipmentChangeWatcher() {}

  /** Client side per-player tracking of last equipment, replacing the old capability provider */
  private static final Map<Player,PlayerLastEquipment> CLIENT_TRACKER = new WeakHashMap<>();

  /** Registers this listener */
  public static void register() {
    // equipment change is used on both sides
    NeoForge.EVENT_BUS.addListener(EquipmentChangeWatcher::onEquipmentChange);

    // only need to track equipment and the player tick on the client
    if (FMLEnvironment.dist == Dist.CLIENT) {
      NeoForge.EVENT_BUS.addListener(EquipmentChangeWatcher::onPlayerTick);
    }
  }


  /* Events */

  /** Serverside modifier hooks */
  private static void onEquipmentChange(LivingEquipmentChangeEvent event) {
    runModifierHooks(event.getEntity(), event.getSlot(), event.getFrom(), event.getTo());
  }

  /** Client side modifier hooks */
  private static void onPlayerTick(PlayerTickEvent.Post event) {
    Player player = event.getEntity();
    if (player.level().isClientSide) {
      CLIENT_TRACKER.computeIfAbsent(player, PlayerLastEquipment::new).update();
    }
  }


  /* Helpers */

  /** Shared modifier hook logic */
  private static void runModifierHooks(LivingEntity entity, EquipmentSlot changedSlot, ItemStack original, ItemStack replacement) {
    EquipmentChangeContext context = new EquipmentChangeContext(entity, changedSlot, original, replacement);

    // first, fire event to notify an item was removed
    IToolStackView tool = context.getOriginalTool();
    if (tool != null && ModifierUtil.validArmorSlot(tool, changedSlot)) {
      for (ModifierEntry entry : tool.getModifierList()) {
        entry.getHook(ModifierHooks.EQUIPMENT_CHANGE).onUnequip(tool, entry, context);
      }
    }

    // next, fire event to notify an item was added
    tool = context.getReplacementTool();
    if (tool != null && ModifierUtil.validArmorSlot(tool, changedSlot)) {
      for (ModifierEntry entry : tool.getModifierList()) {
        entry.getHook(ModifierHooks.EQUIPMENT_CHANGE).onEquip(tool, entry, context);
      }
    }

    // finally, fire events on all other slots to say something changed
    for (EquipmentSlot otherSlot : EquipmentSlot.values()) {
      if (otherSlot != changedSlot) {
        tool = context.getValidTool(otherSlot);
        if (tool != null) {
          for (ModifierEntry entry : tool.getModifierList()) {
            entry.getHook(ModifierHooks.EQUIPMENT_CHANGE).onEquipmentChange(tool, entry, context, otherSlot);
          }
        }
      }
    }
    // fire event for modifiers that want to watch equipment when not equipped
    NeoForge.EVENT_BUS.post(new ToolEquipmentChangeEvent(context));
  }

  /* Required methods */

  /** Data class that runs actual update logic */
  protected static class PlayerLastEquipment implements Runnable {
    @Nullable
    private final Player player;
    private final Map<EquipmentSlot,ItemStack> lastItems = new EnumMap<>(EquipmentSlot.class);

    private PlayerLastEquipment(@Nullable Player player) {
      this.player = player;
      for (EquipmentSlot slot : EquipmentSlot.values()) {
        lastItems.put(slot, ItemStack.EMPTY);
      }
    }

    /** Called on player tick to update the stacks and run the event */
    public void update() {
      // run every tick on the client, should be plenty fast enough
      if (player != null) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
          ItemStack newStack = player.getItemBySlot(slot);
          ItemStack oldStack = lastItems.get(slot);
          if (!ItemStack.matches(oldStack, newStack)) {
            lastItems.put(slot, newStack.copy());
            runModifierHooks(player, slot, oldStack, newStack);
          }
        }
      }
    }

    @Override
    public void run() {}
  }
}
