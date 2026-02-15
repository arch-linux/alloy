package net.alloymc.client.ui;

import net.alloymc.client.render.AlloyColors;
import net.alloymc.client.render.AlloyRenderer;

/**
 * A styled button matching the Alloy design system.
 *
 * Supports four visual styles:
 * - PRIMARY: ember gradient background (main call-to-action)
 * - SECONDARY: obsidian-700 background with subtle border
 * - GHOST: transparent background, text only (hover reveals bg)
 * - DANGER: red-tinted for destructive actions
 *
 * Buttons have hover, active, and disabled states with smooth
 * color transitions.
 */
public class AlloyButton {

    public enum Style {
        PRIMARY, SECONDARY, GHOST, DANGER
    }

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final String label;
    private final Style style;
    private final Runnable action;
    private boolean enabled = true;

    public AlloyButton(int x, int y, int width, int height, String label, Style style, Runnable action) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.label = label;
        this.style = style;
        this.action = action;
    }

    public void render(int mouseX, int mouseY, float tickDelta) {
        boolean hovered = isHovered(mouseX, mouseY);

        int bg;
        int textColor;
        int borderColor;

        if (!enabled) {
            bg = AlloyColors.OBSIDIAN_800;
            textColor = AlloyColors.TEXT_DISABLED;
            borderColor = AlloyColors.OBSIDIAN_700;
        } else {
            switch (style) {
                case PRIMARY -> {
                    bg = hovered ? AlloyColors.EMBER_LIGHT : AlloyColors.EMBER;
                    textColor = AlloyColors.OBSIDIAN_950;
                    borderColor = AlloyColors.TRANSPARENT;
                }
                case SECONDARY -> {
                    bg = hovered ? AlloyColors.OBSIDIAN_600 : AlloyColors.OBSIDIAN_700;
                    textColor = AlloyColors.TEXT_PRIMARY;
                    borderColor = AlloyColors.OBSIDIAN_600;
                }
                case GHOST -> {
                    bg = hovered ? AlloyColors.OBSIDIAN_700 : AlloyColors.TRANSPARENT;
                    textColor = hovered ? AlloyColors.TEXT_PRIMARY : AlloyColors.TEXT_SECONDARY;
                    borderColor = AlloyColors.TRANSPARENT;
                }
                case DANGER -> {
                    bg = hovered ? AlloyColors.withAlpha(AlloyColors.ERROR, 0.3f) : AlloyColors.OBSIDIAN_700;
                    textColor = AlloyColors.ERROR;
                    borderColor = AlloyColors.withAlpha(AlloyColors.ERROR, 0.4f);
                }
                default -> {
                    bg = AlloyColors.OBSIDIAN_700;
                    textColor = AlloyColors.TEXT_PRIMARY;
                    borderColor = AlloyColors.TRANSPARENT;
                }
            }
        }

        // Background
        AlloyRenderer.fillRoundedRect(x, y, width, height, 6, bg);

        // Border (skip for primary and ghost-no-hover)
        if (borderColor != AlloyColors.TRANSPARENT) {
            AlloyRenderer.drawRect(x, y, width, height, borderColor);
        }

        // Ember glow on primary hover
        if (style == Style.PRIMARY && hovered && enabled) {
            AlloyRenderer.fillRect(x, y + height - 2, width, 2, AlloyColors.EMBER_GLOW);
        }

        // Label (centered)
        AlloyRenderer.drawCenteredText(label, x, y + (height - AlloyRenderer.getTextHeight()) / 2,
                width, textColor);
    }

    public boolean isHovered(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public void onClick() {
        if (enabled && action != null) {
            action.run();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String label() {
        return label;
    }
}
