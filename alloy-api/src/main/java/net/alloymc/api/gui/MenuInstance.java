package net.alloymc.api.gui;

import net.alloymc.api.entity.Player;
import net.alloymc.api.inventory.ItemStack;

/**
 * Represents a live, open menu for a player. Provides runtime access to
 * items, data properties, and the ability to close the menu.
 *
 * <p>Obtain a MenuInstance by calling {@code player.openMenu(layout)}.
 */
public interface MenuInstance {

    /**
     * The layout this instance was created from.
     */
    MenuLayout layout();

    /**
     * The player viewing this menu.
     */
    Player viewer();

    /**
     * Gets the item in the given slot.
     *
     * @param slot the slot index
     * @return the item, or null if empty
     */
    ItemStack item(int slot);

    /**
     * Sets the item in the given slot. Updates are sent to the client.
     *
     * @param slot the slot index
     * @param item the item to place
     */
    void setItem(int slot, ItemStack item);

    /**
     * Gets a data property by name.
     *
     * @param name the property name
     * @return the current value
     * @throws IllegalArgumentException if the property doesn't exist
     */
    int getProperty(String name);

    /**
     * Sets a data property by name. The new value is synced to the client.
     *
     * @param name  the property name
     * @param value the new value (16-bit signed range)
     * @throws IllegalArgumentException if the property doesn't exist
     */
    void setProperty(String name, int value);

    /**
     * Closes this menu for the viewer.
     */
    void close();
}
