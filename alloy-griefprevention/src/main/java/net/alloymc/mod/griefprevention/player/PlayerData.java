package net.alloymc.mod.griefprevention.player;

import net.alloymc.api.world.Location;
import net.alloymc.mod.griefprevention.claim.Claim;
import net.alloymc.mod.griefprevention.claim.ShovelMode;
import net.alloymc.mod.griefprevention.visualization.VisualizationType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player data tracked by the GriefPrevention mod.
 * One instance exists per known player (online or offline).
 */
public class PlayerData {

    private final UUID playerID;

    // ---- Claim blocks ----
    private int accruedClaimBlocks;
    private int bonusClaimBlocks;

    // ---- Claims owned by this player (cached) ----
    private final List<Claim> claims = new ArrayList<>();

    // ---- Shovel / claim tool state ----
    private ShovelMode shovelMode = ShovelMode.BASIC;
    private Location lastShovelLocation;
    private Claim claimResizing;
    private Claim claimSubdividing;

    // ---- PvP state ----
    private boolean pvpImmune;
    private long lastPvpTimestamp;
    private long lastPvpWarningTimestamp;

    // ---- Drop protection ----
    private boolean dropsUnlocked;

    // ---- Ignore list ----
    private final Map<UUID, Boolean> ignoredPlayers = new ConcurrentHashMap<>();

    // ---- Visualization ----
    private VisualizationType activeVisualization;

    // ---- Spam tracking ----
    private long lastMessageTimestamp;
    private String lastMessage = "";
    private int spamCount;
    private boolean softMuted;

    // ---- Anti-bot ----
    private Location loginLocation;

    // ---- Warning timestamps ----
    private long lastBuildWarningTimestamp;
    private long lastExplosivesWarningTimestamp;

    // ---- Rescue ----
    private boolean pendingRescue;
    private long lastRescueTimestamp;

    // ---- Ignore claims mode (admin) ----
    private boolean ignoringClaims;

    public PlayerData(UUID playerID) {
        this.playerID = playerID;
    }

    // ---- Identity ----

    public UUID playerID() { return playerID; }

    // ---- Claim blocks ----

    public int accruedClaimBlocks() { return accruedClaimBlocks; }
    public void setAccruedClaimBlocks(int blocks) { this.accruedClaimBlocks = blocks; }
    public void addAccruedClaimBlocks(int blocks) { this.accruedClaimBlocks += blocks; }

    public int bonusClaimBlocks() { return bonusClaimBlocks; }
    public void setBonusClaimBlocks(int blocks) { this.bonusClaimBlocks = blocks; }
    public void addBonusClaimBlocks(int blocks) { this.bonusClaimBlocks += blocks; }

    /**
     * Total available claim blocks (accrued + bonus).
     */
    public int totalClaimBlocks() {
        return accruedClaimBlocks + bonusClaimBlocks;
    }

    /**
     * Claim blocks currently in use by all owned claims.
     */
    public int claimBlocksInUse() {
        int total = 0;
        for (Claim claim : claims) {
            if (!claim.isSubclaim()) {
                total += claim.area();
            }
        }
        return total;
    }

    /**
     * Remaining claim blocks available for new claims.
     */
    public int remainingClaimBlocks() {
        return totalClaimBlocks() - claimBlocksInUse();
    }

    // ---- Claims ----

    public List<Claim> claims() { return claims; }

    // ---- Shovel state ----

    public ShovelMode shovelMode() { return shovelMode; }
    public void setShovelMode(ShovelMode mode) { this.shovelMode = mode; }

    public Location lastShovelLocation() { return lastShovelLocation; }
    public void setLastShovelLocation(Location loc) { this.lastShovelLocation = loc; }

    public Claim claimResizing() { return claimResizing; }
    public void setClaimResizing(Claim claim) { this.claimResizing = claim; }

    public Claim claimSubdividing() { return claimSubdividing; }
    public void setClaimSubdividing(Claim claim) { this.claimSubdividing = claim; }

    // ---- PvP ----

    public boolean isPvpImmune() { return pvpImmune; }
    public void setPvpImmune(boolean immune) { this.pvpImmune = immune; }

    public long lastPvpTimestamp() { return lastPvpTimestamp; }
    public void setLastPvpTimestamp(long timestamp) { this.lastPvpTimestamp = timestamp; }

    public long lastPvpWarningTimestamp() { return lastPvpWarningTimestamp; }
    public void setLastPvpWarningTimestamp(long timestamp) { this.lastPvpWarningTimestamp = timestamp; }

    /**
     * Whether this player is currently in PvP combat.
     */
    public boolean inPvpCombat(int combatTimeoutSeconds) {
        return System.currentTimeMillis() - lastPvpTimestamp < (long) combatTimeoutSeconds * 1000;
    }

    // ---- Drops ----

    public boolean areDropsUnlocked() { return dropsUnlocked; }
    public void setDropsUnlocked(boolean unlocked) { this.dropsUnlocked = unlocked; }

    // ---- Ignore ----

    public Map<UUID, Boolean> ignoredPlayers() { return ignoredPlayers; }

    public boolean isIgnoring(UUID playerID) {
        return Boolean.TRUE.equals(ignoredPlayers.get(playerID));
    }

    public void setIgnoring(UUID playerID, boolean ignoring) {
        if (ignoring) {
            ignoredPlayers.put(playerID, true);
        } else {
            ignoredPlayers.remove(playerID);
        }
    }

    // ---- Visualization ----

    public VisualizationType activeVisualization() { return activeVisualization; }
    public void setActiveVisualization(VisualizationType type) { this.activeVisualization = type; }

    // ---- Spam ----

    public long lastMessageTimestamp() { return lastMessageTimestamp; }
    public void setLastMessageTimestamp(long timestamp) { this.lastMessageTimestamp = timestamp; }

    public String lastMessage() { return lastMessage; }
    public void setLastMessage(String message) { this.lastMessage = message; }

    public int spamCount() { return spamCount; }
    public void setSpamCount(int count) { this.spamCount = count; }
    public void incrementSpamCount() { this.spamCount++; }

    public boolean isSoftMuted() { return softMuted; }
    public void setSoftMuted(boolean muted) { this.softMuted = muted; }

    // ---- Anti-bot ----

    public Location loginLocation() { return loginLocation; }
    public void setLoginLocation(Location location) { this.loginLocation = location; }

    // ---- Warnings ----

    public long lastBuildWarningTimestamp() { return lastBuildWarningTimestamp; }
    public void setLastBuildWarningTimestamp(long timestamp) { this.lastBuildWarningTimestamp = timestamp; }

    public long lastExplosivesWarningTimestamp() { return lastExplosivesWarningTimestamp; }
    public void setLastExplosivesWarningTimestamp(long timestamp) { this.lastExplosivesWarningTimestamp = timestamp; }

    // ---- Rescue ----

    public boolean isPendingRescue() { return pendingRescue; }
    public void setPendingRescue(boolean pending) { this.pendingRescue = pending; }

    public long lastRescueTimestamp() { return lastRescueTimestamp; }
    public void setLastRescueTimestamp(long timestamp) { this.lastRescueTimestamp = timestamp; }

    // ---- Ignore claims ----

    public boolean isIgnoringClaims() { return ignoringClaims; }
    public void setIgnoringClaims(boolean ignoring) { this.ignoringClaims = ignoring; }
}
