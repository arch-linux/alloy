package net.alloymc.mod.griefprevention.claim;

/**
 * Per-world claim mode. Determines how the claim system behaves in each world.
 */
public enum ClaimsMode {

    /**
     * Standard survival claim mode. Players earn claim blocks over time
     * and create claims with a golden shovel.
     */
    SURVIVAL,

    /**
     * Creative mode claims. All players can claim, no block cost,
     * stricter protection rules (no building in wilderness).
     */
    CREATIVE,

    /**
     * Claims disabled in this world.
     */
    DISABLED
}
