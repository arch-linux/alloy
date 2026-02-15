package net.alloymc.client.ui;

import net.alloymc.client.render.AlloyColors;
import net.alloymc.client.render.AlloyRenderer;

/**
 * A container panel with Alloy styling.
 *
 * Renders as a rounded obsidian-800 rectangle with an optional
 * border and title. Use as a visual grouping for related widgets.
 */
public class AlloyPanel {

    private int x;
    private int y;
    private int width;
    private int height;
    private String title;
    private boolean showBorder;

    public AlloyPanel(int x, int y, int width, int height) {
        this(x, y, width, height, null, true);
    }

    public AlloyPanel(int x, int y, int width, int height, String title, boolean showBorder) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.title = title;
        this.showBorder = showBorder;
    }

    public void render(int mouseX, int mouseY, float tickDelta) {
        // Background
        AlloyRenderer.fillRoundedRect(x, y, width, height, 8, AlloyColors.OBSIDIAN_800);

        // Border
        if (showBorder) {
            AlloyRenderer.drawRect(x, y, width, height, AlloyColors.OBSIDIAN_600);
        }

        // Title
        if (title != null && !title.isEmpty()) {
            AlloyRenderer.drawTextWithShadow(title, x + 12, y + 10, AlloyColors.TEXT_SECONDARY);
        }
    }

    /**
     * Returns the Y position where content should start (after the title).
     */
    public int contentTop() {
        return y + (title != null ? 28 : 8);
    }

    /**
     * Returns the usable content width (panel width minus padding).
     */
    public int contentWidth() {
        return width - 24;
    }

    /**
     * Returns the left X position for content (panel X + padding).
     */
    public int contentLeft() {
        return x + 12;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int x() { return x; }
    public int y() { return y; }
    public int width() { return width; }
    public int height() { return height; }
}
