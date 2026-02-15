package net.alloymc.api;

import net.alloymc.api.command.CommandRegistry;
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
    private static Scheduler scheduler;

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
     * Called by the loader during bootstrap. Not for mod use.
     */
    public static void initialize(Server server, EventBus eventBus, CommandRegistry commandRegistry,
                                  PermissionRegistry permissionRegistry, Scheduler scheduler) {
        AlloyAPI.server = server;
        AlloyAPI.eventBus = eventBus;
        AlloyAPI.commandRegistry = commandRegistry;
        AlloyAPI.permissionRegistry = permissionRegistry;
        AlloyAPI.scheduler = scheduler;
    }
}
