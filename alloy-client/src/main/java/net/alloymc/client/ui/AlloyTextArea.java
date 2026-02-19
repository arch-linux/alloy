package net.alloymc.client.ui;

import net.alloymc.client.render.AlloyColors;
import net.alloymc.client.render.AlloyRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * A multi-line scrollable text area for terminal output, logs, etc.
 *
 * Supports appending lines and auto-scrolling to the bottom.
 * Read-only by default — use for displaying output, not editing.
 */
public class AlloyTextArea {

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int maxLines;
    private final List<String> lines = new ArrayList<>();
    private int scrollOffset;
    private boolean autoScroll = true;

    public AlloyTextArea(int x, int y, int width, int height, int maxLines) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.maxLines = maxLines;
    }

    public void render(int mouseX, int mouseY, float tickDelta) {
        // Background
        AlloyRenderer.fillRoundedRect(x, y, width, height, 4, AlloyColors.OBSIDIAN_900);
        AlloyRenderer.drawRect(x, y, width, height, AlloyColors.OBSIDIAN_600);

        // Visible lines
        int lineHeight = AlloyRenderer.getTextHeight() + 2;
        int visibleLines = (height - 8) / lineHeight;
        int startLine = autoScroll
                ? Math.max(0, lines.size() - visibleLines)
                : scrollOffset;

        int textX = x + 4;
        int textY = y + 4;

        for (int i = 0; i < visibleLines && startLine + i < lines.size(); i++) {
            AlloyRenderer.drawTextWithShadow(
                    lines.get(startLine + i), textX, textY + i * lineHeight,
                    AlloyColors.TEXT_SECONDARY);
        }

        // Scrollbar
        if (lines.size() > visibleLines) {
            int barHeight = Math.max(10, height * visibleLines / lines.size());
            int barY = y + (int) ((double) startLine / lines.size() * height);
            AlloyRenderer.fillRect(x + width - 3, barY, 2, barHeight, AlloyColors.OBSIDIAN_600);
        }
    }

    /**
     * Appends a line of text. Trims oldest lines if over the max.
     */
    public void appendLine(String line) {
        lines.add(line);
        while (lines.size() > maxLines) {
            lines.removeFirst();
        }
    }

    /**
     * Clears all text.
     */
    public void clear() {
        lines.clear();
        scrollOffset = 0;
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /**
     * Handles mouse scroll for manual scrolling.
     */
    public boolean mouseScrolled(double amount) {
        int lineHeight = AlloyRenderer.getTextHeight() + 2;
        int visibleLines = (height - 8) / lineHeight;
        if (lines.size() <= visibleLines) return false;

        autoScroll = false;
        scrollOffset -= (int) amount;
        scrollOffset = Math.clamp(scrollOffset, 0, Math.max(0, lines.size() - visibleLines));

        // Re-enable auto-scroll if at bottom
        if (scrollOffset >= lines.size() - visibleLines) {
            autoScroll = true;
        }
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public List<String> lines() { return List.copyOf(lines); }
    public void setAutoScroll(boolean autoScroll) { this.autoScroll = autoScroll; }
}
