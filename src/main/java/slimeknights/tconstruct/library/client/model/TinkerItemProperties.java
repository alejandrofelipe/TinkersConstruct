package slimeknights.tconstruct.library.client.model;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import net.neoforged.neoforge.common.ItemAbilities;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.modifiers.hook.interaction.GeneralInteractionModifierHook;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.item.ranged.ModifiableCrossbowItem;
import slimeknights.tconstruct.library.tools.item.ranged.ModifiableLauncherItem;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import javax.annotation.Nullable;

/**
 * Item-model properties for tinker tools.
 * <p>
 * PORT 1.21.4: the removed {@code ItemProperties}/{@code ItemPropertyFunction} per-item registration is replaced by
 * global conditional/range item-model properties registered once via the Register*ItemModelPropertyEvent events and
 * referenced from the item JSON ({@code condition}/{@code range_dispatch} models). The old per-item register methods are
 * kept as no-ops so their callers compile; the wiring now lives in the item JSON (emitted by the datagen batch).
 */
public class TinkerItemProperties {
  /** Boolean property: the tool is broken */
  public static final ResourceLocation BROKEN_ID = TConstruct.getResource("broken");
  /** Boolean property: a fishing rod is cast */
  public static final ResourceLocation CAST_ID = TConstruct.getResource("cast");
  /** Float property: charge/draw animation stage */
  public static final ResourceLocation CHARGING_ID = TConstruct.getResource("charging");
  /** Float property: bow pull amount */
  public static final ResourceLocation CHARGE_ID = TConstruct.getResource("charge");
  /** Float property: crossbow ammo type (0 none, 1 arrow, 2 firework) */
  public static final ResourceLocation AMMO_ID = TConstruct.getResource("ammo");

  /* Conditional properties */

  /** Property declaring the tool is broken */
  public record Broken() implements ConditionalItemModelProperty {
    public static final MapCodec<Broken> MAP_CODEC = MapCodec.unit(new Broken());
    @Override
    public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed, ItemDisplayContext displayContext) {
      return ToolDamageUtil.isBroken(stack);
    }
    @Override
    public MapCodec<Broken> type() {
      return MAP_CODEC;
    }
  }

  /** Property for casting a fishing rod */
  public record Cast() implements ConditionalItemModelProperty {
    public static final MapCodec<Cast> MAP_CODEC = MapCodec.unit(new Cast());
    @Override
    public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed, ItemDisplayContext displayContext) {
      // must be a fishing rod, and the player must be fishing
      if (entity instanceof Player player && player.fishing != null && stack.canPerformAction(ItemAbilities.FISHING_ROD_CAST)) {
        ItemStack mainhand = player.getMainHandItem();
        return mainhand == stack || (player.getOffhandItem() == stack && !mainhand.canPerformAction(ItemAbilities.FISHING_ROD_CAST));
      }
      return false;
    }
    @Override
    public MapCodec<Cast> type() {
      return MAP_CODEC;
    }
  }

  /* Range properties */

  /** Property declaring the bow is pulling, returning the animation stage */
  public record Charging() implements RangeSelectItemModelProperty {
    public static final MapCodec<Charging> MAP_CODEC = MapCodec.unit(new Charging());
    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity holder, int seed) {
      if (holder != null && holder.isUsingItem() && holder.getUseItem() == stack) {
        ItemUseAnimation anim = stack.getUseAnimation();
        if (anim == ItemUseAnimation.BLOCK) {
          return ModifierUtil.checkPersistentPresent(stack, ModifiableLauncherItem.KEY_DRAWBACK_AMMO) ? 2.5f : 2;
        }
        if (anim == ItemUseAnimation.SPEAR) {
          return 1.75f;
        }
        if (anim != ItemUseAnimation.EAT && anim != ItemUseAnimation.DRINK) {
          return ModifierUtil.checkPersistentPresent(stack, ModifiableLauncherItem.KEY_DRAWBACK_AMMO) ? 1.5f : 1;
        }
      }
      return 0;
    }
    @Override
    public MapCodec<Charging> type() {
      return MAP_CODEC;
    }
  }

  /** Property for the bow pull amount */
  public record Charge() implements RangeSelectItemModelProperty {
    public static final MapCodec<Charge> MAP_CODEC = MapCodec.unit(new Charge());
    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity holder, int seed) {
      if (holder == null || holder.getUseItem() != stack) {
        return 0.0F;
      }
      int drawtime = ModifierUtil.getPersistentInt(stack, GeneralInteractionModifierHook.KEY_DRAWTIME, -1);
      return drawtime == -1 ? 0 : (float) (stack.getUseDuration(holder) - holder.getUseItemRemainingTicks()) / drawtime;
    }
    @Override
    public MapCodec<Charge> type() {
      return MAP_CODEC;
    }
  }

  /** Property declaring the crossbow ammo type (0 none, 1 arrow, 2 firework) */
  public record Ammo() implements RangeSelectItemModelProperty {
    public static final MapCodec<Ammo> MAP_CODEC = MapCodec.unit(new Ammo());
    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
      if (!stack.isComponentsPatchEmpty()) {
        CompoundTag ammo = ToolStack.from(stack).getPersistentData().getCompound(ModifiableCrossbowItem.KEY_CROSSBOW_AMMO);
        if (!ammo.isEmpty()) {
          return ammo.getString("id").equals(BuiltInRegistries.ITEM.getKey(Items.FIREWORK_ROCKET).toString()) ? 2 : 1;
        }
      }
      return 0;
    }
    @Override
    public MapCodec<Ammo> type() {
      return MAP_CODEC;
    }
  }

  /* Registration (fired once on the mod bus) */

  /** Registers the boolean conditional properties */
  public static void registerConditional(RegisterConditionalItemModelPropertyEvent event) {
    event.register(BROKEN_ID, Broken.MAP_CODEC);
    event.register(CAST_ID, Cast.MAP_CODEC);
  }

  /** Registers the float range properties */
  public static void registerRange(RegisterRangeSelectItemModelPropertyEvent event) {
    event.register(CHARGING_ID, Charging.MAP_CODEC);
    event.register(CHARGE_ID, Charge.MAP_CODEC);
    event.register(AMMO_ID, Ammo.MAP_CODEC);
  }

  /* Legacy no-op registration kept so existing callers compile; the properties are now global + referenced by item JSON */

  /** @deprecated PORT 1.21.4: per-item property registration is gone; properties are registered globally and referenced in the item JSON */
  @Deprecated
  public static void registerBrokenProperty(net.minecraft.world.item.Item item) {}

  /** @deprecated PORT 1.21.4: see {@link #registerBrokenProperty} */
  @Deprecated
  public static void registerToolProperties(ItemLike itemlike) {}

  /** @deprecated PORT 1.21.4: see {@link #registerBrokenProperty} */
  @Deprecated
  public static void registerCrossbowProperties(ItemLike item) {}
}
