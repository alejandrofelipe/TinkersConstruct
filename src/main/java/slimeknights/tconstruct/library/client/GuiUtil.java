package slimeknights.tconstruct.library.client;

import com.mojang.blaze3d.textures.GpuTextureView;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Matrix3x2f;
import slimeknights.mantle.client.screen.ElementScreen;
import slimeknights.tconstruct.library.recipe.partbuilder.Pattern;

import javax.annotation.Nullable;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GuiUtil {
  /**
   * Draws the background of a container
   * @param graphics    Graphics context
   * @param screen      Parent screen
   * @param background  Background location
   */
  public static void drawBackground(GuiGraphics graphics, AbstractContainerScreen<?> screen, ResourceLocation background) {
    graphics.blit(RenderPipelines.GUI_TEXTURED, background, screen.leftPos, screen.topPos, 0.0F, 0.0F, screen.imageWidth, screen.imageHeight, 256, 256);
  }

  /**
   * Checks if the given area is hovered
   * @param mouseX    Mouse X position
   * @param mouseY    Mouse Y position
   * @param x         Tank X position
   * @param y         Tank Y position
   * @param width     Tank width
   * @param height    Tank height
   * @return  True if the area is hovered
   */
  public static boolean isHovered(int mouseX, int mouseY, int x, int y, int width, int height) {
    return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
  }

  /**
   * Checks if the given tank area is hovered
   * @param mouseX    Mouse X position
   * @param mouseY    Mouse Y position
   * @param amount    Current tank amount
   * @param capacity  Tank capacity
   * @param x         Tank X position
   * @param y         Tank Y position
   * @param width     Tank width
   * @param height    Tank height
   * @return  True if the tank is hovered, false otherwise
   */
  public static boolean isTankHovered(int mouseX, int mouseY, int amount, int capacity, int x, int y, int width, int height) {
    // check X position first, its easier
    if (mouseX < x || mouseX > x + width || mouseY > y + height) {
      return false;
    }
    // next, try height
    int topHeight = height - (height * amount / capacity);
    return mouseY > y + topHeight;
  }

  /**
   * Renders a fluid tank with a partial fluid level
   * @param screen    Parent screen
   * @param stack     Fluid stack
   * @param capacity  Tank capacity, determines height
   * @param x         Tank X position
   * @param y         Tank Y position
   * @param width     Tank width
   * @param height    Tank height
   * @param depth     Tank depth
   */
  public static void renderFluidTank(GuiGraphics graphics, AbstractContainerScreen<?> screen, FluidStack stack, int capacity, int x, int y, int width, int height, int depth) {
    renderFluidTank(graphics, screen, stack, stack.getAmount(), capacity, x, y, width, height, depth);
  }

  /**
   * Renders a fluid tank with a partial fluid level and an amount override
   * @param screen    Parent screen
   * @param stack     Fluid stack
   * @param capacity  Tank capacity, determines height
   * @param x         Tank X position
   * @param y         Tank Y position
   * @param width     Tank width
   * @param height    Tank height
   * @param depth     Tank depth
   */
  public static void renderFluidTank(GuiGraphics graphics, AbstractContainerScreen<?> screen, FluidStack stack, int amount, int capacity, int x, int y, int width, int height, int depth) {
    if(!stack.isEmpty() && capacity > 0) {
      int maxY = y + height;
      int fluidHeight = Math.min(height * amount / capacity, height);
      renderTiledFluid(graphics, screen, stack, x, maxY - fluidHeight, width, fluidHeight, depth);
    }
  }

  /**
   * Colors and renders a fluid sprite
   * @param graphics    Graphics instance
   * @param screen  Parent screen
   * @param stack   Fluid stack
   * @param x       Fluid X
   * @param y       Fluid Y
   * @param width   Fluid width
   * @param height  Fluid height
   * @param depth   Fluid depth
   */
  public static void renderTiledFluid(GuiGraphics graphics, AbstractContainerScreen<?> screen, FluidStack stack, int x, int y, int width, int height, int depth) {
    if (!stack.isEmpty()) {
      IClientFluidTypeExtensions clientFluid = IClientFluidTypeExtensions.of(stack.getFluid());
      TextureAtlasSprite fluidSprite = screen.getMinecraft().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(clientFluid.getStillTexture(stack));
      // 1.21.5: tint is passed as vertex color instead of the shader color
      renderTiledTextureAtlas(graphics, screen, fluidSprite, x, y, width, height, depth, stack.getFluid().getFluidType().isLighterThanAir(), clientFluid.getTintColor(stack));
    }
  }

  /**
   * Renders a texture atlas sprite tiled over the given area
   * @param graphics    Graphics instance
   * @param screen      Parent screen
   * @param sprite      Sprite to render
   * @param x           X position to render
   * @param y           Y position to render
   * @param width       Render width
   * @param height      Render height
   * @param depth       Render depth
   * @param upsideDown  If true, flips the sprite
   */
  public static void renderTiledTextureAtlas(GuiGraphics graphics, AbstractContainerScreen<?> screen, TextureAtlasSprite sprite, int x, int y, int width, int height, int depth, boolean upsideDown) {
    renderTiledTextureAtlas(graphics, screen, sprite, x, y, width, height, depth, upsideDown, -1);
  }

  /**
   * Renders a texture atlas sprite tiled over the given area
   * @param graphics    Graphics instance
   * @param screen      Parent screen
   * @param sprite      Sprite to render
   * @param x           X position to render
   * @param y           Y position to render
   * @param width       Render width
   * @param height      Render height
   * @param depth       Render depth
   * @param upsideDown  If true, flips the sprite
   * @param color       ARGB tint color
   */
  public static void renderTiledTextureAtlas(GuiGraphics graphics, AbstractContainerScreen<?> screen, TextureAtlasSprite sprite, int x, int y, int width, int height, int depth, boolean upsideDown, int color) {
    // 1.21.8: GUI rendering is deferred, so each tile is submitted as a blit render state. The public blitSprite overloads
    // only draw a whole atlas sprite (or a sub-region of a GUI atlas sprite), so partial tiles are submitted directly with
    // their exact sub-UVs, same as the old immediate quads. Depth is ignored: later submissions draw above earlier ones.
    GpuTextureView texture = screen.getMinecraft().getTextureManager().getTexture(sprite.atlasLocation()).getTextureView();
    TextureSetup textureSetup = TextureSetup.singleTexture(texture);
    Matrix3x2f pose = new Matrix3x2f(graphics.pose());
    ScreenRectangle scissor = graphics.peekScissorStack();

    // tile vertically
    float u1 = sprite.getU0();
    float v1 = sprite.getV0();
    int spriteHeight = sprite.contents().height();
    int spriteWidth = sprite.contents().width();
    int startX = x + screen.leftPos;
    int startY = y + screen.topPos;
    do {
      int renderHeight = Math.min(spriteHeight, height);
      height -= renderHeight;
      // 1.21: TextureAtlasSprite.getU/getV take a normalized [0,1] fraction of the sprite (1.20 took [0,16]) — no *16
      float v2 = sprite.getV((float) renderHeight / spriteHeight);

      // we need to draw the quads per width too
      int x2 = startX;
      int widthLeft = width;
      // tile horizontally
      do {
        int renderWidth = Math.min(spriteWidth, widthLeft);
        widthLeft -= renderWidth;

        float u2 = sprite.getU((float) renderWidth / spriteWidth);
        if(upsideDown) {
          // FIXME: I think this causes tiling errors, look into it
          blitTile(graphics, textureSetup, pose, scissor, x2, x2 + renderWidth, startY, startY + renderHeight, u1, u2, v2, v1, color);
        } else {
          blitTile(graphics, textureSetup, pose, scissor, x2, x2 + renderWidth, startY, startY + renderHeight, u1, u2, v1, v2, color);
        }
        x2 += renderWidth;
      } while(widthLeft > 0);

      startY += renderHeight;
    } while(height > 0);
  }

  /**
   * Submits a single textured tile to the GUI render state
   * @param x1       X start
   * @param x2       X end
   * @param y1       Y start
   * @param y2       Y end
   * @param u1       Texture U start
   * @param u2       Texture U end
   * @param v1       Texture V start
   * @param v2       Texture V end
   * @param color    ARGB color
   */
  private static void blitTile(GuiGraphics graphics, TextureSetup textureSetup, Matrix3x2f pose, @Nullable ScreenRectangle scissor, int x1, int x2, int y1, int y2, float u1, float u2, float v1, float v2, int color) {
    graphics.submitGuiElementRenderState(new BlitRenderState(RenderPipelines.GUI_TEXTURED, textureSetup, pose, x1, y1, x2, y2, u1, u2, v1, v2, color, scissor));
  }

  /**
   * Draws an upwards progress bar. TODO: is this just {@link slimeknights.mantle.client.screen.ScalableElementScreen}?
   * @param element   Element to draw
   * @param x         X position to start
   * @param y         Y position to start
   * @param progress  Progress between 0 and 1
   */
  public static void drawProgressUp(GuiGraphics graphics, ElementScreen element, int x, int y, float progress) {
    int height;
    if (progress > 1) {
      height = element.h;
    } else if (progress < 0) {
      height = 0;
    } else {
      // add an extra 0.5 so it rounds instead of flooring
      height = (int)(progress * element.h + 0.5);
    }
    // amount to offset element by for the height
    int deltaY = element.h - height;
    graphics.blit(RenderPipelines.GUI_TEXTURED, element.texture, x, y + deltaY, (float) element.x, (float) (element.y + deltaY), element.w, height, element.texW, element.texH);
  }

  /**
   * Draws an element with a tint color. Replaces drawing an element under {@code RenderSystem.setShaderColor}, which is gone in 1.21.6+.
   * @param graphics  Graphics instance
   * @param element   Element to draw
   * @param x         X position
   * @param y         Y position
   * @param color     ARGB tint, typically {@link net.minecraft.util.ARGB#white(float)} for translucency
   */
  public static void drawElement(GuiGraphics graphics, ElementScreen element, int x, int y, int color) {
    graphics.blit(RenderPipelines.GUI_TEXTURED, element.texture, x, y, (float) element.x, (float) element.y, element.w, element.h, element.texW, element.texH, color);
  }

  /**
   * Renders a highlight overlay for the given area
   * @param graphics  Graphics instance
   * @param x         Element X position
   * @param y         Element Y position
   * @param width     Element width
   * @param height    Element height
   */
  public static void renderHighlight(GuiGraphics graphics, int x, int y, int width, int height) {
      // 1.21.8: GUI draws are layered by submission order (no depth buffer), so a plain fill after the content draws over it
      graphics.fill(x, y, x + width, y + height, 0x80FFFFFF);
  }

  /** Renders a pattern at the given location */
  public static void renderPattern(GuiGraphics graphics, Pattern pattern, int x, int y) {
    TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(pattern.getTexture());
    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, 16, 16);
  }
}
