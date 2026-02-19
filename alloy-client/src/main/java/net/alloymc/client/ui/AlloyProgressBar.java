package net.alloymc.client.ui;

import net.alloymc.client.render.AlloyColors;
import net.alloymc.client.render.AlloyRenderer;

/**
 * A horizontal or vertical progress bar for furnace arrows, loading indicators, etc.
 *
 * Renders as an obsidian track filled proportionally with an ember gradient.
 * The fill direction is left-to-right (horizontal) or bottom-to-top (vertical).
 */
public class AlloyProgressBar {

    public enum Direction {
        HORIZONTAL, VERTICAL
    }

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final Direction direction;
    private double progress; // 0.0 to 1.0

    public AlloyProgressBar(int x, int y, int width, int height, Direction direction) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.direction = direction;
        this.progress = 0.0;
    }

    public void render(int mouseX, int mouseY, float tickDelta) {
        // Track background
        AlloyRenderer.fillRoundedRect(x, y, width, height, 2, AlloyColors.OBSIDIAN_600);

        // Fill
        if (progress > 0.0) {
            if (direction == Direction.HORIZONTAL) {
                int fillWidth = (int) (progress * width);
                AlloyRenderer.fillRoundedRect(x, y, fillWidth, height, 2, AlloyColors.EMBER);
            } else {
                int fillHeight = (int) (progress * height);
                int fillY = y + height - fillHeight;
                AlloyRenderer.fillRoundedRect(x, fillY, width, fillHeight, 2, AlloyColors.EMBER);
            }
        }
    }

    public double progress() { return progress; }

    public void setProgress(double progress) {
        this.progress = Math.clamp(progress, 0.0, 1.0);
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        return false; // Not interactive
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }
}
