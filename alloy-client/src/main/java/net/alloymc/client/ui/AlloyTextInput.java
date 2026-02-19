package net.alloymc.client.ui;

import net.alloymc.client.render.AlloyColors;
import net.alloymc.client.render.AlloyRenderer;

import java.util.function.Consumer;

/**
 * A single-line text input field with cursor and selection support.
 *
 * Renders as an obsidian-filled box with a border that highlights on focus.
 * The cursor blinks at 500ms intervals.
 */
public class AlloyTextInput {

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final String placeholder;
    private final Consumer<String> onChange;
    private StringBuilder text = new StringBuilder();
    private int cursorPos;
    private boolean focused;
    private long lastBlinkTime;

    public AlloyTextInput(int x, int y, int width, int height,
                          String placeholder, Consumer<String> onChange) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.placeholder = placeholder;
        this.onChange = onChange;
    }

    public void render(int mouseX, int mouseY, float tickDelta) {
        // Background
        int bg = focused ? AlloyColors.OBSIDIAN_700 : AlloyColors.OBSIDIAN_800;
        int border = focused ? AlloyColors.EMBER : AlloyColors.OBSIDIAN_600;
        AlloyRenderer.fillRoundedRect(x, y, width, height, 4, bg);
        AlloyRenderer.drawRect(x, y, width, height, border);

        // Text or placeholder
        int textY = y + (height - AlloyRenderer.getTextHeight()) / 2;
        int textX = x + 6;

        if (text.isEmpty() && !focused) {
            AlloyRenderer.drawTextWithShadow(placeholder, textX, textY, AlloyColors.TEXT_DISABLED);
        } else {
            String display = text.toString();
            AlloyRenderer.drawTextWithShadow(display, textX, textY, AlloyColors.TEXT_PRIMARY);

            // Cursor
            if (focused) {
                long now = System.currentTimeMillis();
                if ((now - lastBlinkTime) % 1000 < 500) {
                    String beforeCursor = display.substring(0, Math.min(cursorPos, display.length()));
                    int cursorX = textX + AlloyRenderer.getTextWidth(beforeCursor);
                    AlloyRenderer.fillRect(cursorX, textY, 1, AlloyRenderer.getTextHeight(),
                            AlloyColors.TEXT_PRIMARY);
                }
            }
        }
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        boolean wasClicked = mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
        focused = wasClicked;
        if (focused) {
            lastBlinkTime = System.currentTimeMillis();
        }
        return wasClicked;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused) return false;
        lastBlinkTime = System.currentTimeMillis();

        // Backspace
        if (keyCode == 259 && cursorPos > 0) {
            text.deleteCharAt(cursorPos - 1);
            cursorPos--;
            notifyChange();
            return true;
        }
        // Delete
        if (keyCode == 261 && cursorPos < text.length()) {
            text.deleteCharAt(cursorPos);
            notifyChange();
            return true;
        }
        // Left arrow
        if (keyCode == 263 && cursorPos > 0) {
            cursorPos--;
            return true;
        }
        // Right arrow
        if (keyCode == 262 && cursorPos < text.length()) {
            cursorPos++;
            return true;
        }
        // Home
        if (keyCode == 268) { cursorPos = 0; return true; }
        // End
        if (keyCode == 269) { cursorPos = text.length(); return true; }

        return false;
    }

    public boolean charTyped(char ch) {
        if (!focused || ch < 32) return false;
        text.insert(cursorPos, ch);
        cursorPos++;
        notifyChange();
        return true;
    }

    private void notifyChange() {
        if (onChange != null) onChange.accept(text.toString());
    }

    public String text() { return text.toString(); }

    public void setText(String text) {
        this.text = new StringBuilder(text != null ? text : "");
        this.cursorPos = this.text.length();
    }

    public boolean isFocused() { return focused; }
    public void setFocused(boolean focused) { this.focused = focused; }
}
