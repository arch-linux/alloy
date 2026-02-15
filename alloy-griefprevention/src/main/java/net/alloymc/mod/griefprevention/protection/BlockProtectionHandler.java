package net.alloymc.mod.griefprevention.protection;

import net.alloymc.api.block.Block;
import net.alloymc.api.entity.Player;
import net.alloymc.api.event.EventHandler;
import net.alloymc.api.event.EventPriority;
import net.alloymc.api.event.Listener;
import net.alloymc.api.event.block.BlockBreakEvent;
import net.alloymc.api.event.block.BlockBurnEvent;
import net.alloymc.api.event.block.BlockDispenseEvent;
import net.alloymc.api.event.block.BlockExplodeEvent;
import net.alloymc.api.event.block.BlockFormEvent;
import net.alloymc.api.event.block.BlockFromToEvent;
import net.alloymc.api.event.block.BlockIgniteEvent;
import net.alloymc.api.event.block.BlockPistonEvent;
import net.alloymc.api.event.block.BlockPlaceEvent;
import net.alloymc.api.event.block.BlockSpreadEvent;
import net.alloymc.api.event.block.SignChangeEvent;
import net.alloymc.api.event.block.StructureGrowEvent;
import net.alloymc.api.inventory.Material;
import net.alloymc.api.world.Location;
import net.alloymc.mod.griefprevention.GriefPreventionMod;
import net.alloymc.mod.griefprevention.claim.Claim;
import net.alloymc.mod.griefprevention.claim.ClaimPermission;
import net.alloymc.mod.griefprevention.claim.ClaimsMode;
import net.alloymc.mod.griefprevention.config.GriefPreventionConfig;
import net.alloymc.mod.griefprevention.message.Messages;
import net.alloymc.mod.griefprevention.player.PlayerData;

import java.util.Iterator;

/**
 * Handles all block-related protection events.
 */
public class BlockProtectionHandler implements Listener {

    private final GriefPreventionMod mod;

    public BlockProtectionHandler(GriefPreventionMod mod) {
        this.mod = mod;
    }

    private GriefPreventionConfig config() { return mod.config(); }

