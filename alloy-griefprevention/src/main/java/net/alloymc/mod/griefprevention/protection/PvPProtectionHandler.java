package net.alloymc.mod.griefprevention.protection;

import net.alloymc.api.entity.Entity;
import net.alloymc.api.entity.Player;
import net.alloymc.api.entity.Projectile;
import net.alloymc.api.event.EventHandler;
import net.alloymc.api.event.EventPriority;
import net.alloymc.api.event.Listener;
import net.alloymc.api.event.entity.EntityDamageByEntityEvent;
import net.alloymc.api.event.entity.EntityDamageEvent;
import net.alloymc.mod.griefprevention.GriefPreventionMod;
import net.alloymc.mod.griefprevention.claim.Claim;
import net.alloymc.mod.griefprevention.config.GriefPreventionConfig;
import net.alloymc.mod.griefprevention.message.Messages;
import net.alloymc.mod.griefprevention.player.PlayerData;

/**
 * Handles PvP-specific protections: fresh spawn immunity, safe zone enforcement,
 * combat logging.
 */
public class PvPProtectionHandler implements Listener {

    private final GriefPreventionMod mod;

    public PvPProtectionHandler(GriefPreventionMod mod) {
        this.mod = mod;
    }

    private GriefPreventionConfig config() { return mod.config(); }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPvP(EntityDamageByEntityEvent event) {
        if (!(event.entity() instanceof Player victim)) return;

        // Resolve attacker
        Player attacker = resolveAttacker(event.damager());
        if (attacker == null) return;
        if (attacker.equals(victim)) return; // Self-damage

        // Check if PvP rules apply in this world
        if (!mod.isPvpWorld(victim.world().name())) return;

        PlayerData victimData = mod.claimManager().getPlayerData(victim.uniqueId());
        PlayerData attackerData = mod.claimManager().getPlayerData(attacker.uniqueId());

        // Fresh spawn immunity
        if (config().pvpProtectFreshSpawns) {
            if (victimData.isPvpImmune()) {
                event.setCancelled(true);
                attacker.sendMessage(Messages.PVP_IMMUNE, Player.MessageType.WARNING);
                return;
            }
            if (attackerData.isPvpImmune()) {
                event.setCancelled(true);
                attacker.sendMessage(Messages.PVP_IMMUNE, Player.MessageType.WARNING);
                return;
            }
        }

        // Safe zone protection (player claims)
        if (config().pvpProtectPlayersInPlayerClaims) {
            Claim victimClaim = mod.claimManager().getClaimAt(victim.location());
            if (victimClaim != null && !victimClaim.isAdminClaim()) {
                event.setCancelled(true);
                attacker.sendMessage(Messages.PVP_NO_COMBAT_IN_CLAIM, Player.MessageType.ERROR);
                return;
            }

            Claim attackerClaim = mod.claimManager().getClaimAt(attacker.location());
            if (attackerClaim != null && !attackerClaim.isAdminClaim()) {
                event.setCancelled(true);
                attacker.sendMessage(Messages.PVP_NO_COMBAT_IN_CLAIM, Player.MessageType.ERROR);
                return;
            }
        }

        // Safe zone protection (admin claims)
        if (config().pvpProtectPlayersInAdminClaims) {
            Claim victimClaim = mod.claimManager().getClaimAt(victim.location());
            if (victimClaim != null && victimClaim.isAdminClaim()) {
                event.setCancelled(true);
                attacker.sendMessage(Messages.PVP_NO_COMBAT_IN_CLAIM, Player.MessageType.ERROR);
                return;
            }

            Claim attackerClaim = mod.claimManager().getClaimAt(attacker.location());
            if (attackerClaim != null && attackerClaim.isAdminClaim()) {
                event.setCancelled(true);
                attacker.sendMessage(Messages.PVP_NO_COMBAT_IN_CLAIM, Player.MessageType.ERROR);
                return;
            }
        }
    }

    /**
     * Track combat engagement (MONITOR priority — after all cancellation logic).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvPMonitor(EntityDamageByEntityEvent event) {
        if (!(event.entity() instanceof Player victim)) return;

        Player attacker = resolveAttacker(event.damager());
        if (attacker == null || attacker.equals(victim)) return;
        if (!mod.isPvpWorld(victim.world().name())) return;

        // Mark both players as in PvP combat
        long now = System.currentTimeMillis();
        mod.claimManager().getPlayerData(victim.uniqueId()).setLastPvpTimestamp(now);
        mod.claimManager().getPlayerData(attacker.uniqueId()).setLastPvpTimestamp(now);

        // Remove PvP immunity
        mod.claimManager().getPlayerData(victim.uniqueId()).setPvpImmune(false);
        mod.claimManager().getPlayerData(attacker.uniqueId()).setPvpImmune(false);
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.shooter() instanceof Player p) return p;
        return null;
    }
}
