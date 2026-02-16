package net.alloymc.loader.impl;

import net.alloymc.api.Server;
import net.alloymc.api.entity.Player;
import net.alloymc.api.world.World;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Stub {@link Server} for the loader bootstrap phase.
 *
 * <p>Provides a real data directory and logger. Player/world collections
 * are empty since the game hasn't started yet.
 */
public class StubServer implements Server {

    private static final Logger LOGGER = Logger.getLogger("AlloyServer");

    private final Path dataDirectory;

    public StubServer(Path gameDir) {
        this.dataDirectory = gameDir;
    }

    @Override
    public Collection<? extends Player> onlinePlayers() {
        return Collections.emptyList();
    }

    @Override
    public Optional<? extends Player> player(UUID id) {
        return Optional.empty();
    }

    @Override
    public Optional<? extends Player> player(String name) {
        return Optional.empty();
    }

    @Override
    public Collection<? extends World> worlds() {
        return Collections.emptyList();
    }

    @Override
    public Optional<? extends World> world(String name) {
        return Optional.empty();
    }

    @Override
    public Path dataDirectory() {
        return dataDirectory;
    }

    @Override
    public Logger logger() {
        return LOGGER;
    }

    @Override
    public long currentTick() {
        return 0;
    }

    @Override
    public void broadcast(String message) {
        LOGGER.info("[Broadcast] " + message);
    }
}
