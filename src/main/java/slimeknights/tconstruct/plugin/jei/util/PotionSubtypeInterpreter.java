package slimeknights.tconstruct.plugin.jei.util;

import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

import javax.annotation.Nullable;

/** Common logic for subtype interpreter between the fluid and item form of our potion. Based on a JEI class with the same name */
public interface PotionSubtypeInterpreter<T> extends IIngredientSubtypeInterpreter<T> {
  /** Gets the potion contents component from the ingredient, or null if it has none */
  @Nullable
  PotionContents getContents(T ingredient);

  @Override
  default String apply(T ingredient, UidContext context) {
    PotionContents contents = getContents(ingredient);
    if (contents == null) {
      return IIngredientSubtypeInterpreter.NONE;
    }
    String potionTypeString = Potion.getName(contents.potion(), "");
    StringBuilder stringBuilder = new StringBuilder(potionTypeString);
    for (MobEffectInstance effect : contents.getAllEffects()) {
      stringBuilder.append(";").append(effect);
    }
    return stringBuilder.toString();
  }
}
