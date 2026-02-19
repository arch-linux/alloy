package net.alloymc.client.ui;

import net.alloymc.client.render.AlloyColors;
import net.alloymc.client.render.AlloyRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A tabbed container that displays one panel at a time.
 *
 * Tab headers are rendered as a horizontal row at the top. The active tab
 * has an ember underline; inactive tabs use a ghost style.
 */
public class AlloyTabPanel {

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final List<String> tabs = new ArrayList<>();
    private int activeTab;
    private Consumer<Integer> onTabChange;

    private static final int TAB_HEIGHT = 28;

    public AlloyTabPanel(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * Adds a tab with the given label.
     *
     * @param label the tab label
     * @return the tab index
     */
    public int addTab(String label) {
        tabs.add(label);
        return tabs.size() - 1;
    }

    public void render(int mouseX, int mouseY, float tickDelta) {
        if (tabs.isEmpty()) return;

        int tabWidth = width / tabs.size();

        // Tab bar background
        AlloyRenderer.fillRect(x, y, width, TAB_HEIGHT, AlloyColors.OBSIDIAN_800);

        // Tabs
        for (int i = 0; i < tabs.size(); i++) {
            int tabX = x + i * tabWidth;
            boolean hovered = mouseX >= tabX && mouseX < tabX + tabWidth
                    && mouseY >= y && mouseY < y + TAB_HEIGHT;
            boolean active = i == activeTab;

            // Tab background
            if (active) {
                AlloyRenderer.fillRect(tabX, y, tabWidth, TAB_HEIGHT, AlloyColors.OBSIDIAN_700);
                AlloyRenderer.fillRect(tabX, y + TAB_HEIGHT - 2, tabWidth, 2, AlloyColors.EMBER);
            } else if (hovered) {
                AlloyRenderer.fillRect(tabX, y, tabWidth, TAB_HEIGHT, AlloyColors.OBSIDIAN_700);
            }

            // Label
            int textColor = active ? AlloyColors.EMBER : AlloyColors.TEXT_SECONDARY;
            AlloyRenderer.drawCenteredText(tabs.get(i), tabX,
                    y + (TAB_HEIGHT - AlloyRenderer.getTextHeight()) / 2,
                    tabWidth, textColor);
        }

        // Content area background
        int contentY = y + TAB_HEIGHT;
        int contentHeight = height - TAB_HEIGHT;
        AlloyRenderer.fillRect(x, contentY, width, contentHeight, AlloyColors.OBSIDIAN_900);
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || tabs.isEmpty()) return false;
        if (mouseY < y || mouseY >= y + TAB_HEIGHT) return false;
        if (mouseX < x || mouseX >= x + width) return false;

        int tabWidth = width / tabs.size();
        int clickedTab = (mouseX - x) / tabWidth;
        if (clickedTab >= 0 && clickedTab < tabs.size() && clickedTab != activeTab) {
            activeTab = clickedTab;
            if (onTabChange != null) onTabChange.accept(activeTab);
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public int activeTab() { return activeTab; }
    public void setActiveTab(int index) { this.activeTab = index; }
    public void setOnTabChange(Consumer<Integer> onTabChange) { this.onTabChange = onTabChange; }

    /**
     * Returns the Y coordinate where tab content begins.
     */
    public int contentY() { return y + TAB_HEIGHT; }

    /**
     * Returns the height available for tab content.
     */
    public int contentHeight() { return height - TAB_HEIGHT; }
}
