package net.alloymc.api.world;

import net.alloymc.api.block.Block;
import net.alloymc.api.block.BlockPosition;
import net.alloymc.api.entity.Entity;
import net.alloymc.api.entity.Player;

import java.util.Collection;
import java.util.UUID;

/**
 * Represents a game world (dimension).
 */
public interface World {

    String name();

    UUID uniqueId();

    /**
     * Returns the block at the given position.
     */
    Block blockAt(int x, int y, int z);

    default Block blockAt(BlockPosition pos) {
        return blockAt(pos.x(), pos.y(), pos.z());
    }

    Block blockAt(Location location);

    /**
     * Returns all entities in this world.
     */
    Collection<? extends Entity> entities();

    /**
     * Returns all players in this world.
     */
    Collection<? extends Player> players();

    /**
     * The world's environment type.
     */
    Environment environment();

    int seaLevel();

    int minHeight();

    int maxHeight();

    /**
     * Whether PvP is enabled in this world.
     */
    boolean pvpEnabled();

    enum Environment {
        OVERWORLD,
        NETHER,
        THE_END
    }
}
