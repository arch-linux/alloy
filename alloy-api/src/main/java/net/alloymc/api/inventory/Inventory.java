package net.alloymc.api.inventory;

/**
 * Represents a container that holds item stacks.
 */
public interface Inventory {

    int size();

    ItemStack item(int slot);

    void setItem(int slot, ItemStack item);

    boolean contains(Material material);

    boolean isEmpty();

    /**
     * Adds an item, returning any overflow that didn't fit.
     */
    ItemStack addItem(ItemStack item);

    void clear();
}
