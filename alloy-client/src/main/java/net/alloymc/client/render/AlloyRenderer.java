package net.alloymc.client.render;

/**
 * Rendering utilities for the Alloy UI system.
 *
 * Provides helper methods for drawing rectangles, gradients, textures,
 * and text with the Alloy design system. All methods are designed to be
 * called during screen/HUD rendering and assume the GL state is set up
 * for 2D GUI rendering (as Minecraft does before calling screen.render).
 *
 * The actual GL calls are stubbed — the loader's injection system will
 * wire these to Minecraft's internal DrawContext/GuiGraphics at runtime.
 * Mods call these methods; the loader handles the bridge.
 */
public final class AlloyRenderer {

    private AlloyRenderer() {}

    // ---- State ----

    /** Current draw context, set by the injection hook before each frame. */
    private static Object drawContext;

    /**
     * Called by the loader's injection hook to set the current draw context.
     * This bridges AlloyRenderer to Minecraft's rendering pipeline.
     */
    public static void setDrawContext(Object context) {
        drawContext = context;
    }

    /**
     * Returns the raw draw context for advanced use.
     * Most mods should use the helper methods instead.
     */
    public static Object drawContext() {
        return drawContext;
    }

    // ---- Rectangles ----

    /**
     * Fill a rectangle with a solid color.
     *
     * @param x      left edge
     * @param y      top edge
     * @param width  rectangle width
     * @param height rectangle height
     * @param color  ARGB color
     */
    public static void fillRect(int x, int y, int width, int height, int color) {
        // Stubbed — the loader injects the actual Minecraft DrawContext.fill() call.
        // At runtime, this becomes: drawContext.fill(x, y, x + width, y + height, color);
    }

    /**
     * Fill a rectangle with a vertical gradient (top to bottom).
     *
     * @param x         left edge
     * @param y         top edge
     * @param width     rectangle width
     * @param height    rectangle height
     * @param colorTop  ARGB color at the top
     * @param colorBot  ARGB color at the bottom
     */
    public static void fillGradient(int x, int y, int width, int height, int colorTop, int colorBot) {
        // Stubbed — wired to DrawContext.fillGradient() at runtime.
    }

    /**
     * Draw a rectangle outline (1px border).
     *
     * @param x      left edge
     * @param y      top edge
     * @param width  rectangle width
     * @param height rectangle height
     * @param color  ARGB border color
     */
    public static void drawRect(int x, int y, int width, int height, int color) {
        fillRect(x, y, width, 1, color);               // top
        fillRect(x, y + height - 1, width, 1, color);  // bottom
        fillRect(x, y + 1, 1, height - 2, color);      // left
        fillRect(x + width - 1, y + 1, 1, height - 2, color); // right
    }

    /**
     * Fill a rounded rectangle. Falls back to sharp corners if radius is 0.
     *
     * @param x      left edge
     * @param y      top edge
     * @param width  rectangle width
     * @param height rectangle height
     * @param radius corner radius in pixels
     * @param color  ARGB color
     */
    public static void fillRoundedRect(int x, int y, int width, int height, int radius, int color) {
        if (radius <= 0) {
            fillRect(x, y, width, height, color);
            return;
        }
        // Approximate rounded corners: fill center + top/bottom strips
        fillRect(x + radius, y, width - 2 * radius, height, color);
        fillRect(x, y + radius, radius, height - 2 * radius, color);
        fillRect(x + width - radius, y + radius, radius, height - 2 * radius, color);
        // Corner fills (simplified — real impl would use circle fill)
        fillRect(x, y, radius, radius, color);
        fillRect(x + width - radius, y, radius, radius, color);
        fillRect(x, y + height - radius, radius, radius, color);
        fillRect(x + width - radius, y + height - radius, radius, radius, color);
    }

    // ---- Text ----

    /**
     * Draw a text string.
     *
     * @param text  the text to render
     * @param x     left position
     * @param y     top position
     * @param color ARGB text color
     */
    public static void drawText(String text, int x, int y, int color) {
        // Stubbed — wired to DrawContext.drawText() at runtime.
    }

    /**
     * Draw text with a shadow behind it.
     *
     * @param text  the text to render
     * @param x     left position
     * @param y     top position
     * @param color ARGB text color
     */
    public static void drawTextWithShadow(String text, int x, int y, int color) {
        // Stubbed — wired to DrawContext.drawTextWithShadow() at runtime.
    }

    /**
     * Draw text centered horizontally within a given width.
     *
     * @param text   the text to render
     * @param x      left edge of the centering area
     * @param y      top position
     * @param width  width of the centering area
     * @param color  ARGB text color
     */
    public static void drawCenteredText(String text, int x, int y, int width, int color) {
        int textWidth = getTextWidth(text);
        int cx = x + (width - textWidth) / 2;
        drawTextWithShadow(text, cx, y, color);
    }

    /**
     * Returns the pixel width of a rendered text string.
     *
     * @param text the text to measure
     * @return width in pixels
     */
    public static int getTextWidth(String text) {
        // Stubbed — wired to TextRenderer.getWidth() at runtime.
        // Approximate: 6 pixels per character (Minecraft's default font)
        return text.length() * 6;
    }

    /**
     * Returns the line height of the current font.
     */
    public static int getTextHeight() {
        // Minecraft's default font height
        return 9;
    }

    // ---- Textures ----

    /**
     * Draw a texture at the specified position.
     *
     * @param texture resource location (e.g. "alloy:textures/alloy_logo.png")
     * @param x       left position
     * @param y       top position
     * @param width   draw width
     * @param height  draw height
     */
    public static void drawTexture(String texture, int x, int y, int width, int height) {
        // Stubbed — wired to DrawContext.drawTexture() at runtime.
    }

    /**
     * Draw a texture region (for sprite sheets / texture atlases).
     *
     * @param texture resource location
     * @param x       left position
     * @param y       top position
     * @param width   draw width
     * @param height  draw height
     * @param u       texture U offset (pixels)
     * @param v       texture V offset (pixels)
     * @param uWidth  texture region width
     * @param vHeight texture region height
     * @param texW    full texture width
     * @param texH    full texture height
     */
    public static void drawTexture(String texture, int x, int y, int width, int height,
                                   int u, int v, int uWidth, int vHeight, int texW, int texH) {
        // Stubbed — wired to DrawContext.drawTexture() at runtime.
    }

    // ---- Scissor (clipping) ----

    /**
     * Enable scissor clipping to a rectangle.
     * All rendering after this call is clipped to the specified region
     * until {@link #disableScissor()} is called.
     */
    public static void enableScissor(int x, int y, int width, int height) {
        // Stubbed — wired to DrawContext.enableScissor() at runtime.
    }

    /**
     * Disable scissor clipping.
     */
    public static void disableScissor() {
        // Stubbed — wired to DrawContext.disableScissor() at runtime.
    }
}
