package net.alloymc.api.inventory;

/**
 * Represents a stack of items.
 */
public interface ItemStack {

    Material type();

    int amount();

    void setAmount(int amount);

    boolean isEmpty();

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
