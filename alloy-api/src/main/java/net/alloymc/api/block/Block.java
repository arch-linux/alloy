package net.alloymc.api.block;

import net.alloymc.api.world.Location;
import net.alloymc.api.world.World;
import net.alloymc.api.inventory.Material;

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
}
