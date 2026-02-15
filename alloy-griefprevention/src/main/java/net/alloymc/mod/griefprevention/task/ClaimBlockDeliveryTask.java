package net.alloymc.mod.griefprevention.task;

import net.alloymc.api.AlloyAPI;
import net.alloymc.api.entity.Player;
import net.alloymc.mod.griefprevention.GriefPreventionMod;
import net.alloymc.mod.griefprevention.config.GriefPreventionConfig;
import net.alloymc.mod.griefprevention.player.PlayerData;

/**
 * Delivers claim blocks to online players at a configured hourly rate.
 * Runs every 5 minutes (6000 ticks) and awards a proportional amount.
 */
public class ClaimBlockDeliveryTask implements Runnable {

    private final GriefPreventionMod mod;

    public ClaimBlockDeliveryTask(GriefPreventionMod mod) {
        this.mod = mod;
    }

    @Override
    public void run() {
        GriefPreventionConfig config = mod.config();
        // 5 minutes = 1/12 of an hour
        int blocksPerDelivery = Math.max(1, config.claimsBlocksAccruedPerHour / 12);

        for (var entity : AlloyAPI.server().onlinePlayers()) {
            if (entity instanceof Player player) {
                PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());

                if (data.accruedClaimBlocks() < config.claimsMaxAccruedBlocks) {
                    int newTotal = Math.min(
                            data.accruedClaimBlocks() + blocksPerDelivery,
                            config.claimsMaxAccruedBlocks
                    );
                    data.setAccruedClaimBlocks(newTotal);
                }
            }
        }
    }
}
