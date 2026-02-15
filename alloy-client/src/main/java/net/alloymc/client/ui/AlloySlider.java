package net.alloymc.client.ui;

import net.alloymc.client.render.AlloyColors;
import net.alloymc.client.render.AlloyRenderer;

import java.util.function.Consumer;

/**
 * A horizontal slider with Alloy styling.
 *
 * Renders as an obsidian track with an ember-colored handle.
 * Displays the current value formatted by a provided formatter.
 */
public class AlloySlider {

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final String label;
    private final double min;
    private final double max;
    private final double step;
    private double value;
    private final ValueFormatter formatter;
    private final Consumer<Double> onChange;
    private boolean dragging = false;

    @FunctionalInterface
    public interface ValueFormatter {
        String format(double value);
    }

    public AlloySlider(int x, int y, int width, int height, String label,
                       double min, double max, double step, double initialValue,
                       ValueFormatter formatter, Consumer<Double> onChange) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = initialValue;
        this.formatter = formatter;
        this.onChange = onChange;
    }

    public void render(int mouseX, int mouseY, float tickDelta) {
        // Label
        AlloyRenderer.drawTextWithShadow(label, x, y, AlloyColors.TEXT_SECONDARY);

        // Value display
        String valueStr = formatter.format(value);
        int valueWidth = AlloyRenderer.getTextWidth(valueStr);
        AlloyRenderer.drawTextWithShadow(valueStr, x + width - valueWidth, y, AlloyColors.EMBER);

        // Track
        int trackY = y + 14;
        int trackHeight = 4;
        AlloyRenderer.fillRoundedRect(x, trackY, width, trackHeight, 2, AlloyColors.OBSIDIAN_600);

        // Filled portion
        double ratio = (value - min) / (max - min);
        int filledWidth = (int) (ratio * width);
        AlloyRenderer.fillRoundedRect(x, trackY, filledWidth, trackHeight, 2, AlloyColors.EMBER);

        // Handle
        int handleSize = 10;
        int handleX = x + filledWidth - handleSize / 2;
        int handleY = trackY - (handleSize - trackHeight) / 2;
        boolean handleHovered = mouseX >= handleX && mouseX < handleX + handleSize
                && mouseY >= handleY && mouseY < handleY + handleSize;

        int handleColor = handleHovered || dragging ? AlloyColors.EMBER_LIGHT : AlloyColors.EMBER;
        AlloyRenderer.fillRoundedRect(handleX, handleY, handleSize, handleSize, 5, handleColor);
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        int trackY = y + 14;
        if (mouseX >= x && mouseX < x + width && mouseY >= trackY - 6 && mouseY < trackY + 14) {
            dragging = true;
            updateValue(mouseX);
            return true;
        }
        return false;
    }

    public boolean mouseDragged(int mouseX, int mouseY) {
        if (dragging) {
            updateValue(mouseX);
            return true;
        }
        return false;
    }

    public void mouseReleased() {
        dragging = false;
    }

    private void updateValue(int mouseX) {
        double ratio = Math.clamp((double) (mouseX - x) / width, 0.0, 1.0);
        double raw = min + ratio * (max - min);
        // Snap to step
        value = Math.round(raw / step) * step;
        value = Math.clamp(value, min, max);
        if (onChange != null) {
            onChange.accept(value);
        }
    }

    public double value() { return value; }
    public void setValue(double value) { this.value = Math.clamp(value, min, max); }
}
