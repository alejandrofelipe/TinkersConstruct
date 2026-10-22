package slimeknights.tconstruct.plugin.jei.util;

import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.PotionContents;

import javax.annotation.Nullable;

/** Common logic for subtype interpreter between the fluid and item form of our potion. Based on a JEI class with the same name */
public interface PotionSubtypeInterpreter<T> extends SimpleSubtypeInterpreter<T> {
  /** Gets the potion contents component from the ingredient, or null if it has none */
  @Nullable
  PotionContents getContents(T ingredient);

  @Nullable
  @Override
  default Object getSubtypeData(T ingredient, UidContext context) {
    PotionContents contents = getContents(ingredient);
    if (contents == null) {
      return null;
    }
    // 1.21.4: the static Potion.getName(Optional<Holder<Potion>>, String) was removed; build a stable id from the potion's base name
    String potionTypeString = contents.potion().map(holder -> holder.value().name()).orElse("");
    StringBuilder stringBuilder = new StringBuilder(potionTypeString);
    for (MobEffectInstance effect : contents.getAllEffects()) {
      stringBuilder.append(";").append(effect);
    }
    return stringBuilder.toString();
  }
}
