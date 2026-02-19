package net.alloymc.api.inventory;

/**
 * Factory for creating new {@link ItemStack} instances from scratch.
 * Registered by the loader during server bootstrap.
 */
public interface ItemFactory {

    /**
     * Creates a new ItemStack of the given material and amount.
     *
     * @param type   the material type
     * @param amount the stack count (1-64)
     * @return a new ItemStack, never null
     */
    ItemStack create(Material type, int amount);
}
