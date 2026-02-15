package net.alloymc.mod.griefprevention.visualization;

import net.alloymc.api.AlloyAPI;
import net.alloymc.api.entity.Player;
import net.alloymc.api.inventory.Material;
import net.alloymc.api.world.Location;
import net.alloymc.mod.griefprevention.claim.Claim;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders claim boundaries to a player using fake block changes.
 * Blocks revert automatically after a timeout.
 */
public final class ClaimVisualization {

    private ClaimVisualization() {}

    /**
     * Show a claim's boundaries to a player.
     */
    public static void show(Player player, Claim claim, VisualizationType type) {
        List<Location> elements = buildElements(claim, player.location(), type);
        for (Location loc : elements) {
            boolean isCorner = isCorner(claim, (int) loc.x(), (int) loc.z());
            Material mat = isCorner ? type.cornerMaterial() : type.edgeMaterial();
            player.sendBlockChange(loc, mat);
        }

        // Schedule revert after 60 seconds (1200 ticks)
        AlloyAPI.scheduler().runTaskLater(() -> revert(player, elements), 1200L);
    }

    /**
     * Show multiple claims (e.g., nearby claims) to a player.
     */
    public static void showAll(Player player, List<Claim> claims, VisualizationType type) {
        for (Claim claim : claims) {
            show(player, claim, type);
        }
    }

    /**
     * Revert fake blocks back to actual blocks.
     */
    public static void revert(Player player, List<Location> locations) {
        if (!player.isOnline()) return;
        for (Location loc : locations) {
            Material actual = loc.world().blockAt(loc).type();
            player.sendBlockChange(loc, actual);
        }
    }

    private static List<Location> buildElements(Claim claim, Location playerLoc, VisualizationType type) {
        List<Location> elements = new ArrayList<>();

        int minX = claim.lesserX();
        int maxX = claim.greaterX();
        int minZ = claim.lesserZ();
        int maxZ = claim.greaterZ();

        // Step size for edges (every other block to avoid overwhelming the client)
        int step = 10;

        // North edge (minZ)
        for (int x = minX; x <= maxX; x += step) {
            elements.add(surfaceAt(playerLoc, x, minZ));
        }

        // South edge (maxZ)
        for (int x = minX; x <= maxX; x += step) {
            elements.add(surfaceAt(playerLoc, x, maxZ));
        }

        // West edge (minX)
        for (int z = minZ; z <= maxZ; z += step) {
            elements.add(surfaceAt(playerLoc, minX, z));
        }

        // East edge (maxX)
        for (int z = minZ; z <= maxZ; z += step) {
            elements.add(surfaceAt(playerLoc, maxX, z));
        }

        // Always include corners
        elements.add(surfaceAt(playerLoc, minX, minZ));
        elements.add(surfaceAt(playerLoc, minX, maxZ));
        elements.add(surfaceAt(playerLoc, maxX, minZ));
        elements.add(surfaceAt(playerLoc, maxX, maxZ));

        return elements;
    }

    private static Location surfaceAt(Location reference, int x, int z) {
        // Use player Y as approximation for surface level
        int y = (int) Math.floor(reference.y());
        return new Location(reference.world(), x, y, z);
    }

    private static boolean isCorner(Claim claim, int x, int z) {
        return (x == claim.lesserX() || x == claim.greaterX())
                && (z == claim.lesserZ() || z == claim.greaterZ());
    }
}
