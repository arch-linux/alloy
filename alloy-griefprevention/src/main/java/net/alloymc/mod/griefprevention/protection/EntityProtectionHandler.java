package net.alloymc.mod.griefprevention.protection;

import net.alloymc.api.block.Block;
import net.alloymc.api.entity.Entity;
import net.alloymc.api.entity.EntityType;
import net.alloymc.api.entity.LivingEntity;
import net.alloymc.api.entity.Player;
import net.alloymc.api.entity.Projectile;
import net.alloymc.api.entity.TameableEntity;
import net.alloymc.api.event.EventHandler;
import net.alloymc.api.event.EventPriority;
import net.alloymc.api.event.Listener;
import net.alloymc.api.event.entity.EntityChangeBlockEvent;
import net.alloymc.api.event.entity.EntityDamageByEntityEvent;
import net.alloymc.api.event.entity.EntityDamageEvent;
import net.alloymc.api.event.entity.EntityExplodeEvent;
import net.alloymc.api.event.entity.EntityInteractEvent;
import net.alloymc.api.event.entity.EntitySpawnEvent;
import net.alloymc.api.event.entity.HangingBreakEvent;
import net.alloymc.api.event.entity.HangingPlaceEvent;
import net.alloymc.api.event.entity.VehicleDamageEvent;
import net.alloymc.api.inventory.Material;
import net.alloymc.mod.griefprevention.GriefPreventionMod;
import net.alloymc.mod.griefprevention.claim.Claim;
import net.alloymc.mod.griefprevention.claim.ClaimPermission;
import net.alloymc.mod.griefprevention.claim.ClaimsMode;
import net.alloymc.mod.griefprevention.config.GriefPreventionConfig;
import net.alloymc.mod.griefprevention.message.Messages;
import net.alloymc.mod.griefprevention.player.PlayerData;

import java.util.Iterator;

/**
 * Handles entity-related protection: damage, block changes, explosions, spawning.
 */
public class EntityProtectionHandler implements Listener {

    private final GriefPreventionMod mod;

    public EntityProtectionHandler(GriefPreventionMod mod) {
        this.mod = mod;
    }

    private GriefPreventionConfig config() { return mod.config(); }

    // ---- Entity Change Block (enderman, sheep, etc.) ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        EntityType type = event.entity().type();

        // Enderman block pickup
        if (type == EntityType.ENDERMAN && !config().endermenMoveBlocks) {
            event.setCancelled(true);
            return;
        }

        // Silverfish block breaking
        if (type == EntityType.SILVERFISH && !config().silverfishBreakBlocks) {
            event.setCancelled(true);
            return;
        }

        // Rabbit crop eating
        if (type == EntityType.RABBIT && !config().rabbitsEatCrops) {
            event.setCancelled(true);
            return;
        }

        // Ravager block breaking
        if (type == EntityType.RAVAGER && !config().claimsRavagersBreakBlocks) {
            Claim claim = mod.claimManager().getClaimAt(event.block().location());
            if (claim != null) {
                event.setCancelled(true);
                return;
            }
        }

        // Farmland trampling
        if (event.block().type() == Material.FARMLAND && !config().creaturesTrampleCrops) {
            if (type != EntityType.PLAYER) {
                event.setCancelled(true);
                return;
            }
        }

