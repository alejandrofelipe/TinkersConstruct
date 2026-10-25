package slimeknights.tconstruct.library.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Matrix4f;
import slimeknights.mantle.client.screen.ElementScreen;
import slimeknights.tconstruct.library.recipe.partbuilder.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GuiUtil {
  /**
   * Draws the background of a container
   * @param graphics    Graphics context
   * @param screen      Parent screen
   * @param background  Background location
   */
  public static void drawBackground(GuiGraphics graphics, AbstractContainerScreen<?> screen, ResourceLocation background) {
    graphics.blit(RenderType::guiTextured, background, screen.leftPos, screen.topPos, 0.0F, 0.0F, screen.imageWidth, screen.imageHeight, 256, 256);
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
  public static void renderFluidTank(PoseStack matrices, AbstractContainerScreen<?> screen, FluidStack stack, int capacity, int x, int y, int width, int height, int depth) {
    renderFluidTank(matrices, screen, stack, stack.getAmount(), capacity, x, y, width, height, depth);
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
  public static void renderFluidTank(PoseStack matrices, AbstractContainerScreen<?> screen, FluidStack stack, int amount, int capacity, int x, int y, int width, int height, int depth) {
    if(!stack.isEmpty() && capacity > 0) {
      int maxY = y + height;
      int fluidHeight = Math.min(height * amount / capacity, height);
      renderTiledFluid(matrices, screen, stack, x, maxY - fluidHeight, width, fluidHeight, depth);
    }
  }

  /**
   * Colors and renders a fluid sprite
   * @param matrices    Matrix instance
   * @param screen  Parent screen
   * @param stack   Fluid stack
   * @param x       Fluid X
   * @param y       Fluid Y
   * @param width   Fluid width
   * @param height  Fluid height
   * @param depth   Fluid depth
   */
  public static void renderTiledFluid(PoseStack matrices, AbstractContainerScreen<?> screen, FluidStack stack, int x, int y, int width, int height, int depth) {
    if (!stack.isEmpty()) {
      IClientFluidTypeExtensions clientFluid = IClientFluidTypeExtensions.of(stack.getFluid());
      TextureAtlasSprite fluidSprite = screen.getMinecraft().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(clientFluid.getStillTexture(stack));
      // 1.21.5: tint is passed as vertex color instead of the shader color
      renderTiledTextureAtlas(matrices, screen, fluidSprite, x, y, width, height, depth, stack.getFluid().getFluidType().isLighterThanAir(), clientFluid.getTintColor(stack));
    }
  }

  /**
   * Renders a texture atlas sprite tiled over the given area
   * @param matrices    Matrix instance
   * @param screen      Parent screen
   * @param sprite      Sprite to render
   * @param x           X position to render
   * @param y           Y position to render
   * @param width       Render width
   * @param height      Render height
   * @param depth       Render depth
   * @param upsideDown  If true, flips the sprite
   */
  public static void renderTiledTextureAtlas(PoseStack matrices, AbstractContainerScreen<?> screen, TextureAtlasSprite sprite, int x, int y, int width, int height, int depth, boolean upsideDown) {
    renderTiledTextureAtlas(matrices, screen, sprite, x, y, width, height, depth, upsideDown, -1);
  }

  /**
   * Renders a texture atlas sprite tiled over the given area
   * @param matrices    Matrix instance
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
  public static void renderTiledTextureAtlas(PoseStack matrices, AbstractContainerScreen<?> screen, TextureAtlasSprite sprite, int x, int y, int width, int height, int depth, boolean upsideDown, int color) {
    // start drawing sprites
    // 1.21.5: BufferUploader is gone, draw through the GUI buffer source (shared with GuiGraphics) and flush right away
    MultiBufferSource.BufferSource bufferSource = screen.getMinecraft().renderBuffers().bufferSource();
    RenderType renderType = RenderType.guiTextured(sprite.atlasLocation());
    VertexConsumer builder = bufferSource.getBuffer(renderType);

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
      Matrix4f matrix = matrices.last().pose();
      // tile horizontally
      do {
        int renderWidth = Math.min(spriteWidth, widthLeft);
        widthLeft -= renderWidth;

        float u2 = sprite.getU((float) renderWidth / spriteWidth);
        if(upsideDown) {
          // FIXME: I think this causes tiling errors, look into it
          buildSquare(matrix, builder, x2, x2 + renderWidth, startY, startY + renderHeight, depth, u1, u2, v2, v1, color);
        } else {
          buildSquare(matrix, builder, x2, x2 + renderWidth, startY, startY + renderHeight, depth, u1, u2, v1, v2, color);
        }
        x2 += renderWidth;
      } while(widthLeft > 0);

      startY += renderHeight;
    } while(height > 0);

    // finish drawing sprites
    bufferSource.endBatch(renderType);
  }

  /**
   * Adds a square of texture to a buffer builder
   * @param builder  Builder instance
   * @param x1       X start
   * @param x2       X end
   * @param y1       Y start
   * @param y2       Y end
   * @param z        Depth
   * @param u1       Texture U start
   * @param u2       Texture U end
   * @param v1       Texture V start
   * @param v2       Texture V end
   * @param color    ARGB color
   */
  private static void buildSquare(Matrix4f matrix, VertexConsumer builder, int x1, int x2, int y1, int y2, int z, float u1, float u2, float v1, float v2, int color) {
    builder.addVertex(matrix, x1, y2, z).setUv(u1, v2).setColor(color);
    builder.addVertex(matrix, x2, y2, z).setUv(u2, v2).setColor(color);
    builder.addVertex(matrix, x2, y1, z).setUv(u2, v1).setColor(color);
    builder.addVertex(matrix, x1, y1, z).setUv(u1, v1).setColor(color);
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
    graphics.blit(RenderType::guiTextured, element.texture, x, y + deltaY, (float) element.x, (float) (element.y + deltaY), element.w, height, element.texW, element.texH);
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
      // 1.21.5: depth test and color mask moved into the render type's pipeline
      graphics.fill(TinkerRenderTypes.GUI_HIGHLIGHT, x, y, x + width, y + height, 100, 0x80FFFFFF);
  }

  /**
   * Flushes pending GUI draws and clears the depth buffer, so everything drawn afterwards paints over what was drawn
   * before regardless of its depth. Replaces the old {@code RenderSystem.disableDepthTest()} idiom for overlays, which
   * no longer exists in 1.21.5 (depth testing is part of the render pipeline); same approach as vanilla {@code PopupScreen}.
   */
  public static void clearDepth(GuiGraphics graphics) {
    graphics.flush();
    RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(Minecraft.getInstance().getMainRenderTarget().getDepthTexture(), 1.0);
  }

  /** Renders a pattern at the given location */
  public static void renderPattern(GuiGraphics graphics, Pattern pattern, int x, int y) {
    TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(pattern.getTexture());
    graphics.blitSprite(RenderType::guiTextured, sprite, x, y, 16, 16);
  }
}
