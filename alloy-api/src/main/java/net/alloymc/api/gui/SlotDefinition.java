package net.alloymc.api.gui;

import net.alloymc.api.inventory.ItemStack;

import java.util.function.Predicate;

/**
 * Defines a single slot in a {@link MenuLayout}: its position, type, and optional item filter.
 *
 * @param index  the slot index in the container (0-based)
 * @param x      the x pixel position in the GUI (left edge of slot)
 * @param y      the y pixel position in the GUI (top edge of slot)
 * @param type   the slot behavior type
 * @param filter optional predicate that restricts which items can be placed in this slot;
 *               null means any item is accepted
 */
public record SlotDefinition(int index, int x, int y, SlotType type, Predicate<ItemStack> filter) {

    /**
     * Creates a slot definition with no item filter.
     */
    public SlotDefinition(int index, int x, int y, SlotType type) {
        this(index, x, y, type, null);
    }

    /**
     * Whether the given item is allowed in this slot.
     * Returns true if no filter is set or if the filter accepts the item.
     */
    public boolean accepts(ItemStack item) {
        if (type == SlotType.DISPLAY) return false;
        if (type == SlotType.OUTPUT) return false;
        if (filter == null) return true;
        return filter.test(item);
    }

    /**
     * Whether players can extract items from this slot.
     */
    public boolean allowsExtract() {
        return type != SlotType.DISPLAY;
    }
}