        // Player-caused block changes require Build permission
        if (type == EntityType.PLAYER && event.entity() instanceof Player player) {
            PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());
            if (!data.isIgnoringClaims()) {
                Claim claim = mod.claimManager().getClaimAt(event.block().location());
                if (claim != null) {
                    String denial = claim.checkPermission(player.uniqueId(), ClaimPermission.BUILD);
                    if (denial != null) {
                        event.setCancelled(true);
                        player.sendMessage(String.format(Messages.NO_BUILD_PERMISSION, mod.ownerName(claim)),
                                Player.MessageType.ERROR);
                    }
                }
            }
        }
    }

    // ---- Entity Interact (farmland trampling) ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityInteract(EntityInteractEvent event) {
        if (event.block().type() == Material.FARMLAND && !config().creaturesTrampleCrops) {
            event.setCancelled(true);
        }
    }

    // ---- Entity Damage ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity victim = event.entity();

        // Never protect hostile mobs
        if (victim.type().isHostile()) return;

        // Protect tamed animals from environmental damage in claims
        if (victim instanceof TameableEntity tamed && tamed.isTamed()) {
            EntityDamageEvent.DamageCause cause = event.cause();
            if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                    || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                    || cause == EntityDamageEvent.DamageCause.FIRE
                    || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                    || cause == EntityDamageEvent.DamageCause.LAVA
                    || cause == EntityDamageEvent.DamageCause.SUFFOCATION
                    || cause == EntityDamageEvent.DamageCause.DROWNING
                    || cause == EntityDamageEvent.DamageCause.FALLING_BLOCK) {
                Claim claim = mod.claimManager().getClaimAt(victim.location());
                if (claim != null) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity victim = event.entity();
        Entity damager = event.damager();

        // Never protect hostile mobs
        if (victim.type().isHostile()) return;

        // Resolve the actual player behind projectiles
        Player attackingPlayer = null;
        if (damager instanceof Player p) {
            attackingPlayer = p;
        } else if (damager instanceof Projectile proj && proj.shooter() instanceof Player p) {
            attackingPlayer = p;
        }

        // ---- Item Frames, Armor Stands, Paintings ----
        if (victim.type() == EntityType.ITEM_FRAME || victim.type() == EntityType.GLOW_ITEM_FRAME
                || victim.type() == EntityType.ARMOR_STAND || victim.type() == EntityType.PAINTING
                || victim.type() == EntityType.ITEM_DISPLAY || victim.type() == EntityType.END_CRYSTAL) {

            if (attackingPlayer != null) {
                PlayerData data = mod.claimManager().getPlayerData(attackingPlayer.uniqueId());
                if (!data.isIgnoringClaims()) {
                    Claim claim = mod.claimManager().getClaimAt(victim.location());
                    if (claim != null) {
                        String denial = claim.checkPermission(attackingPlayer.uniqueId(), ClaimPermission.BUILD);
                        if (denial != null) {
                            event.setCancelled(true);
                            attackingPlayer.sendMessage(String.format(Messages.NO_BUILD_PERMISSION,
                                    mod.ownerName(claim)), Player.MessageType.ERROR);
                        }
                    }
                }
            } else {
                // Non-player damage to these entities in claims is blocked
                Claim claim = mod.claimManager().getClaimAt(victim.location());
                if (claim != null) {
                    event.setCancelled(true);
                }
            }
            return;
        }

        // ---- Creature protection ----
        if (victim.type().isLiving() && !victim.type().isPlayer()) {
            boolean shouldProtect = shouldProtectEntity(victim);

            if (shouldProtect && attackingPlayer != null) {
                PlayerData data = mod.claimManager().getPlayerData(attackingPlayer.uniqueId());
                if (!data.isIgnoringClaims()) {
                    Claim claim = mod.claimManager().getClaimAt(victim.location());
                    if (claim != null) {
                        ClaimPermission required = ClaimPermission.CONTAINER;
                        // Tamed animals: owner can always damage their own pets
                        if (victim instanceof TameableEntity tamed && tamed.isTamed()) {
                            if (attackingPlayer.uniqueId().equals(tamed.ownerUniqueId())) {
                                return; // Owner can damage own pet
                            }
                        }
                        String denial = claim.checkPermission(attackingPlayer.uniqueId(), required);
                        if (denial != null) {
                            event.setCancelled(true);
                            attackingPlayer.sendMessage(String.format(Messages.NO_CONTAINER_PERMISSION,
                                    mod.ownerName(claim)), Player.MessageType.ERROR);
                        }
                    }
                }
            }
        }

        // ---- PvP (handled in PvPProtectionHandler) ----
    }

    // ---- Hanging Entity Break ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (event.cause() == HangingBreakEvent.RemoveCause.PHYSICS) {
            Claim claim = mod.claimManager().getClaimAt(event.entity().location());
            if (claim != null) {
                event.setCancelled(true);
            }
        }
    }

    // ---- Hanging Entity Place ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.player();
        if (player == null) return;

        PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());
        if (data.isIgnoringClaims()) return;

        Claim claim = mod.claimManager().getClaimAt(event.entity().location());
        if (claim != null) {
            String denial = claim.checkPermission(player.uniqueId(), ClaimPermission.BUILD);
            if (denial != null) {
                event.setCancelled(true);
                player.sendMessage(String.format(Messages.NO_BUILD_PERMISSION, mod.ownerName(claim)),
                        Player.MessageType.ERROR);
            }
        }
    }

    // ---- Entity Explosion ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        String worldName = event.entity().world().name();
        ClaimsMode mode = mod.getClaimsMode(worldName);

        Iterator<net.alloymc.api.block.Block> it = event.affectedBlocks().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            Claim claim = mod.claimManager().getClaimAt(block.location());

            if (mode == ClaimsMode.CREATIVE) {
                it.remove();
                continue;
            }

            if (claim != null) {
                if (config().blockClaimExplosions && !claim.areExplosivesAllowed()) {
                    it.remove();
                    continue;
                }
            }

            // Surface creeper/TNT protection
            if (claim == null) {
                boolean isCreeper = event.entity().type() == EntityType.CREEPER;
                if (isCreeper && config().blockSurfaceCreeperExplosions) {
                    if (block.y() > block.world().seaLevel() - 5) {
                        it.remove();
                        continue;
                    }
                }
                if (!isCreeper && config().blockSurfaceOtherExplosions) {
                    if (block.y() > block.world().seaLevel() - 5) {
                        it.remove();
                    }
                }
            }
        }
    }

    // ---- Entity Spawn ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        // In creative worlds, block natural mob spawning in wilderness
        ClaimsMode mode = mod.getClaimsMode(event.entity().world().name());
        if (mode != ClaimsMode.CREATIVE) return;

        if (event.reason() == EntitySpawnEvent.SpawnReason.NATURAL
                || event.reason() == EntitySpawnEvent.SpawnReason.SPAWNER) {
            Claim claim = mod.claimManager().getClaimAt(event.entity().location());
            if (claim == null) {
                event.setCancelled(true);
            }
        }
    }

    // ---- Vehicle Damage ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        Entity vehicle = event.entity();
        Entity attacker = event.attacker();

        if (attacker instanceof Player player) {
            PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());
            if (!data.isIgnoringClaims()) {
                Claim claim = mod.claimManager().getClaimAt(vehicle.location());
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
    }

    private boolean shouldProtectEntity(Entity entity) {
        EntityType type = entity.type();
        if (type == EntityType.HORSE) return config().claimsProtectHorses;
        if (type == EntityType.DONKEY || type == EntityType.MULE) return config().claimsProtectDonkeys;
        if (type == EntityType.LLAMA) return config().claimsProtectLlamas;
        if (type == EntityType.VILLAGER || type == EntityType.WANDERING_TRADER) return config().claimsProtectCreatures;
        if (type.isPassive()) return config().claimsProtectCreatures;
        return false;
    }
}
