package net.alloymc.client.ui;

import net.alloymc.client.render.AlloyColors;
import net.alloymc.client.render.AlloyRenderer;

import java.util.function.Consumer;

/**
 * A boolean toggle checkbox with a label.
 *
 * Renders as a small square with an ember-colored fill when checked,
 * and a text label to the right.
 */
public class AlloyCheckbox {

    private static final int BOX_SIZE = 14;

    private final int x;
    private final int y;
    private final String label;
    private final Consumer<Boolean> onChange;
    private boolean checked;

    public AlloyCheckbox(int x, int y, String label, boolean initialValue, Consumer<Boolean> onChange) {
        this.x = x;
        this.y = y;
        this.label = label;
        this.checked = initialValue;
        this.onChange = onChange;
    }

    public void render(int mouseX, int mouseY, float tickDelta) {
        boolean hovered = isHovered(mouseX, mouseY);

        // Box background
        int bg = checked ? AlloyColors.EMBER : AlloyColors.OBSIDIAN_700;
        int border = hovered ? AlloyColors.EMBER_LIGHT : AlloyColors.OBSIDIAN_600;
        AlloyRenderer.fillRoundedRect(x, y, BOX_SIZE, BOX_SIZE, 3, bg);
        AlloyRenderer.drawRect(x, y, BOX_SIZE, BOX_SIZE, border);

        // Checkmark
        if (checked) {
            AlloyRenderer.drawCenteredText("\u2713", x, y + 2, BOX_SIZE, AlloyColors.OBSIDIAN_950);
        }

        // Label
        int labelX = x + BOX_SIZE + 6;
        int labelY = y + (BOX_SIZE - AlloyRenderer.getTextHeight()) / 2;
        AlloyRenderer.drawTextWithShadow(label, labelX, labelY, AlloyColors.TEXT_PRIMARY);
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        if (isHovered(mouseX, mouseY)) {
            checked = !checked;
            if (onChange != null) onChange.accept(checked);
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    private boolean isHovered(int mouseX, int mouseY) {
        int totalWidth = BOX_SIZE + 6 + AlloyRenderer.getTextWidth(label);
        return mouseX >= x && mouseX < x + totalWidth
                && mouseY >= y && mouseY < y + BOX_SIZE;
    }

    public boolean isChecked() { return checked; }
    public void setChecked(boolean checked) { this.checked = checked; }
}
