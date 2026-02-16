package net.alloymc.api;

import net.alloymc.api.command.CommandRegistry;
import net.alloymc.api.economy.EconomyRegistry;
import net.alloymc.api.event.EventBus;
import net.alloymc.api.permission.PermissionRegistry;
import net.alloymc.api.scheduler.Scheduler;

/**
 * Central access point for the Alloy modding API.
 * All services are registered during loader initialization and accessed through this class.
 */
public final class AlloyAPI {

    private static Server server;
    private static EventBus eventBus;
    private static CommandRegistry commandRegistry;
    private static PermissionRegistry permissionRegistry;
    private static EconomyRegistry economyRegistry;
    private static Scheduler scheduler;
    private static LaunchEnvironment environment = LaunchEnvironment.CLIENT;

    private AlloyAPI() {}

    public static Server server() {
        if (server == null) throw new IllegalStateException("Alloy API not yet initialized");
        return server;
    }

    public static EventBus eventBus() {
        if (eventBus == null) throw new IllegalStateException("Alloy API not yet initialized");
        return eventBus;
    }

    public static CommandRegistry commandRegistry() {
        if (commandRegistry == null) throw new IllegalStateException("Alloy API not yet initialized");
        return commandRegistry;
    }

    public static PermissionRegistry permissionRegistry() {
        if (permissionRegistry == null) throw new IllegalStateException("Alloy API not yet initialized");
        return permissionRegistry;
    }

    public static Scheduler scheduler() {
        if (scheduler == null) throw new IllegalStateException("Alloy API not yet initialized");
        return scheduler;
    }

    /**
     * Returns the economy registry. Mods can use this to access or provide economy services.
     */
    public static EconomyRegistry economy() {
        if (economyRegistry == null) throw new IllegalStateException("Alloy API not yet initialized");
        return economyRegistry;
    }

    /**
     * Returns the current launch environment (CLIENT or SERVER).
     */
    public static LaunchEnvironment environment() {
        return environment;
    }

    /**
     * Called by the loader during bootstrap. Not for mod use.
     */
    public static void initialize(Server server, EventBus eventBus, CommandRegistry commandRegistry,
                                  PermissionRegistry permissionRegistry, Scheduler scheduler) {
        initialize(server, eventBus, commandRegistry, permissionRegistry, scheduler,
                LaunchEnvironment.CLIENT);
    }

    /**
     * Called by the loader during bootstrap with explicit environment. Not for mod use.
     */
    public static void initialize(Server server, EventBus eventBus, CommandRegistry commandRegistry,
                                  PermissionRegistry permissionRegistry, Scheduler scheduler,
                                  LaunchEnvironment environment) {
        AlloyAPI.server = server;
        AlloyAPI.eventBus = eventBus;
        AlloyAPI.commandRegistry = commandRegistry;
        AlloyAPI.permissionRegistry = permissionRegistry;
        AlloyAPI.economyRegistry = new EconomyRegistry();
        AlloyAPI.scheduler = scheduler;
        AlloyAPI.environment = environment;
    }

    /**
     * Upgrades the server instance at runtime. Called by the loader when the
     * Minecraft server finishes starting, replacing the bootstrap stub with
     * a real implementation that delegates to MinecraftServer.
     *
     * <p>Not for mod use.
     */
    public static void setServer(Server server) {
        AlloyAPI.server = server;
    }

    /**
     * Upgrades the scheduler instance at runtime. Called by the loader when
     * the server tick loop is available, replacing the bootstrap stub with
     * a tick-synchronized implementation.
     *
     * <p>Not for mod use.
     */
    public static void setScheduler(Scheduler scheduler) {
        AlloyAPI.scheduler = scheduler;
    }
}
