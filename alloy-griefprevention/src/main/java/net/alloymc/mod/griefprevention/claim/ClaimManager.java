package net.alloymc.mod.griefprevention.claim;

import net.alloymc.api.world.Location;
import net.alloymc.mod.griefprevention.config.GriefPreventionConfig;
import net.alloymc.mod.griefprevention.player.PlayerData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages all claims in memory. Provides creation, deletion, lookup, and spatial queries.
 * Thread-safe.
 */
public class ClaimManager {

    private final GriefPreventionConfig config;

    // All top-level claims
    private final List<Claim> claims = Collections.synchronizedList(new ArrayList<>());

    // Indexed by claim ID
    private final Map<Long, Claim> claimById = new ConcurrentHashMap<>();

    // Spatial index: chunk key -> claims in that chunk
    private final Map<Long, List<Claim>> chunkIndex = new ConcurrentHashMap<>();

    // Player data cache
    private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();

    // Auto-incrementing claim ID
    private final AtomicLong nextClaimId = new AtomicLong(1);

    public ClaimManager(GriefPreventionConfig config) {
        this.config = config;
    }

    // ---- Claim creation ----

    /**
     * Create a new claim. Validates boundaries, overlap, permissions, and claim block budget.
     */
    public CreateClaimResult createClaim(UUID ownerID, String worldName,
                                          int x1, int y1, int z1,
                                          int x2, int y2, int z2,
                                          Claim parent) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        int width = maxX - minX + 1;
        int length = maxZ - minZ + 1;
        int area = width * length;

        // Size checks (skip for admin claims and subclaims)
        if (ownerID != null && parent == null) {
            if (width < config.claimsMinWidth || length < config.claimsMinWidth) {
                return CreateClaimResult.failure(String.format(
                        "Claim too narrow. Minimum width: %d blocks.", config.claimsMinWidth));
            }
            if (area < config.claimsMinArea) {
                return CreateClaimResult.failure(String.format(
                        "Claim too small. Minimum area: %d blocks.", config.claimsMinArea));
            }

            // Check claim count limit
            if (config.claimsMaxPerPlayer > 0) {
                PlayerData data = getPlayerData(ownerID);
                long count = data.claims().stream().filter(c -> !c.isSubclaim()).count();
                if (count >= config.claimsMaxPerPlayer) {
                    return CreateClaimResult.failure(String.format(
                            "You've reached the maximum number of claims (%d).", config.claimsMaxPerPlayer));
                }
            }

            // Check claim blocks
            PlayerData data = getPlayerData(ownerID);
            int remaining = data.remainingClaimBlocks();
            if (area > remaining) {
                return CreateClaimResult.failure(String.format(
                        "Not enough claim blocks. You need %d more.", area - remaining));
            }
        }

        // Check overlap (skip for subclaims — they live inside parent)
        if (parent == null) {
            Claim tempClaim = new Claim(0, ownerID, worldName, x1, y1, z1, x2, y2, z2, null);
            for (Claim existing : claims) {
                if (existing.overlaps(tempClaim)) {
                    return CreateClaimResult.failure("This claim would overlap an existing claim.");
                }
            }
        }

        // Create the claim
        long id = nextClaimId.getAndIncrement();
        Claim claim = new Claim(id, ownerID, worldName, x1, y1, z1, x2, y2, z2, parent);

        // Register
        if (parent != null) {
            parent.children().add(claim);
        } else {
            claims.add(claim);
            indexClaim(claim);
        }
        claimById.put(id, claim);

        // Associate with player
        if (ownerID != null) {
            getPlayerData(ownerID).claims().add(claim);
        }

