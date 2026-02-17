package net.alloymc.core;

import net.alloymc.api.AlloyAPI;
import net.alloymc.api.permission.PermissionRegistry;
import net.alloymc.api.permission.PermissionRegistry.PermissionDefault;
import net.alloymc.core.command.AlloyCommand;
import net.alloymc.core.command.BalanceCommand;
import net.alloymc.core.command.ModsCommand;
import net.alloymc.core.command.PayCommand;
import net.alloymc.core.command.PermissionCommand;
import net.alloymc.core.command.SetMoneyCommand;
import net.alloymc.core.command.TpsCommand;
import net.alloymc.core.economy.FileEconomyProvider;
import net.alloymc.core.monitor.TpsMonitor;
import net.alloymc.core.permission.FilePermissionProvider;
import net.alloymc.loader.api.ModInitializer;

import java.nio.file.Path;

/**
 * Built-in core mod for server administration.
 * Registers commands, permissions, and the TPS monitor.
 */
public class AlloyCore implements ModInitializer {

    private static final double STARTING_BALANCE = 100.0;

    private static AlloyCore instance;
    private TpsMonitor tpsMonitor;
    private FilePermissionProvider permissionProvider;
    private FileEconomyProvider economyProvider;

    @Override
    public void onInitialize() {
        instance = this;
        System.out.println("[AlloyCore] Initializing core mod...");

        // Initialize TPS monitor
        tpsMonitor = new TpsMonitor();

        // Initialize file-based permission provider
        Path dataDir = AlloyAPI.server().dataDirectory();
        permissionProvider = new FilePermissionProvider(dataDir);
        AlloyAPI.permissionRegistry().setProvider(permissionProvider);
        System.out.println("[AlloyCore] File-based permission provider active");

        // Initialize file-based economy provider
        economyProvider = new FileEconomyProvider(dataDir, STARTING_BALANCE);
        AlloyAPI.economy().setProvider(economyProvider);
        System.out.println("[AlloyCore] File-based economy provider active (starting balance: $"
                + String.format("%.2f", STARTING_BALANCE) + ")");

        // Register permissions
        registerPermissions();

        // Register commands
        registerCommands();

        System.out.println("[AlloyCore] Core mod initialized");
    }

    private void registerPermissions() {
        PermissionRegistry registry = AlloyAPI.permissionRegistry();
        registry.register("alloy.command.mods", "List loaded mods", PermissionDefault.TRUE);
        registry.register("alloy.command.tps", "View TPS and MSPT", PermissionDefault.OP);
        registry.register("alloy.command.alloy", "View Alloy info", PermissionDefault.TRUE);
        registry.register("alloy.command.permissions", "Manage permissions", PermissionDefault.OP);
        registry.register("alloy.command.balance", "Check balance", PermissionDefault.TRUE);
        registry.register("alloy.command.pay", "Pay another player", PermissionDefault.TRUE);
        registry.register("alloy.command.setmoney", "Set a player's balance", PermissionDefault.OP);
    }

    private void registerCommands() {
        var commandRegistry = AlloyAPI.commandRegistry();
        commandRegistry.register(new ModsCommand());
        commandRegistry.register(new TpsCommand(tpsMonitor));
        commandRegistry.register(new AlloyCommand());
        commandRegistry.register(new PermissionCommand(permissionProvider));
        commandRegistry.register(new BalanceCommand());
        commandRegistry.register(new PayCommand());
        commandRegistry.register(new SetMoneyCommand());
        System.out.println("[AlloyCore] Registered 7 commands");
    }

    public static AlloyCore getInstance() {
        return instance;
    }

    public TpsMonitor getTpsMonitor() {
        return tpsMonitor;
    }

    public FilePermissionProvider getPermissionProvider() {
        return permissionProvider;
    }
}
