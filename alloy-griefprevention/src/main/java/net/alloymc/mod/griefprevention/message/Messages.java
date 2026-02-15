package net.alloymc.mod.griefprevention.message;

/**
 * All user-facing messages for GriefPrevention.
 * Centralized here for consistency and future localization support.
 */
public final class Messages {

    private Messages() {}

    // ---- Claims ----
    public static final String CLAIM_CREATED = "\u00a7aClaim created! Use /trust to share it with friends.";
    public static final String CLAIM_DELETED = "\u00a7aClaim deleted.";
    public static final String CLAIM_RESIZED = "\u00a7aClaim resized. You now have %d claim blocks remaining.";
    public static final String CLAIM_TOO_SMALL = "\u00a7cThis claim would be too small. The minimum is %dx%d (%d blocks area).";
    public static final String CLAIM_OVERLAPS = "\u00a7cYou can't create a claim here because it would overlap another claim.";
    public static final String CLAIM_NOT_ENOUGH_BLOCKS = "\u00a7cYou don't have enough claim blocks. You need %d more. Use /buyclaimblocks or earn more by playing.";
    public static final String CLAIM_LIMIT_REACHED = "\u00a7cYou've reached the maximum number of claims (%d). Delete a claim before creating a new one.";
    public static final String NOT_YOUR_CLAIM = "\u00a7cThis isn't your claim.";
    public static final String NO_CLAIM_HERE = "\u00a7cThere is no claim here.";
    public static final String CLAIM_ALREADY_EXISTS = "\u00a7cThere's already a claim here.";
    public static final String ABANDON_SUCCESS = "\u00a7aClaim abandoned. You now have %d claim blocks.";
    public static final String ABANDON_ALL_SUCCESS = "\u00a7aAll your claims have been abandoned. You now have %d claim blocks.";
    public static final String ABANDON_ALL_CONFIRM = "\u00a7eAre you sure? This will delete ALL your claims. Use /abandonallclaims confirm";
    public static final String ALL_CLAIMS_DELETED = "\u00a7aDeleted all claims for %s.";

    // ---- Trust ----
    public static final String TRUST_GRANTED = "\u00a7aGranted %s %s trust in %s.";
    public static final String TRUST_REVOKED = "\u00a7aRevoked %s's trust from %s.";
    public static final String TRUST_ALL_REVOKED = "\u00a7aRevoked all permissions from %s.";
    public static final String TRUST_LIST_HEADER = "\u00a7eTrust list for this claim:";
    public static final String TRUST_LIST_MANAGERS = "\u00a76> Managers: %s";
    public static final String TRUST_LIST_BUILDERS = "\u00a7e> Builders: %s";
    public static final String TRUST_LIST_CONTAINERS = "\u00a7a> Containers: %s";
    public static final String TRUST_LIST_ACCESSORS = "\u00a79> Accessors: %s";
    public static final String PLAYER_NOT_FOUND = "\u00a7cPlayer not found.";

    // ---- Permissions (denial) ----
    public static final String NO_BUILD_PERMISSION = "\u00a7cYou don't have %s's permission to build here.";
    public static final String NO_CONTAINER_PERMISSION = "\u00a7cYou don't have %s's permission to use that.";
    public static final String NO_ACCESS_PERMISSION = "\u00a7cYou don't have %s's permission to use that.";
    public static final String NO_EDIT_PERMISSION = "\u00a7cOnly the claim owner can do that.";
    public static final String NO_MANAGE_PERMISSION = "\u00a7cYou don't have permission to manage this claim.";

    // ---- PvP ----
    public static final String PVP_IMMUNE = "\u00a7aYou have PvP immunity as a new spawn. Pick up an item to become vulnerable.";
    public static final String PVP_NO_COMBAT_IN_CLAIM = "\u00a7cPvP is not allowed in this claim.";
    public static final String PVP_IN_COMBAT = "\u00a7cYou are in PvP combat! You cannot %s for %d more seconds.";
    public static final String PVP_COMBAT_LOGOUT = "\u00a7c%s logged out during PvP combat.";
    public static final String PVP_NO_DROP_IN_COMBAT = "\u00a7cYou can't drop items during PvP combat.";

