package net.alloymc.api;

import net.alloymc.api.entity.Player;
import net.alloymc.api.world.World;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Represents the game server instance.
 */
public interface Server {

    /**
     * Returns all currently online players.
     */
    Collection<? extends Player> onlinePlayers();

    /**
     * Finds an online player by UUID.
     */
    Optional<? extends Player> player(UUID id);

    /**
     * Finds an online player by name (case-insensitive).
     */
    Optional<? extends Player> player(String name);

    /**
     * Returns all loaded worlds.
     */
    Collection<? extends World> worlds();

    /**
     * Finds a world by name.
     */
    Optional<? extends World> world(String name);

    /**
     * Returns the server's data directory root.
     */
    Path dataDirectory();

    /**
     * Returns the server logger.
     */
    Logger logger();

    /**
     * Returns the current server tick count.
     */
    long currentTick();

    /**
     * Broadcasts a message to all online players.
     */
    void broadcast(String message);
}
