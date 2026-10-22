package slimeknights.tconstruct.tools.modifiers.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.MobEffectTextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.modifiers.hook.interaction.GeneralInteractionModifierHook;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.tools.TinkerModifiers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Effect for rendering the charge up when you start using a helmet */
public class HelmetChargingEffect extends MobEffect {
  public HelmetChargingEffect() {
    super(MobEffectCategory.NEUTRAL, -1);
  }

  // 1.21.4: MobEffect#initializeClient was removed; register via RegisterClientExtensionsEvent#registerMobEffect
  // (e.g. event.registerMobEffect(ext, effect)) from a client mod-bus handler. Kept here as the extension factory.
  public void initializeClient(Consumer<IClientMobEffectExtensions> consumer) {
    consumer.accept(new IClientMobEffectExtensions() {
      private static final ResourceLocation BAR_KEY = TConstruct.getResource("helmet_charging_bar");
      private final Minecraft mc = Minecraft.getInstance();

      @Override
      public boolean isVisibleInInventory(MobEffectInstance instance) {
        return false;
      }

      @Override
      public boolean renderGuiIcon(MobEffectInstance instance, Gui gui, GuiGraphics graphics, int x, int y, float z, float alpha) {
        // start by drawing the original texture, skip alpha
        MobEffectTextureManager textures = mc.getMobEffectTextures();
        TextureAtlasSprite sprite = textures.get(instance.getEffect());
        graphics.blitSprite(RenderType::guiTextured, sprite, x + 3, y + 3, 18, 18);

        // if the helmet has a drawtime, render the extra bar
        if (mc.player != null) {
          ItemStack helmet = mc.player.getItemBySlot(EquipmentSlot.HEAD);
          if (!helmet.isEmpty()) {
            // if we have a drawtime, draw the bar overlaying the icon
            int duration = instance.getDuration();
            int drawtime = ModifierUtil.getPersistentInt(helmet, GeneralInteractionModifierHook.KEY_DRAWTIME, 0);
            int dd = drawtime + 20;
            if (drawtime > 0 && duration < dd) {
              sprite = textures.getSprite(BAR_KEY);
              int height;
              if (duration < 20) {
                height = 18;
              } else {
                height = (dd - duration) * 18 / drawtime;
              }
              int yOffset = (18 - height);
              // 1.21.4: GuiGraphics#innerBlit is private; clip the full bar sprite to its lower portion with a scissor
              graphics.enableScissor(x + 3, y + 3 + yOffset, x + 21, y + 21);
              graphics.blitSprite(RenderType::guiTextured, sprite, x + 3, y + 3, 18, 18);
              graphics.disableScissor();
            }
          }
        }
        return true;
      }
    });
  }


  /* Helpers */

  /** Starts using the helmet with the charge time rendering */
  public static int startUsingHelmet(IToolStackView tool, LivingEntity living, float speedFactor) {
    int time = GeneralInteractionModifierHook.startDrawing(tool, living, speedFactor);
    living.addEffect(new MobEffectInstance(TinkerModifiers.helmetCharging, time + 20, 0, true, false, true));
    return time;
  }
}
