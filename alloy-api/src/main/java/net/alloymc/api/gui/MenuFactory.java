package net.alloymc.api.gui;

/**
 * Factory for creating {@link MenuLayout.Builder} instances.
 * Registered by the loader during server bootstrap.
 */
public interface MenuFactory {

    /**
     * Creates a new menu layout builder.
     *
     * @param title the display title for the menu
     * @param rows  the number of container rows (1-6)
     * @return a new builder
     * @throws IllegalArgumentException if rows is not 1-6
     */
    MenuLayout.Builder builder(String title, int rows);
}
