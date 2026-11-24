package slimeknights.tconstruct.shared;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontManager;
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
      // 1.21.9: fonts take a provider of glyph sources per font description; always use the uniform font set
      unicodeRenderer = new Font(new Font.Provider() {
        private FontSet fontSet() {
          FontManager resourceManager = Minecraft.getInstance().fontManager;
          return resourceManager.fontSets.get(Minecraft.UNIFORM_FONT);
        }

        @Override
        public GlyphSource glyphs(FontDescription fontDescription) {
          return fontSet().source(false);
        }

        @Override
        public EffectGlyph effect() {
          return fontSet().whiteGlyph();
        }
      });

    return unicodeRenderer;
  }
}