    // ---- Shovel modes ----
    public static final String SHOVEL_BASIC = "\u00a7aShovel set to basic claims mode. Right-click two corners to create a claim.";
    public static final String SHOVEL_ADMIN = "\u00a7eShovel set to admin claims mode.";
    public static final String SHOVEL_SUBDIVIDE = "\u00a7eShovel set to subdivide mode. Click inside a claim to create subdivisions.";

    // ---- Admin ----
    public static final String IGNORE_CLAIMS_ON = "\u00a7eNow ignoring claims.";
    public static final String IGNORE_CLAIMS_OFF = "\u00a7aNow respecting claims.";
    public static final String EXPLOSIVES_ENABLED = "\u00a7aExplosives are now enabled in this claim.";
    public static final String EXPLOSIVES_DISABLED = "\u00a7aExplosives are now disabled in this claim.";
    public static final String CLAIM_TRANSFERRED = "\u00a7aClaim transferred to %s.";
    public static final String BONUS_BLOCKS_ADJUSTED = "\u00a7aAdjusted %s's bonus claim blocks by %d. New total: %d";
    public static final String ACCRUED_BLOCKS_SET = "\u00a7aSet %s's accrued claim blocks to %d.";
    public static final String SUBCLAIM_RESTRICTED = "\u00a7aThis subclaim is now restricted (inherits no parent permissions).";
    public static final String SUBCLAIM_UNRESTRICTED = "\u00a7aThis subclaim now inherits parent permissions.";

    // ---- Claims list ----
    public static final String CLAIMS_LIST_HEADER = "\u00a7e%s's Claims (%d/%d blocks used):";
    public static final String CLAIMS_LIST_ENTRY = "\u00a7a  [%d] %s: (%d, %d) to (%d, %d) = %d blocks";
    public static final String CLAIMS_LIST_EMPTY = "\u00a77No claims.";

    // ---- Trapped ----
    public static final String TRAPPED_PENDING = "\u00a7eStand still for 10 seconds to be rescued...";
    public static final String TRAPPED_RESCUED = "\u00a7aYou have been rescued!";
    public static final String TRAPPED_MOVED = "\u00a7cRescue cancelled because you moved.";
    public static final String TRAPPED_COOLDOWN = "\u00a7cYou must wait before using /trapped again.";

    // ---- Chat ----
    public static final String PLAYER_IGNORED = "\u00a7aYou are now ignoring %s.";
    public static final String PLAYER_UNIGNORED = "\u00a7aYou are no longer ignoring %s.";
    public static final String SOFT_MUTE_ON = "\u00a7e%s has been soft-muted.";
    public static final String SOFT_MUTE_OFF = "\u00a7a%s has been un-muted.";

    // ---- Drops ----
    public static final String DROPS_UNLOCKED = "\u00a7aYour death drops are now unlocked. Anyone can pick them up.";

    // ---- Investigation ----
    public static final String INVESTIGATE_CLAIM = "\u00a7aThis is %s's claim. Size: %dx%d";
    public static final String INVESTIGATE_NO_CLAIM = "\u00a77No claim here. You have %d claim blocks available.";
    public static final String INVESTIGATE_ADMIN_CLAIM = "\u00a7eThis is an admin claim. Size: %dx%d";

    // ---- Errors ----
    public static final String PLAYER_ONLY = "\u00a7cThis command can only be used by players.";
    public static final String NO_PERMISSION = "\u00a7cYou don't have permission to use that command.";
    public static final String USAGE = "\u00a7cUsage: %s";

    // ---- Reload ----
    public static final String CONFIG_RELOADED = "\u00a7aGriefPrevention configuration reloaded.";
}
