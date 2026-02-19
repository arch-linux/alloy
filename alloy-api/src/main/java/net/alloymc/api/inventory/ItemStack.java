package net.alloymc.api.inventory;

import net.alloymc.api.AlloyAPI;

import java.util.List;

/**
 * Represents a stack of items.
 */
public interface ItemStack {

    /**
     * Creates a new ItemStack of the given material and amount.
     *
     * @param type   the material type
     * @param amount the stack count
     * @return a new ItemStack
     */
    static ItemStack create(Material type, int amount) {
        return AlloyAPI.itemFactory().create(type, amount);
    }

    Material type();

    int amount();

    void setAmount(int amount);

    boolean isEmpty();

    /**
     * Returns the custom display name, or null if not set.
     */
    String displayName();

    /**
     * Sets a custom display name for this item.
     * Pass null to clear the custom name.
     */
    void setDisplayName(String name);

    /**
     * Returns the lore lines, or an empty list if none.
     */
    List<String> lore();

    /**
     * Sets the lore lines for this item.
     * Pass null or empty list to clear lore.
     */
    void setLore(List<String> lines);

    /**
     * Returns true if this item has custom persistent data with the given key.
     */
    boolean hasData(String key);

    String getData(String key);

    void setData(String key, String value);

    void removeData(String key);

    /**
     * Create a copy of this item stack.
     */
    ItemStack copy();
}
