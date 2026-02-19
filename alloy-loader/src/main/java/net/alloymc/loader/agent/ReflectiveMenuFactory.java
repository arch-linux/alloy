package net.alloymc.loader.agent;

import net.alloymc.api.gui.MenuFactory;
import net.alloymc.api.gui.MenuLayout;

/**
 * Implements {@link MenuFactory} by creating {@link MenuLayoutBuilderImpl} instances.
 * Registered by the loader during server bootstrap.
 */
public final class ReflectiveMenuFactory implements MenuFactory {

    private final Object minecraftServer;

    public ReflectiveMenuFactory(Object minecraftServer) {
        this.minecraftServer = minecraftServer;
    }

    @Override
    public MenuLayout.Builder builder(String title, int rows) {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("Rows must be 1-6, got: " + rows);
        }
        return new MenuLayoutBuilderImpl(title, rows);
    }

    /**
     * Returns the Minecraft server instance for use by menu instances that
     * need to interact with the MC container system.
     */
    Object server() {
        return minecraftServer;
    }
}
