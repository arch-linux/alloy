package net.alloymc.client.ui;

import net.alloymc.client.render.AlloyColors;
import net.alloymc.client.render.AlloyRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A scrollable list of selectable text entries.
 *
 * The selected entry is highlighted with an ember accent; hovered entries
 * show a subtle obsidian highlight.
 */
public class AlloyListView {

    private static final int ITEM_HEIGHT = 20;

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final List<String> items = new ArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset;
    private Consumer<Integer> onSelect;

    public AlloyListView(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void render(int mouseX, int mouseY, float tickDelta) {
        // Background
        AlloyRenderer.fillRoundedRect(x, y, width, height, 4, AlloyColors.OBSIDIAN_900);
        AlloyRenderer.drawRect(x, y, width, height, AlloyColors.OBSIDIAN_600);

        int visibleItems = height / ITEM_HEIGHT;

        for (int i = 0; i < visibleItems && scrollOffset + i < items.size(); i++) {
            int itemIndex = scrollOffset + i;
            int itemY = y + i * ITEM_HEIGHT;

            boolean hovered = mouseX >= x && mouseX < x + width
                    && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            boolean selected = itemIndex == selectedIndex;

            // Row background
            if (selected) {
                AlloyRenderer.fillRect(x + 1, itemY, width - 2, ITEM_HEIGHT, AlloyColors.OBSIDIAN_700);
                AlloyRenderer.fillRect(x + 1, itemY, 2, ITEM_HEIGHT, AlloyColors.EMBER);
            } else if (hovered) {
                AlloyRenderer.fillRect(x + 1, itemY, width - 2, ITEM_HEIGHT, AlloyColors.OBSIDIAN_800);
            }

            // Text
            int textColor = selected ? AlloyColors.TEXT_PRIMARY : AlloyColors.TEXT_SECONDARY;
            int textY = itemY + (ITEM_HEIGHT - AlloyRenderer.getTextHeight()) / 2;
            AlloyRenderer.drawTextWithShadow(items.get(itemIndex), x + 8, textY, textColor);
        }
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) return false;

        int clickedRow = (mouseY - y) / ITEM_HEIGHT;
        int clickedIndex = scrollOffset + clickedRow;

        if (clickedIndex >= 0 && clickedIndex < items.size()) {
            selectedIndex = clickedIndex;
            if (onSelect != null) onSelect.accept(selectedIndex);
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double amount) {
        int visibleItems = height / ITEM_HEIGHT;
        if (items.size() <= visibleItems) return false;

        scrollOffset -= (int) amount;
        scrollOffset = Math.clamp(scrollOffset, 0, Math.max(0, items.size() - visibleItems));
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public void addItem(String item) { items.add(item); }

    public void setItems(List<String> newItems) {
        items.clear();
        items.addAll(newItems);
        selectedIndex = -1;
        scrollOffset = 0;
    }

    public void clear() {
        items.clear();
        selectedIndex = -1;
        scrollOffset = 0;
    }

    public int selectedIndex() { return selectedIndex; }
    public String selectedItem() { return selectedIndex >= 0 ? items.get(selectedIndex) : null; }
    public void setOnSelect(Consumer<Integer> onSelect) { this.onSelect = onSelect; }
}
