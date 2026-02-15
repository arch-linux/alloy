package net.alloymc.mod.griefprevention.protection;

import net.alloymc.api.AlloyAPI;
import net.alloymc.api.block.Block;
import net.alloymc.api.entity.Player;
import net.alloymc.api.event.EventHandler;
import net.alloymc.api.event.EventPriority;
import net.alloymc.api.event.Listener;
import net.alloymc.api.event.player.PlayerBucketEvent;
import net.alloymc.api.event.player.PlayerChatEvent;
import net.alloymc.api.event.player.PlayerCommandEvent;
import net.alloymc.api.event.player.PlayerDeathEvent;
import net.alloymc.api.event.player.PlayerDropItemEvent;
import net.alloymc.api.event.player.PlayerInteractEntityEvent;
import net.alloymc.api.event.player.PlayerInteractEvent;
import net.alloymc.api.event.player.PlayerItemHeldEvent;
import net.alloymc.api.event.player.PlayerJoinEvent;
import net.alloymc.api.event.player.PlayerQuitEvent;
import net.alloymc.api.event.player.PlayerRespawnEvent;
import net.alloymc.api.event.player.PlayerTeleportEvent;
import net.alloymc.api.inventory.Material;
import net.alloymc.api.world.Location;
import net.alloymc.mod.griefprevention.GriefPreventionMod;
import net.alloymc.mod.griefprevention.claim.Claim;
import net.alloymc.mod.griefprevention.claim.ClaimPermission;
import net.alloymc.mod.griefprevention.claim.ShovelMode;
import net.alloymc.mod.griefprevention.config.GriefPreventionConfig;
import net.alloymc.mod.griefprevention.message.Messages;
import net.alloymc.mod.griefprevention.player.PlayerData;
import net.alloymc.mod.griefprevention.visualization.ClaimVisualization;
import net.alloymc.mod.griefprevention.visualization.VisualizationType;

/**
 * Handles player interaction events — container access, tool use, claim creation,
 * join/quit, chat, teleports, and more.
 */
public class PlayerProtectionHandler implements Listener {

    private final GriefPreventionMod mod;

    public PlayerProtectionHandler(GriefPreventionMod mod) {
        this.mod = mod;
    }

    private GriefPreventionConfig config() { return mod.config(); }

