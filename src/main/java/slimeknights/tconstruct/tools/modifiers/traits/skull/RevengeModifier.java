package slimeknights.tconstruct.tools.modifiers.traits.skull;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.armor.EquipmentChangeModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.armor.OnAttackedModifierHook;
import slimeknights.tconstruct.library.modifiers.impl.NoLevelsModifier;
import slimeknights.tconstruct.library.module.ModuleHookMap.Builder;
import slimeknights.tconstruct.library.tools.context.EquipmentChangeContext;
import slimeknights.tconstruct.library.tools.context.EquipmentContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

/** @deprecated use {@link slimeknights.tconstruct.library.modifiers.modules.combat.MobEffectModule.ArmorCounter} and {@link slimeknights.tconstruct.tools.modules.ClearEffectOnUnequipModule} */
@Deprecated(forRemoval = true)
public class RevengeModifier extends NoLevelsModifier implements EquipmentChangeModifierHook, OnAttackedModifierHook {
  @Override
  protected void registerHooks(Builder hookBuilder) {
    hookBuilder.addHook(this, ModifierHooks.EQUIPMENT_CHANGE, ModifierHooks.ON_ATTACKED);
  }

  @Override
  public void onAttacked(IToolStackView tool, ModifierEntry modifier, EquipmentContext context, EquipmentSlot slotType, DamageSource source, float amount, boolean isDirectDamage) {
    // must be attacked by entity
    Entity trueSource = source.getEntity();
    LivingEntity living = context.getEntity();
    if (trueSource != null && trueSource != living) { // no making yourself mad with slurping or self-destruct or alike
      MobEffectInstance effect = new MobEffectInstance(MobEffects.DAMAGE_BOOST, 300);
      // PORT M3: Forge's per-ItemStack curative items were replaced by NeoForge EffectCure tokens, which cannot encode
      // "cured by removing this specific helmet". Clear default cures (so it is not milk-curable); the modifier's
      // onUnequip already removes the effect when the helmet changes.
      effect.getCures().clear();
      living.addEffect(effect);
    }
  }

  @Override
  public void onUnequip(IToolStackView tool, ModifierEntry modifier, EquipmentChangeContext context) {
    if (context.getChangedSlot() == EquipmentSlot.HEAD) {
      IToolStackView replacement = context.getReplacementTool();
      if (replacement == null || replacement.getModifierLevel(this) == 0) {
        // PORT M3: Forge's curePotionEffects(ItemStack) (matched our per-helmet curative marker) was removed in 1.21.
        // Since the effect applied by this modifier is strength, remove it directly when the helmet is unequipped.
        context.getEntity().removeEffect(MobEffects.DAMAGE_BOOST);
      }
    }
  }
}
