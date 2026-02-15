package net.alloymc.client.ui;

import net.alloymc.client.render.AlloyColors;
import net.alloymc.client.render.AlloyRenderer;

/**
 * A text label with Alloy styling.
 *
 * Supports primary, secondary, muted, and accent color presets.
 * Can be left-aligned or centered within a given width.
 */
public class AlloyLabel {

    public enum Weight {
        PRIMARY(AlloyColors.TEXT_PRIMARY),
        SECONDARY(AlloyColors.TEXT_SECONDARY),
        MUTED(AlloyColors.TEXT_MUTED),
        ACCENT(AlloyColors.EMBER);

        final int color;

        Weight(int color) {
            this.color = color;
        }
    }

    private int x;
    private int y;
    private String text;
    private Weight weight;
    private boolean centered;
    private int centerWidth;

    public AlloyLabel(int x, int y, String text, Weight weight) {
        this.x = x;
        this.y = y;
        this.text = text;
        this.weight = weight;
        this.centered = false;
    }

    /**
     * Creates a centered label.
     *
     * @param x          left edge of the centering area
     * @param y          top position
     * @param width      width of the centering area
     * @param text       label text
     * @param weight     color weight
     */
    public static AlloyLabel centered(int x, int y, int width, String text, Weight weight) {
        AlloyLabel label = new AlloyLabel(x, y, text, weight);
        label.centered = true;
        label.centerWidth = width;
        return label;
    }

    public void render() {
        if (centered) {
            AlloyRenderer.drawCenteredText(text, x, y, centerWidth, weight.color);
        } else {
            AlloyRenderer.drawTextWithShadow(text, x, y, weight.color);
        }
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public String text() { return text; }
    public int x() { return x; }
    public int y() { return y; }
}
