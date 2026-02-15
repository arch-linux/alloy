package net.alloymc.mod.griefprevention.task;

import net.alloymc.api.AlloyAPI;
import net.alloymc.mod.griefprevention.GriefPreventionMod;
import net.alloymc.mod.griefprevention.claim.Claim;
import net.alloymc.mod.griefprevention.config.GriefPreventionConfig;
import net.alloymc.mod.griefprevention.player.PlayerData;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Periodically checks for expired claims and removes them.
 * Runs every 60 seconds.
 */
public class ClaimCleanupTask implements Runnable {

    private static final Logger LOGGER = Logger.getLogger("GriefPrevention.Cleanup");
    private final GriefPreventionMod mod;

    public ClaimCleanupTask(GriefPreventionMod mod) {
        this.mod = mod;
    }

    @Override
    public void run() {
        GriefPreventionConfig config = mod.config();
        if (config.claimsExpirationDaysInactive <= 0) return;

        Instant now = Instant.now();
        Duration maxInactive = Duration.ofDays(config.claimsExpirationDaysInactive);

        List<Claim> toDelete = new ArrayList<>();

        for (Claim claim : mod.claimManager().allClaims()) {
            if (claim.isAdminClaim()) continue;
            if (claim.ownerID() == null) continue;

            // Check if owner is online (skip active players)
            var opt = AlloyAPI.server().player(claim.ownerID());
            if (opt.isPresent()) continue;

            // Check exemptions
            PlayerData data = mod.claimManager().getPlayerData(claim.ownerID());
            if (data.totalClaimBlocks() >= config.claimsExpirationExemptTotalBlocks) continue;
            if (data.bonusClaimBlocks() >= config.claimsExpirationExemptBonusBlocks) continue;

            // Check if claim has expired
            Duration sinceModified = Duration.between(claim.modifiedDate(), now);
            if (sinceModified.compareTo(maxInactive) > 0) {
                toDelete.add(claim);
            }
        }

        for (Claim claim : toDelete) {
            LOGGER.info("Expiring claim " + claim.id() + " (owner: " + claim.ownerID()
                    + ", inactive for " + Duration.between(claim.modifiedDate(), now).toDays() + " days)");
            mod.dataStore().deleteClaim(claim);
            mod.claimManager().deleteClaim(claim);
        }
    }
}
