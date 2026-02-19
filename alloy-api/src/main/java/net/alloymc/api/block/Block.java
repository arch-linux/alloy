package net.alloymc.api.block;

import net.alloymc.api.inventory.Inventory;
import net.alloymc.api.inventory.Material;
import net.alloymc.api.world.Location;
import net.alloymc.api.world.World;

/**
 * Represents a block in a world at a specific position.
 */
public interface Block {

    World world();

    int x();

    int y();

    int z();

    default BlockPosition position() {
        return new BlockPosition(x(), y(), z());
    }

    default Location location() {
        return new Location(world(), x(), y(), z());
    }

    Material type();

    void setType(Material material);

    Block getRelative(BlockFace face);

    default Block getRelative(int dx, int dy, int dz) {
        return world().blockAt(x() + dx, y() + dy, z() + dz);
    }

    boolean isEmpty();

    boolean isLiquid();

    boolean isSolid();

    /**
     * Returns this block's inventory, if it has one.
     *
     * <p>Blocks like chests, furnaces, hoppers, dispensers, and barrels
     * have inventories that can be read and modified. Returns {@code null}
     * for blocks without an inventory (e.g., stone, dirt).
     *
     * @return the block's inventory, or null if the block has no container
     */
    Inventory inventory();
}
