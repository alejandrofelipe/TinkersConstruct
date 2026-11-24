package slimeknights.tconstruct.shared;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.network.chat.FontDescription;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.ClientEventBase;
import slimeknights.tconstruct.library.client.book.TinkerBook;
import slimeknights.tconstruct.library.client.model.UniqueGuiModel;
import slimeknights.tconstruct.library.utils.DomainDisplayName;
import slimeknights.tconstruct.shared.client.FluidParticle;

import javax.annotation.Nullable;

@EventBusSubscriber(modid = TConstruct.MOD_ID, value = Dist.CLIENT)
public class CommonsClientEvents extends ClientEventBase {
  @SubscribeEvent
  static void addResourceListeners(AddClientReloadListenersEvent event) {
    DomainDisplayName.addResourceListener(event);
  }

  @SubscribeEvent
  static void registerBlockModelLoaders(ModelEvent.RegisterLoaders event) {
    event.register(TConstruct.getResource("gui"), UniqueGuiModel.LOADER);
  }

  @SubscribeEvent
  static void clientSetup(final FMLClientSetupEvent event) {
    Font unicode = unicodeFontRender();
    TinkerBook.MATERIALS_AND_YOU.fontRenderer = unicode;
    TinkerBook.TINKERS_GADGETRY.fontRenderer = unicode;
    TinkerBook.PUNY_SMELTING.fontRenderer = unicode;
    TinkerBook.MIGHTY_SMELTING.fontRenderer = unicode;
    TinkerBook.FANTASTIC_FOUNDRY.fontRenderer = unicode;
    TinkerBook.ENCYCLOPEDIA.fontRenderer = unicode;
  }

  @SubscribeEvent
  static void registerParticleFactories(RegisterParticleProvidersEvent event) {
    event.registerSpecial(TinkerCommons.fluidParticle.get(), new FluidParticle.Factory());
  }

  private static Font unicodeRenderer;

  /** Gets the unicode font renderer */
  public static Font unicodeFontRender() {
    if (unicodeRenderer == null)
      // 1.21.9: fonts take a provider of glyph sources per font description; the default font is drawn with the uniform font set,
      // every other description (other fonts, atlas sprites, player heads) resolves like the vanilla font
      unicodeRenderer = new Font(new Font.Provider() {
        /** Provider of the vanilla font, resolves every description */
        private Font.Provider vanilla() {
          return Minecraft.getInstance().font.provider;
        }

        /** Uniform font set, null if the resource packs do not define it */
        @Nullable
        private FontSet uniform() {
          return Minecraft.getInstance().fontManager.fontSets.get(Minecraft.UNIFORM_FONT);
        }

        @Override
        public GlyphSource glyphs(FontDescription fontDescription) {
          if (FontDescription.DEFAULT.equals(fontDescription)) {
            FontSet uniform = uniform();
            if (uniform != null) {
              return uniform.source(false);
            }
          }
          return vanilla().glyphs(fontDescription);
        }

        @Override
        public EffectGlyph effect() {
          FontSet uniform = uniform();
          return uniform != null ? uniform.whiteGlyph() : vanilla().effect();
        }
      });

    return unicodeRenderer;
  }
}