        return CreateClaimResult.success(claim);
    }

    // ---- Claim deletion ----

    /**
     * Delete a claim and all its subclaims.
     */
    public void deleteClaim(Claim claim) {
        // Remove subclaims first
        for (Claim child : new ArrayList<>(claim.children())) {
            claimById.remove(child.id());
            if (child.ownerID() != null) {
                PlayerData data = playerDataMap.get(child.ownerID());
                if (data != null) data.claims().remove(child);
            }
        }
        claim.children().clear();

        // Remove from parent or top-level list
        if (claim.parent() != null) {
            claim.parent().children().remove(claim);
        } else {
            claims.remove(claim);
            unindexClaim(claim);
        }

        claimById.remove(claim.id());

        // Remove from player data
        if (claim.ownerID() != null) {
            PlayerData data = playerDataMap.get(claim.ownerID());
            if (data != null) data.claims().remove(claim);
        }
    }

    /**
     * Delete all claims owned by a player.
     */
    public void deleteAllClaims(UUID ownerID) {
        PlayerData data = playerDataMap.get(ownerID);
        if (data == null) return;
        for (Claim claim : new ArrayList<>(data.claims())) {
            deleteClaim(claim);
        }
    }

    // ---- Claim lookup ----

    /**
     * Find the claim at a given location (checks subclaims first, then parent).
     */
    public Claim getClaimAt(Location location) {
        int x = (int) Math.floor(location.x());
        int y = (int) Math.floor(location.y());
        int z = (int) Math.floor(location.z());
        String worldName = location.world().name();
        long chunkKey = ((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL);

        List<Claim> chunkedClaims = chunkIndex.get(chunkKey);
        if (chunkedClaims == null) return null;

        for (Claim claim : chunkedClaims) {
            if (!claim.worldName().equals(worldName)) continue;
            if (!claim.contains(x, y, z, true)) continue;

            // Check subclaims first (more specific)
            for (Claim child : claim.children()) {
                if (child.contains(x, y, z, true)) {
                    return child;
                }
            }
            return claim;
        }
        return null;
    }

    public Claim getClaimById(long id) {
        return claimById.get(id);
    }

    public List<Claim> allClaims() {
        return Collections.unmodifiableList(claims);
    }

    public Collection<Claim> getClaimsForPlayer(UUID ownerID) {
        PlayerData data = playerDataMap.get(ownerID);
        if (data == null) return Collections.emptyList();
        return Collections.unmodifiableList(data.claims());
    }

    /**
     * Resize a claim to new boundaries.
     */
    public CreateClaimResult resizeClaim(Claim claim, int x1, int y1, int z1, int x2, int y2, int z2) {
        int oldArea = claim.area();
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        int newWidth = maxX - minX + 1;
        int newLength = maxZ - minZ + 1;
        int newArea = newWidth * newLength;

        // Size checks
        if (claim.ownerID() != null && !claim.isSubclaim()) {
            if (newWidth < config.claimsMinWidth || newLength < config.claimsMinWidth) {
                return CreateClaimResult.failure("Claim too narrow.");
            }
            if (newArea < config.claimsMinArea) {
                return CreateClaimResult.failure("Claim too small.");
            }

            // Check claim blocks for the additional area
            int additionalArea = newArea - oldArea;
            if (additionalArea > 0) {
                PlayerData data = getPlayerData(claim.ownerID());
                if (additionalArea > data.remainingClaimBlocks()) {
                    return CreateClaimResult.failure(String.format(
                            "Not enough claim blocks. You need %d more.",
                            additionalArea - data.remainingClaimBlocks()));
                }
            }
        }

        // Overlap check (excluding self)
        if (!claim.isSubclaim()) {
            Claim tempClaim = new Claim(0, claim.ownerID(), claim.worldName(), x1, y1, z1, x2, y2, z2, null);
            for (Claim existing : claims) {
                if (existing == claim) continue;
                if (existing.overlaps(tempClaim)) {
                    return CreateClaimResult.failure("Resizing would overlap an existing claim.");
                }
            }

            // Re-index
            unindexClaim(claim);
            claim.setBounds(x1, y1, z1, x2, y2, z2);
            indexClaim(claim);
        } else {
            claim.setBounds(x1, y1, z1, x2, y2, z2);
        }

        return CreateClaimResult.success(claim);
    }

    // ---- Player data ----

    public PlayerData getPlayerData(UUID playerID) {
        return playerDataMap.computeIfAbsent(playerID, PlayerData::new);
    }

    public Map<UUID, PlayerData> allPlayerData() {
        return Collections.unmodifiableMap(playerDataMap);
    }

    // ---- Spatial index ----

    private void indexClaim(Claim claim) {
        for (long key : claim.chunkKeys()) {
            chunkIndex.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>())).add(claim);
        }
    }

    private void unindexClaim(Claim claim) {
        for (long key : claim.chunkKeys()) {
            List<Claim> list = chunkIndex.get(key);
            if (list != null) {
                list.remove(claim);
                if (list.isEmpty()) chunkIndex.remove(key);
            }
        }
    }

    // ---- Persistence hooks ----

    public AtomicLong nextClaimIdRef() { return nextClaimId; }

    /**
     * Add a claim directly (used when loading from disk). Skips validation.
     */
    public void addLoadedClaim(Claim claim) {
        if (claim.parent() == null) {
            claims.add(claim);
            indexClaim(claim);
        }
        claimById.put(claim.id(), claim);
        if (claim.ownerID() != null) {
            getPlayerData(claim.ownerID()).claims().add(claim);
        }
    }
}
