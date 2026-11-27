package slimeknights.tconstruct.world;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.SkullBlock;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent.LivingVisibilityEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.config.Config;

@SuppressWarnings("unused")
@EventBusSubscriber(modid = TConstruct.MOD_ID)
public class WorldEvents {
  /* Heads */

  @SubscribeEvent
  static void livingVisibility(LivingVisibilityEvent event) {
    Entity lookingEntity = event.getLookingEntity();
    if (lookingEntity == null) {
      return;
    }
    ItemStack helmet = event.getEntity().getItemBySlot(EquipmentSlot.HEAD);
    Item item = helmet.getItem();
    if (item != Items.AIR && TinkerWorld.headItems.contains(item)) {
      if (lookingEntity.getType() == ((TinkerHeadType)((SkullBlock)((BlockItem)item).getBlock()).getType()).getType()) {
        event.modifyVisibility(0.5f);
      }
    }
  }

  @SubscribeEvent
  static void creeperKill(LivingDropsEvent event) {
    DamageSource source = event.getSource();
    if (source != null) {
      Entity entity = source.getEntity();
      if (entity instanceof Creeper creeper) {
        // 1.21.9: canDropMobsSkull() == isPowered() && no skull dropped yet (vanilla sets the flag in killedEntity, before drops)
        if (creeper.isPowered() && !creeper.droppedSkulls) {
          LivingEntity dying = event.getEntity();
          TinkerHeadType headType = TinkerHeadType.fromEntityType(dying.getType());
          if (headType != null && Config.COMMON.headDrops.get(headType).get()) {
            creeper.droppedSkulls = true;
            // only add the entity to the drops: vanilla adds the drops to the level unless another listener cancels the event
            // (spawnAtLocation would also add it to the level right away, as nothing captures drops while the event runs)
            ItemEntity head = new ItemEntity(dying.level(), dying.getX(), dying.getY(), dying.getZ(), new ItemStack(TinkerWorld.heads.get(headType)));
            head.setDefaultPickUpDelay();
            event.getDrops().add(head);
          }
        }
      }
    }
  }

  // 26.1: the wandering trader ancient tool trade is data (villager_trade tconstruct:ancient_tool, AncientToolTradeFunction)
}