    // ---- Player Join ----

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.player();
        PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());

        // Initialize new player claim blocks
        if (!player.hasPlayedBefore()) {
            data.setAccruedClaimBlocks(config().claimsInitialBlocks);
            mod.dataStore().savePlayerData(player.uniqueId(), data);
        }

        data.setLoginLocation(player.location());
        data.setPvpImmune(config().pvpProtectFreshSpawns);
        data.setDropsUnlocked(false);
    }

    // ---- Player Quit ----

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.player();
        PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());

        // PvP combat logout punishment
        if (config().pvpPunishLogout && data.inPvpCombat(config().pvpCombatTimeoutSeconds)) {
            player.setHealth(0);
            AlloyAPI.server().broadcast(String.format(Messages.PVP_COMBAT_LOGOUT, player.displayName()));
        }

        // Save player data
        mod.dataStore().savePlayerData(player.uniqueId(), data);
    }

    // ---- Player Interact (right click blocks) ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.player();
        PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());

        // ---- Investigation tool (stick by default) ----
        if (event.item() != null && event.item().type() == config().claimsInvestigationTool
                && event.action() == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            handleInvestigation(player, event.clickedBlock());
            return;
        }

        // ---- Claim modification tool (golden shovel by default) ----
        if (event.item() != null && event.item().type() == config().claimsModificationTool
                && event.action() == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            handleShovelClick(player, event.clickedBlock(), data);
            event.setCancelled(true);
            return;
        }

        // ---- Block interaction protection ----
        if (event.action() == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK && event.hasBlock()) {
            Block clicked = event.clickedBlock();
            if (data.isIgnoringClaims()) return;

            Claim claim = mod.claimManager().getClaimAt(clicked.location());
            if (claim == null) return;

            Material type = clicked.type();

            // Container protection
            if (type.isContainer() || type == Material.ANVIL || type == Material.CHIPPED_ANVIL
                    || type == Material.DAMAGED_ANVIL || type == Material.BEACON
                    || type == Material.GRINDSTONE || type == Material.STONECUTTER
                    || type == Material.LOOM || type == Material.CARTOGRAPHY_TABLE
                    || type == Material.SMITHING_TABLE || type == Material.JUKEBOX
                    || type == Material.DECORATED_POT || type == Material.COMPOSTER
                    || type == Material.CAULDRON) {
                String denial = claim.checkPermission(player.uniqueId(), ClaimPermission.CONTAINER);
                if (denial != null) {
                    event.setCancelled(true);
                    player.sendMessage(String.format(Messages.NO_CONTAINER_PERMISSION,
                            mod.ownerName(claim)), Player.MessageType.ERROR);
                    return;
                }
            }

            // Lectern
            if (type == Material.LECTERN && config().claimsLecternReadingRequiresAccessTrust) {
                String denial = claim.checkPermission(player.uniqueId(), ClaimPermission.ACCESS);
                if (denial != null) {
                    event.setCancelled(true);
                    player.sendMessage(String.format(Messages.NO_ACCESS_PERMISSION,
                            mod.ownerName(claim)), Player.MessageType.ERROR);
                    return;
                }
            }

            // Door protection
            if (type.isDoor() && config().claimsLockWoodenDoors && type != Material.IRON_DOOR) {
                String denial = claim.checkPermission(player.uniqueId(), ClaimPermission.ACCESS);
                if (denial != null) {
                    event.setCancelled(true);
                    player.sendMessage(String.format(Messages.NO_ACCESS_PERMISSION,
                            mod.ownerName(claim)), Player.MessageType.ERROR);
                    return;
                }
            }

            // Trapdoor protection
            if (type.isTrapdoor() && config().claimsLockTrapDoors) {
                String denial = claim.checkPermission(player.uniqueId(), ClaimPermission.ACCESS);
                if (denial != null) {
                    event.setCancelled(true);
                    player.sendMessage(String.format(Messages.NO_ACCESS_PERMISSION,
                            mod.ownerName(claim)), Player.MessageType.ERROR);
                    return;
                }
            }

            // Fence gate protection
            if (type.isFenceGate() && config().claimsLockFenceGates) {
                String denial = claim.checkPermission(player.uniqueId(), ClaimPermission.ACCESS);
                if (denial != null) {
                    event.setCancelled(true);
                    player.sendMessage(String.format(Messages.NO_ACCESS_PERMISSION,
                            mod.ownerName(claim)), Player.MessageType.ERROR);
                    return;
                }
            }

            // Button/lever protection
            if ((type.isButton() || type == Material.LEVER) && config().claimsPreventButtonsSwitches) {
                String denial = claim.checkPermission(player.uniqueId(), ClaimPermission.ACCESS);
                if (denial != null) {
                    event.setCancelled(true);
                    player.sendMessage(String.format(Messages.NO_ACCESS_PERMISSION,
                            mod.ownerName(claim)), Player.MessageType.ERROR);
                    return;
                }
            }

            // Bed protection
            if (type.isBed()) {
                String denial = claim.checkPermission(player.uniqueId(), ClaimPermission.ACCESS);
                if (denial != null) {
                    event.setCancelled(true);
                    player.sendMessage(String.format(Messages.NO_ACCESS_PERMISSION,
                            mod.ownerName(claim)), Player.MessageType.ERROR);
                    return;
                }
            }

            // Cake
            if (type == Material.CAKE) {
                String denial = claim.checkPermission(player.uniqueId(), ClaimPermission.ACCESS);
                if (denial != null) {
                    event.setCancelled(true);
                    player.sendMessage(String.format(Messages.NO_ACCESS_PERMISSION,
                            mod.ownerName(claim)), Player.MessageType.ERROR);
                }
            }
        }

        // ---- Turtle egg trampling (PHYSICAL action) ----
        if (event.action() == PlayerInteractEvent.Action.PHYSICAL && event.hasBlock()) {
            if (event.clickedBlock().type() == Material.TURTLE_EGG) {
                if (!data.isIgnoringClaims()) {
                    Claim claim = mod.claimManager().getClaimAt(event.clickedBlock().location());
                    if (claim != null) {
                        String denial = claim.checkPermission(player.uniqueId(), ClaimPermission.BUILD);
                        if (denial != null) {
                            event.setCancelled(true);
                        }
                    }
                }
            }
        }
    }

    // ---- Player Interact Entity ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.player();
        PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());
        if (data.isIgnoringClaims()) return;

        // Villager trading protection
        if (event.rightClicked().type() == net.alloymc.api.entity.EntityType.VILLAGER
                && config().claimsVillagerTradingRequiresPermission) {
            Claim claim = mod.claimManager().getClaimAt(event.rightClicked().location());
            if (claim != null) {
                String denial = claim.checkPermission(player.uniqueId(), ClaimPermission.CONTAINER);
                if (denial != null) {
                    event.setCancelled(true);
                    player.sendMessage(String.format(Messages.NO_CONTAINER_PERMISSION,
                            mod.ownerName(claim)), Player.MessageType.ERROR);
                }
            }
        }
    }

    // ---- Ender Pearl Teleport ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.cause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;
        if (!config().claimsEnderPearlsRequireAccessTrust) return;

        Player player = event.player();
        PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());
        if (data.isIgnoringClaims()) return;

        Claim claim = mod.claimManager().getClaimAt(event.to());
        if (claim != null) {
            String denial = claim.checkPermission(player.uniqueId(), ClaimPermission.ACCESS);
            if (denial != null) {
                event.setCancelled(true);
                player.sendMessage(String.format(Messages.NO_ACCESS_PERMISSION,
                        mod.ownerName(claim)), Player.MessageType.ERROR);
            }
        }
    }

    // ---- Bucket Use ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucket(PlayerBucketEvent event) {
        Player player = event.player();
        PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());
        if (data.isIgnoringClaims()) return;

        Claim claim = mod.claimManager().getClaimAt(event.block().location());
        if (claim != null) {
            String denial = claim.checkPermission(player.uniqueId(), ClaimPermission.BUILD);
            if (denial != null) {
                event.setCancelled(true);
                player.sendMessage(String.format(Messages.NO_BUILD_PERMISSION,
                        mod.ownerName(claim)), Player.MessageType.ERROR);
            }
        }
    }

    // ---- Item Drop (PvP combat) ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.player();
        PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());

        if (!config().pvpAllowCombatItemDrop && data.inPvpCombat(config().pvpCombatTimeoutSeconds)) {
            event.setCancelled(true);
            player.sendMessage(Messages.PVP_NO_DROP_IN_COMBAT, Player.MessageType.ERROR);
        }
    }

    // ---- PvP Command Blocking ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandEvent event) {
        Player player = event.player();
        PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());

        if (data.inPvpCombat(config().pvpCombatTimeoutSeconds)) {
            String cmd = event.command().split(" ")[0].toLowerCase();
            for (String blocked : config().pvpBlockedCommands.split(";")) {
                if (cmd.equalsIgnoreCase(blocked.trim())) {
                    event.setCancelled(true);
                    long remaining = (config().pvpCombatTimeoutSeconds * 1000L
                            - (System.currentTimeMillis() - data.lastPvpTimestamp())) / 1000;
                    player.sendMessage(String.format(Messages.PVP_IN_COMBAT, "use that command",
                            Math.max(1, remaining)), Player.MessageType.ERROR);
                    return;
                }
            }
        }
    }

    // ---- Player Death ----

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.player();

        // Death drop protection (lock items to owner)
        boolean pvpWorld = mod.isPvpWorld(player.world().name());
        boolean protectDrops = pvpWorld ? config().protectDeathDropsPvp : config().protectDeathDropsNonPvp;

        if (protectDrops) {
            PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());
            data.setDropsUnlocked(false);
            // Tag items with owner UUID so only they can pick them up
            for (var item : event.drops()) {
                item.setData("gp_owner", player.uniqueId().toString());
            }
        }
    }

    // ---- Item Held Change ----

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.player();
        // When equipping the investigation tool, give a hint
        var item = player.itemInMainHand();
        if (item != null && item.type() == config().claimsInvestigationTool) {
            PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());
            Claim claim = mod.claimManager().getClaimAt(player.location());
            if (claim != null) {
                if (claim.isAdminClaim()) {
                    player.sendMessage(String.format(Messages.INVESTIGATE_ADMIN_CLAIM,
                            claim.width(), claim.length()), Player.MessageType.INFO);
                } else {
                    player.sendMessage(String.format(Messages.INVESTIGATE_CLAIM,
                            mod.ownerName(claim), claim.width(), claim.length()), Player.MessageType.INFO);
                }
            }
        }
    }

    // ---- Chat (spam detection) ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(PlayerChatEvent event) {
        if (!config().spamEnabled) return;

        Player player = event.player();
        PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());

        // Simple spam detection: repeated messages in short time
        long now = System.currentTimeMillis();
        if (event.message().equalsIgnoreCase(data.lastMessage())
                && (now - data.lastMessageTimestamp()) < 2000) {
            data.incrementSpamCount();
            if (data.spamCount() > 5) {
                event.setCancelled(true);
                player.sendMessage(config().spamWarningMessage, Player.MessageType.WARNING);
                return;
            }
        } else {
            data.setSpamCount(0);
        }
        data.setLastMessage(event.message());
        data.setLastMessageTimestamp(now);

        // Soft mute: hide messages from soft-muted players
        if (data.isSoftMuted()) {
            event.setCancelled(true);
            // Still let admins and other soft-muted players see it
            for (var onlinePlayer : AlloyAPI.server().onlinePlayers()) {
                if (onlinePlayer instanceof Player p) {
                    if (p.hasPermission("griefprevention.eavesdrop")
                            || mod.claimManager().getPlayerData(p.uniqueId()).isSoftMuted()) {
                        p.sendMessage("\u00a77[muted] " + player.displayName() + ": " + event.message());
                    }
                }
            }
        }
    }

    // ---- Helper: Claim Investigation ----

    private void handleInvestigation(Player player, Block block) {
        if (block == null) return;
        Claim claim = mod.claimManager().getClaimAt(block.location());

        if (claim == null) {
            PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());
            player.sendMessage(String.format(Messages.INVESTIGATE_NO_CLAIM, data.remainingClaimBlocks()),
                    Player.MessageType.INFO);
        } else {
            VisualizationType vizType = claim.isAdminClaim() ? VisualizationType.ADMIN_CLAIM : VisualizationType.CLAIM;
            ClaimVisualization.show(player, claim, vizType);

            if (claim.isAdminClaim()) {
                player.sendMessage(String.format(Messages.INVESTIGATE_ADMIN_CLAIM,
                        claim.width(), claim.length()), Player.MessageType.INFO);
            } else {
                player.sendMessage(String.format(Messages.INVESTIGATE_CLAIM,
                        mod.ownerName(claim), claim.width(), claim.length()), Player.MessageType.INFO);
            }
        }
    }

    // ---- Helper: Shovel Click (claim creation/resize) ----

    private void handleShovelClick(Player player, Block block, PlayerData data) {
        if (block == null) return;
        Location clickedLoc = block.location();

        switch (data.shovelMode()) {
            case BASIC -> handleBasicShovelClick(player, clickedLoc, data);
            case ADMIN -> handleAdminShovelClick(player, clickedLoc, data);
            case SUBDIVIDE -> handleSubdivideShovelClick(player, clickedLoc, data);
        }
    }

    private void handleBasicShovelClick(Player player, Location loc, PlayerData data) {
        if (data.lastShovelLocation() == null) {
            // First click: set corner
            data.setLastShovelLocation(loc);
            player.sendMessage("\u00a7eFirst corner set! Right-click the opposite corner to create a claim.",
                    Player.MessageType.INFO);
        } else {
            // Second click: create claim
            Location first = data.lastShovelLocation();
            if (!first.world().equals(loc.world())) {
                data.setLastShovelLocation(loc);
                player.sendMessage("\u00a7eDifferent world. Corner reset.", Player.MessageType.WARNING);
                return;
            }

            int y = Math.min(first.toBlockPosition().y(), loc.toBlockPosition().y())
                    - config().claimsExtendIntoGround;
            if (y < config().claimsMinimumY && config().claimsMinimumY != Integer.MIN_VALUE) {
                y = config().claimsMinimumY;
            }

            var result = mod.claimManager().createClaim(
                    player.uniqueId(), loc.world().name(),
                    first.toBlockPosition().x(), y, first.toBlockPosition().z(),
                    loc.toBlockPosition().x(), loc.world().maxHeight(), loc.toBlockPosition().z(),
                    null
            );

            if (result.succeeded()) {
                mod.dataStore().saveClaim(result.claim());
                player.sendMessage(Messages.CLAIM_CREATED, Player.MessageType.SUCCESS);
                ClaimVisualization.show(player, result.claim(), VisualizationType.CLAIM);
            } else {
                player.sendMessage(result.failureReason(), Player.MessageType.ERROR);
            }

            data.setLastShovelLocation(null);
        }
    }

    private void handleAdminShovelClick(Player player, Location loc, PlayerData data) {
        if (!player.hasPermission("griefprevention.adminclaims")) {
            player.sendMessage(Messages.NO_PERMISSION, Player.MessageType.ERROR);
            return;
        }

        if (data.lastShovelLocation() == null) {
            data.setLastShovelLocation(loc);
            player.sendMessage("\u00a7eAdmin claim first corner set.", Player.MessageType.INFO);
        } else {
            Location first = data.lastShovelLocation();
            if (!first.world().equals(loc.world())) {
                data.setLastShovelLocation(loc);
                player.sendMessage("\u00a7eDifferent world. Corner reset.", Player.MessageType.WARNING);
                return;
            }

            var result = mod.claimManager().createClaim(
                    null, // admin claim: no owner
                    loc.world().name(),
                    first.toBlockPosition().x(), loc.world().minHeight(), first.toBlockPosition().z(),
                    loc.toBlockPosition().x(), loc.world().maxHeight(), loc.toBlockPosition().z(),
                    null
            );

            if (result.succeeded()) {
                mod.dataStore().saveClaim(result.claim());
                player.sendMessage(Messages.CLAIM_CREATED, Player.MessageType.SUCCESS);
                ClaimVisualization.show(player, result.claim(), VisualizationType.ADMIN_CLAIM);
            } else {
                player.sendMessage(result.failureReason(), Player.MessageType.ERROR);
            }

            data.setLastShovelLocation(null);
        }
    }

    private void handleSubdivideShovelClick(Player player, Location loc, PlayerData data) {
        Claim parent = mod.claimManager().getClaimAt(loc);
        if (parent == null || parent.isSubclaim()) {
            player.sendMessage("\u00a7cYou must be inside a claim to create subdivisions.", Player.MessageType.ERROR);
            return;
        }

        String denial = parent.checkPermission(player.uniqueId(), ClaimPermission.EDIT);
        if (denial != null && !player.hasPermission("griefprevention.adminclaims")) {
            player.sendMessage(Messages.NOT_YOUR_CLAIM, Player.MessageType.ERROR);
            return;
        }

        if (data.lastShovelLocation() == null) {
            data.setLastShovelLocation(loc);
            data.setClaimSubdividing(parent);
            player.sendMessage("\u00a7eSubdivision corner set. Click the opposite corner.", Player.MessageType.INFO);
        } else {
            Location first = data.lastShovelLocation();
            var result = mod.claimManager().createClaim(
                    parent.ownerID(), parent.worldName(),
                    first.toBlockPosition().x(), parent.lesserY(), first.toBlockPosition().z(),
                    loc.toBlockPosition().x(), parent.greaterY(), loc.toBlockPosition().z(),
                    parent
            );

            if (result.succeeded()) {
                mod.dataStore().saveClaim(parent);
                player.sendMessage(Messages.CLAIM_CREATED, Player.MessageType.SUCCESS);
                ClaimVisualization.show(player, result.claim(), VisualizationType.SUBDIVISION);
            } else {
                player.sendMessage(result.failureReason(), Player.MessageType.ERROR);
            }

            data.setLastShovelLocation(null);
            data.setClaimSubdividing(null);
        }
    }
}
