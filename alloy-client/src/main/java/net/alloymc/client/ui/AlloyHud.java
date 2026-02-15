package net.alloymc.client.ui;

import net.alloymc.client.AlloyClient;
import net.alloymc.client.render.AlloyColors;
import net.alloymc.client.render.AlloyRenderer;

/**
 * Built-in HUD overlay for the Alloy client.
 *
 * Displays debug/info elements on top of the game world:
 * - FPS counter (top-left, toggled with F3)
 * - Coordinates (top-left, below FPS)
 * - Alloy watermark (top-right, subtle)
 *
 * This is the reference HUD layer — other mods can register their own
 * via {@link AlloyClient#registerHudLayer(AlloyClient.HudLayer)}.
 */
public class AlloyHud implements AlloyClient.HudLayer {

    private boolean showDebug = false;

    @Override
    public void render(int screenWidth, int screenHeight, float tickDelta) {
        // Alloy watermark (always visible, very subtle)
        AlloyRenderer.drawTextWithShadow("Alloy",
                screenWidth - AlloyRenderer.getTextWidth("Alloy") - 4, 4,
                AlloyColors.withAlpha(AlloyColors.EMBER, 0.3f));

        if (!showDebug) return;

        // Debug info panel (top-left)
        int padding = 4;
        int lineHeight = AlloyRenderer.getTextHeight() + 2;
        int y = padding;

        // FPS — the actual value is injected by the loader at runtime
        AlloyRenderer.drawTextWithShadow("-- FPS", padding, y, AlloyColors.TEXT_PRIMARY);
        y += lineHeight;

        // Coordinates — injected at runtime
        AlloyRenderer.drawTextWithShadow("0.0 / 0.0 / 0.0", padding, y, AlloyColors.TEXT_SECONDARY);
        y += lineHeight;

        // Facing direction
        AlloyRenderer.drawTextWithShadow("Facing: --", padding, y, AlloyColors.TEXT_SECONDARY);
    }

    @Override
    public boolean isVisible() {
        return true; // Watermark always visible; debug info gated by showDebug
    }

    /**
     * Toggle debug info display. Called by F3 key handler.
     */
    public void toggleDebug() {
        showDebug = !showDebug;
    }

    public boolean isDebugVisible() {
        return showDebug;
    }
}
