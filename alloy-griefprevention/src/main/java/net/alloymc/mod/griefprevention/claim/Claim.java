package net.alloymc.mod.griefprevention.claim;

import net.alloymc.api.world.Location;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a land claim — a protected rectangular region in a world.
 *
 * Claims are defined by two corners (lesser and greater) and extend from
 * the claim's minimum Y to the world height. They can have subdivisions
 * (child claims) and a hierarchical permission system.
 */
public class Claim {

    private long id;
    private UUID ownerID; // null for admin claims
    private String worldName;

    // Boundaries (block coordinates, inclusive)
    private int lesserX;
    private int lesserY;
    private int lesserZ;
    private int greaterX;
    private int greaterY;
    private int greaterZ;

    // Permissions: player UUID string (or "public") -> permission level
    private final Map<String, ClaimPermission> permissions = new HashMap<>();
    private final List<String> managers = new ArrayList<>();

    // Hierarchy
    private Claim parent;
    private final List<Claim> children = new ArrayList<>();

    // State
    private boolean explosivesAllowed;
    private boolean inheritNothing; // for subclaims: if true, don't inherit parent permissions
    private Instant modifiedDate;

    public Claim() {
        this.modifiedDate = Instant.now();
    }

    public Claim(long id, UUID ownerID, String worldName,
                 int x1, int y1, int z1, int x2, int y2, int z2,
                 Claim parent) {
        this.id = id;
        this.ownerID = ownerID;
        this.worldName = worldName;
        this.lesserX = Math.min(x1, x2);
        this.lesserY = Math.min(y1, y2);
        this.lesserZ = Math.min(z1, z2);
        this.greaterX = Math.max(x1, x2);
        this.greaterY = Math.max(y1, y2);
        this.greaterZ = Math.max(z1, z2);
        this.parent = parent;
        this.modifiedDate = Instant.now();
    }

    // ---- Identity ----

    public long id() { return id; }
    public void setId(long id) { this.id = id; }

    public UUID ownerID() { return ownerID; }
    public void setOwnerID(UUID ownerID) { this.ownerID = ownerID; }

    public boolean isAdminClaim() { return ownerID == null; }

    public String worldName() { return worldName; }
    public void setWorldName(String worldName) { this.worldName = worldName; }

    // ---- Boundaries ----

    public int lesserX() { return lesserX; }
    public int lesserY() { return lesserY; }
    public int lesserZ() { return lesserZ; }
    public int greaterX() { return greaterX; }
    public int greaterY() { return greaterY; }
    public int greaterZ() { return greaterZ; }

    public void setBounds(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.lesserX = Math.min(x1, x2);
        this.lesserY = Math.min(y1, y2);
        this.lesserZ = Math.min(z1, z2);
        this.greaterX = Math.max(x1, x2);
        this.greaterY = Math.max(y1, y2);
        this.greaterZ = Math.max(z1, z2);
        this.modifiedDate = Instant.now();
    }

    public int width() { return greaterX - lesserX + 1; }
    public int height() { return greaterY - lesserY + 1; }
    public int length() { return greaterZ - lesserZ + 1; }
    public int area() { return width() * length(); }

    /**
     * Returns true if the given block coordinate is inside this claim's boundaries.
     */
    public boolean contains(int x, int y, int z, boolean ignoreHeight) {
        if (x < lesserX || x > greaterX) return false;
        if (z < lesserZ || z > greaterZ) return false;
        if (!ignoreHeight && (y < lesserY || y > greaterY)) return false;
        return true;
    }

    public boolean contains(Location location, boolean ignoreHeight) {
        if (!location.world().name().equals(worldName)) return false;
        return contains(
                (int) Math.floor(location.x()),
                (int) Math.floor(location.y()),
                (int) Math.floor(location.z()),
                ignoreHeight
        );
    }

    public boolean contains(Location location) {
        return contains(location, true);
    }

    /**
     * Returns true if this claim overlaps with another claim.
     */
    public boolean overlaps(Claim other) {
        if (!worldName.equals(other.worldName)) return false;
        return lesserX <= other.greaterX && greaterX >= other.lesserX
                && lesserZ <= other.greaterZ && greaterZ >= other.lesserZ;
    }

    // ---- Permissions ----

