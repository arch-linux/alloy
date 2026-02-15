package net.alloymc.mod.griefprevention.claim;

/**
 * The mode a player's claim tool (golden shovel) is operating in.
 */
public enum ShovelMode {

    /**
     * Create and resize normal player claims.
     */
    BASIC,

    /**
     * Create admin claims (no owner, no block cost).
     */
    ADMIN,

    /**
     * Create subdivisions within an existing claim.
     */
    SUBDIVIDE
}
