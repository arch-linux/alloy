package net.alloymc.api.gui;

import net.alloymc.api.inventory.ItemStack;

import java.util.List;
import java.util.function.Predicate;

/**
 * Defines the layout of a custom menu screen: title, slots, and synced data properties.
 *
 * <p>Use the {@link Builder} to construct a layout, then open it for a player via
 * {@code player.openMenu(layout)}.
 *
 * <p>Example — a simple furnace-style menu:
 * <pre>{@code
 * MenuLayout layout = AlloyAPI.menuFactory().builder("My Furnace", 3)
 *     .slot(0, 56, 17, SlotType.INPUT)
 *     .slot(1, 56, 53, SlotType.FUEL)
 *     .slot(2, 116, 35, SlotType.OUTPUT)
 *     .property("progress", 0)
 *     .property("fuel", 1)
 *     .build();
 * }</pre>
 */
public interface MenuLayout {

    /**
     * The display title shown at the top of the GUI.
     */
    String title();

    /**
     * The number of rows in the underlying container (1-6).
     */
    int rows();

    /**
     * The defined slots in this layout.
     */
    List<SlotDefinition> slots();

    /**
     * The synced data properties.
     */
    List<DataProperty> properties();

    /**
     * Looks up a data property by name.
     *
     * @param name the property name
     * @return the property, or null if not found
     */
    DataProperty property(String name);

    /**
     * Builder for constructing {@link MenuLayout} instances.
     */
    interface Builder {

        /**
         * Adds a slot at the given position.
         *
         * @param index the container slot index
         * @param x     the x pixel position
         * @param y     the y pixel position
         * @param type  the slot type
         * @return this builder
         */
        Builder slot(int index, int x, int y, SlotType type);

        /**
         * Adds a slot with an item filter.
         *
         * @param index  the container slot index
         * @param x      the x pixel position
         * @param y      the y pixel position
         * @param type   the slot type
         * @param filter predicate that controls which items can be placed in this slot
         * @return this builder
         */
        Builder slot(int index, int x, int y, SlotType type, Predicate<ItemStack> filter);

        /**
         * Adds a 9-column row of NORMAL slots at the given y position, auto-indexed.
         *
         * @param startIndex the starting slot index
         * @param y          the y pixel position
         * @return this builder
         */
        Builder row(int startIndex, int y);

        /**
         * Registers a synced data property.
         *
         * @param name  the property name
         * @param index the ContainerData index
         * @return this builder
         */
        Builder property(String name, int index);

        /**
         * Builds the menu layout.
         *
         * @return the constructed MenuLayout
         */
        MenuLayout build();
    }
}