    // ---- Block Break ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.player();
        PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());

        if (data.isIgnoringClaims()) return;

        Claim claim = mod.claimManager().getClaimAt(event.block().location());
        if (claim == null) return;

        String denial = claim.checkPermission(player.uniqueId(), ClaimPermission.BUILD);
        if (denial != null) {
            event.setCancelled(true);
            String ownerName = mod.ownerName(claim);
            player.sendMessage(String.format(Messages.NO_BUILD_PERMISSION, ownerName), Player.MessageType.ERROR);
        }
    }

    // ---- Block Place ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.player();
        PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());

        if (data.isIgnoringClaims()) return;

        Block block = event.block();
        Claim claim = mod.claimManager().getClaimAt(block.location());

        // Auto-claim for new players placing chests
        if (claim == null && block.type() == Material.CHEST && config().claimsAutoClaimRadius >= 0) {
            if (mod.claimManager().getClaimsForPlayer(player.uniqueId()).isEmpty()) {
                int radius = config().claimsAutoClaimRadius;
                if (radius > 0) {
                    int x = block.x();
                    int z = block.z();
                    int y = block.y() - config().claimsExtendIntoGround;
                    mod.claimManager().createClaim(
                            player.uniqueId(), block.world().name(),
                            x - radius, y, z - radius,
                            x + radius, block.world().maxHeight(), z + radius,
                            null
                    );
                    player.sendMessage(Messages.CLAIM_CREATED, Player.MessageType.SUCCESS);
                }
                return;
            }
        }

        if (claim == null) return;

        String denial = claim.checkPermission(player.uniqueId(), ClaimPermission.BUILD);
        if (denial != null) {
            event.setCancelled(true);
            String ownerName = mod.ownerName(claim);
            player.sendMessage(String.format(Messages.NO_BUILD_PERMISSION, ownerName), Player.MessageType.ERROR);
        }
    }

    // ---- Sign Change ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        Player player = event.player();
        PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());
        if (data.isIgnoringClaims()) return;

        Claim claim = mod.claimManager().getClaimAt(event.block().location());
        if (claim == null) return;

        String denial = claim.checkPermission(player.uniqueId(), ClaimPermission.BUILD);
        if (denial != null) {
            event.setCancelled(true);
            player.sendMessage(String.format(Messages.NO_BUILD_PERMISSION, mod.ownerName(claim)), Player.MessageType.ERROR);
        }
    }

    // ---- Piston ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPiston(BlockPistonEvent event) {
        if (config().pistonMode == GriefPreventionConfig.PistonMode.IGNORED) return;

        Block pistonBlock = event.block();
        Claim pistonClaim = mod.claimManager().getClaimAt(pistonBlock.location());

        for (Block moved : event.movedBlocks()) {
            Location destination = moved.location().add(
                    event.direction().offsetX(),
                    event.direction().offsetY(),
                    event.direction().offsetZ()
            );
            Claim destClaim = mod.claimManager().getClaimAt(destination);

            // Block movement across claim boundaries
            if (pistonClaim != destClaim) {
                // If either is a claim and they're different, block it
                if (pistonClaim != null || destClaim != null) {
                    event.setCancelled(true);
                    return;
                }
            }

            // In CLAIMS_ONLY mode, also check source blocks
            if (config().pistonMode == GriefPreventionConfig.PistonMode.CLAIMS_ONLY) {
                Claim sourceClaim = mod.claimManager().getClaimAt(moved.location());
                if (sourceClaim != destClaim) {
                    if (sourceClaim != null || destClaim != null) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    // ---- Fire / Burning ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        // Block lightning ignition in claims
        if (event.cause() == BlockIgniteEvent.IgniteCause.LIGHTNING) {
            Claim claim = mod.claimManager().getClaimAt(event.block().location());
            if (claim != null) {
                event.setCancelled(true);
                return;
            }
        }

        // Global fire spread setting
        if (event.cause() == BlockIgniteEvent.IgniteCause.SPREAD && !config().fireSpreads) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (!config().fireDestroys) {
            event.setCancelled(true);
            return;
        }

        Claim claim = mod.claimManager().getClaimAt(event.block().location());
        if (claim != null && !config().claimsFireDamages) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (event.source().type() != Material.FIRE) return;

        if (!config().fireSpreads) {
            event.setCancelled(true);
            return;
        }

        Claim sourceClaim = mod.claimManager().getClaimAt(event.source().location());
        Claim destClaim = mod.claimManager().getClaimAt(event.block().location());

        // Don't allow fire to spread into claims from outside
        if (destClaim != null && sourceClaim == null) {
            event.setCancelled(true);
            return;
        }

        // Don't allow fire to spread between different owners' claims
        if (sourceClaim != null && destClaim != null) {
            if (sourceClaim.ownerID() != null && destClaim.ownerID() != null
                    && !sourceClaim.ownerID().equals(destClaim.ownerID())) {
                event.setCancelled(true);
            }
        }
    }

    // ---- Liquid Flow ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (!event.fromBlock().isLiquid()) return;

        Claim sourceClaim = mod.claimManager().getClaimAt(event.fromBlock().location());
        Claim destClaim = mod.claimManager().getClaimAt(event.toBlock().location());

        // Wilderness -> Claim: blocked
        if (sourceClaim == null && destClaim != null) {
            event.setCancelled(true);
            return;
        }

        // Claim A -> Claim B (different owners): blocked
        if (sourceClaim != null && destClaim != null && sourceClaim != destClaim) {
            if (sourceClaim.ownerID() == null || destClaim.ownerID() == null
                    || !sourceClaim.ownerID().equals(destClaim.ownerID())) {
                event.setCancelled(true);
            }
        }
    }

    // ---- Block Formation ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        // In creative worlds, prevent cobblestone/obsidian formation in wilderness
        ClaimsMode mode = mod.getClaimsMode(event.block().world().name());
        if (mode == ClaimsMode.CREATIVE) {
            Material newType = event.newType();
            if (newType == Material.COBBLESTONE || newType == Material.OBSIDIAN
                    || newType == Material.LAVA || newType == Material.WATER) {
                Claim claim = mod.claimManager().getClaimAt(event.block().location());
                if (claim == null) {
                    event.setCancelled(true);
                }
            }
        }
    }

    // ---- Dispenser ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        Material itemType = event.item().type();
        if (itemType != Material.WATER_BUCKET && itemType != Material.LAVA_BUCKET) return;

        // Prevent dispensing fluids across claim boundaries
        // The dispenser is at event.block(), the fluid will go to adjacent block
        // For simplicity, just check the dispenser's claim
        Claim claim = mod.claimManager().getClaimAt(event.block().location());
        ClaimsMode mode = mod.getClaimsMode(event.block().world().name());

        if (mode == ClaimsMode.CREATIVE && claim == null) {
            event.setCancelled(true);
        }
    }

    // ---- Tree Growth ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTreeGrow(StructureGrowEvent event) {
        if (!config().limitTreeGrowth) return;

        Claim sourceClaim = mod.claimManager().getClaimAt(event.block().location());

        for (var pos : event.affectedPositions()) {
            Location loc = new Location(event.block().world(), pos.x(), pos.y(), pos.z());
            Claim blockClaim = mod.claimManager().getClaimAt(loc);

            if (blockClaim != sourceClaim) {
                if (blockClaim != null || sourceClaim != null) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    // ---- Explosions ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.affectedBlocks().iterator(), event.block().world().name(),
                event.block().y());
    }

    private void handleExplosion(Iterator<Block> blocks, String worldName, int sourceY) {
        ClaimsMode mode = mod.getClaimsMode(worldName);

        while (blocks.hasNext()) {
            Block block = blocks.next();
            Claim claim = mod.claimManager().getClaimAt(block.location());

            if (mode == ClaimsMode.CREATIVE) {
                // No explosions in creative
                blocks.remove();
                continue;
            }

            if (claim != null) {
                if (config().blockClaimExplosions && !claim.areExplosivesAllowed()) {
                    blocks.remove();
                    continue;
                }
            }

            // Surface explosion protection
            if (claim == null && config().blockSurfaceOtherExplosions) {
                if (block.y() > block.world().seaLevel() - 5) {
                    blocks.remove();
                }
            }
        }
    }
}
