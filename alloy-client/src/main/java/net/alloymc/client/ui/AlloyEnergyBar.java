package net.alloymc.client.ui;

import net.alloymc.client.render.AlloyColors;
import net.alloymc.client.render.AlloyRenderer;

/**
 * A segmented vertical bar for displaying energy, fluid levels, or similar gauges.
 *
 * Renders as a column of segments, filled from bottom to top. Each segment
 * is individually colored based on the fill level (green → yellow → red).
 */
public class AlloyEnergyBar {

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int segments;
    private double level; // 0.0 to 1.0

    private int emptyColor = AlloyColors.OBSIDIAN_700;
    private int lowColor = 0xFFCC3333;   // red
    private int midColor = 0xFFCCAA33;   // yellow
    private int highColor = 0xFF33CC55;  // green

    public AlloyEnergyBar(int x, int y, int width, int height, int segments) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.segments = segments;
        this.level = 0.0;
    }

    public void render(int mouseX, int mouseY, float tickDelta) {
        int segHeight = height / segments;
        int gap = 1;
        int filledSegments = (int) Math.ceil(level * segments);

        for (int i = 0; i < segments; i++) {
            int segY = y + height - (i + 1) * segHeight;

            if (i < filledSegments) {
                double ratio = (double) i / segments;
                int color;
                if (ratio < 0.33) color = lowColor;
                else if (ratio < 0.66) color = midColor;
                else color = highColor;
                AlloyRenderer.fillRect(x, segY + gap, width, segHeight - gap, color);
            } else {
                AlloyRenderer.fillRect(x, segY + gap, width, segHeight - gap, emptyColor);
            }
        }

        // Border
        AlloyRenderer.drawRect(x, y, width, height, AlloyColors.OBSIDIAN_600);
    }

    public double level() { return level; }

    public void setLevel(double level) {
        this.level = Math.clamp(level, 0.0, 1.0);
    }

    public void setColors(int lowColor, int midColor, int highColor) {
        this.lowColor = lowColor;
        this.midColor = midColor;
        this.highColor = highColor;
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }
}
