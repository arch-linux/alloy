package net.alloymc.api.inventory;

import net.alloymc.api.AlloyAPI;

/**
 * A custom chest-style inventory GUI that can be opened for players.
 * Supports 1-6 rows (9-54 slots) with a custom title.
 */
public interface CustomInventory extends Inventory {

    /**
     * The display title shown at the top of the inventory GUI.
     */
    String title();

    /**
     * The number of rows (1-6).
     */
    int rows();

    /**
     * Creates a new custom inventory with the given title and row count.
     *
     * @param title the display title
     * @param rows  number of rows (1-6)
     * @return a new CustomInventory
     * @throws IllegalArgumentException if rows is not 1-6
     */
    static CustomInventory create(String title, int rows) {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("Rows must be 1-6, got: " + rows);
        }
        return AlloyAPI.inventoryFactory().create(title, rows);
    }
}
