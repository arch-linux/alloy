package net.alloymc.api.inventory;

/**
 * Factory for creating {@link CustomInventory} instances.
 * Registered by the loader during server bootstrap.
 */
public interface InventoryFactory {

    /**
     * Creates a new custom inventory with the given title and row count.
     *
     * @param title the display title for the inventory
     * @param rows  number of rows (1-6)
     * @return a new CustomInventory
     */
    CustomInventory create(String title, int rows);
}
