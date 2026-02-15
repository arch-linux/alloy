package net.alloymc.mod.griefprevention;

import net.alloymc.api.AlloyAPI;
import net.alloymc.api.command.Command;
import net.alloymc.api.event.EventBus;
import net.alloymc.mod.griefprevention.claim.Claim;
import net.alloymc.mod.griefprevention.claim.ClaimManager;
import net.alloymc.mod.griefprevention.claim.ClaimsMode;
import net.alloymc.mod.griefprevention.command.AdminCommands;
import net.alloymc.mod.griefprevention.command.ClaimCommands;
import net.alloymc.mod.griefprevention.command.TrustCommands;
import net.alloymc.mod.griefprevention.command.UtilityCommands;
import net.alloymc.mod.griefprevention.config.GriefPreventionConfig;
import net.alloymc.mod.griefprevention.data.DataStore;
import net.alloymc.mod.griefprevention.data.FlatFileDataStore;
import net.alloymc.mod.griefprevention.protection.BlockProtectionHandler;
import net.alloymc.mod.griefprevention.protection.EntityProtectionHandler;
import net.alloymc.mod.griefprevention.protection.PlayerProtectionHandler;
import net.alloymc.mod.griefprevention.protection.PvPProtectionHandler;
import net.alloymc.mod.griefprevention.task.ClaimBlockDeliveryTask;
import net.alloymc.mod.griefprevention.task.ClaimCleanupTask;
import net.alloymc.mod.griefprevention.task.DataSaveTask;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * GriefPrevention mod entry point for the Alloy modding ecosystem.
 *
 * Implements the ModInitializer contract: on initialization, it loads config,
 * sets up data persistence, registers event listeners, registers commands,
 * and starts scheduled tasks.
 */
public class GriefPreventionMod implements net.alloymc.loader.api.ModInitializer {

    private static final Logger LOGGER = Logger.getLogger("GriefPrevention");
    private static GriefPreventionMod instance;

    private GriefPreventionConfig config;
    private ClaimManager claimManager;
    private DataStore dataStore;
    private Path configFile;

    public static GriefPreventionMod instance() {
        return instance;
    }

    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("GriefPrevention for Alloy - Initializing...");

        // ---- Configuration ----
        Path dataDir = AlloyAPI.server().dataDirectory().resolve("griefprevention");
        configFile = dataDir.resolve("config.properties");
        config = new GriefPreventionConfig();
        config.load(configFile);
        LOGGER.info("Configuration loaded from " + configFile);

        // ---- Data store ----
        dataStore = new FlatFileDataStore(dataDir.resolve("data"));
        claimManager = new ClaimManager(config);
        dataStore.loadAll(claimManager);
        LOGGER.info("Data loaded: " + claimManager.allClaims().size() + " claims");

        // ---- Event listeners ----
        EventBus bus = AlloyAPI.eventBus();
        bus.register(new BlockProtectionHandler(this));
        bus.register(new EntityProtectionHandler(this));
        bus.register(new PlayerProtectionHandler(this));
        bus.register(new PvPProtectionHandler(this));
        LOGGER.info("Event listeners registered.");

        // ---- Commands ----
        ClaimCommands.registerAll(this);
        TrustCommands.registerAll(this);
        AdminCommands.registerAll(this);
        UtilityCommands.registerAll(this);
        LOGGER.info("Commands registered.");

        // ---- Permissions ----
        registerPermissions();

        // ---- Scheduled tasks ----
        // Deliver claim blocks every 5 minutes (6000 ticks)
        AlloyAPI.scheduler().runTaskTimer(new ClaimBlockDeliveryTask(this), 6000L, 6000L);
        // Cleanup expired claims every 60 seconds (1200 ticks)
        AlloyAPI.scheduler().runTaskTimer(new ClaimCleanupTask(this), 1200L, 1200L);
        // Auto-save every 5 minutes
        AlloyAPI.scheduler().runAsyncTimer(new DataSaveTask(this), 6000L, 6000L);

        LOGGER.info("GriefPrevention initialized successfully.");
    }

    // ---- Public API ----

    public GriefPreventionConfig config() { return config; }
    public ClaimManager claimManager() { return claimManager; }
    public DataStore dataStore() { return dataStore; }

    public void registerCommand(Command command) {
        AlloyAPI.commandRegistry().register(command);
    }

    public void reloadConfig() {
        config = new GriefPreventionConfig();
        config.load(configFile);
        LOGGER.info("Configuration reloaded.");
    }

    /**
     * Returns the display name of a claim's owner, or "an administrator" for admin claims.
     */
    public String ownerName(Claim claim) {
        if (claim.isAdminClaim()) return "an administrator";
        UUID ownerID = claim.ownerID();
        Optional<?> opt = AlloyAPI.server().player(ownerID);
        if (opt.isPresent()) {
            return ((net.alloymc.api.entity.Player) opt.get()).displayName();
        }
        return ownerID.toString();
    }

    /**
     * Returns the claims mode for a given world.
     */
    public ClaimsMode getClaimsMode(String worldName) {
        return config.worldClaimModes.getOrDefault(worldName, ClaimsMode.SURVIVAL);
    }

    /**
     * Returns whether PvP rules apply in a world.
     */
    public boolean isPvpWorld(String worldName) {
        Boolean override = config.worldPvpRules.get(worldName);
        if (override != null) return override;
        // Default: PvP is enabled based on world setting
        return true;
    }

    /**
     * Called on server shutdown.
     */
    public void shutdown() {
        LOGGER.info("Saving all GriefPrevention data...");
        dataStore.saveAll(claimManager);
        dataStore.close();
        LOGGER.info("GriefPrevention shut down.");
    }

    private void registerPermissions() {
        var reg = AlloyAPI.permissionRegistry();
        reg.register("griefprevention.claims", "Basic claim management");
        reg.register("griefprevention.adminclaims", "Create and manage admin claims");
        reg.register("griefprevention.deleteclaims", "Delete other players' claims");
        reg.register("griefprevention.adjustclaimblocks", "Adjust player claim blocks");
        reg.register("griefprevention.transferclaim", "Transfer claims between players");
        reg.register("griefprevention.ignoreclaims", "Toggle claim bypass mode");
        reg.register("griefprevention.claimslistother", "View other players' claim lists");
        reg.register("griefprevention.abandonallclaims", "Abandon all claims at once");
        reg.register("griefprevention.trapped", "Use /trapped to escape claims");
        reg.register("griefprevention.unlockdrops", "Unlock death drops for pickup");
        reg.register("griefprevention.ignore", "Ignore/unignore players in chat");
        reg.register("griefprevention.separate", "Force-separate players in chat");
        reg.register("griefprevention.softmute", "Soft-mute players");
        reg.register("griefprevention.reload", "Reload configuration");
        reg.register("griefprevention.eavesdrop", "See soft-muted messages");
    }
}