    /**
     * Sets a player's permission level in this claim.
     *
     * @param playerID the player UUID string, or "public" for all players
     * @param level    the permission level to grant
     */
    public void setPermission(String playerID, ClaimPermission level) {
        permissions.put(playerID.toLowerCase(), level);
        modifiedDate = Instant.now();
    }

    /**
     * Removes a player's explicit permission from this claim.
     */
    public void removePermission(String playerID) {
        permissions.remove(playerID.toLowerCase());
        modifiedDate = Instant.now();
    }

    /**
     * Clears all permissions from this claim.
     */
    public void clearPermissions() {
        permissions.clear();
        modifiedDate = Instant.now();
    }

    /**
     * Returns the permission map (unmodifiable view for display).
     */
    public Map<String, ClaimPermission> permissions() {
        return Map.copyOf(permissions);
    }

    /**
     * Check whether a player has (at least) the required permission in this claim.
     *
     * @param playerID   the player's UUID
     * @param required   the minimum permission needed
     * @return null if allowed, or a denial message string if denied
     */
    public String checkPermission(UUID playerID, ClaimPermission required) {
        // Owner always has EDIT (highest)
        if (playerID != null && playerID.equals(ownerID)) {
            return null;
        }

        // Check explicit permission for this player
        ClaimPermission playerPerm = permissions.get(playerID != null ? playerID.toString().toLowerCase() : null);
        if (playerPerm != null && required.isGrantedBy(playerPerm)) {
            return null;
        }

        // Check "public" permission
        ClaimPermission publicPerm = permissions.get("public");
        if (publicPerm != null && required.isGrantedBy(publicPerm)) {
            return null;
        }

        // Check managers
        if (playerID != null && managers.contains(playerID.toString().toLowerCase())) {
            if (required.isGrantedBy(ClaimPermission.MANAGE)) {
                return null;
            }
        }

        // If this is a subclaim and doesn't restrict inheritance, check parent
        if (parent != null && !inheritNothing) {
            return parent.checkPermission(playerID, required);
        }

        return required.denialMessage();
    }

    public boolean hasPermission(UUID playerID, ClaimPermission required) {
        return checkPermission(playerID, required) == null;
    }

    // ---- Managers ----

    public List<String> managers() { return managers; }

    public void addManager(String playerID) {
        String lower = playerID.toLowerCase();
        if (!managers.contains(lower)) {
            managers.add(lower);
            modifiedDate = Instant.now();
        }
    }

    public void removeManager(String playerID) {
        managers.remove(playerID.toLowerCase());
        modifiedDate = Instant.now();
    }

    // ---- Hierarchy ----

    public Claim parent() { return parent; }
    public void setParent(Claim parent) { this.parent = parent; }

    public List<Claim> children() { return children; }

    public boolean isSubclaim() { return parent != null; }

    public boolean canContainSubclaims() {
        // Subclaims cannot be further subdivided
        return !isSubclaim();
    }

    // ---- State ----

    public boolean areExplosivesAllowed() { return explosivesAllowed; }
    public void setExplosivesAllowed(boolean allowed) {
        this.explosivesAllowed = allowed;
        modifiedDate = Instant.now();
    }

    public boolean inheritNothing() { return inheritNothing; }
    public void setInheritNothing(boolean inheritNothing) {
        this.inheritNothing = inheritNothing;
        modifiedDate = Instant.now();
    }

    public Instant modifiedDate() { return modifiedDate; }
    public void setModifiedDate(Instant date) { this.modifiedDate = date; }

    // ---- Chunk keys ----

    /**
     * Returns all chunk keys (packed long) that this claim spans.
     */
    public List<Long> chunkKeys() {
        List<Long> keys = new ArrayList<>();
        int minChunkX = lesserX >> 4;
        int maxChunkX = greaterX >> 4;
        int minChunkZ = lesserZ >> 4;
        int maxChunkZ = greaterZ >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                keys.add(((long) cx << 32) | (cz & 0xFFFFFFFFL));
            }
        }
        return keys;
    }

    @Override
    public String toString() {
        return "Claim{id=" + id + ", owner=" + ownerID + ", world=" + worldName
                + ", bounds=[" + lesserX + "," + lesserY + "," + lesserZ
                + " -> " + greaterX + "," + greaterY + "," + greaterZ + "]}";
    }
}
